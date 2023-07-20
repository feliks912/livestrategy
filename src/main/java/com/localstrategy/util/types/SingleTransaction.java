package com.localstrategy.util.types;

public class SingleTransaction {
    private double price;
    private double amount;
    private long timestamp;

    private int latency;

    private boolean isWall;

    public SingleTransaction(){

    }

    public double getPrice() {
        return this.price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public double getAmount() {
        return this.amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public long getTimestamp() {
        return this.timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public void setLatency(int latency){
        this.latency = latency;
    }

    public int getLatency(){
        return this.latency;
    }

    public void setWall(boolean isWall){
        this.isWall = isWall;
    }

    public boolean isWall(){
        return this.isWall;
    }


    @Override
    public String toString() {
        return "SingleTransaction{" +
                "price=" + price +
                ", amount=" + amount +
                ", timestamp=" + timestamp +
                ", latency=" + latency +
                '}';
    }
}
