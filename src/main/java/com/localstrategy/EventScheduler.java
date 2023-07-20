package com.localstrategy;

import com.localstrategy.util.enums.EventType;
import com.localstrategy.util.types.Event;

import java.util.PriorityQueue;

public class EventScheduler {

    //FIXME: Might be issues when adding the first element.
        //  When there's no first element the LatencyHandler can produce any latency.
        //  The first event must be the first exchange transaction whose latency is 0.

    private final PriorityQueue<Event> exchangeQueue;
    private final PriorityQueue<Event> localQueue;

    private Event lastExchangeEvent;
    private Event lastLocalEvent;


    public EventScheduler(LatencyHandler latencyHandler) {
        exchangeQueue = new PriorityQueue<>();
        localQueue = new PriorityQueue<>();
    }


    public void addExchangeEvent(Event event){
        event.setEventLatency(event.getEventType().equals(EventType.TRANSACTION) ? 0 : LatencyHandler.calculateLatency(event, lastExchangeEvent));
        exchangeQueue.add(event);

        lastExchangeEvent = event;
    }

    public void addLocalEvent(Event event){
        event.setEventLatency(LatencyHandler.calculateLatency(event, lastLocalEvent));
        localQueue.add(event);

        lastLocalEvent = event;
    }


    public Event getNextExchangeEvent(){
        return exchangeQueue.poll();
    }

    public Event getNextLocalEvent(){
        return localQueue.poll();
    }
}
