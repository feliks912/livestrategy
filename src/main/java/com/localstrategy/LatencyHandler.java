package com.localstrategy;

import com.localstrategy.util.enums.OrderAction;
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

    private final Map<Long, UserDataResponse> userDataStream = new HashMap<>();  //UserDataStream gets parsed from the exchange to the user
    
    private final Map<Long, ArrayList<Map<OrderAction, Order>>> actionRequestsMap = new HashMap<>(); //actionRequestsMap get parsed from client to the exchange

    private int previousUserDataStreamLatency;
    private int previousPendingOrdersLatency;
    private long previousLatencyCalculationTimestamp;

    private static final Random random = new Random();

    public LatencyHandler() {

    }

    //TODO: Thanks ChatGPT
    public ArrayList<UserDataResponse> getDelayedUserDataStream(long currentLocalTimestamp) {

        ArrayList<UserDataResponse> userStreamsToReturn = new ArrayList<>();
        ArrayList<Long> keysToRemove = new ArrayList<>();

        for (Map.Entry<Long, UserDataResponse> entry : userDataStream.entrySet()) {
            if (currentLocalTimestamp - entry.getKey() > previousUserDataStreamLatency) {
                keysToRemove.add(entry.getKey());
                userStreamsToReturn.add(entry.getValue());
            }
        }

        for(Long keyToRemove : keysToRemove){
            userDataStream.remove(keyToRemove);
        }

        return userStreamsToReturn;
    }

    public ArrayList<Map<OrderAction, Order>> getDelayedUserActionRequests(long currentExchangeTimestamp) {

        ArrayList<Map<OrderAction, Order>> actionsToReturn = new ArrayList<>();
        ArrayList<Long> keysToRemove = new ArrayList<>();

        for (Map.Entry<Long, ArrayList<Map<OrderAction, Order>>> entry : actionRequestsMap.entrySet()) {
            if (currentExchangeTimestamp - entry.getKey() > previousPendingOrdersLatency) {
                actionsToReturn.addAll(entry.getValue());
                keysToRemove.add(entry.getKey());
            }
        }

        for(Long keyToRemove : keysToRemove) {
            actionRequestsMap.remove(keyToRemove);
        }

        return actionsToReturn;
    }

    
    public void recalculateLatencies(long currentExchangeTimestamp){
        if(currentExchangeTimestamp - previousLatencyCalculationTimestamp > Math.max(previousPendingOrdersLatency, previousUserDataStreamLatency)){
            previousUserDataStreamLatency = calculateUserDataStreamLatency();
            previousPendingOrdersLatency = calculatePendingPositionsLatency();
            previousLatencyCalculationTimestamp = currentExchangeTimestamp;
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

    public void addUserDataStream(UserDataResponse userDataStream, long currentExchangeTimestamp) {
        this.userDataStream.put(currentExchangeTimestamp, userDataStream);
    }

    public void addUserAction(OrderAction actionType, Order order, long currentLocalTimestamp) {

        Map<OrderAction, Order> tempMap = new HashMap<>();
        tempMap.put(actionType, new Order(order));

        for(Map.Entry<Long, ArrayList<Map<OrderAction, Order>>> entry : actionRequestsMap.entrySet()){
            if(entry.getKey().equals(currentLocalTimestamp)){
                entry.getValue().add(tempMap);
            } else {
                ArrayList<Map<OrderAction, Order>> tempList = new ArrayList<>();
                tempList.add(tempMap);
                actionRequestsMap.put(currentLocalTimestamp, tempList);
            }
        }
    }
}
