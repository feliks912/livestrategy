package com.localstrategy;

import com.localstrategy.util.types.Event;

import java.util.Random;

public class LatencyHandler {

    //TODO:


    //TODO: Implement latency walls. That one will be fun because we probably won't end up using this individualistic, one-by-one implementation. Rather the latencies will be calculated for the entire day up-front, and all events will draw from those
    //FIXME: Add static Exchange processing time to all action requests, but include it before calculating the latency rule (or after, does it matter?)

    private final int EXHANGE_PROCESSING_TIME_MEAN = 5; //5ms average processing time

    private final int PACKET_DELIVEY_TIME_MEAN = 30; //TODO: here we define our parameters for latency distribution
    private static final Random random = new Random();

    public static int calculateLatency(Event event, Event lastEvent){

        int latency;
        long latencyRule = lastEvent.getDelayedTimestamp() - event.getTimestamp(); // ln+1 => tn + ln - tn+1

        do{
            latency = calculateLatency();
        }while(latency < latencyRule);

        return latency;
    }

    private static int calculateLatency(){ //FIXME: Where did I get these numbers from? Unconfirmed.
        double latency;
        do{
            latency = 25 + random.nextGaussian() * 10;
        } while(latency < 15 || latency > 35);

        return (int) latency;
    }

}
