package com.localstrategy.util.types;

import com.localstrategy.util.enums.ActionResponse;
import com.localstrategy.util.enums.EventDestination;
import com.localstrategy.util.enums.EventType;
import com.localstrategy.util.enums.OrderAction;

import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

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

    private final OrderAction request;
    private final ActionResponse response;

    private final Order order;

    // Common constructor
    private Event(EventType eventType,
                  long eventTimestamp,
                  EventDestination destination,
                  SingleTransaction transaction,
                  UserDataStream userDataStream,
                  OrderAction request,
                  ActionResponse response,
                  Order order) {
        this.eventId = eventIdCounter.incrementAndGet();
        this.eventType = eventType;
        this.eventTimestamp = eventTimestamp;
        this.destination = destination;
        this.transaction = transaction;
        this.userDataStream = userDataStream;
        this.request = request;
        this.response = response;
        this.order = order;
    }

    // Specific constructors
    public Event(long eventTimestamp, EventDestination destination, SingleTransaction transaction) {
        this(EventType.TRANSACTION, eventTimestamp, destination, transaction, null, null, null, null);
    }

    public Event(long eventTimestamp, EventDestination destination, UserDataStream userDataStream) {
        this(EventType.USER_DATA_STREAM, eventTimestamp, destination, null, userDataStream, null, null, null);
    }

    public Event(long eventTimestamp, EventDestination destination, OrderAction action, Order order) {
        this(EventType.ACTION_REQUEST, eventTimestamp, destination, null, null, action, null, order);
    }

    public Event(long eventTimestamp, EventDestination destination, ActionResponse response, Order order) {
        this(EventType.ACTION_RESPONSE, eventTimestamp, destination, null, null, null, response, order);
    }

    @Override
    public int compareTo(Event other) {
        int timestampComparison = Long.compare(this.eventDelayedTimestamp, other.eventDelayedTimestamp);
        return (timestampComparison != 0) ? timestampComparison : Long.compare(this.eventId, other.eventId);
    }

    public long getId(){
        return this.eventId;
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

    public long getDelayedTimestamp() {
        return eventDelayedTimestamp;
    }

    public SingleTransaction getTransaction() {
        return transaction;
    }

    public UserDataStream getUserDataStream() {
        return userDataStream;
    }

    public OrderAction getActionRequest() {
        return this.request;
    }

    public ActionResponse getActionResponse() {
        return this.response;
    }

    public Order getOrder() { return this.order; }

    @Override
    public String toString() {
//        String returnString = "Event{" +
//                "eventType=" + eventType +
//                ", eventId=" + eventId +
//                ", destination=" + destination + ": ";

        String returnString = destination + ": ";

        switch(eventType){
            case ACTION_REQUEST -> returnString = returnString.concat("Action= " + request + ", order Id= " + order.getId() + ", position Id= " + order.getPositionId() + ", order purpose= " + order.getPurpose() + ", order direction= " + order.getDirection());
            case ACTION_RESPONSE -> returnString = returnString.concat("Response= " + response + ", order Id= " + order.getId() + ", position Id= " + order.getPositionId() + ", order purpose= " + order.getPurpose() + ", order direction= " + order.getDirection());
            case TRANSACTION -> returnString = returnString.concat(transaction.toString());
            case USER_DATA_STREAM -> returnString = returnString.concat(userDataStream.userAssets().toString() + " " + userDataStream.updatedOrders().stream().map(o -> String.format("%d", o.getId())).collect(Collectors.joining(" ")));
        }

        return returnString;
    }
}
