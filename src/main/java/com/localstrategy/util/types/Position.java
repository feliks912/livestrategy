package com.localstrategy.util.types;

import com.localstrategy.util.enums.*;

import java.util.ArrayList;

public class Position implements Cloneable {
    private long id;
    private double openPrice;
    private double stopLossPrice;
    private double initialStopLossPrice;
    private double size;
    private double closingPrice = 0;
    private OrderSide direction;
    private boolean breakEven = false;
    private double borrowCollateral;
    private boolean filled = false;
    private boolean closed = false;
    private double profit = 0;
    private boolean partiallyClosed = false;
    private OrderType orderType;
    private long openTimestamp;
    private double hourlyInterestRate;
    private double appropriateUnitPositionValue;
    private double fillPrice;
    private long fillTimestamp;
    private long closeTimestamp;
    private boolean activeStopLoss;
    private boolean closedBeforeStopLoss;
    private double totalUnpaidInterest;
    private boolean reversed;
    private boolean isStopLoss;
    private RejectionReason rejectionReason;
    private boolean stopLossRequestSent = false;
    private boolean automaticBorrow;
    private boolean autoRepayAtCancel = true;

    private double marginBuyBorrowAmount = 0;

    private boolean cancelled = false;

    private Order entryOrder;
    private Order stopLossOrder;

    private Order closeOrder;

    private PositionGroup group = PositionGroup.NEW;

    private static long positionId = 0;

    public Position(
            double openPrice,
            double initialStopLossPrice,
            double size,
            OrderType orderType,
            double borrowCollateral,
            double appropriateUnitPositionValue,
            long openTimestamp) {

        this.id = positionId++;
        this.orderType = orderType;
        this.stopLossPrice = initialStopLossPrice;
        this.initialStopLossPrice = initialStopLossPrice;
        this.openPrice = openPrice;
        this.openTimestamp = openTimestamp;
        this.size = size;
        this.direction = openPrice > stopLossPrice ? OrderSide.BUY : OrderSide.SELL;
        this.appropriateUnitPositionValue = appropriateUnitPositionValue;
        this.borrowCollateral = borrowCollateral;
    
        this.entryOrder = new Order(
            openPrice,
            direction,
            true,
            false,
            size, 
            orderType,
            borrowCollateral,
            appropriateUnitPositionValue,
            openTimestamp,
            OrderPurpose.ENTRY
        );

        this.stopLossOrder = new Order(
            stopLossPrice, 
            direction == OrderSide.BUY ? OrderSide.SELL : OrderSide.BUY, 
            false,
            true,
            size,
            OrderType.LIMIT,
            borrowCollateral,
            appropriateUnitPositionValue,
            openTimestamp,
            OrderPurpose.STOP
        );
    }

    public Order createCloseOrder(SingleTransaction transaction){
        Order closeOrder = new Order(
                transaction.price(),
                this.stopLossOrder.getDirection(),
                false,
                false,
                size + (entryOrder.getDirection().equals(OrderSide.SELL) ? entryOrder.getTotalUnpaidInterest() : 0),
                OrderType.MARKET,
                borrowCollateral,
                appropriateUnitPositionValue,
                transaction.timestamp(),
                OrderPurpose.CLOSE
        );
        return closeOrder;
    }

    @Override
    protected Position clone() throws CloneNotSupportedException {
        try {
            Position clonedPosition = (Position) super.clone();

            clonedPosition.entryOrder = this.entryOrder.clone();
            clonedPosition.stopLossOrder = this.stopLossOrder.clone();
            if (this.closeOrder != null) {
                clonedPosition.closeOrder = this.closeOrder.clone();
            }

            return clonedPosition;
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
            return null;
        }
    }



    public double closePosition(long closeTimestamp){
        if(closed || !entryOrder.getStatus().equals(OrderStatus.FILLED)){
            return 0;
        }

        if(stopLossOrder.getStatus().equals(OrderStatus.FILLED)){
            this.profit = (stopLossOrder.getFillPrice() - entryOrder.getFillPrice()) * size * (direction.equals(OrderSide.BUY) ? 1 : -1);
        } else if(closeOrder != null && closeOrder.getStatus().equals(OrderStatus.FILLED)) {
            this.profit = (closeOrder.getFillPrice() - entryOrder.getFillPrice()) * size * (direction.equals(OrderSide.BUY) ? 1 : -1);
        }
        closed = true;

        this.closeTimestamp = closeTimestamp;
        return profit;
    }

    public double getMarginBuyBorrowAmount() {
        return marginBuyBorrowAmount;
    }

    public void setMarginBuyBorrowAmount(double marginBuyBorrowAmount) {
        this.marginBuyBorrowAmount = marginBuyBorrowAmount;
    }

    public PositionGroup getGroup() {
        return group;
    }

    public void setGroup(PositionGroup group) {
        this.group = group;
    }

    public boolean isStopLossRequestSent(){
        return this.stopLossRequestSent;
    }

    public void setStopLossRequestSent(boolean sent){
        this.stopLossRequestSent = sent;
    }

    public boolean isCancelled(){
        return this.cancelled;
    }

    public void setCancelled(boolean cancelled){
        this.cancelled = cancelled;
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


    public long getId() {
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

    public double getBorrowCollateral() {
        return this.borrowCollateral;
    }

    public void setBorrowCollateral(double borrowCollateral) {
        this.borrowCollateral = borrowCollateral;
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

    public Order getStopOrder() {
        return this.stopLossOrder;
    }

    public void setStopOrder(Order stopLossOrder) {
        this.stopLossOrder = stopLossOrder;
    }

    public Order getCloseOrder() { return this.closeOrder; }

    public void setCloseOrder(Order closeOrder) { this.closeOrder = closeOrder; }

    public static ArrayList<Position> deepCopyPositionList(ArrayList<Position> originalList) throws CloneNotSupportedException {
        ArrayList<Position> newList = new ArrayList<>();

        for (Position pos : originalList) {
            Position newPos = pos.clone();
            newList.add(newPos);
        }
        return newList;
    }
    
}
