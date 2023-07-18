package com.localstrategy;

import com.localstrategy.util.enums.*;
import com.localstrategy.util.types.SingleTransaction;
import com.localstrategy.util.types.UserDataResponse;

import java.util.ArrayList;
import java.util.Map;


//TODO: I didn't consider how EXACTLY these price walls work.

//TODO: Check if overall borrow / profit logic is alright

//TODO: Separate Order and Position classes - Position class is a local class comprising of position value and a stop-loss order, Order class is a single Binance-supported order request



//FIXME: Refactor

//Note - we don't use borrowed funds when calculating or positions sizes. That's important during live trading. handle it properly that's why I made the request to the Binance API team

//TODO: Parse strategy as parameter from App instead of hard coding it into TempStrategyExecutor, unless TempStrategyExecutor is the strategy (later)
//TODO: Add symbol rules (later)
//TODO: Binance doesn't actually keep track of filled positions but it does offer a list (later)
//TODO: Add manual borrow action (later)
//TODO: Position liquidation (later)

public class Binance {

    public final static int MAX_PROG_ORDERS = 5;

    private final int MAX_BORROW_USDT = 900_000;
    private final int MAX_BORROW_BTC = 72;

    private final ArrayList<Order> newOrders = new ArrayList<>();
    private final ArrayList<Order> filledOrders = new ArrayList<>();
    private final ArrayList<Order> cancelledOrders = new ArrayList<>();
    private final ArrayList<Order> rejectedOrders = new ArrayList<>();

    private final ArrayList<Order> previousOrders = new ArrayList<>();

    private final ArrayList<Order> acceptedOrders = new ArrayList<>();

    private final UserAssets userAssets = new UserAssets();
    private final LatencyHandler latencyHandler = new LatencyHandler();
    private final ArrayList<UserAssets> userAssetsList = new ArrayList<>();
    private final SlippageHandler slippageHandler = new SlippageHandler();
    private final TierManager tierManager = new TierManager();
    private final LocalStrategy localStrategy;

    private SingleTransaction transaction;

    private long previousInterestTimestamp = 0;

    private boolean userAssetsUpdated = false;
    private boolean ordersUpdated = false;

    public Binance(Double initialUSDTPortfolio){

        this.localStrategy = new LocalStrategy(this);

        this.userAssets.setFreeUSDT(initialUSDTPortfolio);
        this.userAssetsList.add(userAssets);
    }

    public void newTransaction(SingleTransaction transaction, boolean isWall) {
        this.transaction = transaction;

        latencyHandler.recalculateLatencies(transaction.getTimestamp());
        conditionallyAddInterest();

        checkFills();
        checkMarginLevel();
        
        handleUserRequest(latencyHandler.getDelayedUserActionRequests(transaction.getTimestamp()));

        updateUserDataStream();

        localStrategy.onTransaction(transaction, isWall);
    }

    private void updateUserDataStream(){
        if(userAssetsUpdated || ordersUpdated){
            latencyHandler.addUserDataStream(
                new UserDataResponse(
                    userAssets, // userAssets copy constructor
                    newOrders,
                    filledOrders,
                    cancelledOrders,
                    rejectedOrders
                ),
                transaction.getTimestamp());

            if(userAssetsUpdated){
                userAssetsList.add(new UserAssets(userAssets)); // userAssets copy constructor
            }

            userAssetsUpdated = false;
            ordersUpdated = false;
        }
    }

    
    private void handleUserRequest(ArrayList<Map<OrderAction, Order>> entryBlock){

        if(entryBlock.isEmpty()){
            return;
        }

        for(Map<OrderAction, Order> entry : entryBlock){

            Map.Entry<OrderAction, Order> tempMap = entry.entrySet().iterator().next();

            OrderAction orderAction = tempMap.getKey();
            Order order = tempMap.getValue();

            switch (orderAction) {
                case CREATE_ORDER -> { //Thanks ChatGPT for refactoring
                    boolean canProcessOrder = (order.getOrderType().equals(OrderType.MARKET)) ||
                            (order.getOrderType().equals(OrderType.LIMIT) &&
                                    (newOrders
                                            .stream()
                                            .filter(p -> p.getOrderType()
                                                    .equals(OrderType.LIMIT))
                                            .count()
                                            < Binance.MAX_PROG_ORDERS));

                    // Max programmatic orders reached?
                    if (!canProcessOrder) {
                        rejectOrder(RejectionReason.EXCESS_PROG_ORDERS, order);
                        continue;
                    }

                    // Immediate trigger?
                    if (order.getOrderType().equals(OrderType.LIMIT) &&
                            ((order.getDirection().equals(OrderSide.BUY) &&
                                    (order.isStopLoss() && transaction.getPrice() >= order.getOpenPrice() ||
                                            !order.isStopLoss() && transaction.getPrice() <= order.getOpenPrice())) ||
                                    (order.getDirection().equals(OrderSide.SELL) &&
                                            (order.isStopLoss() && transaction.getPrice() <= order.getOpenPrice() ||
                                                    !order.isStopLoss() && transaction.getPrice() >= order.getOpenPrice())))) {

                        rejectOrder(RejectionReason.WOULD_TRIGGER_IMMEDIATELY, order);
                        continue;
                    }
                    if (order.isAutomaticBorrow() && order.getOrderType().equals(OrderType.LIMIT)) { // "If you use MARGIN_BUY, the amount necessary to borrow in order to execute your order later will be borrowed at the creation time, regardless of the order type (whether it's limit or stop-limit)."
                        RejectionReason rejectionReason = borrowFunds(order);

                        if (rejectionReason != null) {
                            rejectOrder(rejectionReason, order);
                            continue;
                        }
                    }
                    newOrders.add(order);
                    ordersUpdated = true;
                }
                case CANCEL_ORDER -> { //FIXME: Rejecting a cancel_order position will remove the position from new positions to rejected positions which isn't what we want.
                    int index = newOrders.indexOf(order);
                    if (index == -1 || !order.getOrderType().equals(OrderType.LIMIT)) {
                        rejectOrder(RejectionReason.INVALID_ORDER_STATE, order);
                        continue;
                    }
                    if (order.isAutoRepayAtCancel()) {

                        RejectionReason rejectionReason = repayFunds(order);

                        if (rejectionReason != null) {
                            rejectOrder(rejectionReason, order);
                            continue;
                        }
                    }
                    order.setStatus(OrderStatus.CANCELED);
                    cancelledOrders.add(order);
                    newOrders.remove(order);
                    ordersUpdated = true;
                }
                case REPAY -> { //FIXME: User doesn't get a response from this, or for that matter any other action unless they manually loop through positions. Add some sort of response other than updating userDataStream
                    RejectionReason rejectionReason = repayFunds(order);
                    if (rejectionReason != null) {
                        rejectOrder(rejectionReason, order);
                    }
                }
                default -> {
                }
            }
        }
    }

    private void checkFills(){
        for(Order order : newOrders){
            if(order.getStatus().equals(OrderStatus.NEW)){

                boolean isMarketOrder = order.getOrderType().equals(OrderType.MARKET);
                boolean isLong = order.getDirection().equals(OrderSide.BUY);

                double fillPrice = slippageHandler.getSlippagePrice(
                    isMarketOrder ? transaction.getPrice() : order.getOpenPrice(),
                    order.getSize(), 
                    order.getDirection()
                );

                if(isMarketOrder ||
                        ((order.getDirection().equals(OrderSide.BUY) &&
                            (order.isStopLoss() && transaction.getPrice() >= order.getOpenPrice() ||
                            !order.isStopLoss() && transaction.getPrice() <= order.getOpenPrice())) ||
                        (order.getDirection().equals(OrderSide.SELL) &&
                            (order.isStopLoss() && transaction.getPrice() >= order.getOpenPrice() ||
                            !order.isStopLoss() && transaction.getPrice() <= order.getOpenPrice())))){

                    //Buy
                    //Sell
                    if(order.isAutomaticBorrow()){
                        //Borrow funds for market orders at the time of filling the order (principally incorrect, functionally equivalent)
                        if(isMarketOrder){
                            RejectionReason rejectionReason = borrowFunds(order);

                            if(rejectionReason != null){
                                rejectOrder(rejectionReason, order);
                                continue;
                            }
                        }

                    }
                    if(isLong){
                        if(userAssets.getFreeUSDT() < order.getBorrowedAmount()){
                            rejectOrder(RejectionReason.INSUFFICIENT_FUNDS, order);
                            continue;
                        }

                        //Buy BTC
                        userAssets.setFreeUSDT(
                            userAssets.getFreeUSDT()
                                - order.getBorrowedAmount()
                        );

                        userAssets.setFreeBTC(
                            userAssets.getFreeBTC()
                                + order.getSize()
                        );

                    } else { //Short
                        if(userAssets.getFreeBTC() < order.getSize()){
                            rejectOrder(RejectionReason.INSUFFICIENT_FUNDS, order);
                            continue;
                        }
                        //Sell BTC
                        userAssets.setFreeBTC(
                            userAssets.getFreeBTC()
                                - order.getSize()
                        );
                        userAssets.setFreeUSDT(
                            userAssets.getFreeUSDT()
                                + order.getBorrowedAmount()
                        );
                    }

                    order.setFillPrice(fillPrice);
                    acceptedOrders.add(order);
                }
            }
        }

        if(!acceptedOrders.isEmpty()){

            for(Order order : acceptedOrders){
                order.setStatus(OrderStatus.FILLED);
            }

            filledOrders.addAll(acceptedOrders);
            newOrders.removeAll(acceptedOrders);
            acceptedOrders.clear();

            ordersUpdated = true;
            userAssetsUpdated = true;
        }

        newOrders.removeAll(rejectedOrders);
    }

    private void conditionallyAddInterest(){
        if((!filledOrders.isEmpty() || !newOrders.isEmpty()) && 
            transaction.getTimestamp() - previousInterestTimestamp > 1000 * 60 * 60){

            previousInterestTimestamp = transaction.getTimestamp();

            double totalUnpaidInterest = 0;

            for(Order order : filledOrders){
                order.increaseUnpaidInterest(transaction.getPrice());
                totalUnpaidInterest += order.getTotalUnpaidInterest();
            }

            for(Order order : newOrders){
                order.increaseUnpaidInterest(transaction.getPrice());
                totalUnpaidInterest += order.getTotalUnpaidInterest();
            }

            userAssets.setTotalUnpaidInterest(totalUnpaidInterest);

            userAssetsUpdated = true;
            ordersUpdated = true;
        }
    }

    private RejectionReason borrowFunds(Order order){

        boolean isMarketOrder = order.getOrderType().equals(OrderType.MARKET);
        boolean isLong = order.getDirection().equals(OrderSide.BUY);

        double fillPrice = slippageHandler.getSlippagePrice(
            isMarketOrder ? transaction.getPrice() : order.getOpenPrice(),
            order.getSize(), 
            order.getDirection()
        );

        if(isLong){

            double positionValue = order.getSize() * fillPrice;

            //Check maximum borrow amount
            if(positionValue + userAssets.getTotalBorrowedUSDT() > MAX_BORROW_USDT){
                return RejectionReason.EXCESS_BORROW;
            }

            //Calculate leverage
            tierManager.checkAndUpdateTier(positionValue + userAssets.getTotalBorrowedUSDT(), userAssets.getTotalBorrowedBTC());

            //Calculate required margin
            double requiredMargin = positionValue / tierManager.getCurrentLeverage();
            
            if(userAssets.getFreeUSDT() < requiredMargin){
                return RejectionReason.INSUFFICIENT_MARGIN;
            }

            //Lock funds
            userAssets.setFreeUSDT(
                userAssets.getFreeUSDT()
                    - requiredMargin
            );

            userAssets.setLockedUSDT(
                userAssets.getLockedUSDT()
                    + requiredMargin
            );

            //Receive asset
            userAssets.setFreeUSDT(
                userAssets.getFreeUSDT() 
                    + positionValue
            );

            userAssets.setTotalBorrowedUSDT(
                userAssets.getTotalBorrowedUSDT()
                    + positionValue  
            );

            userAssets.setTotalUnpaidInterest(
                userAssets.getTotalUnpaidInterest() 
                    + order.getTotalUnpaidInterest());

            order.setMargin(requiredMargin);
            order.setBorrowedAmount(positionValue);
        
        } else {

            double positionSize = order.getSize();

            //Check maximum borrow amount
            if(order.getSize() + userAssets.getTotalBorrowedBTC() > MAX_BORROW_BTC){
                return RejectionReason.EXCESS_BORROW;
            }

            //Calculate leverage
            tierManager.checkAndUpdateTier(userAssets.getTotalBorrowedUSDT(), positionSize + userAssets.getTotalBorrowedBTC());

            double requiredMargin = positionSize * transaction.getPrice() / tierManager.getCurrentLeverage();

            //Check margin requirement
            if(userAssets.getFreeUSDT() < requiredMargin){
                return RejectionReason.INSUFFICIENT_MARGIN;
            }

            //Lock funds
            userAssets.setFreeUSDT(
                userAssets.getFreeUSDT()
                    - requiredMargin
            );

            userAssets.setLockedUSDT(
                userAssets.getLockedUSDT()
                    + requiredMargin  
            );

            //Receive funds
            userAssets.setFreeBTC(
                userAssets.getFreeBTC()
                    + positionSize
            );

            userAssets.setTotalBorrowedBTC(
                userAssets.getTotalBorrowedBTC()
                    + positionSize  
            );

            userAssets.setTotalUnpaidInterest(
                userAssets.getTotalUnpaidInterest() 
                    + order.getTotalUnpaidInterest());

            order.setMargin(requiredMargin);
            order.setBorrowedAmount(positionSize);
        }
        return null;
    }

    //TODO: Check and refactor

    private RejectionReason repayFunds(Order order){
        if(order.getBorrowedAmount() == 0){
            return RejectionReason.INVALID_ORDER_STATE;
        }

        if(order.getDirection().equals(OrderSide.BUY)){
            //Return USDT

            if(userAssets.getFreeUSDT() < order.getBorrowedAmount() + order.getTotalUnpaidInterest()){
                return RejectionReason.INSUFFICIENT_FUNDS;
            }

            if(userAssets.getLockedUSDT() < order.getMargin()){
                System.out.println("Error at repayFunds side BUY - locked USDT has insufficient funds to unlock margin?");
                System.exit(1);
            }

            if(userAssets.getTotalBorrowedUSDT() < order.getBorrowedAmount()){
                System.out.println("Error at repayFunds side BUY - borrowed USDT would go negative if we were to exclude the borrowed amount?");
                System.exit(1);
            }

            userAssets.setFreeUSDT(
                userAssets.getFreeUSDT()
                    - order.getBorrowedAmount()
                    + order.getMargin()
                    - order.getTotalUnpaidInterest()
            );

            userAssets.setLockedUSDT(
                userAssets.getLockedUSDT()
                    - order.getMargin()
            );

            userAssets.setTotalBorrowedUSDT(
                userAssets.getTotalBorrowedUSDT()
                    - order.getBorrowedAmount()  
            );

        } else {
            //Return BTC

            if(userAssets.getFreeBTC() < order.getSize() || userAssets.getFreeUSDT() < order.getMargin()
                    - order.getTotalUnpaidInterest()){
                        return RejectionReason.INSUFFICIENT_FUNDS;
            }

            if(userAssets.getLockedUSDT() < order.getMargin()){
                System.out.println("Error at repayFunds side SELL - locked USDT has insufficient funds to unlock margin?");
                System.exit(1);
            }

            if(userAssets.getTotalBorrowedBTC() < order.getBorrowedAmount()){
                System.out.println("Error at repayFunds side SELL - borrowed BTC would go negative if we were to exclude the borrowed amount?");
                System.exit(1);
            }

            userAssets.setFreeBTC(
                userAssets.getFreeBTC()
                    - order.getSize()  
            );

            userAssets.setFreeUSDT(
                userAssets.getFreeUSDT()
                    + order.getMargin()
                    - order.getTotalUnpaidInterest()
            );

            userAssets.setLockedUSDT(
                userAssets.getLockedUSDT()
                    - order.getMargin()  
            );

            userAssets.setTotalBorrowedBTC(
                userAssets.getTotalBorrowedBTC()
                    - order.getSize()  
            );
        }

        if(userAssets.getTotalBorrowedBTC() < order.getBorrowedAmount()){
            System.out.println("Error at repayFunds - unpaid interest would go negative?");
            System.exit(1);
        }

        userAssets.setTotalUnpaidInterest(
            userAssets.getTotalUnpaidInterest()
                - order.getTotalUnpaidInterest()
        );

        userAssetsUpdated = true;

        return null;
    }

    private double checkMarginLevel(){
        double totalAssetValue = 
            userAssets.getFreeUSDT() + userAssets.getLockedUSDT() +
            (userAssets.getFreeBTC() + userAssets.getLockedBTC()) * transaction.getPrice();

        double totalBorrowedAssetValue = 
            userAssets.getTotalBorrowedUSDT() + 
            userAssets.getTotalBorrowedBTC() * transaction.getPrice();

        if(totalBorrowedAssetValue + userAssets.getTotalUnpaidInterest() == 0){
            return 999;
        }

        double marginLevel = totalAssetValue / (totalBorrowedAssetValue + userAssets.getTotalUnpaidInterest());

        userAssets.setMarginLevel(marginLevel);

        if(marginLevel < 1.05){
            System.out.println("marginLevel = " + marginLevel);

            for(Order order : filledOrders){
                liquidatePosition(order);
            }
        }

        return marginLevel;
    }

    //TODO: liquidate 'position'?
    private void liquidatePosition(Order order){
        System.out.println("Liquidation");
        System.exit(1);
    }

    private void rejectOrder(RejectionReason reason, Order order){

        order.setStatus(OrderStatus.REJECTED);
        order.setRejectionReason(reason);
        rejectedOrders.add(order);

        ordersUpdated = true;
    }

    public double getTotalAssetsValue(){
        if(transaction != null){
            return userAssets.getTotalAssetValue(transaction.getPrice());
        }
        return 0.0;
    }

    public LatencyHandler getLatencyHandler(){
        return this.latencyHandler;
    }

    public void addToUserAssetList(UserAssets userAsset){
        this.userAssetsList.add(userAsset);
    }

    public ArrayList<Order> getAllOrders(){
        ArrayList<Order> orders = new ArrayList<>();
        orders.addAll(newOrders);
        orders.addAll(filledOrders);
        orders.addAll(rejectedOrders);
        orders.addAll(cancelledOrders);
        orders.addAll(previousOrders);

        return orders;
    }

    public UserAssets getUserAssets(){
        return this.userAssets;
    }

}
