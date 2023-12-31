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

    private long previousDelayedTimestamp = 0;

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
            default -> {
                long eventTime = event.getTimestamp();
                if(previousDelayedTimestamp > eventTime + currentLatency){
                    System.out.println("Latency error.");
                    event.setEventLatency((int) (currentLatency + (previousDelayedTimestamp - eventTime - currentLatency)));
                } else {
                    event.setEventLatency(currentLatency);
                }
                previousDelayedTimestamp = event.getDelayedTimestamp();
            }
//            case ACTION_RESPONSE, USER_DATA_STREAM -> {
//                event.setEventLatency(currentLatency + EXCHANGE_PROCESSING_TIME);
//                if(event.getDelayedTimestamp() > previousDelayedTimestamp){
//                    System.out.println("Latency error.");
//                }
//
//                previousDelayedTimestamp = event.getDelayedTimestamp();
//            }
//            case ACTION_REQUEST -> {
//
//                int totalLatency = LOCAL_PROCESSING_TIME;
//
//                //TODO: This break stuff. We're checking if stoploss is created before an entry order, and this makes it not so.
////                if(event.getActionRequest().equals(OrderAction.CREATE_ORDER)
////                        && event.getOrder().isAutomaticBorrow()
////                        && event.getOrder().getPurpose().equals(OrderPurpose.ENTRY)){
////                    totalLatency += EXCHANGE_PROCESSING_TIME; //Compensating for borrowing
////                }
//
//                //event.setEventLatency(currentLatency + totalLatency);
//
//                if(event.getDelayedTimestamp() > previousDelayedTimestamp){
//                    System.out.println("Latency error.");
//                }
//                previousDelayedTimestamp = event.getDelayedTimestamp();
//
//            }
        }


        eventQueue.add(event);
    }

    public Event getNextEvent(){

        Event event = eventQueue.poll();

        return event;
    }
}
