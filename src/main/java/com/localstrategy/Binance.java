package com.localstrategy;

import com.localstrategy.util.enums.*;
import com.localstrategy.util.types.Event;
import com.localstrategy.util.types.SingleTransaction;
import com.localstrategy.util.types.UserDataStream;

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

    public final static int ALGO_ORDER_LIMIT = 5;

    private final int MAX_BORROW_USDT = 900_000;
    private final int MAX_BORROW_BTC = 72;

    private final ArrayList<Order> newOrders = new ArrayList<>();
    private final ArrayList<Order> filledOrders = new ArrayList<>();
    private final ArrayList<Order> cancelledOrders = new ArrayList<>();
    private final ArrayList<Order> rejectedOrders = new ArrayList<>();
    private final ArrayList<Order> acceptedOrderList = new ArrayList<>();

    private final ArrayList<Order> updatedOrders = new ArrayList<>();

    private final UserAssets userAssets = new UserAssets();
    private final ArrayList<UserAssets> userAssetsList = new ArrayList<>();
    private final TierManager tierManager = new TierManager();

    private SingleTransaction transaction;

    private long previousInterestTimestamp = 0;

    private boolean userAssetsUpdated = false;
    private boolean ordersUpdated = false;

    private final EventScheduler scheduler;

    private Event currentEvent;

    public Binance(Double initialUSDTPortfolio, EventScheduler scheduler){

        this.scheduler = scheduler;

        this.userAssets.setFreeUSDT(initialUSDTPortfolio);
        this.userAssetsList.add(userAssets);
    }

    public void onEvent(Event event){

        if(event == null){
            System.out.println("Exchange Error - Null exchange event in onEvent.");
            return;
        }

        this.currentEvent = event;

        switch(event.getType()){
            case TRANSACTION -> {
                this.transaction = event.getTransaction();
                checkFills();
            }
            case ACTION_REQUEST -> {
                handleUserActionRequest(event.getActionRequest());
            }
            default -> System.out.println("Exchange Error - Something other than transaction or action request ended up exchange's event stream.");
        }

        addInterest();
        checkMarginLevel();
        updateUserDataStream();
    }

    private void updateUserDataStream(){
        if(userAssetsUpdated || !updatedOrders.isEmpty()){

            scheduler.addEvent(new Event(currentEvent.getDelayedTimestamp(), EventDestination.LOCAL, new UserDataStream(userAssets, updatedOrders)));

            if(userAssetsUpdated){
                userAssetsList.add(new UserAssets(userAssets));
            }

            updatedOrders.clear();

            userAssetsUpdated = false;
            ordersUpdated = false;
        }
    }
    
    private void handleUserActionRequest(Map<OrderAction, Order> entryBlock){

        if(entryBlock == null){
            System.out.println("Exchange error - EntryBlock in handleUserActionRequest is NULL which shouldn't be.");
        }

        if(entryBlock.isEmpty()){
            System.out.println("Exchange Error - EntryBlock in handleUserActionRequest is empty which shouldn't be.");
        }

        for(Map.Entry<OrderAction, Order> actionRequest : entryBlock.entrySet()){

            OrderAction orderAction = actionRequest.getKey();
            Order order = actionRequest.getValue();

            switch (orderAction) {
                case CREATE_ORDER -> {
                    boolean progOrdersReached = order.getType().equals(OrderType.LIMIT) &&
                            (newOrders.stream()
                                    .filter(p -> p.getType()
                                            .equals(OrderType.LIMIT))
                                    .count()
                                    >= Binance.ALGO_ORDER_LIMIT);

                    if (progOrdersReached) {
                        order.setStatus(OrderStatus.REJECTED);
                        order.setRejectionReason(RejectionReason.MAX_NUM_ALGO_ORDERS);
                        createActionResponse(ActionResponse.ORDER_REJECTED, order);
                        continue;
                    }

                    // Immediate trigger?
                    if (order.getType().equals(OrderType.LIMIT) &&
                            ((order.getDirection().equals(OrderSide.BUY) &&
                                    (order.isStopLoss() && transaction.price() >= order.getOpenPrice() ||
                                            !order.isStopLoss() && transaction.price() <= order.getOpenPrice())) ||
                                    (order.getDirection().equals(OrderSide.SELL) &&
                                            (order.isStopLoss() && transaction.price() <= order.getOpenPrice() ||
                                                    !order.isStopLoss() && transaction.price() >= order.getOpenPrice())))) {

                        order.setStatus(OrderStatus.REJECTED);
                        order.setRejectionReason(RejectionReason.WOULD_TRIGGER_IMMEDIATELY);
                        createActionResponse(ActionResponse.ORDER_REJECTED, order);
                        continue;
                    }

                    // "If you use MARGIN_BUY, the amount necessary to borrow in order to execute your order later will be borrowed at the creation time, regardless of the order type (whether it's limit or stop-limit)."
                    if (order.isAutomaticBorrow() && order.getType().equals(OrderType.LIMIT)) {
                        //We checked for already available funds in the checkFills method for market orders

                        boolean isLong = order.getDirection().equals(OrderSide.BUY);

                        //Hacky as hell here - binance usually borrows extra funds I already see we're going to have issues with automatic borrowings.
                        double fillPrice = SlippageHandler.getSlippageFillPrice(
                                order.getOpenPrice(),
                                order.getSize(),
                                order.getDirection()
                        );

                        if(userAssets.getFreeUSDT() < order.getSize() * fillPrice && (!isLong && userAssets.getFreeBTC() < order.getSize())) {//Unless we have enough funds.

                            RejectionReason rejectionReason = borrowFunds(order);
                            if (rejectionReason != null) {
                                order.setStatus(OrderStatus.REJECTED);
                                order.setRejectionReason(rejectionReason);
                                createActionResponse(ActionResponse.ORDER_REJECTED, order);
                                continue;
                            }
                        }
                    }

                    order.setStatus(OrderStatus.NEW);
                    newOrders.add(order);

                    createActionResponse(ActionResponse.ORDER_CREATED, order);
                    updatedOrders.add(order);
                }
                case CANCEL_ORDER -> {
                    if (!newOrders.contains(order) || !order.getType().equals(OrderType.LIMIT)) {
                        order.setRejectionReason(RejectionReason.INVALID_ORDER_STATE);
                        createActionResponse(ActionResponse.ACTION_REJECTED, order);
                        continue;
                    }
                    if (order.isAutoRepayAtCancel()) {

                        RejectionReason rejectionReason = repayFunds(order);

                        if (rejectionReason != null) {
                            order.setRejectionReason(rejectionReason);
                            createActionResponse(ActionResponse.ACTION_REJECTED, order);
                            continue;
                        }
                    }

                    order.setStatus(OrderStatus.CANCELED);
                    cancelledOrders.add(order);
                    newOrders.remove(order);

                    createActionResponse(ActionResponse.ORDER_CANCELLED, order);
                    updatedOrders.add(order);
                }
                case REPAY_FUNDS -> {
                    RejectionReason rejectionReason = repayFunds(order);
                    if (rejectionReason != null) {
                        order.setRejectionReason(rejectionReason);
                        createActionResponse(ActionResponse.ACTION_REJECTED, order);
                        continue;
                    }
                    createActionResponse(ActionResponse.FUNDS_REPAID, order);
                }
                default -> {
                }
            }
        }
    }

    private void checkFills(){
        for(Order order : newOrders){
            if(order.getStatus().equals(OrderStatus.NEW)){ //FIXME: Status field is mutable by the client. We're not editing it too much but still not good practice

                boolean isMarketOrder = order.getType().equals(OrderType.MARKET);
                boolean isLong = order.getDirection().equals(OrderSide.BUY);

                double fillPrice = SlippageHandler.getSlippageFillPrice(
                    isMarketOrder ? transaction.price() : order.getOpenPrice(),
                    order.getSize(), 
                    order.getDirection()
                );

                if(isMarketOrder ||
                        ((order.getDirection().equals(OrderSide.BUY) &&
                            (order.isStopLoss() && transaction.price() >= order.getOpenPrice() ||
                            !order.isStopLoss() && transaction.price() <= order.getOpenPrice())) ||
                        (order.getDirection().equals(OrderSide.SELL) &&
                            (order.isStopLoss() && transaction.price() >= order.getOpenPrice() ||
                            !order.isStopLoss() && transaction.price() <= order.getOpenPrice())))){

                    if(isMarketOrder && order.isAutomaticBorrow()){

                        //FIXME: We still don't understand which asset Binance checks when deciding whether we have enough funds. We'll assume it only checks...?
                        if(userAssets.getFreeUSDT() < order.getSize() * fillPrice && (!isLong && userAssets.getFreeBTC() < order.getSize())){

                            RejectionReason rejectionReason = borrowFunds(order);
                            if(rejectionReason != null){
                                order.setStatus(OrderStatus.REJECTED);
                                order.setRejectionReason(rejectionReason);
                                createActionResponse(ActionResponse.ORDER_REJECTED, order);
                                rejectedOrders.add(order); //For removal at the end of the method
                                continue;
                            }
                        }
                    }
                    if(isLong){
                        if(userAssets.getFreeUSDT() < order.getBorrowedAmount()){
                            if(isMarketOrder){
                                order.setStatus(OrderStatus.REJECTED);
                                order.setRejectionReason(RejectionReason.INSUFFICIENT_FUNDS);
                                createActionResponse(ActionResponse.ORDER_REJECTED, order);
                                rejectedOrders.add(order);
                            } else {
                                rejectOrder(RejectionReason.INSUFFICIENT_FUNDS, order); //This one for example, reports over a user stream.
                            }
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
                            if(isMarketOrder){
                                order.setStatus(OrderStatus.REJECTED);
                                order.setRejectionReason(RejectionReason.INSUFFICIENT_FUNDS);
                                createActionResponse(ActionResponse.ORDER_REJECTED, order);
                                rejectedOrders.add(order);
                            } else {
                                rejectOrder(RejectionReason.INSUFFICIENT_FUNDS, order); //FIXME: This one for example, would report over a user stream.
                            }
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
                    acceptedOrderList.add(order);
                }
            }
        }

        if(!acceptedOrderList.isEmpty()){

            for(Order order : acceptedOrderList){
                order.setStatus(OrderStatus.FILLED);
            }

            filledOrders.addAll(acceptedOrderList);
            newOrders.removeAll(acceptedOrderList);
            updatedOrders.addAll(acceptedOrderList);
            acceptedOrderList.clear();

            userAssetsUpdated = true;
        }

        newOrders.removeAll(rejectedOrders);
    }

    private void addInterest(){
        if((!filledOrders.isEmpty() || !newOrders.isEmpty()) && 
            currentEvent.getDelayedTimestamp() - previousInterestTimestamp > 1000 * 60 * 60){

            previousInterestTimestamp = currentEvent.getDelayedTimestamp();

            userAssets.setTotalUnpaidInterest(
                    userAssets.getTotalUnpaidInterest()
                        + userAssets.getTotalBorrowedUSDT() * TierManager.HOURLY_USDT_INTEREST_RATE
                        + userAssets.getTotalBorrowedBTC() * TierManager.HOURLY_BTC_INTEREST_RATE);

            userAssetsUpdated = true;
        }
    }

    private RejectionReason borrowFunds(Order order){

        boolean isMarketOrder = order.getType().equals(OrderType.MARKET);
        boolean isLong = order.getDirection().equals(OrderSide.BUY);

        double fillPrice = SlippageHandler.getSlippageFillPrice(
            isMarketOrder ? transaction.price() : order.getOpenPrice(),
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
        
        } else { //Short

            double positionSize = order.getSize();

            //Check maximum borrow amount
            if(order.getSize() + userAssets.getTotalBorrowedBTC() > MAX_BORROW_BTC){
                return RejectionReason.EXCESS_BORROW;
            }

            //Calculate leverage
            tierManager.checkAndUpdateTier(userAssets.getTotalBorrowedUSDT(), positionSize + userAssets.getTotalBorrowedBTC());

            double requiredMargin = positionSize * transaction.price() / tierManager.getCurrentLeverage();

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
    //TODO: Fixme: Binance isn't calculating our order interest for us, we must do so locally.
    // Currently the repay amount is saved in an order, but we must locally add the unpaid interest amount to that which would offset other calculations.
    //FIXME: Convert the repayFunds so an amount is sent, not an order? Or an order along with the amount?
    private RejectionReason repayFunds(Order order) {
        if(order.getBorrowedAmount() == 0){
            return RejectionReason.INVALID_ORDER_STATE;
        }

        if(order.getDirection().equals(OrderSide.BUY)){
            //Return USDT

            if(userAssets.getFreeUSDT() < order.getBorrowedAmount() + order.getTotalUnpaidInterest()){
                return RejectionReason.INSUFFICIENT_FUNDS;
            }

            if(userAssets.getLockedUSDT() < order.getMargin()){
                System.out.println("Exchange Error at repayFunds side BUY - locked USDT has insufficient funds to unlock margin?");
            }

            if(userAssets.getTotalBorrowedUSDT() < order.getBorrowedAmount()){
                System.out.println("Exchange Error at repayFunds side BUY - borrowed USDT would go negative if we were to exclude the borrowed amount?");
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
                System.out.println("Exchange Error at repayFunds side SELL - locked USDT has insufficient funds to unlock margin?");
            }

            if(userAssets.getTotalBorrowedBTC() < order.getBorrowedAmount()){
                System.out.println("Exchange Error at repayFunds side SELL - borrowed BTC would go negative if we were to exclude the borrowed amount?");
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
            System.out.println("Exchange Error at repayFunds - unpaid interest would go negative?");
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
            (userAssets.getFreeBTC() + userAssets.getLockedBTC()) * transaction.price();

        double totalBorrowedAssetValue = 
            userAssets.getTotalBorrowedUSDT() + 
            userAssets.getTotalBorrowedBTC() * transaction.price();

        if(totalBorrowedAssetValue + userAssets.getTotalUnpaidInterest() == 0){
            return 999;
        }

        double marginLevel = totalAssetValue / (totalBorrowedAssetValue + userAssets.getTotalUnpaidInterest());

        userAssets.setMarginLevel(marginLevel);

        if(marginLevel < 1.05){
            System.out.println("marginLevel = " + marginLevel);

            for(Order order : filledOrders){
                liquidateOrder(order);
            }
        }

        return marginLevel;
    }

    //TODO: liquidate 'position'?
    private void liquidateOrder(Order order){
        System.out.println("Liquidation");
        System.exit(1);
    }

    private void createActionResponse(ActionResponse response, Order order){
        scheduler.addEvent(new Event(currentEvent.getDelayedTimestamp(), EventDestination.LOCAL, response, order));
    }

    private void rejectOrder(RejectionReason reason, Order order){
        order.setStatus(OrderStatus.REJECTED);
        order.setRejectionReason(reason);
        rejectedOrders.add(order);
        updatedOrders.add(order);
    }

    public double getTotalAssetsValue(){
        if(transaction != null){
            return userAssets.getTotalAssetValue(transaction.price());
        }
        return 0.0;
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

        return orders;
    }

    public UserAssets getUserAssets(){
        return this.userAssets;
    }

}
