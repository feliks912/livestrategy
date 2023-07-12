package com.localstrategy;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.ArrayList;

/* Four latencies:
 *  user data - handles currency values, order responses
 *  order execution - handle positions
 */

//ExchangeLatencyHandler is supposed to parse events between the exchange and our local client. When an even is requested it is first stored inside the ExchangeLatencyHandler until the time has passed, when it becomes visible to the client or binance, dependent on the sender. The latency for each of four categories is recalculated every max(categories) seconds.

//For order execution and response, Position is parsed
//Or position list, basically the current state of all positions



public class ExchangeLatencyHandler {

    private Map<Long, UserDataStream> userDataStream;  //UserDataStream gets parsed from the exchange to the 
    private Map<Long, ArrayList<Position>> pendingPositions; //Pending positions get parsed from client to the exchange

    private Map<Long, Map<OrderAction, ArrayList<Position>>> positionsUpdateMap;

    private int previousUserDataStreamLatency;
    private int previousPendingPositionsLatency;
    private long previousLatencyCalculationTimestamp;

    private static Random random = new Random();

    public ExchangeLatencyHandler() {

    }

    //TODO: Thank ChatGPT
    public UserDataStream getDelayedUserDataStream(long currentLocalTimestamp) {
        // Search through the userDataStream map.
        for (Map.Entry<Long, UserDataStream> entry : userDataStream.entrySet()) {
            if (currentLocalTimestamp - entry.getKey() > previousUserDataStreamLatency) {
                // If the current time minus the key (the timestamp when the data was stored)
                // is larger than the current latency, return the data and remove it from the map.
                UserDataStream data = entry.getValue();
                userDataStream.remove(entry.getKey());
                return data;
            }
        }
        // If no suitable entry was found, return null.
        return null;
    }

    public Map<OrderAction, ArrayList<Position>> getDelayedLocalPositionsUpdate(long currentExchangeTimestamp) {
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

    public void addUserDataStream(UserDataStream userDataStream, long currentExchangeTimestamp) {
        this.userDataStream.put(currentExchangeTimestamp, userDataStream);
    }

    public void addPendingPositions(OrderAction actionType, ArrayList<Position> pendingPositions, long currentLocalTimestamp) {

        Map<OrderAction, ArrayList<Position>> tempMap = new HashMap<OrderAction, ArrayList<Position>>();
        tempMap.put(actionType, pendingPositions);

        this.positionsUpdateMap.put(currentLocalTimestamp, tempMap);
    }

}
