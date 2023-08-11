package com.localstrategy.util.types;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Candle(
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        int volume,
        int tick,
        int index,
        long timestamp,
        long lastTransactionId) {

    public Candle(BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, int volume, int tick, int index, long timestamp, long lastTransactionId) {
        this.open = roundToDecimalPlaces(open);
        this.high = roundToDecimalPlaces(high);
        this.low = roundToDecimalPlaces(low);
        this.close = roundToDecimalPlaces(close);
        this.volume = volume;
        this.tick = tick;
        this.index = index;
        this.timestamp = timestamp;
        this.lastTransactionId = lastTransactionId;
    }

    private BigDecimal roundToDecimalPlaces(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
