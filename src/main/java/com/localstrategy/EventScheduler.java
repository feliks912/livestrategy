package com.localstrategy;

import com.localstrategy.util.enums.EventDestination;
import com.localstrategy.util.enums.EventType;
import com.localstrategy.util.types.Event;

import java.util.PriorityQueue;

public class EventScheduler {

    //FIXME: Might be issues when adding the first element.
        //  When there's no first element the LatencyHandler can produce any latency.
        //  The first event must be the first exchange transaction whose latency is 0.

    private final PriorityQueue<Event> eventQueue;

    private Event lastExchangeEvent;
    private Event lastLocalEvent;


    public EventScheduler() {
        eventQueue = new PriorityQueue<>();
    }


    public void addEvent(Event event) {
        boolean isTransaction = event.getEventType().equals(EventType.TRANSACTION);
        boolean isDestinationExchange = event.getDestination().equals(EventDestination.EXCHANGE);

        if (isDestinationExchange && isTransaction) {
            event.setEventLatency(0);
        } else {
            Event lastEvent = isTransaction ? lastExchangeEvent : lastLocalEvent;
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
