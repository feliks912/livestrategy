package com.localstrategy;

import java.util.ArrayList;

public class TempStrategyExecutor {

    private static final int CANDLE_VOLUME = 2_000_000;
    public static final double RISK_PCT = 0.1;
    private static final int SLIPPAGE_PCT = 15; //In relation to the difference between our entry and stoploss price difference, how much in percentage of slippage are we ready to accept (total, not the average fill price)

    CandleConstructor candleConstructor = new CandleConstructor(CANDLE_VOLUME);
    ZigZag zigZag;

    ExchangeHandler exchangeHandler;

    ExchangeLatencyHandler exchangeLatencyHandler;

    public TempStrategyExecutor(ExchangeHandler exchangeHandler, ExchangeLatencyHandler exchangeLatencyHandler){

        //TODO: Any call to exchangeHandler has a response latency. Local variables are instant.
        this.exchangeHandler = exchangeHandler;
        this.exchangeLatencyHandler = exchangeLatencyHandler;

        //TODO: Print parameters on strategy start
    }

    private void priceUpdate(SingleTransaction transaction, Candle candle){
        //Let's say there's a new order created locally and pendingPositions now holds positions to be executed by Binance
        //At the end of current transaction processing, we add the pendingPositons to an ExchangeLatencyHandler object
        //It is stored in ELH until the time has parsed, and can then be read by the ExchangeHandler
        //It is stored with the current transaction timestamp. The latency for the current period is calculated and held ExchangeLatencyHander to which the timestamp is compared when the list is not empty
    }

    private void newCandle(SingleTransaction transaction, ArrayList<Candle> candles){

    }

    public void onTransaction(SingleTransaction transaction){
         Candle candle = candleConstructor.processTradeEvent(transaction);

        priceUpdate(transaction, candleConstructor.getLastCandle());

        if(candle != null){ // New candle
            newCandle(transaction, candleConstructor.getCandles());
        }
    }
}
