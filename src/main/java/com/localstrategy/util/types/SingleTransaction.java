package com.localstrategy.util.types;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record SingleTransaction(
        BigDecimal price,
        BigDecimal amount,
        long timestamp) {

    public SingleTransaction(BigDecimal price, BigDecimal amount, long timestamp) {
        this.price = price.setScale(2, RoundingMode.HALF_UP);
        this.amount = amount.setScale(8, RoundingMode.HALF_UP);
        this.timestamp = timestamp;
    }
}
