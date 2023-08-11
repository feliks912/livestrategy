package com.localstrategy.util.old;

import com.localstrategy.LocalHandler;
import com.localstrategy.util.indicators.ZigZag;
import com.localstrategy.util.types.Candle;
import com.localstrategy.util.types.Position;
import com.localstrategy.util.types.SingleTransaction;
import com.localstrategy.util.types.UserAssets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Strategy {
    private LocalHandler handler;
    private List<Position> activePositions;
    private List<Position> inactivePositions;
    private SingleTransaction transaction;
    private ArrayList<Candle> candles;
    public Strategy(LocalHandler localHandler, ArrayList<Candle> candles, ArrayList<Position> activePositions, ArrayList<Position> inactivePositions){
        this.handler = localHandler;

        this.candles = candles;

        this.activePositions = Collections.unmodifiableList(activePositions);
        this.inactivePositions = Collections.unmodifiableList(inactivePositions);
    }

    private boolean printed = false;

    public void priceUpdate(SingleTransaction transaction){
        this.transaction = transaction;
    }

    private boolean firstHighLowFound, usedLow, usedHigh = false;
    private double rangeLow, rangeHigh, lastLow, lastHigh, orderEntryPriceLong, orderEntryPriceShort, stopLossPriceShort, stopLossPriceLong = 0;
    private long topHighIndex, bottomLowIndex, lastLowIndex, lastHighIndex = 0;

    private int ZZDepth = 2;
    private int ZZBackstep = 2;

    private int distance = 1000;

    private final ZigZag zigZagIndicator = new ZigZag(ZZDepth, 0, ZZBackstep, 0);

    public void candleUpdate(Candle lastCandle){
        if(candles.size() < 2 * ZZDepth + ZZBackstep + 1){
            return;
        }

        if(!firstHighLowFound && lastLow != 0 && lastHigh != 0){
            firstHighLowFound = true;
        }

        updateZigZagValue(candles);

        if(!usedLow && lastCandle.close() > stopLossPriceShort){
            usedLow = true;
        }

        if(!usedHigh && lastCandle.close() < stopLossPriceLong){
            usedHigh = true;
        }

        if(!usedHigh && lastCandle.tick() > -distance && lastCandle.close() < rangeHigh){

            UserAssets assets = handler.getUserAssets();

            for(Position position : activePositions){
                handler.closePosition(position);
            }

            handler.activateStopLoss(handler.executeMarketOrder(stopLossPriceLong));

            usedHigh = true;
        } else if(!usedHigh && lastCandle.tick() > -distance && lastCandle.close() >= rangeHigh){
            usedHigh = true;
        }
        if(!usedLow && lastCandle.tick() < distance && lastCandle.close() > rangeLow){

            UserAssets assets = handler.getUserAssets();

            for(Position position : activePositions){
                handler.closePosition(position);
            }

            handler.activateStopLoss(handler.executeMarketOrder(stopLossPriceShort));

            usedLow = true;
        } else if(!usedLow && lastCandle.tick() < distance && lastCandle.close() <= rangeLow){
            usedLow = true;
        }

        //---------------------------------------------

        if(firstHighLowFound){
            if(lastCandle.tick() <= -distance){
                bottomLowIndex = lastCandle.index(); //Save the lastCandle index

                stopLossPriceLong = lastCandle.low();

                rangeHigh = lastCandle.high();

                for (int i = 1; i < candles.size(); i++) {  // Iterate backwards from bottomLowIndex to 0
                    Candle c = candles.get(candles.size() - i); //FIX
                    if (c.tick() < -distance) {
                        if(c.high() > rangeHigh){
                            rangeHigh = c.high();
                        }
                    } else {
                        break;  // Exit the loop as soon as tick() > -distance
                    }
                }

                usedHigh = false; //Entry switch on
            }

            if (lastCandle.tick() >= distance){ //'new' high
                topHighIndex = lastCandle.index(); //Save the lastCandle index

                stopLossPriceShort = lastCandle.high();

                rangeLow = lastCandle.low();

                for (int i = 1; i < candles.size(); i++) {  // Iterate backwards from bottomLowIndex to 0
                    Candle c = candles.get(candles.size() - i); //FIX
                    if (c.tick() > distance) {
                        if(c.low() < rangeLow){
                            rangeLow = c.low();
                        }
                    } else {
                        break;  // Exit the loop as soon as tick() < distance
                    }
                }

                usedLow = false; //Entry switch on
            }
        }

        if(lastLowIndex < topHighIndex && lastCandle.close() > lastLow){
            orderEntryPriceShort = lastLow;
        }
        if(lastHighIndex < bottomLowIndex && lastCandle.close() < lastHigh){
            orderEntryPriceLong = lastHigh;
        }
    }

    public void updateZigZagValue(ArrayList<Candle> candles){

        List<Candle> candleSublist = candles.subList(
                candles.size()-1 - 2 * ZZDepth - ZZBackstep,
                candles.size());

        double[] highsArray = candleSublist.stream()
                .mapToDouble(Candle::high)
                .toArray();

        double[] lowsArray = candleSublist.stream()
                .mapToDouble(Candle::low)
                .toArray();

        zigZagIndicator.calculate(highsArray.length, highsArray, lowsArray);

        double zigZagValue = zigZagIndicator.getZigzagBuffer()[ZZDepth];

        if(zigZagValue != 0){

            Candle zigZagCandle = candles.get(candles.size()-1 - ZZBackstep - ZZDepth); // OK

            if(zigZagValue == zigZagCandle.high()){
                lastHighIndex = zigZagCandle.index();
                lastHigh = zigZagValue;
            } else if(zigZagValue == zigZagCandle.low()) {
                lastLowIndex = zigZagCandle.index();
                lastLow = zigZagValue;
            }
        }
    }
}
