package com.localstrategy.util.types;

import com.localstrategy.util.enums.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;

public class Position implements Cloneable {
    private static final int PRECISION_GENERAL = 8;
    private static final int PRECISION_PRICE = 2;

    private long id;
    private BigDecimal openPrice;
    private BigDecimal stopLossPrice;
    private BigDecimal initialStopLossPrice;
    private BigDecimal size;
    private double closingPrice = 0;
    private OrderSide direction;
    private boolean breakEven = false;
    private BigDecimal borrowCollateral;
    private boolean filled = false;
    private boolean closed = false;
    private BigDecimal profit = BigDecimal.ZERO;
    private boolean partiallyClosed = false;
    private OrderType orderType;
    private long openTimestamp;
    private BigDecimal hourlyInterestRate;
    private BigDecimal appropriateUnitPositionValue;
    private BigDecimal fillPrice;
    private long fillTimestamp;
    private long closeTimestamp;
    private boolean activeStopLoss;
    private boolean closedBeforeStopLoss;
    private BigDecimal totalUnpaidInterest;
    private boolean reversed;
    private boolean isStopLoss;
    private RejectionReason rejectionReason;
    private boolean stopLossRequestSent = false;
    private boolean automaticBorrow;
    private boolean autoRepayAtCancel = true;

    private BigDecimal marginBuyBorrowAmount = BigDecimal.ZERO;

    private boolean cancelled = false;

    private Order entryOrder;
    private Order stopLossOrder;

    private Order closeOrder;

    private boolean repaid = false;
    private boolean repaidRequestSent = false;

    private double RR = 0;

    private PositionGroup group = PositionGroup.NEW;

    private boolean buyBackExecuted = false;

    private static long positionId = 0;

    public Position(
            BigDecimal openPrice,
            BigDecimal initialStopLossPrice,
            BigDecimal size,
            OrderType orderType,
            BigDecimal borrowCollateral,
            BigDecimal appropriateUnitPositionValue,
            long openTimestamp) {

        this.id = positionId++;
        this.orderType = orderType;
        this.stopLossPrice = initialStopLossPrice;
        this.initialStopLossPrice = initialStopLossPrice;
        this.openPrice = openPrice.setScale(PRECISION_PRICE, RoundingMode.HALF_UP);
        this.openTimestamp = openTimestamp;
        this.size = size.setScale(PRECISION_GENERAL, RoundingMode.HALF_UP);
        this.direction = openPrice.compareTo(stopLossPrice) > 0 ? OrderSide.BUY : OrderSide.SELL;
        this.appropriateUnitPositionValue = appropriateUnitPositionValue.setScale(PRECISION_GENERAL, RoundingMode.HALF_UP);
        this.borrowCollateral = borrowCollateral.setScale(PRECISION_GENERAL, RoundingMode.HALF_UP);

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
                OrderPurpose.ENTRY,
                id
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
                OrderPurpose.STOP,
                id
        );
    }

    public Order createCloseOrder(SingleTransaction transaction) {
        Order closeOrder = new Order(
                BigDecimal.valueOf(transaction.price()),
                this.stopLossOrder.getDirection(),
                false,
                false,
                size.add(entryOrder.getDirection().equals(OrderSide.SELL) ? entryOrder.getTotalUnpaidInterest() : BigDecimal.ZERO),
                OrderType.MARKET,
                borrowCollateral,
                appropriateUnitPositionValue,
                transaction.timestamp(),
                OrderPurpose.CLOSE,
                id
        );

        this.closeOrder = closeOrder;

        return closeOrder;
    }

    @Override
    protected Position clone() {
        try {
            Position clonedPosition = (Position) super.clone();

            // Clone BigDecimal fields
            clonedPosition.openPrice = this.openPrice.setScale(this.openPrice.scale(), RoundingMode.HALF_UP);
            clonedPosition.stopLossPrice = this.stopLossPrice.setScale(this.stopLossPrice.scale(), RoundingMode.HALF_UP);
            clonedPosition.initialStopLossPrice = this.initialStopLossPrice.setScale(this.initialStopLossPrice.scale(), RoundingMode.HALF_UP);
            clonedPosition.size = this.size.setScale(this.size.scale(), RoundingMode.HALF_UP);
            clonedPosition.appropriateUnitPositionValue = this.appropriateUnitPositionValue.setScale(this.appropriateUnitPositionValue.scale(), RoundingMode.HALF_UP);
            clonedPosition.borrowCollateral = this.borrowCollateral.setScale(this.borrowCollateral.scale(), RoundingMode.HALF_UP);
            clonedPosition.marginBuyBorrowAmount = this.marginBuyBorrowAmount.setScale(this.marginBuyBorrowAmount.scale(), RoundingMode.HALF_UP);

            // Clone other fields
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


    public double closePosition(long closeTimestamp) {

        if (closed || !entryOrder.getStatus().equals(OrderStatus.FILLED)) {
            return 0;
        }

        if (stopLossOrder.getStatus().equals(OrderStatus.FILLED)) {
            this.profit = (stopLossOrder.getFillPrice().subtract(entryOrder.getFillPrice())).multiply(size)
                    .multiply(direction.equals(OrderSide.BUY) ? BigDecimal.ONE : BigDecimal.ONE.negate());
//            if(profit.doubleValue() >= 0){
//                System.out.println("Stoplossed position resulted in positive profit of " + profit);
//            }
            this.closingPrice = stopLossOrder.getFillPrice().doubleValue();
        } else if (closeOrder != null && closeOrder.getStatus().equals(OrderStatus.FILLED)) {
            this.profit = (closeOrder.getFillPrice().subtract(entryOrder.getFillPrice())).multiply(size)
                    .multiply(direction.equals(OrderSide.BUY) ? BigDecimal.ONE : BigDecimal.ONE.negate());
            this.closingPrice = closeOrder.getFillPrice().doubleValue();
        }
        closed = true;

        this.closeTimestamp = closeTimestamp;

        calculateRR(0);

        return profit.doubleValue();
    }

    public double getRR() {
        return RR;
    }

    public double calculateProfit(double closePrice) {
        BigDecimal closePriceBigDecimal = BigDecimal.valueOf(closePrice);
        return (closePriceBigDecimal.subtract(fillPrice)).multiply(size)
                .multiply(direction.equals(OrderSide.BUY) ? BigDecimal.ONE : BigDecimal.ONE.negate()).doubleValue();
    }

    public boolean isBuyBackExecuted() {
        return buyBackExecuted;
    }

    public void setBuyBackExecuted(boolean buyBackExecuted) {
        this.buyBackExecuted = buyBackExecuted;
    }

    public boolean isRepaid() {
        return repaid;
    }

    public void setRepaid(boolean repaid) {
        this.repaid = repaid;
    }

    public boolean isRepayRequestSent() {
        return repaidRequestSent;
    }

    public void setRepayRequestSent(boolean repaidRequestSent) {
        this.repaidRequestSent = repaidRequestSent;
    }

    public double calculateRR(double currentPrice) {

        Order respectiveOrder;

        if(currentPrice == 0){
            if(stopLossOrder.getStatus().equals(OrderStatus.FILLED)){
                respectiveOrder = stopLossOrder;
            } else if(closeOrder != null){
                respectiveOrder = closeOrder;
            } else {
                this.RR = 0.0;
                return 0.0;
            }

            if(fillPrice.subtract(initialStopLossPrice).compareTo(BigDecimal.ZERO) == 0){
                this.RR = 0.0;
                return 0.0;
            }

            this.RR = respectiveOrder.getFillPrice().subtract(fillPrice).divide(fillPrice.subtract(initialStopLossPrice), PRECISION_PRICE, RoundingMode.HALF_UP).doubleValue();
        } else {
            double fillPriceDouble = fillPrice.doubleValue();
            this.RR = (currentPrice - fillPriceDouble) / (fillPriceDouble - initialStopLossPrice.doubleValue());
        }

        return RR;
    }

    public boolean isStopLoss() {
        return isStopLoss;
    }

    public void setStopLoss(boolean stopLoss) {
        isStopLoss = stopLoss;
    }

    public RejectionReason getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(RejectionReason rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public Order getEntryOrder() {
        return entryOrder;
    }

    public void setEntryOrder(Order entryOrder) {
        this.entryOrder = entryOrder;
    }

    public Order getStopOrder() {
        return stopLossOrder;
    }

    public void setStopOrder(Order stopLossOrder) {
        this.stopLossOrder = stopLossOrder;
    }

    public Order getCloseOrder() {
        return closeOrder;
    }

    public void setCloseOrder(Order closeOrder) {
        this.closeOrder = closeOrder;
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

    public BigDecimal getStopLossPrice() {
        return this.stopLossPrice;
    }

    public void setStopLossPrice(BigDecimal stopLossPrice) {
        this.stopLossPrice = stopLossPrice.setScale(PRECISION_PRICE, RoundingMode.HALF_UP);
    }

    public BigDecimal getInitialStopLossPrice() {
        return this.initialStopLossPrice;
    }

    public void setInitialStopLossPrice(BigDecimal initialStopLossPrice) {
        this.initialStopLossPrice = initialStopLossPrice.setScale(PRECISION_PRICE, RoundingMode.HALF_UP);
    }

    public BigDecimal getSize() {
        return this.size;
    }

    public void setSize(BigDecimal size) {
        this.size = size.setScale(PRECISION_GENERAL, RoundingMode.HALF_UP);
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

    public boolean isBreakEvenActive() {
        return this.breakEven;
    }

    public boolean getBreakEven() {
        return this.breakEven;
    }

    public void setBreakEvenStatus(boolean breakEven) {
        this.breakEven = breakEven;
    }

    public BigDecimal getBorrowCollateral() {
        return this.borrowCollateral;
    }

    public void setBorrowCollateral(BigDecimal borrowCollateral) {
        this.borrowCollateral = borrowCollateral.setScale(PRECISION_GENERAL, RoundingMode.HALF_UP);
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

    public BigDecimal getProfit() {
        return this.profit;
    }

    public void setProfit(BigDecimal profit) {
        this.profit = profit.setScale(PRECISION_GENERAL, RoundingMode.HALF_UP);
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

    public long getCloseTimestamp() {
        return this.closeTimestamp;
    }

    public void setCloseTimestamp(long closeTimestamp) {
        this.closeTimestamp = closeTimestamp;
    }

    public boolean isStopLossActive() {
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

    public BigDecimal getTotalUnpaidInterest() {
        return this.totalUnpaidInterest;
    }

    public void setTotalUnpaidInterest(BigDecimal totalUnpaidInterest) {
        this.totalUnpaidInterest = totalUnpaidInterest.setScale(PRECISION_GENERAL, RoundingMode.HALF_UP);
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

    public BigDecimal getMarginBuyBorrowAmount() {
        return marginBuyBorrowAmount;
    }

    public void setMarginBuyBorrowAmount(BigDecimal marginBuyBorrowAmount) {
        this.marginBuyBorrowAmount = marginBuyBorrowAmount.setScale(PRECISION_GENERAL, RoundingMode.HALF_UP);
    }

    public PositionGroup getGroup() {
        return group;
    }

    public void setGroup(PositionGroup group) {
        this.group = group;
    }

    public boolean isStopLossRequestSent() {
        return this.stopLossRequestSent;
    }

    public void setStopLossRequestSent(boolean sent) {
        this.stopLossRequestSent = sent;
    }

    public boolean isCancelled() {
        return this.cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public Order createEntryOrder() {
        return entryOrder.clone();
    }

    public Order createStopLossOrder() {
        return stopLossOrder.clone();
    }

    public Order createCloseOrder() {
        return closeOrder != null ? closeOrder.clone() : null;
    }

    public static ArrayList<Position> deepCopyPositionList(ArrayList<Position> originalList) throws CloneNotSupportedException {
        ArrayList<Position> newList = new ArrayList<>();

        for (Position pos : originalList) {
            Position newPos = pos.clone();
            newList.add(newPos);
        }
        return newList;
    }

    @Override
    public String toString() {
        return "Position{" +
                "id=" + id +
                ", openPrice=" + openPrice +
                ", stopLossPrice=" + stopLossPrice +
                ", initialStopLossPrice=" + initialStopLossPrice +
                ", size=" + size +
                ", closingPrice=" + closingPrice +
                ", direction=" + direction +
                ", breakEven=" + breakEven +
                ", borrowCollateral=" + borrowCollateral +
                ", filled=" + filled +
                ", closed=" + closed +
                ", profit=" + profit +
                ", partiallyClosed=" + partiallyClosed +
                ", orderType=" + orderType +
                ", openTimestamp=" + openTimestamp +
                ", hourlyInterestRate=" + hourlyInterestRate +
                ", appropriateUnitPositionValue=" + appropriateUnitPositionValue +
                ", fillPrice=" + fillPrice +
                ", fillTimestamp=" + fillTimestamp +
                ", closeTimestamp=" + closeTimestamp +
                ", activeStopLoss=" + activeStopLoss +
                ", closedBeforeStopLoss=" + closedBeforeStopLoss +
                ", totalUnpaidInterest=" + totalUnpaidInterest +
                ", reversed=" + reversed +
                ", isStopLoss=" + isStopLoss +
                ", rejectionReason=" + rejectionReason +
                ", stopLossRequestSent=" + stopLossRequestSent +
                ", automaticBorrow=" + automaticBorrow +
                ", autoRepayAtCancel=" + autoRepayAtCancel +
                ", marginBuyBorrowAmount=" + marginBuyBorrowAmount +
                ", cancelled=" + cancelled +
                ", entryOrder=" + entryOrder +
                ", stopLossOrder=" + stopLossOrder +
                ", closeOrder=" + closeOrder +
                ", repaid=" + repaid +
                ", repaidRequestSent=" + repaidRequestSent +
                ", RR=" + RR +
                ", group=" + group +
                '}';
    }
}