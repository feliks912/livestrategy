package com.localstrategy;

import java.util.ArrayList;

import com.localstrategy.util.enums.OrderAction;
import com.localstrategy.util.helper.CandleConstructor;
import com.localstrategy.util.types.Candle;
import com.localstrategy.util.types.SingleTransaction;
import com.localstrategy.util.types.UserDataResponse;

public class LocalStrategy {

    private static final int CANDLE_VOLUME = 2_000_000;
    private static final double RISK_PCT = 0.1;
    private static final int SLIPPAGE_PCT = 15; //In relation to the difference between our entry and stoploss price difference, how much in percentage of slippage are we ready to accept (total, not the average fill price)

    CandleConstructor candleConstructor = new CandleConstructor(CANDLE_VOLUME);

    Exchange exchangeHandler;

    Candle lastCandle;

    LatencyHandler exchangeLatencyHandler;

    private ArrayList<Position> pendingPositions = new ArrayList<Position>();

    private ArrayList<Position> newPositions = new ArrayList<Position>();
    private ArrayList<Position> filledPositions = new ArrayList<Position>();
    private ArrayList<Position> cancelledPositions = new ArrayList<Position>();
    private ArrayList<Position> rejectedOrders = new ArrayList<Position>();

    private ArrayList<Position> previousPositions = new ArrayList<Position>();

    private UserAssets userAssets = new UserAssets();

    boolean test = false;

    //TODO: Edit RiskManager
    private TierManager tierManager = new TierManager();

    //TODO: Use orderMaker to request an order action
    private OrderRequest orderRequest;
       

    public LocalStrategy(Exchange exchangeHandler){

        //TODO: Any call to exchangeHandler has a response latency. Local variables are instant.
        this.exchangeHandler = exchangeHandler;
        this.exchangeLatencyHandler = exchangeHandler.getExchangeLatencyHandler();

        userAssets = exchangeHandler.getUserassets();

        orderRequest =  new OrderRequest(
            pendingPositions,
            newPositions,
            tierManager, 
            userAssets, 
            LocalStrategy.RISK_PCT, 
            Exchange.MAX_PROG_ORDERS,
            LocalStrategy.SLIPPAGE_PCT);

        //TODO: Print parameters on strategy start
    }

    
    private void priceUpdate(SingleTransaction transaction, boolean isWall){
        //Let's say there's a new order created locally and pendingPositions now holds positions to be executed by Binance
        //At the end of current transaction processing, we add the pendingPositons to an ExchangeLatencyHandler object
        //It is stored in ELH until the time has parsed, and can then be read by the ExchangeHandler
        //It is stored with the current transaction timestamp. The latency for the current period is calculated and held ExchangeLatencyHander to which the timestamp is compared when the list is not empty

        //Position position = orderRequest.newMarketOrder(transaction, transaction.getPrice() - 100);
        if(!test){
            Position position = orderRequest.newLimitOrder(transaction.getPrice() - 100, transaction.getPrice() - 200, false, transaction);
            if(position != null){
                ArrayList<Position> tempPosition = new ArrayList<Position>();
                tempPosition.add(position);

                exchangeLatencyHandler.addUserAction(OrderAction.CREATE_ORDER, tempPosition, transaction.getTimestamp());
                test = true;
                System.out.println(transaction.getPrice());
            }
        }

        ArrayList<UserDataResponse> userData = exchangeLatencyHandler.getDelayedUserDataStream(transaction.getTimestamp());
        if(userData.size() != 0){
            UserDataResponse lastUserData = userData.get(userData.size() - 1);

            UserAssets userAsset = lastUserData.getUserAssets();

            //System.out.println(userAsset.toString());

            newPositions = lastUserData.getNewPositions();
            filledPositions = lastUserData.getFilledPositions();
            rejectedOrders = lastUserData.getRejectedPositions();

            if(!filledPositions.isEmpty()){
                Position position = filledPositions.get(0);
                System.out.println(position.getStatus() + " " + position.getFillPrice());
            }

            pendingPositions.removeAll(newPositions);
            pendingPositions.removeAll(filledPositions);
            pendingPositions.removeAll(rejectedOrders);
        }
    }

    private void newCandle(SingleTransaction transaction, ArrayList<Candle> candles){
        
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
