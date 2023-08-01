package com.localstrategy;

import com.localstrategy.util.enums.EventDestination;
import com.localstrategy.util.enums.EventType;
import com.localstrategy.util.types.Event;

import java.util.PriorityQueue;

public class EventScheduler {

    private final PriorityQueue<Event> eventQueue = new PriorityQueue<>();

    public void addEvent(Event event) {

        boolean isTransaction = event.getType().equals(EventType.TRANSACTION);
        boolean isDestinationExchange = event.getDestination().equals(EventDestination.EXCHANGE);

        if (isDestinationExchange && isTransaction) {
            event.setEventLatency(0); //Exchange transaction
        } else {
            event.setEventLatency(LatencyProcessor.getCurrentLatency());
        }



        eventQueue.add(event);
    }

    public Event getNextEvent(){
        return eventQueue.poll();
    }
}
