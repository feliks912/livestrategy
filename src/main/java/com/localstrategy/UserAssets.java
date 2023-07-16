package com.localstrategy;

public class UserAssets {

    public static final double HOURLY_BTC_INTEREST_RATE = 0.00019667;
    public static final double HOURLY_USDT_INTEREST_RATE = 0.00067638;

    private double freeUSDT = 0;
    private double lockedUSDT = 0;
    private double freeBTC = 0;
    private double lockedBTC = 0;

    private double totalBorrowedUSDT = 0;
    private double totalBorrowedBTC = 0;

    private double marginLevel;

    //TODO: Calculate total unpaid interest and add an incrementing function
    private double totalUnpaidInterest = 0;

    public UserAssets(){

    }

    public UserAssets(UserAssets userAssets) {
        this.freeUSDT = userAssets.getFreeUSDT();
        this.lockedUSDT = userAssets.getLockedUSDT();
        this.freeBTC = userAssets.getFreeBTC();
        this.lockedBTC = userAssets.getLockedBTC();
        this.totalBorrowedUSDT = userAssets.getTotalBorrowedUSDT();
        this.totalBorrowedBTC = userAssets.getTotalBorrowedBTC();
        this.marginLevel = userAssets.getMarginLevel();
        this.totalUnpaidInterest = userAssets.getTotalUnpaidInterest();
    }

    public UserAssets(double freeUSDT, double lockedUSDT, double freeBTC, double lockedBTC) {
        this.freeUSDT = freeUSDT;
        this.lockedUSDT = lockedUSDT;
        this.freeBTC = freeBTC;
        this.lockedBTC = lockedBTC;
    }

    public double getMarginLevel() {
        return this.marginLevel;
    }

    public void setMarginLevel(double marginLevel) {
        this.marginLevel = marginLevel;
    }

    public double getFreeUSDT() {
        return this.freeUSDT;
    }

    public void setFreeUSDT(double freeUSDT) {
        this.freeUSDT = freeUSDT;
    }

    public double getLockedUSDT() {
        return this.lockedUSDT;
    }

    public void setLockedUSDT(double lockedUSDT) {
        this.lockedUSDT = lockedUSDT;
    }

    public double getFreeBTC() {
        return this.freeBTC;
    }

    public void setFreeBTC(double freeBTC) {
        this.freeBTC = freeBTC;
    }

    public double getLockedBTC() {
        return this.lockedBTC;
    }

    public void setLockedBTC(double lockedBTC) {
        this.lockedBTC = lockedBTC;
    }

    public double getTotalAssetValue(double currentPrice){
        return this.freeUSDT + this.lockedUSDT + (freeBTC + lockedBTC) * currentPrice;
    }

    public void setTotalBorrowedUSDT(double amount){
        this.totalBorrowedUSDT = amount;
    }

    public double getTotalBorrowedUSDT(){
        return this.totalBorrowedUSDT;
    }

    public void setTotalBorrowedBTC(double amount){
        this.totalBorrowedBTC = amount;
    }
    
    public double getTotalBorrowedBTC(){
        return this.totalBorrowedBTC;
    }

    public void setTotalUnpaidInterest(double interest){
        this.totalUnpaidInterest = interest;
    }

    public double getTotalUnpaidInterest(){
        return this.totalUnpaidInterest;
    }

    public void increaseTotalUnpaidInterest(double currentPrice){
        this.totalUnpaidInterest += 
            totalBorrowedUSDT * HOURLY_USDT_INTEREST_RATE + 
            totalBorrowedBTC * HOURLY_BTC_INTEREST_RATE * currentPrice;
    }

    @Override
    public String toString() {
        return "{" +
            " freeUSDT='" + getFreeUSDT() + "'" +
            ", lockedUSDT='" + getLockedUSDT() + "'" +
            ", freeBTC='" + getFreeBTC() + "'" +
            ", lockedBTC='" + getLockedBTC() + "'" +
            ", totalBorrowedUSDT='" + getTotalBorrowedUSDT() + "'" +
            ", totalBorrowedBTC='" + getTotalBorrowedBTC() + "'" +
            ", marginLevel='" + getMarginLevel() + "'" +
            ", totalUnpaidInterest='" + getTotalUnpaidInterest() + "'" +
            "}";
    }
}
