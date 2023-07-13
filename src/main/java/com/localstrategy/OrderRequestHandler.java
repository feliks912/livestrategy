package com.localstrategy;

import java.util.ArrayList;

import com.localstrategy.util.enums.OrderSide;
import com.localstrategy.util.enums.OrderType;
import com.localstrategy.util.types.SingleTransaction;

public class OrderRequestHandler {

    public int totalOrderLimit;
    public int lastOrderId = 0;

    private double positionSize;
    private double requiredMargin;
    private double amountToBorrow;

    private ArrayList<Position> activePositions = new ArrayList<Position>();
    private ArrayList<Position> pendingPositions = new ArrayList<Position>();
    private AssetHandler portfolio;
    private double risk;
    private RiskManager riskManager;

    public double borrowedAmount;

    //TODO: Add stop limit orders
    public OrderRequestHandler(ArrayList<Position> pendingPositions, ArrayList<Position> activePositions, RiskManager riskManager, AssetHandler portfolio, double risk, int totalOrderLimit){
        this.pendingPositions = pendingPositions;
        this.activePositions = activePositions;
        this.riskManager = riskManager;
        this.portfolio = portfolio;
        this.risk = risk;
        this.totalOrderLimit = totalOrderLimit;
    }

    public void newMarketOrder(SingleTransaction transaction, double stopLossPrice){
        if(!calculateOrderParameters(transaction.getPrice(), stopLossPrice)){
            pendingPositions.add(
                new Position(transaction.getPrice(), stopLossPrice, positionSize, OrderType.MARKET, requiredMargin, amountToBorrow, ++lastOrderId)
            );
        }
    }

    public void newLimitOrder(double entryPrice, double stopLossPrice, SingleTransaction transaction){
        if(!calculateOrderParameters(entryPrice, stopLossPrice)){
            pendingPositions.add(
                new Position(transaction.getPrice(), stopLossPrice, positionSize, OrderType.LIMIT, requiredMargin, amountToBorrow, ++lastOrderId)
            );
        }
    } 

    private boolean calculateOrderParameters(double entryPrice, double stopLossPrice){
        if (Math.abs(entryPrice - stopLossPrice) > 2) { //$2 difference between entry and stop required.

            positionSize = portfolio.getFreeUSDT() * risk / 100 / Math.abs(entryPrice - stopLossPrice);

            double slippageLimitedPositionSize = Math.min(
                OrderBookHandler.getMaximumOrderSize(entryPrice, Math.abs(entryPrice - stopLossPrice), 10, (entryPrice > stopLossPrice ? OrderSide.BUY : OrderSide.SELL)),
                OrderBookHandler.getMaximumOrderSize(stopLossPrice, Math.abs(entryPrice - stopLossPrice), 10, (entryPrice > stopLossPrice ? OrderSide.SELL : OrderSide.BUY))
            );
        
            positionSize = Math.min(positionSize, slippageLimitedPositionSize);

            positionSize = Math.max(10 / entryPrice, positionSize);
            positionSize = Math.max(positionSize, 0.00001);
            //FIXME: Commented to simulate ideal environment.
            //positionSize = Math.max(0.00001, Math.min(positionSize, 152));

            double borrowedUSDT = portfolio.getTotalBorrowedUSDT();
            double borrowedBTC = portfolio.getTotalBorrowedBTC();

            amountToBorrow = positionSize;

            if(entryPrice > stopLossPrice){
                amountToBorrow *= entryPrice;

                if(borrowedUSDT + amountToBorrow > 900000){
                    amountToBorrow = 900000 - borrowedUSDT;
                    requiredMargin = positionSize * entryPrice - amountToBorrow;
                } 
                else {
                    riskManager.checkAndUpdateTier(borrowedUSDT + amountToBorrow, borrowedBTC);
                    amountToBorrow = positionSize * entryPrice;
                    requiredMargin = positionSize * entryPrice / riskManager.getCurrentLeverage();
                }
            } 
            else {
                if(borrowedBTC + amountToBorrow > 72){
                    amountToBorrow = 72 - borrowedBTC;
                    requiredMargin = (positionSize - amountToBorrow) * entryPrice;
                } 
                else {
                    riskManager.checkAndUpdateTier(borrowedUSDT, borrowedBTC + amountToBorrow);
                    amountToBorrow = positionSize;
                    requiredMargin = positionSize * entryPrice / riskManager.getCurrentLeverage();
                }
            }

            //FIXME: The required margin and active positions size must also be checked at the time of filling.
            if (portfolio.getFreeUSDT() > requiredMargin && activePositions.size() < 4){
                return false;
            }
            return true;
        }
        return true;
    }
}
