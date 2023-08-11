package com.localstrategy;

import com.localstrategy.util.types.Candle;
import com.localstrategy.util.types.Position;
import com.localstrategy.util.types.SingleTransaction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Strategy2 {
    private LocalHandler handler;
    private List<Position> activePositions;
    private List<Position> inactivePositions;
    private SingleTransaction transaction;
    private ArrayList<Candle> candles;
    public Strategy2(LocalHandler localHandler, ArrayList<Candle> candles, ArrayList<Position> activePositions, ArrayList<Position> inactivePositions){
        this.handler = localHandler;

        this.candles = candles;

        this.activePositions = Collections.unmodifiableList(activePositions);
        this.inactivePositions = Collections.unmodifiableList(inactivePositions);
    }

    public void priceUpdate(SingleTransaction transaction){
        this.transaction = transaction;
    }

    boolean packingLow = false;
    boolean packingHigh = false;
    double stopPriceLow = Double.MAX_VALUE;
    double stopPriceHigh = 0;
    int distance = 100;

    public void candleUpdate(Candle lastCandle){
        if(lastCandle.tick() <= -distance){ // New lows for long
            packingLow = true;
            if(lastCandle.low().doubleValue() < stopPriceLow){
                stopPriceLow = lastCandle.low().doubleValue();
            }
        } else if (packingLow){

            for(Position position : activePositions){
                handler.closePosition(position);
            }

            handler.activateStopLoss(handler.executeMarketOrder(BigDecimal.valueOf(stopPriceLow)));

            packingLow = false;
            stopPriceLow = Double.MAX_VALUE;
        }

        if(lastCandle.tick() >= distance){ // New highs for short
            packingHigh = true;
            if(lastCandle.high().doubleValue() > stopPriceHigh){
                stopPriceHigh = lastCandle.high().doubleValue();
            }
        } else if (packingHigh){

            for(Position position : activePositions){
                handler.closePosition(position);
            }

            handler.activateStopLoss(handler.executeMarketOrder(BigDecimal.valueOf(stopPriceHigh)));

            packingHigh = false;
            stopPriceHigh = 0;
        }
    }
}
