package com.localstrategy.types;

public class SingleTransaction {
    private double price;
    private double amount;
    private long timestamp;

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


    @Override
    public String toString() {
        return "{" +
            ", price='" + getPrice() + "'" +
            ", amount='" + getAmount() + "'" +
            ", timestamp='" + getTimestamp() + "'" +
            "}";
    }


}
