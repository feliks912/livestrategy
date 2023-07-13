package com.localstrategy.types;

public class Candle {
    private double open;
    private double high;
    private double low;
    private double close;
    private int volume;
    private int tick;
    private int index;
    private long timestamp;
    private long lastTransactionId;

    public long getLastTransactionId(){
        return this.lastTransactionId;
    }

    public void setLastTransactionId(long lastTransactionId){
        this.lastTransactionId = lastTransactionId;
    }

    public int getTick() {
        return this.tick;
    }

    public void setTick(int tick) {
        this.tick = tick;
    }

    public double getOpen() {
        return this.open;
    }

    public void setOpen(double open) {
        this.open = open;
    }

    public double getHigh() {
        return this.high;
    }

    public void setHigh(double high) {
        this.high = high;
    }

    public double getLow() {
        return this.low;
    }

    public void setLow(double low) {
        this.low = low;
    }

    public long getTimestamp() {
        return this.timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public double getClose() {
        return this.close;
    }

    public void setClose(double close) {
        this.close = close;
    }

    public int getVolume() {
        return this.volume;
    }

    public void setVolume(int volume) {
        this.volume = volume;
    }

    public int getIndex() {
        return this.index;
    }

    public void setIndex(int index) {
        this.index = index;
    }


    @Override
    public String toString() {
        return "{" +
            " open='" + getOpen() + "'" +
            ", high='" + getHigh() + "'" +
            ", low='" + getLow() + "'" +
            ", close='" + getClose() + "'" +
            ", volume='" + getVolume() + "'" +
            ", tick='" + getTick() + "'" +
            ", index='" + getIndex() + "'" +
            ", timestamp='" + getTimestamp() + "'" +
            "}";
    }

}

    
