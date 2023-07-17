package com.localstrategy;

import com.localstrategy.util.enums.OrderSide;
import com.localstrategy.util.enums.OrderType;
import com.localstrategy.util.enums.RejectionReason;
import com.localstrategy.util.enums.OrderStatus;
import com.localstrategy.util.types.Candle;

import java.util.ArrayList;

public class Position {
    private int id;
    private double openPrice;
    private double stopLossPrice;
    private double initialStopLossPrice;
    private double size;
    private double closingPrice = 0;
    private OrderSide direction;
    private int openIndex;
    private boolean breakEven = false;
    private double margin;
    private boolean filled = false;
    private boolean closed = false;
    private int filledIndex = 0;
    private int closedIndex = 0;
    private double profit = 0;
    private boolean partiallyClosed = false;
    private OrderType orderType;
    private long openTimestamp;
    private double hourlyInterestRate;
    private double borrowedAmount;
    private double fillPrice;
    private long fillTimestamp;
    private long closeTimestamp;
    private double exchangeLatency;
    private boolean activeStopLoss;
    private double trailingPct;
    private boolean closedBeforeStopLoss;
    private long closedBeforeStopLossTimestamp;
    private int initialStopLossIndex;
    private int entryPriceIndex;
    private int tick;
    private double totalUnpaidInterest;
    private long closeRequestTimestamp;
    private boolean reversed;
    private boolean isStopLoss;

    private RejectionReason rejectionReason;

    private OrderStatus status;

    private boolean automaticBorrow = true;
    private boolean autoRepayAtCancel = true;

    public Position(
            double openPrice,
            double initialStopLossPrice,
            boolean isStopLoss,
            double size,
            OrderType orderType,
            double margin,
            double borrowedAmount,
            int positionId) {

        this.id = positionId;
        this.orderType = orderType;
        this.stopLossPrice = initialStopLossPrice;
        this.isStopLoss = isStopLoss;
        this.initialStopLossPrice = initialStopLossPrice;
        this.automaticBorrow = isStopLoss ? false : true; //Stoplosses don't borrow
        this.status = OrderStatus.NEW;
        this.openPrice = openPrice;
        this.size = size;
        this.direction = openPrice > stopLossPrice ? OrderSide.BUY : OrderSide.SELL;
        this.borrowedAmount = borrowedAmount;
        this.margin = margin;
        this.hourlyInterestRate = direction.equals(OrderSide.BUY) ? UserAssets.HOURLY_USDT_INTEREST_RATE / 100 : UserAssets.HOURLY_BTC_INTEREST_RATE / 100;
        this.totalUnpaidInterest = borrowedAmount * hourlyInterestRate * (direction.equals(OrderSide.BUY) ? 1 : openPrice);
    }

    public Position(Position other) {
        this.id = other.id;
        this.openPrice = other.openPrice;
        this.stopLossPrice = other.stopLossPrice;
        this.initialStopLossPrice = other.initialStopLossPrice;
        this.size = other.size;
        this.closingPrice = other.closingPrice;
        this.direction = other.direction;
        this.openIndex = other.openIndex;
        this.breakEven = other.breakEven;
        this.margin = other.margin;
        this.filled = other.filled;
        this.closed = other.closed;
        this.filledIndex = other.filledIndex;
        this.closedIndex = other.closedIndex;
        this.profit = other.profit;
        this.partiallyClosed = other.partiallyClosed;
        this.orderType = other.orderType;
        this.openTimestamp = other.openTimestamp;
        this.hourlyInterestRate = other.hourlyInterestRate;
        this.borrowedAmount = other.borrowedAmount;
        this.fillPrice = other.fillPrice;
        this.fillTimestamp = other.fillTimestamp;
        this.closeTimestamp = other.closeTimestamp;
        this.exchangeLatency = other.exchangeLatency;
        this.activeStopLoss = other.activeStopLoss;
        this.trailingPct = other.trailingPct;
        this.closedBeforeStopLoss = other.closedBeforeStopLoss;
        this.closedBeforeStopLossTimestamp = other.closedBeforeStopLossTimestamp;
        this.initialStopLossIndex = other.initialStopLossIndex;
        this.entryPriceIndex = other.entryPriceIndex;
        this.tick = other.tick;
        this.totalUnpaidInterest = other.totalUnpaidInterest;
        this.closeRequestTimestamp = other.closeRequestTimestamp;
        this.reversed = other.reversed;
        this.isStopLoss = other.isStopLoss;
        this.status = other.status;
        this.automaticBorrow = other.automaticBorrow;
        this.autoRepayAtCancel = other.autoRepayAtCancel;
        this.rejectionReason = other.rejectionReason;
    }
    

    public double checkStopLoss(Candle candle, long closeTimestamp) {
        if (closed) {
            return 0;
        }

        if ((direction.equals(OrderSide.BUY) && candle.getLow() <= stopLossPrice) || (direction.equals(OrderSide.SELL) && candle.getHigh() >= stopLossPrice)) {
            return closePosition(stopLossPrice, closeTimestamp);
        }

        return 0;
    }

    public double calculateProfit(double closePrice) {
        return (closePrice - fillPrice) * size * (direction.equals(OrderSide.BUY) ? 1 : -1);
    }

    public double fillPosition(double fillPrice, long fillTime) {
        if (!closed && !filled) {
            filled = true;
            this.fillPrice = fillPrice;
            fillTimestamp = fillTime;
            return margin;
        }
        return 0;
    }

    public double closePosition(double closePrice, long closeTimestamp) {
        if (closed) {
            return 0;
        }

        closed = true;
        this.closeTimestamp = closeTimestamp;
        closingPrice = closePrice;

        if (!filled) {
            return 0;
        }

        profit += calculateProfit(closePrice);

        return profit;
    }

    public double cancelPosition(long closeTimestamp) {
        if (filled) {
            return 0;
        }

        return closePosition(openPrice, closeTimestamp);
    }

    public void setTrailingPct(double price) {
        this.trailingPct = Math.abs(openPrice - stopLossPrice) / price;
    }

    public double getTrailingPct() {
        return this.trailingPct;
    }

    public double calculateRR(double closePrice) {
        return (closePrice - fillPrice) / (fillPrice - initialStopLossPrice);
    }

    public int getInitialStopLossIndex() {
        return this.initialStopLossIndex;
    }

    public void setInitialStopLossIndex(int initialStopLossIndex) {
        this.initialStopLossIndex = initialStopLossIndex;
    }

    public int getEntryPriceIndex() {
        return this.entryPriceIndex;
    }

    public void setSize(double size) {
        this.size = size;
    }

    public void setEntryPriceIndex(int entryPriceIndex) {
        this.entryPriceIndex = entryPriceIndex;
    }

    public long getOpenTimestamp() {
        return this.openTimestamp;
    }

    public void setMargin(double margin) {
        this.margin = margin;
    }

    public double getBorrowedAmount() {
        return this.borrowedAmount;
    }

    public void setBorrowedAmount(double amount) {
        this.borrowedAmount = amount;
    }

    public OrderType getOrderType() {
        return this.orderType;
    }

    public int getId() {
        return this.id;
    }

    public double getOpenPrice() {
        return this.openPrice;
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

    public double getSize() {
        return this.size;
    }

    public double getClosingPrice() {
        return this.closingPrice;
    }

    public OrderSide getDirection() {
        return this.direction;
    }

    public int getOpenIndex() {
        return this.openIndex;
    }

    public boolean isBreakEven() {
        return this.breakEven;
    }

    public boolean isBreakevenSet() {
        return this.breakEven;
    }

    public void setBreakevenFlag(boolean breakEven) {
        this.breakEven = breakEven;
    }

    public double getMargin() {
        return this.margin;
    }

    public boolean isFilled() {
        return this.filled;
    }

    public boolean isClosed() {
        return this.closed;
    }

    public int getFilledIndex() {
        return this.filledIndex;
    }

    public int getClosedIndex() {
        return this.closedIndex;
    }

    public double getProfit() {
        return this.profit;
    }

    public boolean getPartiallyClosed() {
        return this.partiallyClosed;
    }
    
    public void setCloseRequestTimestamp(long timestamp) {
        this.closeRequestTimestamp = timestamp;
    }
    
    public long getCloseRequestTimestamp() {
        return this.closeRequestTimestamp;
    }

    public double getTotalUnpaidInterest() {
        return this.totalUnpaidInterest;
    }

    // FIXME: borrowedAmount doesn't account for partial closes
    public void increaseUnpaidInterest(double currentPrice) {
        this.totalUnpaidInterest += borrowedAmount * hourlyInterestRate * (direction.equals(OrderSide.BUY) ? 1 : currentPrice);
    }

    public void setStoplossActive() {
        this.activeStopLoss = true;
    }

    public boolean isStoplossActive() {
        return this.activeStopLoss;
    }

    public double getExchangeLatency() {
        return this.exchangeLatency;
    }

    public void setClosedBeforeStoploss(long closedBeforeStoplossTimestamp) {
        this.closedBeforeStopLoss = true;
        this.closedBeforeStopLossTimestamp = closedBeforeStoplossTimestamp;
    }

    public long getClosedBeforeStoplossTimestamp() {
        return this.closedBeforeStopLossTimestamp;
    }

    public boolean isClosedBeforeStoploss() {
        return this.closedBeforeStopLoss;
    }

    public int getTick() {
        return this.tick;
    }

    public void setReversed(boolean reversed) {
        this.reversed = reversed;
    }

    public boolean isReversed() {
        return this.reversed;
    }

    public double getFillPrice() {
        return this.fillPrice;
    }

    public void setFillPrice(double fillPrice) {
        this.fillPrice = fillPrice;
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

    public void setAutoRepayAtCancel(boolean autoRepayAtCancel) {
        this.autoRepayAtCancel = autoRepayAtCancel;
    }

    public OrderStatus getStatus(){
        return this.status;
    }

    public void setStatus(OrderStatus status){
        this.status = status;
    }

    public boolean isStopLoss(){
        return this.isStopLoss;
    }

    public void setOrderType(OrderType type){
        this.orderType = type;
    }

    public void setRejectionReason(RejectionReason reason){
        this.rejectionReason = rejectionReason;
    }

    public RejectionReason getRejectionReason(){
        return this.rejectionReason;
    }

    public static ArrayList<Position> deepCopyPositionList(ArrayList<Position> originalList) {
        ArrayList<Position> newList = new ArrayList<>();

        for (Position pos : originalList) {
            // Assuming that all fields in Position are primitive types or immutable objects
            // so a shallow copy is fine.
            Position newPos = new Position(pos);
            newList.add(newPos);
        }
        return newList;
    }
    
}
