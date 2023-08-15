package com.localstrategy.util.helper;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class TierManager {

    public static final BigDecimal MAX_BORROW_USDT = BigDecimal.valueOf(900_000);
    public static final BigDecimal MAX_BORROW_BTC = BigDecimal.valueOf(72);

    private static final int[] MAX_BORROWING_AMOUNT_USDT_TIERS = { 90000, 180000, 270000, 360000, 450000, 540000, 630000, 720000, 810000, 900000 };
    private static final double[] MAX_BORROWING_AMOUNT_BTC_TIERS = { 7.2, 14.4, 21.6, 28.8, 36, 43.2, 50.4, 57.6, 64.8, 72 };
    private static final double[] LEVERAGE_TIERS = { 10, 8.91, 8.05, 7.36, 6.79, 6.31, 5.91, 5.57, 5.26, 5 };

    public static final BigDecimal HOURLY_BTC_INTEREST_RATE_PCT = new BigDecimal("0.00017504").setScale(8, RoundingMode.HALF_UP);
    public static final BigDecimal HOURLY_USDT_INTEREST_RATE_PCT = new BigDecimal("0.00061537").setScale(8, RoundingMode.HALF_UP);


    private int currentTier;
    private double currentLeverage;

    public TierManager() {

        updateTier(0);
    }

    private void updateTier(int newTier) {
        this.currentTier = newTier;
        this.currentLeverage = LEVERAGE_TIERS[currentTier];
    }

    public void checkAndUpdateTier(double totalBorrowedAmountUsdt, double totalBorrowedAmountBtc) {
        int i;
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

    public double getCurrentLeverage(){
        return this.currentLeverage;
    }
}