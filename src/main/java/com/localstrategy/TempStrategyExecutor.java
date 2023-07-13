package com.localstrategy;

import java.util.ArrayList;

import com.localstrategy.util.helper.CandleConstructor;
import com.localstrategy.util.old.ZigZag;
import com.localstrategy.util.types.Candle;
import com.localstrategy.util.types.SingleTransaction;

public class TempStrategyExecutor {

    private static final int CANDLE_VOLUME = 2_000_000;
    public static final double RISK_PCT = 0.1;
    private static final int SLIPPAGE_PCT = 15; //In relation to the difference between our entry and stoploss price difference, how much in percentage of slippage are we ready to accept (total, not the average fill price)

    CandleConstructor candleConstructor = new CandleConstructor(CANDLE_VOLUME);
    ZigZag zigZag;

    ExchangeHandler exchangeHandler;

    ExchangeLatencyHandler exchangeLatencyHandler;

    //TODO: Edit RiskManager
    /* private RiskManager riskManager = new RiskManager(filledPositions);

    //TODO: Use orderMaker to request an order action
    private OrderRequestHandler orderRequestHandler = 
        new OrderRequestHandler(
            unfilledPositions, 
            filledPositions, 
            riskManager, 
            userAssets, 
            TempStrategyExecutor.RISK_PCT, 
            TOTAL_ORDER_LIMIT); */

    public TempStrategyExecutor(ExchangeHandler exchangeHandler){

        //TODO: Any call to exchangeHandler has a response latency. Local variables are instant.
        this.exchangeHandler = exchangeHandler;
        this.exchangeLatencyHandler = exchangeHandler.getExchangeLatencyHandler();

        //TODO: Print parameters on strategy start
    }



    
    private void priceUpdate(SingleTransaction transaction, Candle Previouscandle, boolean isWall){
        //Let's say there's a new order created locally and pendingPositions now holds positions to be executed by Binance
        //At the end of current transaction processing, we add the pendingPositons to an ExchangeLatencyHandler object
        //It is stored in ELH until the time has parsed, and can then be read by the ExchangeHandler
        //It is stored with the current transaction timestamp. The latency for the current period is calculated and held ExchangeLatencyHander to which the timestamp is compared when the list is not empty
    }

    private void newCandle(SingleTransaction transaction, ArrayList<Candle> candles){


        //exchangeHandler.addToUserAssetList(userAsset);
    }





    public void onTransaction(SingleTransaction transaction, boolean isWall){
         Candle candle = candleConstructor.processTradeEvent(transaction);

        priceUpdate(transaction, candleConstructor.getLastCandle(), isWall);

        if(candle != null){ // New candle
            newCandle(transaction, candleConstructor.getCandles());
        }
    }
}
