package com.localstrategy;

public class Position {
    private int id;
    private double entryPrice;
    private double stopLossPrice;
    private double initialStopLossPrice;
    private double size;
    private double closingPrice;
    private int direction;
    private int openIndex;
    private boolean breakEven;
    private double margin;
    private boolean filled;
    private boolean closed;
    private int filledIndex;
    private int closedIndex;
    private double profit;
    private boolean partiallyClosed;
    private String orderType;
    private long openTimestamp;
    private double hourlyInterestRate;
    private double borrowedAmount;

    private double trailingPct = 0;

    private int initialStopLossIndex;
    private int entryPriceIndex;

    private double totalUnpaidInterest;

    private long closeRequestTimestamp;

    public Position(int PositionId, String orderType, double entryPrice, double initialStopLossPrice, double size, double margin, int openIndex, long openTimestamp, double borrowedAmount) {
        this.id = PositionId;
        this.orderType = orderType;
        this.entryPrice = entryPrice;
        this.stopLossPrice = initialStopLossPrice;
        this.entryPriceIndex = openIndex;
        this.initialStopLossPrice = openIndex;
        this.initialStopLossPrice = initialStopLossPrice;
        this.size = size;
        this.closingPrice = 0;
        this.direction = entryPrice > stopLossPrice ? 1 : -1;
        this.openIndex = openIndex;
        this.openTimestamp = openTimestamp;
        this.borrowedAmount = borrowedAmount;
        this.breakEven = false;
        this.margin = margin;
        this.filled = orderType.equals("limit") ? false : true;
        this.closed = false;
        this.filledIndex = orderType.equals("limit") ? 0 : openIndex;
        this.closedIndex = 0;
        this.profit = 0;
        this.partiallyClosed = false;
        this.hourlyInterestRate = direction == 1 ? RiskManager.HOURLY_USDT_INTEREST_RATE / 100 : RiskManager.HOURLY_BTC_INTEREST_RATE / 100;
        this.totalUnpaidInterest = borrowedAmount * hourlyInterestRate * (direction == 1 ? 1 : entryPrice);
    }
    

    public double checkStopLoss(Candle candle, long closeTimestamp) {
        if (closed) {
            return 0;
        }

        if ((direction == 1 && candle.getLow() <= stopLossPrice) || (direction == -1 && candle.getHigh() >= stopLossPrice)) {
            return closePosition(candle.getIndex(), closeTimestamp, stopLossPrice);
        }

        return 0;
    }

    public double calculateProfit(double closePrice) {
        return (closePrice - entryPrice) * direction * size;
    }

    public double fillPosition(int fillTime) {
        if (!closed && !filled) {
            filled = true;
            filledIndex = fillTime;
            return margin;
        }
        return 0;
    }

    public double payCommission(){
        return size * StrategyExecutor.brokerCommissionRate / 100;
    }

    //FIXME: Check calculations
    public double partialClose(int closeIndex, long closeTimestamp, double closePrice, double positionPercentage) {
        if (closed || positionPercentage <= 0 || positionPercentage > 1) {
            return 0;
        }

        double tempCommission = payCommission();
        
        // Calculate the profit per unit size
        double profitPerUnitSize = (closePrice - entryPrice) * direction;
        
        double partialSize = positionPercentage * size;
        
        if (partialSize >= size) {
            return closePosition(closeIndex, closeTimestamp, closePrice);
        }
        
        margin *= partialSize / size;
        size -= partialSize;
        
        double partialProfit = partialSize * profitPerUnitSize;

        tempCommission -= payCommission();
        
        long positionLengthInHours = (closeTimestamp - openTimestamp) / 1000 / 60 / 60;
        tempCommission += partialSize * (1 + hourlyInterestRate) * positionLengthInHours;

        profit += partialProfit;

        partiallyClosed = true;
        
        return partialProfit - tempCommission;
}

    public double closePosition(int closeTime, long closeTimestamp, double closePrice) {
        if (closed) {
            return 0;
        }

        closed = true;
        closedIndex = closeTime;
        closingPrice = closePrice;

        if(!filled){
            return 0;
        }

        profit += calculateProfit(closePrice);

        return profit - totalUnpaidInterest;
    }

    public void setTrailingPct(double price){
        this.trailingPct = Math.abs(entryPrice - stopLossPrice) / price;
    }

    public double getTrailingPct(){
        return this.trailingPct;
    }

    public double calculateRR(double closePrice){
        return (closePrice - entryPrice) / (entryPrice - initialStopLossPrice);
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

    public void setSize(double size){
        this.size = size;
    }

    public void setEntryPriceIndex(int entryPriceIndex) {
        this.entryPriceIndex = entryPriceIndex;
    }

    public long getOpenTimestamp(){
        return this.openTimestamp;
    }

    public void setMargin(double margin){
        this.margin = margin;
    }

    public double getBorrowedAmount(){
        return this.borrowedAmount;
    }

    public void setBorrowedAmount(double amount){
        this.borrowedAmount = amount;
    }

    public String getOrderType() {
        return this.orderType;
    }

    public int getId() {
        return this.id;
    }

    public double getEntryPrice() {
        return this.entryPrice;
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

    public int getDirection() {
        return this.direction;
    }

    public int getOpenIndex() {
        return this.openIndex;
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

    public boolean getPartiallyClosed(){
        return this.partiallyClosed;
    }
    
    public void setCloseRequestTimestamp(long timestamp){
        this.closeRequestTimestamp = timestamp;
    }
    
    public long getCloseRequestTimestamp(){
        return this.closeRequestTimestamp;
    }

    public double getTotalUnpaidInterest(){
        return this.totalUnpaidInterest;
    }

    //FIXME: borrowedAmount doesn't account for partial closes
    public void increaseUnpaidInterest(double currentPrice){
        this.totalUnpaidInterest += borrowedAmount * hourlyInterestRate * (direction == 1 ? 1 : currentPrice);
    }
}
