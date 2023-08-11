package com.localstrategy;

import com.localstrategy.util.enums.*;
import com.localstrategy.util.helper.EventScheduler;
import com.localstrategy.util.helper.SlippageHandler;
import com.localstrategy.util.helper.TierManager;
import com.localstrategy.util.helper.UserAssets;
import com.localstrategy.util.types.Event;
import com.localstrategy.util.types.Order;
import com.localstrategy.util.types.SingleTransaction;
import com.localstrategy.util.types.UserDataStream;

import java.util.ArrayList;
import java.util.Map;


//TODO: I didn't consider how EXACTLY these price walls work.

//TODO: Check if overall borrow / profit logic is alright

//TODO: Separate Order and Position classes - Position class is a local class comprising of position value and a stop-loss order, Order class is a single BinanceHandler-supported order request


//FIXME: Refactor

//Note - we don't use borrowed funds when calculating or positions sizes. That's important during live trading. handle it properly that's why I made the request to the BinanceHandler API team

//TODO: Parse strategy as parameter from App instead of hard coding it into TempStrategyExecutor, unless TempStrategyExecutor is the strategy (later)
//TODO: Add symbol rules (later)
//TODO: BinanceHandler doesn't actually keep track of filled positions but it does offer a list (later)
//TODO: Add manual borrow action (later)
//TODO: Position liquidation (later)

public class BinanceHandler {

    public final static int ALGO_ORDER_LIMIT = 5;

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

    private final EventScheduler scheduler;

    private Event currentEvent;

    public BinanceHandler(Double initialUSDTPortfolio, EventScheduler scheduler) {

        this.scheduler = scheduler;

        this.userAssets.setFreeUSDT(initialUSDTPortfolio);
        this.userAssetsList.add(userAssets);
    }

    public void onEvent(Event event) {

        if (event == null) {
            System.out.println(currentEvent.getId() + "Exchange Error - Null exchange event in onEvent.");
            return;
        }

        this.currentEvent = event;

        increaseInterest();

        switch (event.getType()) {
            case TRANSACTION -> {
                this.transaction = event.getTransaction();
                checkFills();
            }

            case ACTION_REQUEST -> {
                handleUserActionRequest(event.getActionRequest());
            }

            default ->
                    System.out.println(currentEvent.getId() + "Exchange Error - Something other than transaction or action request ended up exchange's event stream.");
        }

        checkMarginLevel();
        updateUserDataStream();
    }

    private void updateUserDataStream() {
        if (userAssetsUpdated || !updatedOrders.isEmpty()) {

            userAssets.setTimestamp(currentEvent.getDelayedTimestamp());

            scheduler.addEvent(new Event(currentEvent.getDelayedTimestamp(), EventDestination.LOCAL, new UserDataStream(userAssets, updatedOrders)));

            if (userAssetsUpdated) {
                userAssetsList.add(new UserAssets(userAssets));
            }

            updatedOrders.clear();

            userAssetsUpdated = false;
        }
    }

    private void handleUserActionRequest(Map<OrderAction, Order> entryBlock) {

        if (entryBlock == null) {
            System.out.println(currentEvent.getId() + "Exchange error - EntryBlock in handleUserActionRequest is NULL which shouldn't be.");
            return;
        } else if (entryBlock.isEmpty()) {
            System.out.println(currentEvent.getId() + "Exchange Error - EntryBlock in handleUserActionRequest is empty which shouldn't be.");
        }

        for (Map.Entry<OrderAction, Order> actionRequest : entryBlock.entrySet()) {

            OrderAction orderAction = actionRequest.getKey();
            Order order = actionRequest.getValue();

            switch (orderAction) {
                case CREATE_ORDER -> {
                    boolean progOrdersReached = order.getType().equals(OrderType.LIMIT) &&
                            (newOrders.stream()
                                    .filter(p -> p.getType()
                                            .equals(OrderType.LIMIT))
                                    .count()
                                    >= BinanceHandler.ALGO_ORDER_LIMIT);

                    if (progOrdersReached) {
                        order.setStatus(OrderStatus.REJECTED);
                        order.setRejectionReason(RejectionReason.MAX_NUM_ALGO_ORDERS);
                        createActionResponse(ActionResponse.ORDER_REJECTED, order);
                        continue;
                    }

                    // Immediate trigger?
                    if (order.getType().equals(OrderType.LIMIT) &&
                            ((order.getDirection().equals(OrderSide.BUY) &&
                                    ((order.isStopLoss() && transaction.price() >= order.getOpenPrice()) ||
                                            (!order.isStopLoss() && transaction.price() <= order.getOpenPrice()))) ||
                                    (order.getDirection().equals(OrderSide.SELL) &&
                                            ((order.isStopLoss() && transaction.price() <= order.getOpenPrice()) ||
                                                    (!order.isStopLoss() && transaction.price() >= order.getOpenPrice()))))) {

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

                        if (isLong) {
                            order.setAppropriateUnitPositionValue(order.getSize() * fillPrice);
                        }

                        if ((isLong && userAssets.getFreeUSDT() < order.getSize() * fillPrice) || (!isLong && userAssets.getFreeBTC() < order.getSize())) { //Unless we have enough funds.

                            RejectionReason rejectionReason = borrowFunds(order, fillPrice);
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
                    boolean newOrdersContainsOrder = newOrders.stream().anyMatch(o -> o.getId() == order.getId());

                    if (!newOrdersContainsOrder || !order.getType().equals(OrderType.LIMIT)) {
                        order.setRejectionReason(RejectionReason.INVALID_ORDER_STATE);
                        createActionResponse(ActionResponse.ACTION_REJECTED, order);
                        continue;
                    }
                    if (order.isAutoRepayAtCancel() && order.getMarginBuyBorrowAmount() != 0.0) {

                        RejectionReason rejectionReason = repayFunds(order);

                        if (rejectionReason != null) {
                            order.setRejectionReason(rejectionReason);
                            createActionResponse(ActionResponse.ACTION_REJECTED, order);
                            continue;
                        }
                    }

                    order.setStatus(OrderStatus.CANCELED);
                    cancelledOrders.add(order);
                    newOrders.removeIf(o -> o.getId() == order.getId());

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

    private void checkFills() {
        for (Order order : newOrders) {
            if (order.getStatus().equals(OrderStatus.NEW)) { //FIXME: Status field is mutable by the client. We're not editing it too much but still not good practice

                boolean isMarketOrder = order.getType().equals(OrderType.MARKET);
                boolean isLong = order.getDirection().equals(OrderSide.BUY);

                if (isMarketOrder ||
                        ((isLong &&
                                ((order.isStopLoss() && transaction.price() >= order.getOpenPrice()) ||
                                        (!order.isStopLoss() && transaction.price() <= order.getOpenPrice()))) ||
                                (!isLong &&
                                        ((order.isStopLoss() && transaction.price() <= order.getOpenPrice()) ||
                                                (!order.isStopLoss() && transaction.price() >= order.getOpenPrice()))))) {

                    double fillPrice = SlippageHandler.getSlippageFillPrice(
                            isMarketOrder ? transaction.price() : order.getOpenPrice(),
                            order.getSize(),
                            order.getDirection()
                    );

                    if (isLong) {
                        order.setAppropriateUnitPositionValue(order.getSize() * fillPrice);
                    }

                    if (isMarketOrder && order.isAutomaticBorrow()) {

                        //FIXME: BinanceHandler only checks for asset we must borrow
                        if ((isLong && userAssets.getFreeUSDT() < order.getSize() * fillPrice - 0.000000001) || (!isLong && userAssets.getFreeBTC() < order.getSize() - 0.000000001)) {

                            RejectionReason rejectionReason = borrowFunds(order, fillPrice);
                            if (rejectionReason != null) {
                                order.setStatus(OrderStatus.REJECTED);
                                order.setRejectionReason(rejectionReason);
                                createActionResponse(ActionResponse.ORDER_REJECTED, order);
                                rejectedOrders.add(order); //For removal at the end of the method
                                continue;
                            }
                        }
                    }
                    if (isLong) {
                        if (userAssets.getFreeUSDT() < order.getAppropriateUnitPositionValue() - 0.000000001) {
                            if (isMarketOrder) { // Action response
                                order.setStatus(OrderStatus.REJECTED);
                                order.setRejectionReason(RejectionReason.INSUFFICIENT_FUNDS);
                                createActionResponse(ActionResponse.ORDER_REJECTED, order);
                                rejectedOrders.add(order);
                            } else { // Rejected order
                                rejectOrder(RejectionReason.INSUFFICIENT_FUNDS, order); //This one for example, reports over a user stream.
                            }
                            continue;
                        }

                        //Buy BTC

                        userAssets.setFreeUSDT(
                                userAssets.getFreeUSDT()
                                        - order.getSize() * fillPrice
                        );

                        userAssets.setFreeBTC(
                                userAssets.getFreeBTC()
                                        + order.getSize()
                        );

                    } else { //Short
                        if (userAssets.getFreeBTC() < order.getSize() - 0.000000001) {
                            if (isMarketOrder) {
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
                                        + order.getSize() * fillPrice
                        );
                    }

                    order.setFillPrice(fillPrice);
                    order.setFillTimestamp(currentEvent.getDelayedTimestamp());

                    acceptedOrderList.add(order);
                }
            }
        }

        if (!acceptedOrderList.isEmpty()) {

            for (Order order : acceptedOrderList) {
                order.setStatus(OrderStatus.FILLED);
            }

            filledOrders.addAll(acceptedOrderList);
            for (Order order : acceptedOrderList) {
                newOrders.removeIf(o -> o.getId() == order.getId());
            }
            updatedOrders.addAll(acceptedOrderList);
            acceptedOrderList.clear();

            userAssetsUpdated = true;
        }

        for (Order order : rejectedOrders) {
            newOrders.removeIf(o -> o.getId() == order.getId());
        }
    }

    private void increaseInterest() {
        if (currentEvent.getDelayedTimestamp() - previousInterestTimestamp >= 1000 * 60 * 60) {

            previousInterestTimestamp = currentEvent.getDelayedTimestamp();
            previousInterestTimestamp -= currentEvent.getDelayedTimestamp() % (1000 * 60 * 60);

            if (userAssets.getTotalBorrowedBTC() != 0 || userAssets.getTotalBorrowedUSDT() != 0) {

                userAssets.setRemainingInterestBTC(
                        userAssets.getRemainingInterestBTC()
                                + userAssets.getTotalBorrowedBTC() * TierManager.HOURLY_BTC_INTEREST_RATE
                );

                userAssets.setRemainingInterestUSDT(
                        userAssets.getRemainingInterestUSDT()
                                + userAssets.getTotalBorrowedUSDT() * TierManager.HOURLY_USDT_INTEREST_RATE
                );

                userAssetsUpdated = true;
            }
        }
    }

    private RejectionReason borrowFunds(Order order, double fillPrice) {

        if (order.getDirection().equals(OrderSide.BUY)) {

            double positionValue = order.getSize() * fillPrice;

            //Check maximum borrow amount
            int MAX_BORROW_USDT = 900_000;
            if (positionValue + userAssets.getTotalBorrowedUSDT() > MAX_BORROW_USDT) {
                return RejectionReason.EXCESS_BORROW;
            }

            //Calculate leverage

            tierManager.checkAndUpdateTier(positionValue + userAssets.getTotalBorrowedUSDT(), userAssets.getTotalBorrowedBTC());

            //Calculate required margin
            order.setBorrowCollateral(order.getSize() * fillPrice / tierManager.getCurrentLeverage());

            if (userAssets.getFreeUSDT() < order.getBorrowCollateral() - 0.000000001) {
                return RejectionReason.INSUFFICIENT_MARGIN;
            }

            order.setMarginBuyBorrowAmount(positionValue);

            //Lock funds
            userAssets.setFreeUSDT(
                    userAssets.getFreeUSDT()
                            - order.getBorrowCollateral()
            );

            userAssets.setLockedUSDT(
                    userAssets.getLockedUSDT()
                            + order.getBorrowCollateral()
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

            order.initializeInterest(fillPrice);

            userAssets.setRemainingInterestUSDT(
                    userAssets.getRemainingInterestUSDT()
                            + order.getTotalUnpaidInterest()
            );

        } else { //Short - borrow BTC

            //Check maximum borrow amount
            int MAX_BORROW_BTC = 72;
            if (order.getSize() + userAssets.getTotalBorrowedBTC() > MAX_BORROW_BTC) {
                return RejectionReason.EXCESS_BORROW;
            }

            //Calculate leverage
            tierManager.checkAndUpdateTier(userAssets.getTotalBorrowedUSDT(), order.getSize() + userAssets.getTotalBorrowedBTC());

            order.setBorrowCollateral(order.getSize() * fillPrice / tierManager.getCurrentLeverage());

            //Check margin requirement
            if (userAssets.getFreeUSDT() < order.getBorrowCollateral() - 0.000000001) {
                return RejectionReason.INSUFFICIENT_MARGIN;
            }

            order.setMarginBuyBorrowAmount(order.getSize());

            //Lock funds - USDT as collateral
            userAssets.setFreeUSDT(
                    userAssets.getFreeUSDT()
                            - order.getBorrowCollateral()
            );

            userAssets.setLockedUSDT(
                    userAssets.getLockedUSDT()
                            + order.getBorrowCollateral()
            );

            //Receive funds
            userAssets.setFreeBTC(
                    userAssets.getFreeBTC()
                            + order.getSize()
            );

            userAssets.setTotalBorrowedBTC(
                    userAssets.getTotalBorrowedBTC()
                            + order.getSize()
            );

            order.initializeInterest(fillPrice);

            userAssets.setRemainingInterestBTC(
                    userAssets.getRemainingInterestBTC()
                            + order.getTotalUnpaidInterest()
            );
        }

        return null;
    }

    //TODO: Check and refactor
    //TODO: Fixme: BinanceHandler isn't calculating our order interest for us, we must do so locally.
    // Currently the repay amount is saved in an order, but we must locally add the unpaid interest amount to that which would offset other calculations.
    //FIXME: Convert the repayFunds so an amount is sent, not an order? Or an order along with the amount?
    private RejectionReason repayFunds(Order order) {

        //FIXME: Not functional when only repaying interest.
//        if (order.getMarginBuyBorrowAmount() == 0.0) {
//            return RejectionReason.INVALID_ORDER_STATE; // No funds were actually borrowed
//        }

        if (order.getDirection().equals(OrderSide.BUY)) {
            //Return USDT

            if (userAssets.getFreeUSDT() < order.getAppropriateUnitPositionValue() + order.getTotalUnpaidInterest() - 0.000000001) {
                return RejectionReason.INSUFFICIENT_FUNDS;
            }

            if (userAssets.getLockedUSDT() < order.getBorrowCollateral() - 0.0000000001) {
                System.out.println(currentEvent.getId() + "Exchange Error at repayFunds side BUY - locked USDT has insufficient funds to unlock margin?");
            }

            if (userAssets.getTotalBorrowedUSDT() < order.getAppropriateUnitPositionValue()) {
                System.out.println(currentEvent.getId() + "Exchange Error at repayFunds side BUY - borrowed USDT would go negative if we were to exclude the borrowed amount?");
            }

            userAssets.setFreeUSDT(
                    userAssets.getFreeUSDT()
                            + order.getBorrowCollateral() // Margin
                            - order.getMarginBuyBorrowAmount() // How much we borrowed if we borrowed
                            - order.getTotalUnpaidInterest()
            );

            userAssets.setLockedUSDT(
                    userAssets.getLockedUSDT()
                            - order.getBorrowCollateral()
            );

            userAssets.setTotalBorrowedUSDT(
                    userAssets.getTotalBorrowedUSDT()
                            - order.getMarginBuyBorrowAmount()
            );

            if (userAssets.getRemainingInterestUSDT() < order.getTotalUnpaidInterest()) {
                System.out.println(currentEvent.getId() + "Exchange Error at repayFunds - unpaid interest would go negative?");
            }

            userAssets.setRemainingInterestUSDT(
                    userAssets.getRemainingInterestUSDT()
                            - order.getTotalUnpaidInterest()
            );

        } else {
            //Return BTC

            if (userAssets.getFreeBTC() < order.getMarginBuyBorrowAmount() - 0.000000001 || userAssets.getFreeUSDT() + order.getBorrowCollateral() <
                    order.getTotalUnpaidInterest() - 0.000000001) {
                return RejectionReason.INSUFFICIENT_FUNDS;
            }

            if (userAssets.getLockedUSDT() < order.getBorrowCollateral() - 0.0000000001) {
                System.out.println(currentEvent.getId() + "Exchange Error at repayFunds side SELL - locked USDT has insufficient funds to unlock margin?");
            }

            if (userAssets.getTotalBorrowedBTC() < order.getAppropriateUnitPositionValue() - 0.0000000001) {
                System.out.println(currentEvent.getId() + "Exchange Error at repayFunds side SELL - borrowed BTC would go negative if we were to exclude the borrowed amount?");
            }

            userAssets.setFreeBTC(
                    userAssets.getFreeBTC()
                            - order.getMarginBuyBorrowAmount()
                            - order.getTotalUnpaidInterest()
            );

            userAssets.setFreeUSDT(
                    userAssets.getFreeUSDT()
                            + order.getBorrowCollateral()
            );

            userAssets.setLockedUSDT(
                    userAssets.getLockedUSDT()
                            - order.getBorrowCollateral()
            );

            userAssets.setTotalBorrowedBTC(
                    userAssets.getTotalBorrowedBTC()
                            - order.getMarginBuyBorrowAmount()
            );

            if (userAssets.getRemainingInterestBTC() < order.getTotalUnpaidInterest()) {
                System.out.println(currentEvent.getId() + "Exchange Error at repayFunds - unpaid interest would go negative?");
            }

            userAssets.setRemainingInterestBTC(
                    userAssets.getRemainingInterestBTC()
                            - order.getTotalUnpaidInterest()
            );
        }

        userAssetsUpdated = true;

        return null;
    }

    private double checkMarginLevel() {
        double totalAssetValue = userAssets.getTotalAssetValue(transaction.price());

        double totalBorrowedAssetValue =
                userAssets.getTotalBorrowedUSDT() +
                        userAssets.getTotalBorrowedBTC() * transaction.price();

        double marginLevel;
        if (totalBorrowedAssetValue
                + userAssets.getRemainingInterestUSDT()
                + userAssets.getRemainingInterestBTC() * transaction.price() == 0) {

            marginLevel = 999;
        } else {
            marginLevel = Math.min(999, totalAssetValue /
                    (totalBorrowedAssetValue
                            + userAssets.getRemainingInterestUSDT()
                            + userAssets.getRemainingInterestBTC() * transaction.price()
                    )
            );
        }

        userAssets.setMarginLevel(marginLevel);

        if (marginLevel <= 1.05) {
            System.out.println(currentEvent.getId() + "marginLevel = " + marginLevel);

            for (Order order : filledOrders) {
                liquidateOrder(order);
            }
        }

        return marginLevel;
    }

    //TODO: liquidate 'position'?
    private void liquidateOrder(Order order) {
        System.out.println(currentEvent.getId() + "Liquidation");
        System.exit(1);
    }

    private void createActionResponse(ActionResponse response, Order order) {
        scheduler.addEvent(new Event(currentEvent.getDelayedTimestamp(), EventDestination.LOCAL, response, order));
    }

    private void rejectOrder(RejectionReason reason, Order order) {
        order.setStatus(OrderStatus.REJECTED);
        order.setRejectionReason(reason);
        rejectedOrders.add(order);
        updatedOrders.add(order);
    }

    public double getTotalAssetsValue() {
        if (transaction != null) {
            return userAssets.getTotalAssetValue(transaction.price());
        }
        return 0.0;
    }

    public void addToUserAssetList(UserAssets userAsset) {
        this.userAssetsList.add(userAsset);
    }

    public ArrayList<Order> getAllOrders() {
        ArrayList<Order> orders = new ArrayList<>();
        orders.addAll(newOrders);
        orders.addAll(filledOrders);
        orders.addAll(rejectedOrders);
        orders.addAll(cancelledOrders);

        return orders;
    }

    public UserAssets getUserAssets() {
        return this.userAssets;
    }

}
