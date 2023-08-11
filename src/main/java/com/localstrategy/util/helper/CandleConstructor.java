package com.localstrategy.util.helper;

import com.localstrategy.StrategyStarter;
import com.localstrategy.util.types.Candle;
import com.localstrategy.util.types.SingleTransaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;

public class CandleConstructor {
    private boolean closeOnNext;
    private BigDecimal totalVolume;
    private BigDecimal candleOpen;
    private BigDecimal candleHigh;
    private BigDecimal candleLow;
    private final int volumePerCandle;
    private int transactionCount;
    private long lastTransactionId;
    private final ArrayList<Candle> candles = new ArrayList<>();
    private int previousDayOfMonth = -1;
    private int candleIndex = 0;

    private BigDecimal closeOnNextOpen;
    private int closeOnNextTransactionCount;

    private static final int MAXIMUM_CANDLES_SIZE = 2000;

    public CandleConstructor(int volumePerCandle) {
        this.volumePerCandle = volumePerCandle;
    }

    public Candle processTradeEvent(SingleTransaction transactionEvent) {

        //TODO: We reset the total volume but don't adjust the prices? Look into it
        //TODO: Commented method compatible with streaming
        /*int currentDayOfMonth = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(transactionEvent.timestamp()),
                ZoneId.systemDefault()
        ).getDayOfMonth();

        if(currentDayOfMonth != previousDayOfMonth){
            totalVolume = BigDecimal.ZERO;
        }

        previousDayOfMonth = currentDayOfMonth;*/

        if (StrategyStarter.newDay) {
            totalVolume = BigDecimal.ZERO;
            StrategyStarter.newDay = false;
        }

        //FIXME: Return value is off. Theoretically 2 candles can be formed
        //TODO: What?
        Candle returnValue = null;

        BigDecimal currentPrice = transactionEvent.price().setScale(2, RoundingMode.HALF_UP);

        //First transaction, candle volume larger than maximum or new day
        if (totalVolume.compareTo(BigDecimal.ZERO) == 0) {

            //If one transaction had volume larger than the maximum candle volume we must read the next transaction's price to set it as close
            if (closeOnNext) {

                returnValue = makeAndGetCandle(transactionEvent, currentPrice, closeOnNextOpen, closeOnNextTransactionCount);

                closeOnNext = false;
            }
            candleOpen = currentPrice; //Perhaps set open price to this price only when starting from 0th candle, otherwise the open of the candle is not the close of the previous candle and that might be a problem
            candleHigh = currentPrice;
            candleLow = currentPrice;
        }

        BigDecimal currentVolume = transactionEvent.amount().setScale(8, RoundingMode.HALF_UP);

        if (totalVolume.add(currentVolume).compareTo(BigDecimal.valueOf(volumePerCandle)) < 0) {
            transactionCount++;
            totalVolume = totalVolume.add(currentVolume);
            candleHigh = candleHigh.max(currentPrice);
            candleLow = candleLow.min(currentPrice);

            //lastTransactionId = transactionEvent.getTransactionId(); //FIXME: Uncomment lastTransactionId during live testing
        } else {
            boolean closeOnNext = currentVolume.compareTo(BigDecimal.valueOf(volumePerCandle)) > 0;
            totalVolume = closeOnNext ? BigDecimal.ZERO : currentVolume; //FIXME: This transfers volume over days as well

            if (!closeOnNext) { //If normal candle formed not a single transaction candle

                returnValue = makeAndGetCandle(transactionEvent, currentPrice, candleOpen, transactionCount);

            } else {
                closeOnNextOpen = candleOpen;
                closeOnNextTransactionCount = transactionCount;
            }

            candleOpen = currentPrice; //Correct - when volume is not higher than maximum volume - this transaction's price is current candle's group close and next candle's open.
            candleHigh = currentPrice;
            candleLow = currentPrice;

            transactionCount = 1;
        }

        return returnValue;
    }

    private Candle makeAndGetCandle(SingleTransaction transactionEvent, BigDecimal currentPrice, BigDecimal candleOpen, int candleVolume) {

        Candle candle;
        candleHigh = candleHigh.max(currentPrice);
        candleLow = candleLow.min(currentPrice);

        candle = new Candle(
                candleOpen,
                candleHigh,
                candleLow,
                currentPrice,
                candleVolume,
                calculateDistance(candleHigh, candleLow),
                candleIndex++,
                transactionEvent.timestamp(),
                lastTransactionId
        );

        if (candles.size() >= MAXIMUM_CANDLES_SIZE) {
            candles.remove(0);
        }

        candles.add(candle);
        return candle;
    }

    public int calculateDistance(BigDecimal candleHigh, BigDecimal candleLow) {
        int highDistance = 0;
        int lowDistance = 0;

        //for (int i = 1; candleIndex - i >= 0; i++) { //THis is when we don't shave off candles in the list

        int i = 1;

        for (; i < candles.size(); i++) {
            Candle leftCandle = candles.get(candles.size() - 1 - i);

            if (highDistance == 0 && candleHigh.compareTo(leftCandle.high()) < 0) {
                highDistance = i;
            }
            if (lowDistance == 0 && candleLow.compareTo(leftCandle.low()) > 0) {
                lowDistance = -i;
            }
            if (highDistance != 0 && lowDistance != 0) {
                break;
            }
        }

        if (i == candles.size()) { //Went through all candles
            if (highDistance == 0) {
                highDistance = i;
            }
            if (lowDistance == 0) {
                lowDistance = -i;
            }
        }

        if (highDistance > -lowDistance) {
            return highDistance;
        }

        return lowDistance;
    }

    public Candle getLastCandle() {
        return candles.get(candles.size() - 1);
    }

    public int getLastCandleIndex() {
        return candles.size();
    }

    public ArrayList<Candle> getCandles() {
        return candles;
    }
}
