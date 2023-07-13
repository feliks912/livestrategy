package com.localstrategy.helper;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.Instant;
import java.util.ArrayList;

import com.localstrategy.types.Candle;
import com.localstrategy.types.SingleTransaction;

public class CandleConstructor {
    private double currentPrice;
    private boolean candleToBeClosed;
    private double totalVolume;
    private double candleOpen;
    private double candleHigh;
    private double candleLow;
    private double currentVolume;
    private double volumePerCandle;
    private int transactionCount;
    private long lastTransactionId;
    private ArrayList<Candle> candles = new ArrayList<>();
    private int previousDayOfMonth = -1;
    private int currentDayOfMonth;
    private Candle candle;
    private int candleIndex = 0;

    private static final int MAXIMUM_CANDLES_SIZE = 2000;

    public CandleConstructor(double volumePerCandle){
        this.volumePerCandle = volumePerCandle;
    }

    public Candle processTradeEvent(SingleTransaction transactionEvent){

        //TODO: We reset the total volume but don't adjust the prices? Look into it
        currentDayOfMonth = LocalDateTime.ofInstant(
                                Instant.ofEpochMilli(transactionEvent.getTimestamp()),
                                ZoneId.systemDefault()
                            ).getDayOfMonth();

        if(currentDayOfMonth != previousDayOfMonth){
            totalVolume = 0;
        }

        previousDayOfMonth = currentDayOfMonth;

        //FIXME: Return value is off. Theoretically 2 candles can be formed
        //TODO: What?
        Candle returnValue = null;

        currentPrice = transactionEvent.getPrice();

        //First transaction, candle volume larger than maximum or new day
        if(totalVolume == 0){

            //If one transaction had volume larger than the maximum candle volume we must read the next transaction's price to set it as close
            if(candleToBeClosed){
                candle.setHigh(Math.max(candleHigh, currentPrice));
                candle.setLow(Math.min(candleLow, currentPrice));
                candle.setClose(currentPrice);

                candle.setTimestamp(transactionEvent.getTimestamp()); //This is the time it closed TODO: this correct?

                calculateDistance(candle);

                if(candles.size() >= MAXIMUM_CANDLES_SIZE){
                    candles.remove(0);
                }

                candles.add(candle);

                returnValue = candle;

                candleToBeClosed = false;
            }
            candleOpen = currentPrice; //Perhaps set open price to this price only when starting from from 0th candle. Otherwise the open of the candle is not the close of the previous candle and that might be a problem
            candleHigh = currentPrice;
            candleLow = currentPrice;
        }

        currentVolume = transactionEvent.getAmount();

        if(totalVolume + currentVolume < volumePerCandle){
            transactionCount++;
            totalVolume += currentVolume;
            candleHigh = Math.max(currentPrice, candleHigh);
            candleLow = Math.min(currentPrice, candleLow);
            //lastTransactionId = transactionEvent.getTransactionId();
        } else {
            totalVolume = currentVolume > volumePerCandle ? 0 : currentVolume; //FIXME: This transfers volume over days as well
            candleToBeClosed = currentVolume > volumePerCandle ? true : false;

            candle = new Candle();
            candle.setOpen(candleOpen);
            candle.setHigh(Math.max(candleHigh, currentPrice));
            candle.setLow(Math.min(candleLow, currentPrice));
            candle.setClose(currentPrice);

            candle.setVolume(transactionCount);
            candle.setTimestamp(transactionEvent.getTimestamp());
            candle.setIndex(candleIndex);
            candleIndex++;
            candle.setLastTransactionId(lastTransactionId); //OK it's the last transaction ID from the transaction IN the candle

            if(candleToBeClosed == false){ //If normal candle formed not a single transaction candle
                calculateDistance(candle);

                if(candles.size() >= MAXIMUM_CANDLES_SIZE){
                    candles.remove(0);
                }

                candles.add(candle);

                if(returnValue != null){
                    System.out.println("obviously an error.");
                }

                returnValue = candle;
            }

            candleOpen = currentPrice; //Correct - when volume is not higher than maximum volume - this transaction's price is current candle's group close and next candle's open.
            candleHigh = currentPrice;
            candleLow = currentPrice;

            transactionCount = 1;
        }

        return returnValue;
    }

    public void calculateDistance(Candle candle){
        int highDistance = 0;
        int lowDistance = 0;

        //for (int i = 1; candleIndex - i >= 0; i++) { //THis is when we don't shave off candles in the list
        
        int i = 1;
        
        for(; i < candles.size(); i++){
            Candle leftCandle = candles.get(candles.size()-1 - i);

            if (highDistance == 0 && candle.getHigh() < leftCandle.getHigh()) {
                highDistance = i;
            }
            if (lowDistance == 0 && candle.getLow() > leftCandle.getLow()) {
                lowDistance = -i;
            }
            if (highDistance != 0 && lowDistance != 0) {
                break;
            }
        }
        
        if(i == candles.size()){ //Went through all candles
            if(highDistance == 0){
                highDistance = i;
            }
            if(lowDistance == 0){
                lowDistance = -i;
            }
        }

        if(highDistance > -lowDistance){
            candle.setTick(highDistance);
        } else {
            candle.setTick(lowDistance);
        }
    }

    public double getCurrentPrice(){
        return this.currentPrice;
    }

    public Candle getLastCandle(){
        return candles.get(candles.size() - 1);
    }

    public int getLastCandleIndex(){
        return candles.size();
    }

    public ArrayList<Candle> getCandles(){
        return candles;
    }

    public void setCandles(ArrayList<Candle> candles){
        this.candles = candles;
    }
}
