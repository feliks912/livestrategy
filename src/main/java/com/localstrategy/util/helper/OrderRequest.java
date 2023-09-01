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

    private static double maxPositionSize = 0;
    private static double maxPositionSizeFiltered = 0;

    private final ArrayList<Position> activePositions;
    private UserAssets userAssets;
    private final double risk;
    private final TierManager riskManager;
    private final BigDecimal slippagePct;

    private BigDecimal positionSize;
    private BigDecimal amountToBorrow;
    private BigDecimal requiredMargin;

    private BigDecimal stopLossPrice;

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

    public Position newMarketPosition(SingleTransaction transaction, BigDecimal stopLossPrice, boolean compensateStopLoss){
        this.stopLossPrice = stopLossPrice;
        if(calculatePositionParameters(BigDecimal.valueOf(transaction.price()), stopLossPrice, transaction, compensateStopLoss)){
            Position position = new Position(
                BigDecimal.valueOf(transaction.price()),
                this.stopLossPrice,
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

    public Position newLimitPosition(BigDecimal entryPrice, BigDecimal stopLossPrice, SingleTransaction transaction, boolean compensateStopLoss){
        this.stopLossPrice = stopLossPrice;
        if(calculatePositionParameters(entryPrice, stopLossPrice, transaction, compensateStopLoss)){
            Position position = new Position(
                entryPrice, 
                this.stopLossPrice,
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
    private boolean calculatePositionParameters(BigDecimal entryPrice, BigDecimal stopLossPrice, SingleTransaction transaction, boolean compensateStopLoss){

        BigDecimal absPriceDiff = entryPrice.subtract(stopLossPrice).abs();

        if (absPriceDiff.compareTo(BigDecimal.valueOf(2)) > 0 && transaction != null) { //Minimum $2 difference between entry and stop.

            BigDecimal totalFreeUsdt = userAssets.getFreeUSDT()
                    .add(userAssets.getLockedUSDT())
                    .subtract(userAssets.getTotalBorrowedUSDT())
                    .subtract(userAssets.getRemainingInterestUSDT());

            // USDT is locked when borrowing USDT or BTC
            // FreeUSDT is got when we sell borrowed BTC or when we borrow USDT

            positionSize = totalFreeUsdt.multiply(BigDecimal.valueOf(risk))
                    .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
                    .divide(absPriceDiff, 6, RoundingMode.HALF_UP);

//            positionSize = totalFreeUsdt.multiply(BigDecimal.valueOf(risk))
//                    .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
//                    .divide(BigDecimal.ONE
//                            .add(BigDecimal.valueOf(0.00016)
//                                    .multiply(stopLossPrice.divide(entryPrice, 10, RoundingMode.HALF_UP))
//                                    .multiply(BigDecimal.valueOf(riskManager.getCurrentLeverage()))), 10, RoundingMode.HALF_UP)
//                    .divide(absPriceDiff, 6, RoundingMode.HALF_UP);

            if(positionSize.doubleValue() > maxPositionSize) {
                maxPositionSize = positionSize.doubleValue();
                System.out.println("New max position value: " + maxPositionSize);
            }

            BigDecimal slippageLimitedPositionSize = SlippageHandler.getMaximumOrderSizeFromBook(entryPrice, absPriceDiff, slippagePct, (entryPrice.compareTo(stopLossPrice) > 0 ? OrderSide.BUY : OrderSide.SELL))
                    .min(SlippageHandler.getMaximumOrderSizeFromBook(stopLossPrice, absPriceDiff, slippagePct, (entryPrice.compareTo(stopLossPrice) > 0 ? OrderSide.SELL : OrderSide.BUY)));

//            BigDecimal slippageLimitedPositionSize = SlippageHandler.getMaximumOrderSize(entryPrice, absPriceDiff, slippagePct, (entryPrice.compareTo(stopLossPrice) > 0 ? OrderSide.BUY : OrderSide.SELL))
//                    .min(SlippageHandler.getMaximumOrderSize(stopLossPrice, absPriceDiff, slippagePct, (entryPrice.compareTo(stopLossPrice) > 0 ? OrderSide.SELL : OrderSide.BUY)));

            positionSize = slippageLimitedPositionSize
                    .min(positionSize)
                    .max(BigDecimal.valueOf(10).divide(entryPrice, 8, RoundingMode.HALF_UP))
                    .max(BigDecimal.valueOf(0.00001))
                    .min(BigDecimal.valueOf(152));

            if(positionSize.doubleValue() > maxPositionSizeFiltered){
                maxPositionSizeFiltered = positionSize.doubleValue();
                System.out.println("New max filtered position size: " + maxPositionSizeFiltered);
            }

            //Compensate for stoploss slippage
            if(compensateStopLoss){
                BigDecimal fillPrice = SlippageHandler.getSlippageFillPrice(
                        stopLossPrice,
                        positionSize,
                        entryPrice.compareTo(stopLossPrice) > 0 ? OrderSide.SELL : OrderSide.BUY
                );

                if(entryPrice.compareTo(stopLossPrice) > 0){
                    stopLossPrice = stopLossPrice.add(stopLossPrice.subtract(fillPrice).abs());
                } else {
                    stopLossPrice = stopLossPrice.subtract(fillPrice.subtract(stopLossPrice).abs());
                }
            }

            this.stopLossPrice = stopLossPrice;

            BigDecimal borrowedUSDT = userAssets.getTotalBorrowedUSDT();
            BigDecimal borrowedBTC = userAssets.getTotalBorrowedBTC();

            amountToBorrow = positionSize.setScale(8, RoundingMode.HALF_UP);

            double totalAssetValue;

            double totalBorrowAndInterestValue;

            if(entryPrice.compareTo(stopLossPrice) > 0){ //Buy
                amountToBorrow = amountToBorrow.multiply(entryPrice);

                if(borrowedUSDT.add(amountToBorrow).compareTo(TierManager.MAX_BORROW_USDT) > 0){
                    //System.out.println("No bueno borrow USDT.");
                    return false;
                }

                //FIXME: Tiss a bit hacky - discard position if we wouldn't borrow
                if(amountToBorrow.compareTo(userAssets.getFreeUSDT()) <= 0 && activePositions.size() > 1){
                    return false;
                }

                totalAssetValue = userAssets.getFreeUSDT().add(amountToBorrow).add(userAssets.getLockedUSDT())
                        .add(userAssets.getFreeBTC().add(userAssets.getLockedBTC()).multiply(entryPrice)).doubleValue();

                totalBorrowAndInterestValue = userAssets.getTotalBorrowedUSDT().add(amountToBorrow).add(userAssets.getTotalBorrowedBTC().multiply(entryPrice))
                        .add(userAssets.getRemainingInterestUSDT()).add(userAssets.getRemainingInterestBTC().multiply(entryPrice)).doubleValue();

                riskManager.checkAndUpdateTier(borrowedUSDT.add(amountToBorrow).doubleValue(), borrowedBTC.doubleValue());
            }
            else {
                if(borrowedBTC.add(amountToBorrow).compareTo(TierManager.MAX_BORROW_BTC) > 0){
                    //System.out.println("No bueno borrow BTC.");
                    return false;
                }

                if(amountToBorrow.compareTo(userAssets.getFreeBTC()) <= 0 && activePositions.size() > 1){
                    return false;
                }

                totalAssetValue = userAssets.getFreeUSDT().add(userAssets.getLockedUSDT())
                        .add(userAssets.getFreeBTC().add(userAssets.getLockedBTC().add(amountToBorrow)).multiply(entryPrice)).doubleValue();

                totalBorrowAndInterestValue = userAssets.getTotalBorrowedUSDT().add((userAssets.getTotalBorrowedBTC().add(amountToBorrow)).multiply(entryPrice))
                        .add(userAssets.getRemainingInterestUSDT()).add(userAssets.getRemainingInterestBTC().multiply(entryPrice)).doubleValue();

                riskManager.checkAndUpdateTier(borrowedUSDT.doubleValue(), borrowedBTC.add(amountToBorrow).doubleValue());
            }

            double marginLevel;

            if (totalBorrowAndInterestValue <= 0) {
                marginLevel = 999;
            } else {
                marginLevel = Math.min(999, totalAssetValue / totalBorrowAndInterestValue);
            }

            if (marginLevel <= 1.1) {
                return false; // Just discard the position if our margin level would be less than 1.1 after execution
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

            if(userAssets.getFreeUSDT().compareTo(requiredMargin) > 0 &&
                    programmaticCounter < totalProgrammaticOrderLimit){
                return true;
            } else {
                System.out.println("Not - margin or prog counter");
            }
        }

        return false;
    }

    public void setUserAssets(UserAssets assets){
        this.userAssets = assets;
    }
}
