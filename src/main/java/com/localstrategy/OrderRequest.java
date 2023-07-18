package com.localstrategy;

import com.localstrategy.util.enums.OrderSide;
import com.localstrategy.util.enums.OrderType;
import com.localstrategy.util.types.SingleTransaction;

import java.util.ArrayList;

public class OrderRequest {

    public int totalProgrammaticOrderLimit;
    public int lastPositionId = 0;

    private double positionSize;
    private double requiredMargin;

    private final ArrayList<Position> pendingPositions;
    private final ArrayList<Position> newPositions;
    private final UserAssets userAssets;
    private final double risk;
    private final TierManager riskManager;
    private final int slippagePct;

    public double borrowedAmount;

    //TODO: Add stop limit orders
    public OrderRequest(
        ArrayList<Position> pendingPositions,
        ArrayList<Position> newPositions,
        TierManager riskManager,
        UserAssets userAssets,
        double risk, 
        int totalOrderLimit,
        int slippagePct){

            this.pendingPositions = pendingPositions;
            this.newPositions = newPositions;
            this.riskManager = riskManager;
            this.userAssets = userAssets;
            this.risk = risk;
            this.totalProgrammaticOrderLimit = totalOrderLimit;
            this.slippagePct = slippagePct;
    }

    public Position newMarketPosition(SingleTransaction transaction, double stopLossPrice){
        if(calculatePositionParameters(transaction.getPrice(), stopLossPrice)){
            Position position = new Position(
                transaction.getPrice(), 
                stopLossPrice, 
                positionSize, 
                OrderType.MARKET,
                requiredMargin, 
                borrowedAmount, 
                ++lastPositionId, 
                transaction.getTimestamp()
            );
            pendingPositions.add(position);
            return position;
        }
        return null;
    }

    public Position newLimitPosition(double entryPrice, double stopLossPrice, SingleTransaction transaction){
        if(calculatePositionParameters(entryPrice, stopLossPrice)){
            Position position = new Position(
                entryPrice, 
                stopLossPrice, 
                positionSize, 
                OrderType.LIMIT, 
                requiredMargin, 
                borrowedAmount, 
                ++lastPositionId, 
                transaction.getTimestamp()
            );
            pendingPositions.add(position);
            return position;
        }
        return null;
    }

    //FIXME: This still calculates amount to borrow even if the position size goes over. It borrows and then takes everything else from our portfolio. Binance doesn't support that with automatic borrowings
    //TODO: Add return statuses
    //TODO: Add condition when slippage crosses the stop-loss
    private boolean calculatePositionParameters(double entryPrice, double stopLossPrice){
        if (Math.abs(entryPrice - stopLossPrice) > 2) { //Minimum $2 difference between entry and stop.

            positionSize = userAssets.getFreeUSDT() * risk / 100 / Math.abs(entryPrice - stopLossPrice);

            double slippageLimitedPositionSize = Math.min(
                SlippageHandler.getMaximumOrderSize(entryPrice, Math.abs(entryPrice - stopLossPrice), slippagePct, (entryPrice > stopLossPrice ? OrderSide.BUY : OrderSide.SELL)),
                SlippageHandler.getMaximumOrderSize(stopLossPrice, Math.abs(entryPrice - stopLossPrice), slippagePct, (entryPrice > stopLossPrice ? OrderSide.SELL : OrderSide.BUY))
            );
        
            positionSize = Math.min(152, Math.max(0.00001, 
                Math.max(10 / entryPrice, Math.min(
                    slippageLimitedPositionSize, positionSize))));

            double borrowedUSDT = userAssets.getTotalBorrowedUSDT();
            double borrowedBTC = userAssets.getTotalBorrowedBTC();

            double amountToBorrow = positionSize;

            if(entryPrice > stopLossPrice){
                amountToBorrow *= entryPrice;

                if(borrowedUSDT + amountToBorrow > 900000){
                    return false;
                }

                riskManager.checkAndUpdateTier(borrowedUSDT + amountToBorrow, borrowedBTC);
            }
            else {
                if(borrowedBTC + amountToBorrow > 72){
                    return false;
                } 
                
                riskManager.checkAndUpdateTier(borrowedUSDT, borrowedBTC + amountToBorrow);
            }

            requiredMargin = positionSize * entryPrice / riskManager.getCurrentLeverage();

            return userAssets.getFreeUSDT() > requiredMargin &&
                    pendingPositions.size()
                            + newPositions.size() < totalProgrammaticOrderLimit;
        }
        return false;
    }
}
