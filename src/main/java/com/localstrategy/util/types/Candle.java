package com.localstrategy.util.types;

public record Candle(
        double open,
        double high,
        double low,
        double close,
        int volume,
        int tick,
        int index,
        long timestamp,
        long lastTransactionId) {}
