package com.localstrategy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.localstrategy.util.enums.OrderAction;
import com.localstrategy.util.types.UserDataResponse;

import java.util.ArrayList;

public class LatencyHandler {

    //TODO: Additional latency on automaticBorrow
    //TODO: It's possible multiple positions get parsed at once. We still need to process them in sequence.

    private int TRANSACTION_LATENCY = 0;
    private int TRADE_EXECUTION_LATENCY = 0; //request -> execution
    private int TRADE_REPORT_LATENCY = 0; //request -> response
    private int USER_DATA_LATENCY = 0;
    private int BORROW_LATENCY = 0;

    private Map<Long, UserDataResponse> userDataStream = new HashMap<Long, UserDataResponse>();  //UserDataStream gets parsed from the exchange to the user
    
    private Map<Long, Map<OrderAction, ArrayList<Position>>> actionRequestsMap = new HashMap<Long, Map<OrderAction, ArrayList<Position>>>(); //actionRequestsMap get parsed from client to the exchange

    private int previousUserDataStreamLatency;
    private int previousPendingPositionsLatency;
    private long previousLatencyCalculationTimestamp;

    private static Random random = new Random();

    public LatencyHandler() {

    }

    //TODO: Thanks ChatGPT
    public ArrayList<UserDataResponse> getDelayedUserDataStream(long currentLocalTimestamp) {

        ArrayList<UserDataResponse> userStreamsToReturn = new ArrayList<UserDataResponse>();

        ArrayList<Long> keysToRemove = new ArrayList<Long>();

        // Search through the userDataStream map.
        for (Map.Entry<Long, UserDataResponse> entry : userDataStream.entrySet()) {
            if (currentLocalTimestamp - entry.getKey() > previousUserDataStreamLatency) {
                // If the current time minus the key (the timestamp when the data was stored)
                // is larger than the current latency, return the data and remove it from the map.
                keysToRemove.add(entry.getKey());
                userStreamsToReturn.add(entry.getValue());
            }
        }

        for(Long keyToRemove : keysToRemove){
            userDataStream.remove(keyToRemove);
        }

        // If no suitable entry was found, return null.
        return userStreamsToReturn;
    }

    public ArrayList<Map<OrderAction, ArrayList<Position>>> getDelayedUserActionRequests(long currentExchangeTimestamp) {
        ArrayList<Map<OrderAction, ArrayList<Position>>> actionsToReturn = new ArrayList<Map<OrderAction, ArrayList<Position>>>();
        ArrayList<Long> keysToRemove = new ArrayList<Long>();

        for (Map.Entry<Long, Map<OrderAction, ArrayList<Position>>> entry : actionRequestsMap.entrySet()) {
            if (currentExchangeTimestamp - entry.getKey() > previousPendingPositionsLatency) {
                // If the current time minus the key (the timestamp when the data was stored)
                // is larger than the current latency, return the data and remove it from the map.
                actionsToReturn.add(entry.getValue());
                keysToRemove.add(entry.getKey());
            }
        }

        for(Long keyToRemove : keysToRemove) {
            actionRequestsMap.remove(keyToRemove);
        }

        return actionsToReturn;
    }

    
    public void recalculateLatencies(long currentExchangeTimestamp){
        if(currentExchangeTimestamp - previousLatencyCalculationTimestamp > Math.max(previousPendingPositionsLatency, previousUserDataStreamLatency)){
            previousUserDataStreamLatency = calculateUserDataStreamLatency();
            previousPendingPositionsLatency = calculatePendingPositionsLatency();
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

    public void addUserAction(OrderAction actionType, ArrayList<Position> pendingPositions, long currentLocalTimestamp) {

        Map<OrderAction, ArrayList<Position>> tempMap = new HashMap<OrderAction, ArrayList<Position>>();
        tempMap.put(actionType, pendingPositions);

        this.actionRequestsMap.put(currentLocalTimestamp, tempMap);
    }

}
