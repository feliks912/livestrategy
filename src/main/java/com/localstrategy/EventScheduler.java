package com.localstrategy;

import com.localstrategy.util.enums.EventDestination;
import com.localstrategy.util.enums.EventType;
import com.localstrategy.util.types.Event;

import java.util.PriorityQueue;

public class EventScheduler {

    private final PriorityQueue<Event> eventQueue = new PriorityQueue<>();

    private Event lastExchangeEvent;
    private Event lastLocalEvent;

    public void addEvent(Event event) {

        boolean isTransaction = event.getType().equals(EventType.TRANSACTION);
        boolean isDestinationExchange = event.getDestination().equals(EventDestination.EXCHANGE);

        if (isDestinationExchange && isTransaction) {
            event.setEventLatency(0); //Exchange transaction
        } else {
            Event lastEvent = isDestinationExchange ? lastExchangeEvent : lastLocalEvent;
            event.setEventLatency(LatencyHandler.calculateLatency(event, lastEvent));
        }

        eventQueue.add(event);

        if(isDestinationExchange && !isTransaction){ //Exchange transactions don't get added to lastExchangeEvent so future events don't scan it when calculating latency. This is permitted since the exchange transaction is the origin event and all other events are a reaction to it. This way we can load the next transaction once we reach the current one.
            lastExchangeEvent = event;
        } else if(!isDestinationExchange){
            lastLocalEvent = event;
        }
    }

    public Event getNextEvent(){
        return eventQueue.poll();
    }

    public boolean isEmpty(){
        return eventQueue.isEmpty();
    }
}
