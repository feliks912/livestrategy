package com.localstrategy.util.types;

public record SingleTransaction(
        double price,
        double amount,
        long timestamp) {}
