package com.localstrategy;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import com.localstrategy.util.enums.OrderAction;
import com.localstrategy.util.types.UserDataResponse;

import java.util.ArrayList;

public class ExchangeLatencyHandler {

    private int TRANSACTION_LATENCY = 0;
    private int TRADE_EXECUTION_LATENCY = 0; //request -> execution
    private int TRADE_REPORT_LATENCY = 0; //request -> response
    private int USER_DATA_LATENCY = 0;

    private Map<Long, UserDataResponse> userDataStream;  //UserDataStream gets parsed from the exchange to the 
    private Map<Long, ArrayList<Position>> pendingPositions; //Pending positions get parsed from client to the exchange

    private Map<Long, Map<OrderAction, ArrayList<Position>>> positionsUpdateMap;

    private int previousUserDataStreamLatency;
    private int previousPendingPositionsLatency;
    private long previousLatencyCalculationTimestamp;

    private static Random random = new Random();

    public ExchangeLatencyHandler() {

    }

    //TODO: Thanks ChatGPT
    public UserDataResponse getDelayedUserDataStream(long currentLocalTimestamp) {
        // Search through the userDataStream map.
        for (Map.Entry<Long, UserDataResponse> entry : userDataStream.entrySet()) {
            if (currentLocalTimestamp - entry.getKey() > previousUserDataStreamLatency) {
                // If the current time minus the key (the timestamp when the data was stored)
                // is larger than the current latency, return the data and remove it from the map.
                UserDataResponse data = entry.getValue();
                userDataStream.remove(entry.getKey());
                return data;
            }
        }
        // If no suitable entry was found, return null.
        return null;
    }

    public Map<OrderAction, ArrayList<Position>> getDelayedUserActionRequests(long currentExchangeTimestamp) {
        // Search through the pendingPositions map.
        for (Map.Entry<Long, Map<OrderAction, ArrayList<Position>>> entry : positionsUpdateMap.entrySet()) {
            if (currentExchangeTimestamp - entry.getKey() > previousPendingPositionsLatency) {
                // If the current time minus the key (the timestamp when the data was stored)
                // is larger than the current latency, return the data and remove it from the map.
                Map<OrderAction, ArrayList<Position>> positions = entry.getValue();
                pendingPositions.remove(entry.getKey());
                return positions;
            }
        }
        // If no suitable entry was found, return null.
        return null;
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

    public void addPendingPositions(OrderAction actionType, ArrayList<Position> pendingPositions, long currentLocalTimestamp) {

        Map<OrderAction, ArrayList<Position>> tempMap = new HashMap<OrderAction, ArrayList<Position>>();
        tempMap.put(actionType, pendingPositions);

        this.positionsUpdateMap.put(currentLocalTimestamp, tempMap);
    }

}
