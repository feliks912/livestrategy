package com.localstrategy;

import com.localstrategy.util.enums.OrderSide;
import com.localstrategy.util.enums.OrderStatus;
import com.localstrategy.util.enums.OrderType;
import com.localstrategy.util.enums.RejectionReason;
import com.localstrategy.util.helper.TierManager;

import java.util.ArrayList;

public class Order implements Cloneable{

    private static long lastId = 0;
    private long id;
    private double openPrice;
    private double size;
    private OrderSide direction;
    private double borrowCollateral;
    private OrderType orderType;
    private long openTimestamp;
    private double hourlyInterestRate;
    private double appropriateUnitPositionValue;
    private double fillPrice;
    private long fillTimestamp;
    private double totalUnpaidInterest;
    private boolean isStopLoss;
    private RejectionReason rejectionReason;
    private OrderStatus status;
    private boolean automaticBorrow;
    private double marginBuyBorrowAmount = 0;
    private boolean autoRepayAtCancel = true;

    public Order(){
        this.id = lastId++;
    }

    public Order(
            double openPrice,
            OrderSide direction,
            boolean automaticBorrow,
            boolean isStopLoss,
            double size,
            OrderType orderType,
            double borrowCollateral,
            double appropriateUnitPositionValue,
            long openTimestamp) {

        this.id = lastId++;
        this.orderType = orderType;
        this.automaticBorrow = automaticBorrow; //Stop-losses don't borrow funds as the funds are already borrowed
        this.isStopLoss = isStopLoss;
        this.status = OrderStatus.NEW;
        this.openPrice = openPrice;
        this.size = size;
        this.direction = direction;
        this.appropriateUnitPositionValue = appropriateUnitPositionValue;
        this.borrowCollateral = borrowCollateral;
        this.openTimestamp = openTimestamp;
        this.hourlyInterestRate = direction.equals(OrderSide.BUY) ? TierManager.HOURLY_USDT_INTEREST_RATE / 100 : TierManager.HOURLY_BTC_INTEREST_RATE / 100;
        this.totalUnpaidInterest = appropriateUnitPositionValue * hourlyInterestRate * (direction.equals(OrderSide.BUY) ? 1 : openPrice);
    }

    @Override
    public Order clone() {
        try {
            return (Order) super.clone();
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
            return null;
        }
    }

    public double getMarginBuyBorrowAmount() {
        return marginBuyBorrowAmount;
    }

    public void setMarginBuyBorrowAmount(double marginBuyBorrowAmount) {
        this.marginBuyBorrowAmount = marginBuyBorrowAmount;
    }



    public long getId() {
        return this.id;
    }

    public void setId(long id) {
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

    public double getBorrowCollateral() {
        return this.borrowCollateral;
    }

    public void setBorrowCollateral(double borrowCollateral) {
        this.borrowCollateral = borrowCollateral;
    }

    public OrderType getType() {
        return this.orderType;
    }

    public void setType(OrderType orderType) {
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

    public double getAppropriateUnitPositionValue() {
        return this.appropriateUnitPositionValue;
    }

    public void setAppropriateUnitPositionValue(double appropriateUnitPositionValue) {
        this.appropriateUnitPositionValue = appropriateUnitPositionValue;
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
        this.totalUnpaidInterest += appropriateUnitPositionValue * hourlyInterestRate * (direction.equals(OrderSide.BUY) ? 1 : currentPrice);
    }

    public static ArrayList<Order> deepCopyOrderList(ArrayList<Order> originalList) {
        ArrayList<Order> newList = new ArrayList<>();

        for (Order pos : originalList) {
            Order newPos = pos.clone();
            newList.add(newPos);
        }
        return newList;
    }
    
}
