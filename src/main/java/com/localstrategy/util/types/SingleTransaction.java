package com.localstrategy.util.types;

public record SingleTransaction(
        double price,
        double amount,
        long timestamp) {

    public SingleTransaction(double price, double amount, long timestamp) {
        this.price = price;
        this.amount = amount;
        this.timestamp = timestamp;
    }
}
