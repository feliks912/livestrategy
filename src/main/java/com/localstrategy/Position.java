package com.localstrategy;

import com.localstrategy.util.enums.OrderSide;
import com.localstrategy.util.enums.OrderType;
import com.localstrategy.util.enums.RejectionReason;

import java.util.ArrayList;

public class Position {
    private int id;
    private double openPrice;
    private double stopLossPrice;
    private double initialStopLossPrice;
    private double size;
    private double closingPrice = 0;
    private OrderSide direction;
    private boolean breakEven = false;
    private double margin;
    private boolean filled = false;
    private boolean closed = false;
    private double profit = 0;
    private boolean partiallyClosed = false;
    private OrderType orderType;
    private long openTimestamp;
    private double hourlyInterestRate;
    private double borrowedAmount;
    private double fillPrice;
    private long fillTimestamp;
    private long closeTimestamp;
    private boolean activeStopLoss;
    private boolean closedBeforeStopLoss;
    private double totalUnpaidInterest;
    private long closeRequestTimestamp;
    private boolean reversed;
    private boolean isStopLoss;
    private RejectionReason rejectionReason;
    private boolean automaticBorrow;
    private boolean autoRepayAtCancel = true;

    private Order entryOrder;
    private Order stopLossOrder;

    private Order closeOrder;

    public Position(
            double openPrice,
            double initialStopLossPrice,
            double size,
            OrderType orderType,
            double margin,
            double borrowedAmount,
            int positionId,
            long openTimestamp) {

        this.id = positionId;
        this.orderType = orderType;
        this.stopLossPrice = initialStopLossPrice;
        this.initialStopLossPrice = initialStopLossPrice;
        this.openPrice = openPrice;
        this.openTimestamp = openTimestamp;
        this.size = size;
        this.direction = openPrice > stopLossPrice ? OrderSide.BUY : OrderSide.SELL;
        this.borrowedAmount = borrowedAmount;
        this.margin = margin;
    
        this.entryOrder = new Order(
            openPrice,
            direction,
            false, 
            size, 
            orderType, 
            margin, 
            borrowedAmount, 
            openTimestamp
        );

        this.stopLossOrder = new Order(
            stopLossPrice, 
            direction == OrderSide.BUY ? OrderSide.SELL : OrderSide.BUY, 
            true, 
            size, 
            OrderType.LIMIT, 
            margin, 
            borrowedAmount, 
            openTimestamp
        );
    }

    public Position(Position other) {
        this.id = other.id;
        this.openPrice = other.openPrice;
        this.stopLossPrice = other.stopLossPrice;
        this.initialStopLossPrice = other.initialStopLossPrice;
        this.size = other.size;
        this.closingPrice = other.closingPrice;
        this.direction = other.direction;
        this.breakEven = other.breakEven;
        this.margin = other.margin;
        this.filled = other.filled;
        this.closed = other.closed;
        this.profit = other.profit;
        this.partiallyClosed = other.partiallyClosed;
        this.orderType = other.orderType;
        this.openTimestamp = other.openTimestamp;
        this.hourlyInterestRate = other.hourlyInterestRate;
        this.borrowedAmount = other.borrowedAmount;
        this.fillPrice = other.fillPrice;
        this.fillTimestamp = other.fillTimestamp;
        this.closeTimestamp = other.closeTimestamp;
        this.activeStopLoss = other.activeStopLoss;
        this.closedBeforeStopLoss = other.closedBeforeStopLoss;
        this.totalUnpaidInterest = other.totalUnpaidInterest;
        this.closeRequestTimestamp = other.closeRequestTimestamp;
        this.reversed = other.reversed;
        this.isStopLoss = other.isStopLoss;
        this.automaticBorrow = other.automaticBorrow;
        this.autoRepayAtCancel = other.autoRepayAtCancel;
        this.rejectionReason = other.rejectionReason;
        this.entryOrder = new Order(other.entryOrder);
        this.stopLossOrder = new Order(other.stopLossOrder);
        this.closeOrder = new Order(other.closeOrder);
    }

    public double calculateProfit(double closePrice) {
        return (closePrice - fillPrice) * size * (direction.equals(OrderSide.BUY) ? 1 : -1);
    }

    public double calculateRR(double closePrice) {
        return (closePrice - fillPrice) / (fillPrice - initialStopLossPrice);
    }

    public void setClosedBeforeStoploss() {
        this.closedBeforeStopLoss = true;
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

    public double getStopLossPrice() {
        return this.stopLossPrice;
    }

    public void setStopLossPrice(double stopLossPrice) {
        this.stopLossPrice = stopLossPrice;
    }

    public double getInitialStopLossPrice() {
        return this.initialStopLossPrice;
    }

    public void setInitialStopLossPrice(double initialStopLossPrice) {
        this.initialStopLossPrice = initialStopLossPrice;
    }

    public double getSize() {
        return this.size;
    }

    public void setSize(double size) {
        this.size = size;
    }

    public double getClosingPrice() {
        return this.closingPrice;
    }

    public void setClosingPrice(double closingPrice) {
        this.closingPrice = closingPrice;
    }

    public OrderSide getDirection() {
        return this.direction;
    }

    public void setDirection(OrderSide direction) {
        this.direction = direction;
    }

    public boolean isBreakEven() {
        return this.breakEven;
    }

    public boolean getBreakEven() {
        return this.breakEven;
    }

    public void setBreakEven(boolean breakEven) {
        this.breakEven = breakEven;
    }

    public double getMargin() {
        return this.margin;
    }

    public void setMargin(double margin) {
        this.margin = margin;
    }

    public boolean isFilled() {
        return this.filled;
    }

    public boolean getFilled() {
        return this.filled;
    }

    public void setFilled(boolean filled) {
        this.filled = filled;
    }

    public boolean isClosed() {
        return this.closed;
    }

    public boolean getClosed() {
        return this.closed;
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
    }

    public double getProfit() {
        return this.profit;
    }

    public void setProfit(double profit) {
        this.profit = profit;
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

    public long getCloseTimestamp() {
        return this.closeTimestamp;
    }

    public void setCloseTimestamp(long closeTimestamp) {
        this.closeTimestamp = closeTimestamp;
    }

    public boolean isActiveStopLoss() {
        return this.activeStopLoss;
    }

    public boolean getActiveStopLoss() {
        return this.activeStopLoss;
    }

    public void setActiveStopLoss(boolean activeStopLoss) {
        this.activeStopLoss = activeStopLoss;
    }

    public boolean isClosedBeforeStopLoss() {
        return this.closedBeforeStopLoss;
    }

    public boolean getClosedBeforeStopLoss() {
        return this.closedBeforeStopLoss;
    }

    public void setClosedBeforeStopLoss(boolean closedBeforeStopLoss) {
        this.closedBeforeStopLoss = closedBeforeStopLoss;
    }

    public double getTotalUnpaidInterest() {
        return this.totalUnpaidInterest;
    }

    public void setTotalUnpaidInterest(double totalUnpaidInterest) {
        this.totalUnpaidInterest = totalUnpaidInterest;
    }

    public long getCloseRequestTimestamp() {
        return this.closeRequestTimestamp;
    }

    public void setCloseRequestTimestamp(long closeRequestTimestamp) {
        this.closeRequestTimestamp = closeRequestTimestamp;
    }

    public boolean isReversed() {
        return this.reversed;
    }

    public boolean getReversed() {
        return this.reversed;
    }

    public void setReversed(boolean reversed) {
        this.reversed = reversed;
    }

    public boolean isAutomaticBorrow() {
        return this.automaticBorrow;
    }

    public boolean getAutomaticBorrow() {
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

    public Order getEntryOrder() {
        return this.entryOrder;
    }

    public void setEntryOrder(Order entryOrder) {
        this.entryOrder = entryOrder;
    }

    public Order getStopLossOrder() {
        return this.stopLossOrder;
    }

    public void setStopLossOrder(Order stopLossOrder) {
        this.stopLossOrder = stopLossOrder;
    }

    public Order getCloseOrder() { return this.closeOrder; }

    public void setCloseOrder(Order closeOrder) { this.closeOrder = closeOrder; }

    public static ArrayList<Position> deepCopyPositionList(ArrayList<Position> originalList) {
        ArrayList<Position> newList = new ArrayList<>();

        for (Position pos : originalList) {
            Position newPos = new Position(pos);
            newList.add(newPos);
        }
        return newList;
    }
    
}
