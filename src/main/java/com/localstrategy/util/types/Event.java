package com.localstrategy.util.types;

import com.localstrategy.Order;
import com.localstrategy.util.enums.ActionResponse;
import com.localstrategy.util.enums.EventDestination;
import com.localstrategy.util.enums.EventType;
import com.localstrategy.util.enums.OrderAction;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class Event implements Comparable<Event> {

    private final EventType eventType;
    private static final AtomicLong eventIdCounter = new AtomicLong(0);

    private final long eventId;
    private final EventDestination destination;

    private final long eventTimestamp;
    private int eventLatency;
    private long eventDelayedTimestamp;

    private final SingleTransaction transaction;
    private final UserDataStream userDataStream;
    private final Map<OrderAction, Order> actionRequest;
    private final Map<ActionResponse, Order> actionResponse;

    // Common constructor
    private Event(EventType eventType, long eventTimestamp, EventDestination destination,
                  SingleTransaction transaction, UserDataStream userDataStream,
                  Map<OrderAction, Order> actionRequest, Map<ActionResponse, Order> actionResponse) {
        this.eventId = eventIdCounter.incrementAndGet();
        this.eventType = eventType;
        this.eventTimestamp = eventTimestamp;
        this.destination = destination;
        this.transaction = transaction;
        this.userDataStream = userDataStream;
        this.actionRequest = actionRequest;
        this.actionResponse = actionResponse;
    }

    // Specific constructors
    public Event(long eventTimestamp, EventDestination destination, SingleTransaction transaction) {
        this(EventType.TRANSACTION, eventTimestamp, destination, transaction, null, null, null);
    }

    public Event(long eventTimestamp, EventDestination destination, UserDataStream userDataStream) {
        this(EventType.USER_DATA_STREAM, eventTimestamp, destination, null, userDataStream, null, null);
    }

    public Event(long eventTimestamp, EventDestination destination, OrderAction action, Order order) {
        this(EventType.ACTION_REQUEST, eventTimestamp, destination, null, null, Collections.singletonMap(action, order), null);
    }

    public Event(long eventTimestamp, EventDestination destination, ActionResponse response, Order order) {
        this(EventType.ACTION_RESPONSE, eventTimestamp, destination, null, null, null, Collections.singletonMap(response, order));
    }

    @Override
    public int compareTo(Event other) {
        int timestampComparison = Long.compare(this.eventDelayedTimestamp, other.eventDelayedTimestamp);
        return (timestampComparison != 0) ? timestampComparison : Long.compare(this.eventId, other.eventId);
    }

    public void setEventLatency(int latency){
        this.eventLatency = latency;
        this.eventDelayedTimestamp = this.eventTimestamp + latency;
    }

    public EventType getType() {
        return eventType;
    }

    public EventDestination getDestination() {
        return destination;
    }

    public long getTimestamp() {
        return eventTimestamp;
    }

    public int getEventLatency() {
        return eventLatency;
    }

    public long getDelayedTimestamp() {
        return eventDelayedTimestamp;
    }

    public SingleTransaction getTransaction() {
        return transaction;
    }

    public UserDataStream getUserDataStream() {
        return userDataStream;
    }

    public Map<OrderAction, Order> getActionRequest() {
        return actionRequest;
    }

    public Map<ActionResponse, Order> getActionResponse() {
        return actionResponse;
    }

    @Override
    public String toString() {
        return "Event{" +
                "eventType=" + eventType +
                ", eventId=" + eventId +
                ", destination=" + destination +
                ", eventTimestamp=" + eventTimestamp +
                ", eventLatency=" + eventLatency +
                ", eventDelayedTimestamp=" + eventDelayedTimestamp +
                ", transaction=" + transaction +
                ", userDataStream=" + userDataStream +
                ", actionRequest=" + actionRequest +
                ", actionResponse=" + actionResponse +
                '}';
    }
}
