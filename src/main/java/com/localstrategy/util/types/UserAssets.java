package com.localstrategy.util.types;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class UserAssets {

    private final static int PRECISION_GENERAL = 8;
    
    private long timestamp;

    private BigDecimal freeUSDT = BigDecimal.ZERO;
    private BigDecimal lockedUSDT = BigDecimal.ZERO;
    private BigDecimal freeBTC = BigDecimal.ZERO;
    private BigDecimal lockedBTC = BigDecimal.ZERO;

    private BigDecimal totalBorrowedUSDT = BigDecimal.ZERO;
    private BigDecimal totalBorrowedBTC = BigDecimal.ZERO;

    private double marginLevel = 999;

    private BigDecimal totalUnpaidBTCInterest = BigDecimal.ZERO;

    private BigDecimal totalUnpaidUSDTInterest = BigDecimal.ZERO;

    private double savePrice;

    public UserAssets() {
    }

    public UserAssets(UserAssets userAssets) {
        this.timestamp = userAssets.getTimestamp();
        this.freeUSDT = userAssets.getFreeUSDT();
        this.lockedUSDT = userAssets.getLockedUSDT();
        this.freeBTC = userAssets.getFreeBTC();
        this.lockedBTC = userAssets.getLockedBTC();
        this.totalBorrowedUSDT = userAssets.getTotalBorrowedUSDT();
        this.totalBorrowedBTC = userAssets.getTotalBorrowedBTC();
        this.marginLevel = userAssets.getMarginLevel();
        this.totalUnpaidUSDTInterest = userAssets.getRemainingInterestUSDT();
        this.totalUnpaidBTCInterest = userAssets.getRemainingInterestBTC();
        this.savePrice = userAssets.getSavePrice();
    }

    public double getMomentaryOwnedAssets(){
        return freeUSDT.add(lockedUSDT).subtract(totalBorrowedUSDT).subtract(totalUnpaidUSDTInterest).add(
                freeBTC.add(lockedBTC).subtract(totalBorrowedBTC).subtract(totalUnpaidBTCInterest).multiply(BigDecimal.valueOf(savePrice))
        ).doubleValue();
    }

    public void setSavePrice(double price){
        this.savePrice = price;
    }

    public double getSavePrice(){
        return this.savePrice;
    }

    public BigDecimal getRemainingInterestUSDT() {
        return totalUnpaidUSDTInterest;
    }

    public void setRemainingInterestUSDT(BigDecimal totalUnpaidUSDTInterest) {
        this.totalUnpaidUSDTInterest = totalUnpaidUSDTInterest.setScale(PRECISION_GENERAL, RoundingMode.HALF_UP);
    }

    public BigDecimal getRemainingInterestBTC() {
        return totalUnpaidBTCInterest;
    }

    public void setRemainingInterestBTC(BigDecimal totalUnpaidBTCInterest) {
        this.totalUnpaidBTCInterest = totalUnpaidBTCInterest.setScale(PRECISION_GENERAL, RoundingMode.HALF_UP);
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public double getMarginLevel() {
        return marginLevel;
    }

    public void setMarginLevel(double marginLevel) {
        this.marginLevel = marginLevel;
    }

    public BigDecimal getFreeUSDT() {
        return freeUSDT;
    }

    public void setFreeUSDT(BigDecimal freeUSDT) {
        this.freeUSDT = freeUSDT.setScale(PRECISION_GENERAL, RoundingMode.HALF_UP);
    }

    public BigDecimal getLockedUSDT() {
        return lockedUSDT;
    }

    public void setLockedUSDT(BigDecimal lockedUSDT) {
        this.lockedUSDT = lockedUSDT.setScale(PRECISION_GENERAL, RoundingMode.HALF_UP);
    }

    public BigDecimal getFreeBTC() {
        return freeBTC;
    }

    public void setFreeBTC(BigDecimal freeBTC) {
        this.freeBTC = freeBTC.setScale(PRECISION_GENERAL, RoundingMode.HALF_UP);
    }

    public BigDecimal getLockedBTC() {
        return lockedBTC;
    }

    public void setLockedBTC(BigDecimal lockedBTC) {
        this.lockedBTC = lockedBTC.setScale(PRECISION_GENERAL, RoundingMode.HALF_UP);
    }

    public BigDecimal getTotalAssetValue(BigDecimal currentPrice) {
        return freeUSDT.add(lockedUSDT).add(freeBTC.add(lockedBTC).multiply(currentPrice));
    }

    public void setTotalBorrowedUSDT(BigDecimal amount) {
        this.totalBorrowedUSDT = amount.setScale(PRECISION_GENERAL, RoundingMode.HALF_UP);
    }

    public BigDecimal getTotalBorrowedUSDT() {
        return totalBorrowedUSDT;
    }

    public void setTotalBorrowedBTC(BigDecimal amount) {
        this.totalBorrowedBTC = amount.setScale(PRECISION_GENERAL, RoundingMode.HALF_UP);
    }

    public BigDecimal getTotalBorrowedBTC() {
        return totalBorrowedBTC;
    }

    @Override
    public String toString() {
        return "UserAssets{" +
                // "timestamp=" + timestamp +
                ", freeUSDT=" + freeUSDT +
                ", lockedUSDT=" + lockedUSDT +
                ", freeBTC=" + freeBTC +
                ", lockedBTC=" + lockedBTC +
                ", totalBorrowedUSDT=" + totalBorrowedUSDT +
                ", totalBorrowedBTC=" + totalBorrowedBTC +
                // ", marginLevel=" + marginLevel +
                // ", totalUnpaidBTCInterest=" + totalUnpaidBTCInterest +
                // ", totalUnpaidUSDTInterest=" + totalUnpaidUSDTInterest +
                '}';
    }
}
