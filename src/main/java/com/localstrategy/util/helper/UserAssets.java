package com.localstrategy.util.helper;

public class UserAssets {

    private long timestamp;

    private double freeUSDT = 0;
    private double lockedUSDT = 0;
    private double freeBTC = 0;
    private double lockedBTC = 0;

    private double totalBorrowedUSDT = 0;
    private double totalBorrowedBTC = 0;

    private double marginLevel = 999;

    private double totalUnpaidBTCInterest = 0;

    private double totalUnpaidUSDTInterest = 0;

    public UserAssets(){

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
    }

    public double getRemainingInterestUSDT() {
        return totalUnpaidUSDTInterest;
    }

    public void setRemainingInterestUSDT(double totalUnpaidUSDTInterest) {
        this.totalUnpaidUSDTInterest = totalUnpaidUSDTInterest;
    }

    public double getRemainingInterestBTC() {
        return totalUnpaidBTCInterest;
    }

    public void setRemainingInterestBTC(double totalUnpaidBTCInterest) {
        this.totalUnpaidBTCInterest = totalUnpaidBTCInterest;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
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

    @Override
    public String toString() {
        return "UserAssets{" +
//                "timestamp=" + timestamp +
                ", freeUSDT=" + freeUSDT +
                ", lockedUSDT=" + lockedUSDT +
                ", freeBTC=" + freeBTC +
                ", lockedBTC=" + lockedBTC +
                ", totalBorrowedUSDT=" + totalBorrowedUSDT +
                ", totalBorrowedBTC=" + totalBorrowedBTC +
//                ", marginLevel=" + marginLevel +
//                ", totalUnpaidBTCInterest=" + totalUnpaidBTCInterest +
//                ", totalUnpaidUSDTInterest=" + totalUnpaidUSDTInterest +
                '}';
    }
}
