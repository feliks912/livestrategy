package com.localstrategy;

import java.util.ArrayList;

import com.binance.api.client.domain.OrderSide;

public class OrderMaker {

    public static void newOrder(
            ArrayList<Position> positions,
            int timeIndex,
            String orderType,
            double entryPrice,
            double stoplossPrice,
            double portfolio,
            double risk,
            double marginUsed,
            long openTimestamp,
            RiskManager riskManager
    ) {
        if (Math.abs(entryPrice - stoplossPrice) > 5) { //$5 difference between entry and stop required.

            double positionSize;
            double borrowedBTC;
            double borrowedUSDT;
            double requiredMargin;
            double borrowAmount;

            positionSize = (portfolio - marginUsed) * risk / 100 / Math.abs(entryPrice - stoplossPrice);

            

            //FIXME: Commented to simulate ideal environment.
            positionSize = Math.max(10 / entryPrice, positionSize);
            positionSize = Math.max(0.00001, Math.min(positionSize, 152));

            positionSize = Math.min(positionSize, riskManager.getMaximumOrderSize(entryPrice, Math.abs(entryPrice - stoplossPrice), 10, (entryPrice > stoplossPrice ? OrderSide.BUY : OrderSide.SELL)));

            positionSize = Math.min(positionSize, riskManager.getMaximumOrderSize(stoplossPrice, Math.abs(entryPrice - stoplossPrice), 10, (entryPrice > stoplossPrice ? OrderSide.SELL : OrderSide.BUY)));

            riskManager.calculateBorrows();
            borrowedBTC = riskManager.getTotalBTCBorrows();
            borrowedUSDT = riskManager.getTotalUSDTBorrows();

            borrowAmount = positionSize * riskManager.getBorrowRatio();

            if(entryPrice > stoplossPrice){
                borrowAmount *= entryPrice;

                if(borrowedUSDT + borrowAmount > 900000){
                    borrowAmount = 900000 - borrowedUSDT;
                    requiredMargin = positionSize * entryPrice - borrowAmount;
                } 
                else {
                    riskManager.checkAndUpdateTier(borrowedUSDT + borrowAmount, borrowedBTC);
                    borrowAmount = positionSize * entryPrice * riskManager.getBorrowRatio();
                    requiredMargin = positionSize * entryPrice / riskManager.getCurrentLeverage();
                }
            } 
            else {
                if(borrowedBTC + borrowAmount > 72){
                    borrowAmount = 72 - borrowedBTC;
                    requiredMargin = (positionSize - borrowAmount) * entryPrice;
                } 
                else {
                    riskManager.checkAndUpdateTier(borrowedUSDT, borrowedBTC + borrowAmount);
                    borrowAmount = positionSize * riskManager.getBorrowRatio();
                    requiredMargin = positionSize * entryPrice / riskManager.getCurrentLeverage();
                }
            }

            if (portfolio - marginUsed > requiredMargin && positions.size() < 4) {
                positions.add(
                        new Position(
                            StrategyExecutor.maxOrderId,
                            orderType,
                            entryPrice,
                            stoplossPrice,
                            positionSize,
                            requiredMargin,
                            timeIndex,
                            openTimestamp,
                            borrowAmount
                        )
                );
                StrategyExecutor.maxOrderId++;
            }
        }
    }
}
