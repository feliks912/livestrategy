package com.localstrategy.util.helper;

import com.localstrategy.util.enums.OrderSide;

public class SlippageHandler {

    private static final double ORDERBOOK_PCT = 0.4; //0.4%
    private static final double ORDERBOOK_QTY = 150; //150 BTC

    private static final double SQUARE_ROOT_MODEL_CONSTANT = 0.5;
    private static final long BTCUSDT_DAILY_VOLUME = 8_570_000_000L;


    public static double getSlippageFillPrice(double price, double orderSize, OrderSide orderSide) {
        double direction = (orderSide.equals(OrderSide.BUY) ? 1 : -1);
        return price * (1 + ORDERBOOK_PCT / 100 / ORDERBOOK_QTY / Math.sqrt(2) * orderSize * direction);
    }

    public static double getMaximumOrderSize(double price, double priceDifference, double percentage, OrderSide orderSide) {
        double direction = (orderSide.equals(OrderSide.BUY) ? 1 : -1);
        double fillingPrice = price + direction * (percentage / 100) * priceDifference;
        double priceRatio = Math.abs((fillingPrice - price) / price);

        return priceRatio * ORDERBOOK_QTY * Math.sqrt(2) / (ORDERBOOK_PCT / 100);
    }

    public static double getRootSlippageFillPrice(double price, double volatility, double orderSize, OrderSide orderSide){
        double deltaPrice = volatility * SQUARE_ROOT_MODEL_CONSTANT * Math.sqrt(orderSize*price / 2*BTCUSDT_DAILY_VOLUME);
        deltaPrice *= orderSide.equals(OrderSide.BUY) ? 1 : -1;
        return price + deltaPrice;
    }

    //FIXME: fix me
    public static double getRootMaximumOrderSize(double price, double priceDifference, double percentage, double volatility, OrderSide orderSide){
        double direction = (orderSide.equals(OrderSide.BUY) ? 1 : -1);
        double fillingPrice = price + direction * (percentage / 100) * priceDifference;

        return 0.0;
    }
}
