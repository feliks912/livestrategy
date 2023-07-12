package com.localstrategy;

public class OrderBookHandler {

    private static final double ORDERBOOK_PCT = 0.4; //0.4%
    private static final double ORDERBOOK_QTY = 150; //150 BTC

    public OrderBookHandler() {
    }

    public double getSlippagePrice(double price, double positionSize, OrderSide orderSide) {
        double direction = (orderSide.equals(OrderSide.BUY) ? 1 : -1);
        return price * (1 + ORDERBOOK_PCT / 100 / ORDERBOOK_QTY / Math.sqrt(2) * positionSize * direction);
    }

    public static double getMaximumOrderSize(double price, double priceDifference, double percentage, OrderSide orderSide) {
        double direction = (orderSide.equals(OrderSide.BUY) ? 1 : -1);
        double fillingPrice = price + direction * (percentage / 100) * priceDifference;
        double priceRatio = Math.abs((fillingPrice - price) / price);
        return priceRatio * ORDERBOOK_QTY * Math.sqrt(2) / (ORDERBOOK_PCT / 100);
    }
}
