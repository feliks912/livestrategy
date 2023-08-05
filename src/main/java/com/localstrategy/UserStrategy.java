package com.localstrategy;

import com.localstrategy.util.helper.Position;
import com.localstrategy.util.types.Candle;
import com.localstrategy.util.types.SingleTransaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class UserStrategy {
    private LocalHandler handler;
    private List<Position> activePositions;
    private List<Position> inactivePositions;
    private SingleTransaction transaction;
    private ArrayList<Candle> candles;
    public UserStrategy(LocalHandler localHandler, SingleTransaction transaction, ArrayList<Candle> candles, ArrayList<Position> activePositions, ArrayList<Position> inactivePositions){
        this.handler = localHandler;

        this.candles = candles;

        this.activePositions = Collections.unmodifiableList(activePositions);
        this.inactivePositions = Collections.unmodifiableList(inactivePositions);
    }

    private boolean printed = false;

    public void priceUpdate(SingleTransaction transaction){
    }

    public void candleUpdate(Candle lastCandle){

    }
}
