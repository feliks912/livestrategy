package com.localstrategy;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import com.localstrategy.util.enums.OrderAction;
import com.localstrategy.util.types.UserDataResponse;

import java.util.ArrayList;

public class LatencyHandler {

    //TODO: add additional latency on automaticBorrow = true
    //FIXME: Test userDataStream latency. It shouldn't be (lol shouldn't...) much different than order execution reponse latency

    private int TRANSACTION_LATENCY_MEAN = 0;
    private int TRANSACTION_LATENCY_STD = 0;

    private int TRADE_EXECUTION_LATENCY_MEAN = 0;
    private int TRADE_EXECUTION_LATENCY_STD = 0;

    private int TRADE_REPORT_LATENCY_MEAN = 0;
    private int TRADE_REPORT_LATENCY_STD = 0;

    private int USER_DATA_LATENCY_MEAN = 0;
    private int USER_DATA_LATENCY_STD = 0;

    private int BORROW_LATENCY_MEAN = 0;
    private int BORROW_LATENCY_STD = 0;

    private Map<Long, UserDataResponse> userDataStream = new HashMap<Long, UserDataResponse>();  //UserDataStream gets parsed from the exchange to the user
    
    private Map<Long, ArrayList<Map<OrderAction, Position>>> actionRequestsMap = new HashMap<Long, ArrayList<Map<OrderAction, Position>>>(); //actionRequestsMap get parsed from client to the exchange

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

    public ArrayList<Map<OrderAction, Position>> getDelayedUserActionRequests(long currentExchangeTimestamp) {

        ArrayList<Map<OrderAction, Position>> actionsToReturn = new ArrayList<Map<OrderAction, Position>>();
        ArrayList<Long> keysToRemove = new ArrayList<Long>();

        for (Map.Entry<Long, ArrayList<Map<OrderAction, Position>>> entry : actionRequestsMap.entrySet()) {
            if (currentExchangeTimestamp - entry.getKey() > previousPendingPositionsLatency) {
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

    public void addUserAction(OrderAction actionType, Position position, long currentLocalTimestamp) {

        Map<OrderAction, Position> tempMap = new HashMap<OrderAction, Position>();
        tempMap.put(actionType, new Position(position));

        for(Map.Entry<Long, ArrayList<Map<OrderAction, Position>>> entry : actionRequestsMap.entrySet()){
            if(entry.getKey().equals(currentLocalTimestamp)){
                entry.getValue().add(tempMap);
            } else {
                ArrayList<Map<OrderAction, Position>> tempList = new ArrayList<Map<OrderAction, Position>>();
                tempList.add(tempMap);
                actionRequestsMap.put(currentLocalTimestamp, tempList);
            }
        }
    }

}
