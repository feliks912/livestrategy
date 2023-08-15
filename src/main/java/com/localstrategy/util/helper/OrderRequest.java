package com.localstrategy.util.helper;

import com.localstrategy.util.enums.OrderSide;
import com.localstrategy.util.enums.OrderStatus;
import com.localstrategy.util.enums.OrderType;
import com.localstrategy.util.enums.PositionGroup;
import com.localstrategy.util.types.Position;
import com.localstrategy.util.types.SingleTransaction;
import com.localstrategy.util.types.UserAssets;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;

public class OrderRequest {

    public int totalProgrammaticOrderLimit;

    private final ArrayList<Position> activePositions;
    private UserAssets userAssets;
    private final double risk;
    private final TierManager riskManager;
    private final BigDecimal slippagePct;

    private BigDecimal positionSize;
    private BigDecimal amountToBorrow;
    private BigDecimal requiredMargin;

    //TODO: Add stop limit orders
    public OrderRequest(
        ArrayList<Position> activePositions,
        TierManager riskManager,
        double risk, 
        int totalOrderLimit,
        int slippagePct){

            this.activePositions = activePositions;
            this.riskManager = riskManager;
            this.risk = risk;
            this.totalProgrammaticOrderLimit = totalOrderLimit;
            this.slippagePct = BigDecimal.valueOf(slippagePct);
    }

    public Position newMarketPosition(SingleTransaction transaction, BigDecimal stopLossPrice){
        if(calculatePositionParameters(BigDecimal.valueOf(transaction.price()), stopLossPrice, transaction)){
            Position position = new Position(
                BigDecimal.valueOf(transaction.price()),
                stopLossPrice, 
                positionSize, 
                OrderType.MARKET,
                requiredMargin, 
                amountToBorrow,
                transaction.timestamp()
            );
            position.setGroup(PositionGroup.PENDING);
            activePositions.add(position);
            return position;
        }
        return null;
    }

    public Position newLimitPosition(BigDecimal entryPrice, BigDecimal stopLossPrice, SingleTransaction transaction){
        if(calculatePositionParameters(entryPrice, stopLossPrice, transaction)){
            Position position = new Position(
                entryPrice, 
                stopLossPrice, 
                positionSize, 
                OrderType.LIMIT, 
                requiredMargin, 
                amountToBorrow,
                transaction.timestamp()
            );
            position.setGroup(PositionGroup.PENDING);
            activePositions.add(position);
            return position;
        }
        return null;
    }


    //FIXME: This still calculates amount to borrow even if the position size goes over. It borrows and then takes everything else from our portfolio. BinanceHandler doesn't support that with automatic borrowings
    //TODO: Add return statuses
    //TODO: Add condition when slippage crosses the stop-loss
    private boolean calculatePositionParameters(BigDecimal entryPrice, BigDecimal stopLossPrice, SingleTransaction transaction){

        BigDecimal absPriceDiff = entryPrice.subtract(stopLossPrice).abs();

        if (absPriceDiff.compareTo(BigDecimal.valueOf(2)) > 0 && transaction != null) { //Minimum $2 difference between entry and stop.

            BigDecimal totalFreeUsdt = userAssets.getFreeUSDT()
                    .add(userAssets.getLockedUSDT())
                    .subtract(userAssets.getTotalBorrowedUSDT())
                    .subtract(userAssets.getRemainingInterestUSDT());

            // USDT is locked when borrowing USDT or BTC
            // FreeUSDT is got when we sell borrowed BTC or when we borrow USDT


            positionSize = totalFreeUsdt.multiply(BigDecimal.valueOf(risk))
                    .divide(BigDecimal.valueOf(100))
                    .divide(absPriceDiff, 6, RoundingMode.HALF_UP);

            BigDecimal slippageLimitedPositionSize = SlippageHandler.getMaximumOrderSize(entryPrice, absPriceDiff, slippagePct, (entryPrice.compareTo(stopLossPrice) > 0 ? OrderSide.BUY : OrderSide.SELL))
                    .min(SlippageHandler.getMaximumOrderSize(stopLossPrice, absPriceDiff, slippagePct, (entryPrice.compareTo(stopLossPrice) > 0 ? OrderSide.SELL : OrderSide.BUY)));

            positionSize = slippageLimitedPositionSize
                    .min(positionSize)
                    .max(BigDecimal.valueOf(10).divide(entryPrice, 8, RoundingMode.HALF_UP))
                    .max(BigDecimal.valueOf(0.00001))
                    .min(BigDecimal.valueOf(152));

            BigDecimal borrowedUSDT = userAssets.getTotalBorrowedUSDT();
            BigDecimal borrowedBTC = userAssets.getTotalBorrowedBTC();

            amountToBorrow = positionSize.setScale(8, RoundingMode.HALF_UP);

            if(entryPrice.compareTo(stopLossPrice) > 0){
                amountToBorrow = amountToBorrow.multiply(entryPrice);

                if(borrowedUSDT.add(amountToBorrow).compareTo(TierManager.MAX_BORROW_USDT) > 0){
                    return false;
                }

                riskManager.checkAndUpdateTier(borrowedUSDT.add(amountToBorrow).doubleValue(), borrowedBTC.doubleValue());
            }
            else {
                if(borrowedBTC.add(amountToBorrow).compareTo(TierManager.MAX_BORROW_BTC) > 0){
                    return false;
                } 
                
                riskManager.checkAndUpdateTier(borrowedUSDT.doubleValue(), borrowedBTC.add(amountToBorrow).doubleValue());
            }

            requiredMargin = positionSize.multiply(entryPrice).divide(BigDecimal.valueOf(riskManager.getCurrentLeverage()), 8, RoundingMode.HALF_UP);

            int programmaticCounter = 0;
            for(Position position : activePositions){
                switch (position.getGroup()){
                    case PENDING -> {
                        if(position.getEntryOrder().getType().equals(OrderType.LIMIT) && position.getEntryOrder().getStatus() == OrderStatus.NEW){
                            programmaticCounter++;
                        }
                    }
                    case NEW -> {
                        if(position.getEntryOrder().getType().equals(OrderType.LIMIT) && position.getEntryOrder().getStatus() == OrderStatus.NEW){
                            programmaticCounter++;
                        }
                        if(position.isStopLossActive()){
                            programmaticCounter++;
                        }
                    }
                    case FILLED -> {
                        if(position.isStopLossActive()){
                            programmaticCounter++;
                        }
                    }
                }
            }

            return userAssets.getFreeUSDT().compareTo(requiredMargin) > 0 &&
                    programmaticCounter < totalProgrammaticOrderLimit;
        }
        return false;
    }

    public void setUserAssets(UserAssets assets){
        this.userAssets = assets;
    }
}
