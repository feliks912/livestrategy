package com.localstrategy;

import com.localstrategy.util.enums.RejectionReason;
import com.localstrategy.util.helper.CandleConstructor;
import com.localstrategy.util.types.Candle;
import com.localstrategy.util.types.Event;
import com.localstrategy.util.types.SingleTransaction;

import java.util.ArrayList;
import java.util.Map;

public class LocalStrategy {

    private static final int CANDLE_VOLUME = 2_000_000;
    private static final double RISK_PCT = 0.1;
    private static final int SLIPPAGE_PCT = 15; //In relation to the difference between our entry and stop-loss price difference, how much in percentage of slippage are we ready to accept (total, not the average fill price)
    
    private CandleConstructor candleConstructor = new CandleConstructor(CANDLE_VOLUME);

    private ArrayList<Position> pendingPositions = new ArrayList<>();

    private ArrayList<Position> newPositions = new ArrayList<>();
    private ArrayList<Position> filledPositions = new ArrayList<>();
    private ArrayList<Position> cancelledPositions = new ArrayList<>();
    private ArrayList<Position> rejectedOrders = new ArrayList<>();

    private ArrayList<Map<RejectionReason, Position>> rejectedActions = new ArrayList<>();

    private ArrayList<Position> previousPositions = new ArrayList<>();

    private UserAssets userAssets = new UserAssets();
    private TierManager tierManager = new TierManager();
    private OrderRequest orderRequest;
    private Binance exchangeHandler;
    private Candle lastCandle;
    private LatencyHandler latencyHandler;

    private final EventScheduler scheduler;

    public LocalStrategy(double initialFreeUSDT, EventScheduler scheduler){

        this.scheduler = scheduler;

        //TODO: Any call to exchangeHandler has a response latency. Local variables are instant.

        userAssets.setFreeUSDT(initialFreeUSDT);

        orderRequest =  new OrderRequest(
            pendingPositions,
            newPositions,
            tierManager, 
            userAssets, 
            RISK_PCT,
            Binance.ALGO_ORDER_LIMIT,
            SLIPPAGE_PCT);

        //TODO: Print parameters on strategy start
    }

    
    private void priceUpdate(SingleTransaction transaction, boolean isWall){
    }

    private void newCandle(SingleTransaction transaction, ArrayList<Candle> candles){
        
    }
    

    public void onEvent(Event event){

    }

    public void onTransaction(SingleTransaction transaction, boolean isWall){
        Candle candle = candleConstructor.processTradeEvent(transaction);

        priceUpdate(transaction, isWall);

        if(candle != null){ // New candle
            lastCandle = candle;
            newCandle(transaction, candleConstructor.getCandles());
        }
    }
}
