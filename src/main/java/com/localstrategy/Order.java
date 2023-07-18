package com.localstrategy;

import com.localstrategy.util.enums.OrderSide;
import com.localstrategy.util.enums.OrderStatus;
import com.localstrategy.util.enums.OrderType;
import com.localstrategy.util.enums.RejectionReason;

import java.util.ArrayList;

public class Order {
    private int id;
    private double openPrice;
    private double size;
    private OrderSide direction;
    private double margin;
    private OrderType orderType;
    private long openTimestamp;
    private double hourlyInterestRate;
    private double borrowedAmount;
    private double fillPrice;
    private long fillTimestamp;
    private double totalUnpaidInterest;
    private boolean isStopLoss;
    private RejectionReason rejectionReason;
    private OrderStatus status;
    private boolean automaticBorrow;
    private boolean autoRepayAtCancel = true;

    public Order(
            double openPrice,
            OrderSide direction,
            boolean isStopLoss,
            double size,
            OrderType orderType,
            double margin,
            double borrowedAmount,
            long openTimestamp) {
                
        this.orderType = orderType;
        this.isStopLoss = isStopLoss;
        this.automaticBorrow = !isStopLoss; //Stop-losses don't borrow funds as the funds are already borrowed
        this.status = OrderStatus.NEW;
        this.openPrice = openPrice;
        this.size = size;
        this.direction = direction;
        this.borrowedAmount = borrowedAmount;
        this.margin = margin;
        this.openTimestamp = openTimestamp;
        this.hourlyInterestRate = direction.equals(OrderSide.BUY) ? TierManager.HOURLY_USDT_INTEREST_RATE / 100 : TierManager.HOURLY_BTC_INTEREST_RATE / 100;
        this.totalUnpaidInterest = borrowedAmount * hourlyInterestRate * (direction.equals(OrderSide.BUY) ? 1 : openPrice);
    }

    public Order(Order other) {
        this.id = other.id;
        this.openPrice = other.openPrice;
        this.size = other.size;
        this.direction = other.direction;
        this.margin = other.margin;
        this.orderType = other.orderType;
        this.openTimestamp = other.openTimestamp;
        this.hourlyInterestRate = other.hourlyInterestRate;
        this.borrowedAmount = other.borrowedAmount;
        this.fillPrice = other.fillPrice;
        this.fillTimestamp = other.fillTimestamp;
        this.totalUnpaidInterest = other.totalUnpaidInterest;
        this.isStopLoss = other.isStopLoss;
        this.status = other.status;
        this.automaticBorrow = other.automaticBorrow;
        this.autoRepayAtCancel = other.autoRepayAtCancel;
        this.rejectionReason = other.rejectionReason;
    }

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public double getOpenPrice() {
        return this.openPrice;
    }

    public void setOpenPrice(double openPrice) {
        this.openPrice = openPrice;
    }

    public double getSize() {
        return this.size;
    }

    public void setSize(double size) {
        this.size = size;
    }

    public OrderSide getDirection() {
        return this.direction;
    }

    public void setDirection(OrderSide direction) {
        this.direction = direction;
    }

    public double getMargin() {
        return this.margin;
    }

    public void setMargin(double margin) {
        this.margin = margin;
    }

    public OrderType getOrderType() {
        return this.orderType;
    }

    public void setOrderType(OrderType orderType) {
        this.orderType = orderType;
    }

    public long getOpenTimestamp() {
        return this.openTimestamp;
    }

    public void setOpenTimestamp(long openTimestamp) {
        this.openTimestamp = openTimestamp;
    }

    public double getHourlyInterestRate() {
        return this.hourlyInterestRate;
    }

    public void setHourlyInterestRate(double hourlyInterestRate) {
        this.hourlyInterestRate = hourlyInterestRate;
    }

    public double getBorrowedAmount() {
        return this.borrowedAmount;
    }

    public void setBorrowedAmount(double borrowedAmount) {
        this.borrowedAmount = borrowedAmount;
    }

    public double getFillPrice() {
        return this.fillPrice;
    }

    public void setFillPrice(double fillPrice) {
        this.fillPrice = fillPrice;
    }

    public long getFillTimestamp() {
        return this.fillTimestamp;
    }

    public void setFillTimestamp(long fillTimestamp) {
        this.fillTimestamp = fillTimestamp;
    }

    public double getTotalUnpaidInterest() {
        return this.totalUnpaidInterest;
    }

    public void setTotalUnpaidInterest(double totalUnpaidInterest) {
        this.totalUnpaidInterest = totalUnpaidInterest;
    }

    public boolean isStopLoss() {
        return this.isStopLoss;
    }

    public void setIsStopLoss(boolean isStopLoss) {
        this.isStopLoss = isStopLoss;
    }

    public RejectionReason getRejectionReason() {
        return this.rejectionReason;
    }

    public void setRejectionReason(RejectionReason rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public OrderStatus getStatus() {
        return this.status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public boolean isAutomaticBorrow() {
        return this.automaticBorrow;
    }

    public void setAutomaticBorrow(boolean automaticBorrow) {
        this.automaticBorrow = automaticBorrow;
    }

    public boolean isAutoRepayAtCancel() {
        return this.autoRepayAtCancel;
    }

    public boolean getAutoRepayAtCancel() {
        return this.autoRepayAtCancel;
    }

    public void setAutoRepayAtCancel(boolean autoRepayAtCancel) {
        this.autoRepayAtCancel = autoRepayAtCancel;
    }


    // TODO: account for partial closes in borrowedAmount
    public void increaseUnpaidInterest(double currentPrice) {
        this.totalUnpaidInterest += borrowedAmount * hourlyInterestRate * (direction.equals(OrderSide.BUY) ? 1 : currentPrice);
    }

    public static ArrayList<Order> deepCopyOrderList(ArrayList<Order> originalList) {
        ArrayList<Order> newList = new ArrayList<>();

        for (Order pos : originalList) {
            Order newPos = new Order(pos);
            newList.add(newPos);
        }
        return newList;
    }
    
}
