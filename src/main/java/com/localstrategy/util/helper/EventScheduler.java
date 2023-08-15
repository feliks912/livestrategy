package com.localstrategy.util.helper;

import com.localstrategy.LatencyProcessor;
import com.localstrategy.util.enums.EventDestination;
import com.localstrategy.util.types.Event;

import java.util.PriorityQueue;

public class EventScheduler {

    int MAXIMUM_INSTANTANEOUS_EVENT_COUNT = 15000; //It seems around 15000 transactions can pile up...
    int EXCHANGE_PROCESSING_TIME = 5; //5 ms
    int LOCAL_PROCESSING_TIME = 1; //1 ms

    private final PriorityQueue<Event> eventQueue = new PriorityQueue<>(MAXIMUM_INSTANTANEOUS_EVENT_COUNT);

    public void addEvent(Event event) {

        int currentLatency = LatencyProcessor.getCurrentLatency();

        switch (event.getType()){
            case TRANSACTION -> {
                if(event.getDestination().equals(EventDestination.EXCHANGE)) {
                    event.setEventLatency(0);
                    break;
                }
                event.setEventLatency(currentLatency);
            }
            case ACTION_RESPONSE, USER_DATA_STREAM -> event.setEventLatency(currentLatency + EXCHANGE_PROCESSING_TIME);
            case ACTION_REQUEST -> event.setEventLatency(currentLatency + LOCAL_PROCESSING_TIME);
        }

        eventQueue.add(event);
    }

    public Event getNextEvent(){
        return eventQueue.poll();
    }
}
