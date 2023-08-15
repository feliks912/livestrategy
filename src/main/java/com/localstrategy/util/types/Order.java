package com.localstrategy.util.types;

import com.localstrategy.util.enums.*;
import com.localstrategy.util.helper.TierManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;

public class Order implements Cloneable {

    private static final int PRECISION_GENERAL = 8;
    private static final int PRECISION_PRICE = 2;
    
    private static long lastId = 0;
    private long id;
    private BigDecimal openPrice;
    private BigDecimal size;
    private OrderSide direction;

    private OrderType orderType;
    private long openTimestamp;
    private BigDecimal hourlyInterestRate;
    private BigDecimal borrowCollateral;
    private BigDecimal appropriateUnitPositionValue;
    private BigDecimal marginBuyBorrowAmount = BigDecimal.ZERO;
    private BigDecimal fillPrice;
    private long fillTimestamp;
    private BigDecimal totalUnpaidInterest = BigDecimal.ZERO;
    private boolean isStopLoss;
    private RejectionReason rejectionReason;
    private OrderStatus status;
    private boolean automaticBorrow;

    private long positionId;

    private boolean autoRepayAtCancel = true;

    private OrderPurpose purpose;

    public Order(
            BigDecimal openPrice,
            OrderSide direction,
            boolean automaticBorrow,
            boolean isStopLoss,
            BigDecimal size,
            OrderType orderType,
            BigDecimal borrowCollateral,
            BigDecimal appropriateUnitPositionValue,
            long openTimestamp,
            OrderPurpose purpose,
            long positionId) {

        this.id = lastId++;
        this.orderType = orderType;
        this.automaticBorrow = automaticBorrow;
        this.isStopLoss = isStopLoss;
        this.status = OrderStatus.NEW;
        this.openPrice = openPrice.setScale(PRECISION_PRICE, RoundingMode.HALF_UP);
        this.size = size.setScale(PRECISION_GENERAL, RoundingMode.HALF_UP);
        this.direction = direction;
        this.appropriateUnitPositionValue = appropriateUnitPositionValue.setScale(PRECISION_GENERAL, RoundingMode.HALF_UP);
        this.borrowCollateral = borrowCollateral.setScale(PRECISION_GENERAL, RoundingMode.HALF_UP);
        this.openTimestamp = openTimestamp;
        this.purpose = purpose;
        this.positionId = positionId;

        this.hourlyInterestRate = direction.equals(OrderSide.BUY)
                ? TierManager.HOURLY_USDT_INTEREST_RATE_PCT.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP)
                : TierManager.HOURLY_BTC_INTEREST_RATE_PCT.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
    }

    public long getPositionId(){
        return this.positionId;
    }

    public OrderPurpose getPurpose() {
        return this.purpose;
    }

    public void initializeInterest() {
        this.totalUnpaidInterest = this.appropriateUnitPositionValue.multiply(this.hourlyInterestRate).divide(BigDecimal.valueOf(100), PRECISION_GENERAL, RoundingMode.HALF_UP);
    }

    public void increaseUnpaidInterest() {
        this.totalUnpaidInterest = this.totalUnpaidInterest.add(this.appropriateUnitPositionValue.multiply(this.hourlyInterestRate).divide(BigDecimal.valueOf(100), PRECISION_GENERAL, RoundingMode.HALF_UP));
    }

    @Override
    public Order clone() {
        try {
            Order clonedOrder = (Order) super.clone();

            // Clone BigDecimal fields
            clonedOrder.openPrice = this.openPrice.setScale(this.openPrice.scale(), RoundingMode.HALF_UP);
            clonedOrder.size = this.size.setScale(this.size.scale(), RoundingMode.HALF_UP);
            clonedOrder.appropriateUnitPositionValue = this.appropriateUnitPositionValue.setScale(this.appropriateUnitPositionValue.scale(), RoundingMode.HALF_UP);
            clonedOrder.borrowCollateral = this.borrowCollateral.setScale(this.borrowCollateral.scale(), RoundingMode.HALF_UP);
            clonedOrder.marginBuyBorrowAmount = this.marginBuyBorrowAmount.setScale(this.marginBuyBorrowAmount.scale(), RoundingMode.HALF_UP);

            // Handle other fields as needed

            return clonedOrder;
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
            return null;
        }
    }


    public BigDecimal getMarginBuyBorrowAmount() {
        return marginBuyBorrowAmount;
    }

    public void setMarginBuyBorrowAmount(BigDecimal marginBuyBorrowAmount) {
        this.marginBuyBorrowAmount = marginBuyBorrowAmount.setScale(PRECISION_GENERAL, RoundingMode.HALF_UP);
    }

    public long getId() {
        return this.id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public BigDecimal getOpenPrice() {
        return this.openPrice;
    }

    public void setOpenPrice(BigDecimal openPrice) {
        this.openPrice = openPrice.setScale(PRECISION_PRICE, RoundingMode.HALF_UP);
    }

    public BigDecimal getSize() {
        return this.size;
    }

    public void setSize(BigDecimal size) {
        this.size = size.setScale(PRECISION_GENERAL, RoundingMode.HALF_UP);
    }

    public OrderSide getDirection() {
        return this.direction;
    }

    public void setDirection(OrderSide direction) {
        this.direction = direction;
    }

    public BigDecimal getBorrowCollateral() {
        return this.borrowCollateral;
    }

    public void setBorrowCollateral(BigDecimal borrowCollateral) {
        this.borrowCollateral = borrowCollateral.setScale(PRECISION_GENERAL, RoundingMode.HALF_UP);
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

    public BigDecimal getAppropriateUnitPositionValue() {
        return this.appropriateUnitPositionValue;
    }

    public void setAppropriateUnitPositionValue(BigDecimal appropriateUnitPositionValue) {
        this.appropriateUnitPositionValue = appropriateUnitPositionValue.setScale(PRECISION_GENERAL, RoundingMode.HALF_UP);
    }

    public BigDecimal getFillPrice() {
        return this.fillPrice;
    }

    public void setFillPrice(BigDecimal fillPrice) {
        this.fillPrice = fillPrice.setScale(PRECISION_PRICE, RoundingMode.HALF_UP);
    }

    public long getFillTimestamp() {
        return this.fillTimestamp;
    }

    public void setFillTimestamp(long fillTimestamp) {
        this.fillTimestamp = fillTimestamp;
    }

    public BigDecimal getTotalUnpaidInterest() {
        return this.totalUnpaidInterest;
    }

    public void setTotalUnpaidInterest(BigDecimal totalUnpaidInterest) {
        this.totalUnpaidInterest = totalUnpaidInterest.setScale(PRECISION_GENERAL, RoundingMode.HALF_UP);
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

    public static ArrayList<Order> deepCopyOrderList(ArrayList<Order> originalList) {
        ArrayList<Order> newList = new ArrayList<>();

        for (Order pos : originalList) {
            Order newPos = pos.clone();
            newList.add(newPos);
        }
        return newList;
    }
}