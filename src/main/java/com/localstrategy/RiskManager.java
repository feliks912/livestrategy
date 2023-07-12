package com.localstrategy;

import java.util.ArrayList;

import com.binance.api.client.domain.OrderSide;

public class RiskManager {

    private static final double[] MAX_BORROWING_AMOUNT_USDT_TIERS = { 90000, 180000, 270000, 360000, 450000, 540000, 630000, 720000, 810000, 900000 };
    private static final double[] MAX_BORROWING_AMOUNT_BTC_TIERS = { 7.2, 14.4, 21.6, 28.8, 36, 43.2, 50.4, 57.6, 64.8, 72 };
    private static final double[] LEVERAGE_TIERS = { 10, 8.91, 8.05, 7.36, 6.79, 6.31, 5.91, 5.57, 5.26, 5 };

    private int currentTier;
    private double currentLeverage;
    private double totalBorrowedAmountUsdt;
    private double totalBorrowedAmountBtc;

    ArrayList<Position> positions = new ArrayList<Position>();

    public RiskManager(ArrayList<Position> positions) {
        this.positions = positions;
        updateTier(0);
    }

    private void updateTier(int newTier) {
        this.currentTier = newTier;
        this.currentLeverage = LEVERAGE_TIERS[currentTier];
    }

    public void checkAndUpdateTier(double totalBorrowedAmountUsdt, double totalBorrowedAmountBtc) {
        int i = 0;
        for (i = 0; i < MAX_BORROWING_AMOUNT_USDT_TIERS.length; i++) {
            if (totalBorrowedAmountUsdt <= MAX_BORROWING_AMOUNT_USDT_TIERS[i] && totalBorrowedAmountBtc <= MAX_BORROWING_AMOUNT_BTC_TIERS[i]) {
                break;
            }
        }
        if(i == 10){i--;}
        if (i != currentTier) {
            updateTier(i);
        }
    }

    //Run on each new position
    public void calculateBorrows() {
        totalBorrowedAmountUsdt = 0;
        totalBorrowedAmountBtc = 0;

        for (Position position : positions) {
            if (!position.isClosed()) {
                if (position.getDirection().equals(OrderSide.BUY)) { // Long
                    totalBorrowedAmountUsdt += position.getBorrowedAmount();
                } else {
                    totalBorrowedAmountBtc += position.getBorrowedAmount();
                }
            }
        }
        checkAndUpdateTier(totalBorrowedAmountUsdt, totalBorrowedAmountBtc);

        return;
    }

    //TODO: Optimize speed, keep sum of variables.
    public double calculateMarginLevel(double currentPrice, double freeMargin) {
        double totalBTCAmount = 0;
        double totalUnpaidInterest = 0;
        double totalBorrowedBtc = 0;
        double totalBorrowedUsdt = 0;

        for (Position position : positions) {
            if (!position.isClosed()) {
                
                totalBTCAmount += position.getSize();
                totalUnpaidInterest += position.getTotalUnpaidInterest();

                if (position.getDirection().equals(OrderSide.BUY)) { // Long
                    totalBorrowedUsdt += position.getBorrowedAmount();
                } else {
                    totalBorrowedBtc += position.getBorrowedAmount();
                }

                totalUnpaidInterest += position.getTotalUnpaidInterest();
            }
        }

        //In USDT
        double totalAssetValue = freeMargin + totalBTCAmount * currentPrice;
        double totalBorrowedAssetValue = totalBorrowedUsdt + totalBorrowedBtc * currentPrice;

        double marginLevel = totalAssetValue / (totalBorrowedAssetValue + totalUnpaidInterest);
       
        return marginLevel;
    }

    public double getBorrowRatio() {
        return (double) (this.currentLeverage - 1) / this.currentLeverage;
    }

    public int getCurrentTier(){
        return this.currentTier;
    }

    public void setCurrentTier(int tier){
        updateTier(tier);
    }

    public double getCurrentLeverage(){
        return this.currentLeverage;
    }

    public double getTotalUSDTBorrows(){
        return this.totalBorrowedAmountUsdt;
    }

    public double getTotalBTCBorrows(){
        return this.totalBorrowedAmountBtc;
    }   
}