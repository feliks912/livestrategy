package com.localstrategy;

import java.util.ArrayList;

public class OrderBookHandler {
    
    private static double depthPct;
    private static double depthQty;

    public OrderBookHandler(double depthPct, double depthQty){
        OrderBookHandler.depthPct = depthPct;
        OrderBookHandler.depthQty = depthQty;
    }

    //Returns the average filling price given slippage and an orderbook model
    //TODO: Recheck calculations
    public double getSlippagePrice(double price, double positionSize, OrderSide orderSide) {
        return price * (1 + depthPct/100 /depthQty/Math.sqrt(2)*positionSize*(orderSide.equals(OrderSide.BUY) ? 1 : -1));
    }

    //Returns the maximum order side so slippage is a percentage of the delta between entry and stop
    public static double getMaximumOrderSize(double price, double priceDifference, double percentage, OrderSide orderSide){
        
        //FIXME: Refactor lol

        double fillingPrice;
        if(orderSide.equals(OrderSide.BUY)){
            fillingPrice = price + percentage / 100 * priceDifference;

            return ((fillingPrice - price) / price) * depthQty * Math.sqrt(2) / depthPct/100;
        } else {
            fillingPrice = price - percentage / 100 * priceDifference;

            return ((price - fillingPrice) / price) * depthQty * Math.sqrt(2) / depthPct/100;
        }
    }
}
