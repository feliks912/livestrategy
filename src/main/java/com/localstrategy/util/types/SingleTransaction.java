package com.localstrategy.util.types;

//TODO: How are walls detectable irl? Can we still stream and 'dYnaMyCallY' detect them as soon as they come?

public record SingleTransaction(
        double price,
        double amount,
        long timestamp) {}
