package com.localstrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.binance.api.client.domain.OrderStatus;
import com.localstrategy.util.enums.OrderAction;
import com.localstrategy.util.enums.OrderType;
import com.localstrategy.util.enums.RejectionReason;
import com.localstrategy.util.helper.CandleConstructor;
import com.localstrategy.util.types.Candle;
import com.localstrategy.util.types.SingleTransaction;
import com.localstrategy.util.types.UserDataResponse;

public class LocalStrategy {

    private static final int CANDLE_VOLUME = 2_000_000;
    private static final double RISK_PCT = 0.1;
    private static final int SLIPPAGE_PCT = 15; //In relation to the difference between our entry and stoploss price difference, how much in percentage of slippage are we ready to accept (total, not the average fill price)
    
    private CandleConstructor candleConstructor = new CandleConstructor(CANDLE_VOLUME);

    private ArrayList<Position> pendingPositions = new ArrayList<Position>();

    private ArrayList<Position> newPositions = new ArrayList<Position>();
    private ArrayList<Position> filledPositions = new ArrayList<Position>();
    private ArrayList<Position> cancelledPositions = new ArrayList<Position>();
    private ArrayList<Position> rejectedOrders = new ArrayList<Position>();

    private ArrayList<Map<RejectionReason, Position>> rejectedActions = new ArrayList<Map<RejectionReason, Position>>();

    private ArrayList<Position> previousPositions = new ArrayList<Position>();

    private UserAssets userAssets = new UserAssets();
    private TierManager tierManager = new TierManager();
    private OrderRequest orderRequest;
    private Binance exchangeHandler;
    private Candle lastCandle;
    private LatencyHandler latencyHandler;
       

    public LocalStrategy(Binance exchangeHandler){

        //TODO: Any call to exchangeHandler has a response latency. Local variables are instant.
        this.exchangeHandler = exchangeHandler;
        this.latencyHandler = exchangeHandler.getLatencyHandler();

        userAssets = exchangeHandler.getUserassets();

        orderRequest =  new OrderRequest(
            pendingPositions,
            newPositions,
            tierManager, 
            userAssets, 
            LocalStrategy.RISK_PCT, 
            Binance.MAX_PROG_ORDERS,
            LocalStrategy.SLIPPAGE_PCT);

        //TODO: Print parameters on strategy start
    }

    
    private void priceUpdate(SingleTransaction transaction, boolean isWall){

        ArrayList<UserDataResponse> userDataResponses = latencyHandler.getDelayedUserDataStream(transaction.getTimestamp());
        if(!userDataResponses.isEmpty()){

            //We only look for the latest update
            int lastIndex = userDataResponses.size() - 1;

            userAssets = userDataResponses.get(lastIndex).getUserAssets();

            //TODO: Add closed positions to previousPositions

            ArrayList<Position> tempNewPositions = userDataResponses.get(lastIndex).getNewPositions();
            ArrayList<Position> newPositionDiff = findDifferences(tempNewPositions, newPositions);
            if(!newPositionDiff.isEmpty()){
                for(Position position : newPositionDiff){
                    if(newPositions.contains(position)) {
                        //Handle position removal
                        System.out.println("Removed new position: " + position.getId());
                        //Confirm new position
                    } else {
                        //Handle new position
                        System.out.println("Added new position: " + position.getId());
                        //Rejected new positions handled here
                    }
                }
            }

            ArrayList<Position> tempFilledPositions = userDataResponses.get(lastIndex).getFilledPositions();
            ArrayList<Position> filledPositionDiff = findDifferences(tempFilledPositions, filledPositions);
            if(!filledPositionDiff.isEmpty()){
                for(Position position : filledPositionDiff){
                    if(newPositions.contains(position)) {
                        //Handle position removal
                        System.out.println("Removed filled position: " + position.getId());
                        //Confirm new position
                    } else {
                        //Handle new position
                        System.out.println("Added filled position: " + position.getId());
                    }
                }
            }

            ArrayList<Position> tempCancelledPositions = userDataResponses.get(lastIndex).getCancelledPositions();
            ArrayList<Position> canceledPositionDiff = findDifferences(tempCancelledPositions, cancelledPositions);
            if(!canceledPositionDiff.isEmpty()){
                for(Position position : canceledPositionDiff){
                    //Handle new cancelled positions

                    System.out.println("Added canceled position: " + position.getId());
                }
            }

            ArrayList<Position> tempRejectedOrders = userDataResponses.get(lastIndex).getRejectedPositions();
            /* ArrayList<Position> rejectedPositionsDiff = findDifferences(tempRejectedOrders, rejectedOrders);
            if(!rejectedPositionsDiff.isEmpty()){
                //Handle new order rejections

            } */

            ArrayList<Map<RejectionReason, Position>> rejectedActions = userDataResponses.get(lastIndex).getRejectedActions();
            if(!rejectedActions.isEmpty()){
                for(Map<RejectionReason, Position> rejection : rejectedActions){
                    Map.Entry<RejectionReason, Position> entry = rejection.entrySet().iterator().next();

                    RejectionReason reason = entry.getKey();
                    Position position = entry.getValue();

                    switch(reason){
                        case INSUFFICIENT_MARGIN:
                            //Handle insufficient margin
                            //Repeat order with smaller size if limit
                            //Discard?
                            break;
                        case WOULD_TRIGGER_IMMEDIATELY:
                            if(position.isStopLoss()){ //Attempted to create a stoploss but failed, price is now going away from the stoploss
                                //Create new market order for the opposite direction
                                Position marketStopLoss = new Position(position);
                                position.setOrderType(OrderType.MARKET);
                                latencyHandler.addUserAction(OrderAction.CREATE_ORDER, tempRejectedOrders, lastIndex);
                            }
                            break;
                        case EXCESS_PROG_ORDERS:
                            if(position.isStopLoss()){ //For some reason could be we can't create a stoploss due to programmatic position count despite we test it locally
                                //Close the position if no other clear action is available
                            }
                            break;
                        case EXCESS_BORROW:
                            if(position.isStopLoss()){ //What could this be? Close position on market

                            }
                            break;
                        case INSUFFICIENT_FUNDS:
                            break;
                        case INVALID_ORDER_STATE:
                            break;
                    }
                }
            }

            newPositions = Position.deepCopyPositionList(tempNewPositions);
            filledPositions = Position.deepCopyPositionList(filledPositionDiff);
            cancelledPositions = Position.deepCopyPositionList(canceledPositionDiff);
            rejectedOrders = Position.deepCopyPositionList(tempRejectedOrders);
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

    //Returns a list of Position objects in list2 but not in list1
    public ArrayList<Position> findDifferences(ArrayList<Position> list1, ArrayList<Position> list2) {
        ArrayList<Position> diff = new ArrayList<>();

        for (Position pos1 : list1) {
            boolean found = false;
            for (Position pos2 : list2) {
                if (pos1.getId() == pos2.getId()) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                diff.add(pos1);
            }
        }

        for (Position pos2 : list2) {
            boolean found = false;
            for (Position pos1 : list1) {
                if (pos1.getId() == pos2.getId()) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                diff.add(pos2);
            }
        }

        return diff;
    }
}
