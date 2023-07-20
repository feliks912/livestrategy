package com.localstrategy;

import com.localstrategy.util.enums.ActionResponse;
import com.localstrategy.util.enums.OrderAction;
import com.localstrategy.util.types.SingleTransaction;
import com.localstrategy.util.types.UserDataResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class LatencyHandler {

    //TODO: add additional latency on automaticBorrow = true
    //FIXME: Test userDataStream latency. It shouldn't be (lol shouldn't...) much different than order execution response latency

    private final int TRADE_EXECUTION_LATENCY_MEAN = 0;
    private final int TRADE_EXECUTION_LATENCY_STD = 0;

    private final int TRADE_REPORT_LATENCY_MEAN = 0;
    private final int TRADE_REPORT_LATENCY_STD = 0;

    private final int USER_DATA_LATENCY_MEAN = 0;
    private final int USER_DATA_LATENCY_STD = 0;

    private final int BORROW_LATENCY_MEAN = 0;
    private final int BORROW_LATENCY_STD = 0;

    private final int TRANSACTION_LATENCY_MEAN = 0;
    private final int TRANSACTION_LATENCY_STD = 0;


    private final ArrayList<SingleTransaction> delayedTransactions = new ArrayList<>();

    private final Map<Long, UserDataResponse> userDataStream = new HashMap<>();  //UserDataStream gets parsed from the exchange to the user
    private final Map<Long, ArrayList<Map<OrderAction, Order>>> actionRequestsMap = new HashMap<>(); //actionRequestsMap get parsed from client to the exchange
    private final Map<Long, ArrayList<Map<ActionResponse, Order>>> actionResponseMap = new HashMap<>();

    private int previousUserDataStreamLatency;
    private int previousPendingOrdersLatency;
    private long previousLatencyCalculationTimestamp;
    private static final Random random = new Random();
    public LatencyHandler() {}



    public void addTransactionEvent(SingleTransaction transaction){

    }

    //Transactions have no delay for Binance
    //Transactions have delay for client
    //Both the exchange and the client operate on discrete transactions
    //Therefore how do we make the client tick if we use delayed transactions?
    public SingleTransaction getDelayedTransaction(long localTime){

        SingleTransaction transaction = delayedTransactions.get(0);

        if(localTime - transaction.getTimestamp() >= transaction.getLatency()){
            delayedTransactions.remove(0);
            return transaction;
        }

        return null;
    }


    public void addActionRequest(OrderAction actionType, Order order, long localTime) {

        Map<OrderAction, Order> tempMap = new HashMap<>();
        tempMap.put(actionType, new Order(order));

        for(Map.Entry<Long, ArrayList<Map<OrderAction, Order>>> entry : actionRequestsMap.entrySet()){
            if(entry.getKey().equals(localTime)){
                entry.getValue().add(tempMap);
            } else {
                ArrayList<Map<OrderAction, Order>> tempList = new ArrayList<>();
                tempList.add(tempMap);
                actionRequestsMap.put(localTime, tempList);
            }
        }
    }

    public ArrayList<Map<OrderAction, Order>> getDelayedActionRequests(long exchangeTime) {

        ArrayList<Map<OrderAction, Order>> actionsToReturn = new ArrayList<>();
        ArrayList<Long> keysToRemove = new ArrayList<>();

        for (Map.Entry<Long, ArrayList<Map<OrderAction, Order>>> entry : actionRequestsMap.entrySet()) {
            if (exchangeTime - entry.getKey() > previousPendingOrdersLatency) {
                actionsToReturn.addAll(entry.getValue());
                keysToRemove.add(entry.getKey());
            }
        }

        for(Long keyToRemove : keysToRemove) {
            actionRequestsMap.remove(keyToRemove);
        }

        return actionsToReturn;
    }



    public void addActionResponse(ActionResponse actionResponse, Order order, long exchangeTime){
        Map<ActionResponse, Order> tempMap = new HashMap<>();
        tempMap.put(actionResponse, new Order(order));

        for(Map.Entry<Long, ArrayList<Map<ActionResponse, Order>>> entry : actionResponseMap.entrySet()){
            if(entry.getKey().equals(exchangeTime)){
                entry.getValue().add(tempMap);
            } else {
                ArrayList<Map<ActionResponse, Order>> tempList = new ArrayList<>();
                tempList.add(tempMap);
                actionResponseMap.put(exchangeTime, tempList);
            }
        }
    }

    public ArrayList<Map<ActionResponse, Order>> getDelayedActionResponses(long localTime){
        ArrayList<Map<ActionResponse, Order>> responsesToReturn = new ArrayList<>();
        ArrayList<Long> keysToRemove = new ArrayList<>();

        for (Map.Entry<Long, ArrayList<Map<ActionResponse, Order>>> entry : actionResponseMap.entrySet()) {
            if (localTime - entry.getKey() > previousPendingOrdersLatency) {
                responsesToReturn.addAll(entry.getValue());
                keysToRemove.add(entry.getKey());
            }
        }

        for(Long keyToRemove : keysToRemove) {
            actionResponseMap.remove(keyToRemove);
        }

        return responsesToReturn;
    }



    public void addUserDataStream(UserDataResponse userDataStream, long exchangeTime) {
        this.userDataStream.put(exchangeTime, userDataStream);
    }

    public ArrayList<UserDataResponse> getDelayedUserDataStream(long localTime) {

        ArrayList<UserDataResponse> userStreamsToReturn = new ArrayList<>();
        ArrayList<Long> keysToRemove = new ArrayList<>();

        for (Map.Entry<Long, UserDataResponse> entry : userDataStream.entrySet()) {
            if (localTime - entry.getKey() > previousUserDataStreamLatency) {
                keysToRemove.add(entry.getKey());
                userStreamsToReturn.add(entry.getValue());
            }
        }

        for(Long keyToRemove : keysToRemove){
            userDataStream.remove(keyToRemove);
        }

        return userStreamsToReturn;
    }




    public void recalculateLatencies(long exchangeTime){

        if(exchangeTime - previousLatencyCalculationTimestamp >
                Math.max(previousPendingOrdersLatency, previousUserDataStreamLatency)){
            previousUserDataStreamLatency = calculateUserDataStreamLatency();
            previousPendingOrdersLatency = calculatePendingPositionsLatency();
            previousLatencyCalculationTimestamp = exchangeTime;
        }
    }

    public int calculateUserDataStreamLatency(){
        double latency;

        do{
            latency = 25 + random.nextGaussian() * 10;
        } while(latency < 15 || latency > 35);

        return (int) latency;
    }

    public int calculatePendingPositionsLatency(){
        double latency;

        do{
            latency = 20 + random.nextGaussian() * 10;
        } while(latency < 10 || latency > 30);

        return (int) latency;
    }

}
