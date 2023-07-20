package com.localstrategy.util.types;

import com.localstrategy.Order;
import com.localstrategy.util.enums.ActionResponse;
import com.localstrategy.util.enums.EventType;
import com.localstrategy.util.enums.OrderAction;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class Event implements Comparable<Event> {

    private final EventType eventType;

    private static final AtomicLong eventIdCounter = new AtomicLong(0);

    private final long eventId;

    private final long eventTimestamp;
    private int eventLatency;
    private long eventDelayedTimestamp;

    private final SingleTransaction transaction;
    private final UserDataStream userDataStream;
    private final ArrayList<Map<OrderAction, Order>> actionRequests;
    private final ArrayList<Map<ActionResponse, Order>> actionResponses;

    private Event(Builder builder) {
        this.eventId = eventIdCounter.incrementAndGet();

        this.eventType = builder.eventType;

        this.eventTimestamp = builder.eventTimestamp;

        this.transaction = builder.transaction;
        this.userDataStream = builder.userDataStream;
        this.actionRequests = builder.actionRequests;
        this.actionResponses = builder.actionResponses;
    }

    @Override
    public int compareTo(Event other) {
        int timestampComparison = Long.compare(this.eventDelayedTimestamp, other.eventDelayedTimestamp);
        return (timestampComparison != 0) ? timestampComparison : Long.compare(this.eventId, other.eventId);
    }

    public static class Builder {
        private EventType eventType;
        private final long eventTimestamp;
        private SingleTransaction transaction;
        private UserDataStream userDataStream;
        private ArrayList<Map<OrderAction, Order>> actionRequests;
        private ArrayList<Map<ActionResponse, Order>> actionResponses;

        public Builder(long eventTimestamp) {
            this.eventTimestamp = eventTimestamp;
        }

        public Builder withTransaction(SingleTransaction transaction) {
            this.transaction = transaction;
            this.eventType = EventType.TRANSACTION;
            return this;
        }

        public Builder withUserDataStream(UserDataStream userDataStream) {
            this.userDataStream = userDataStream;
            this.eventType = EventType.USER_DATA_STREAM;
            return this;
        }

        public Builder withActionRequests(ArrayList<Map<OrderAction, Order>> actionRequests) {
            this.actionRequests = actionRequests;
            this.eventType = EventType.ACTION_REQUEST;
            return this;
        }

        public Builder withActionResponses(ArrayList<Map<ActionResponse, Order>> actionResponses) {
            this.actionResponses = actionResponses;
            this.eventType = EventType.ACTION_RESPONSE;
            return this;
        }

        public Event build() {
            return new Event(this);
        }
    }

    public void setEventLatency(int latency){
        this.eventLatency = latency;
        this.eventDelayedTimestamp = latency + eventTimestamp;
    }

    public EventType getEventType(){
        return this.eventType;
    }

    public long getTimestamp() { return this.eventTimestamp; }

    public long getDelayedTimestamp() { return this.eventDelayedTimestamp; }
}
