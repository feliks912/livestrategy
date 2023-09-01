package com.localstrategy;

import com.localstrategy.util.enums.*;
import com.localstrategy.util.helper.EventScheduler;
import com.localstrategy.util.helper.SlippageHandler;
import com.localstrategy.util.helper.TierManager;
import com.localstrategy.util.types.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedList;


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

    public final static boolean LOG_KEY_EVENTS = false;

    private final ArrayList<Order> newOrders = new ArrayList<>();
    private final ArrayList<Order> filledOrders = new ArrayList<>();
    private final ArrayList<Order> cancelledOrders = new ArrayList<>();
    private final ArrayList<Order> rejectedOrders = new ArrayList<>();
    private final ArrayList<Order> acceptedOrderList = new ArrayList<>();

    private final ArrayList<Order> updatedOrders = new ArrayList<>();

    private final LinkedList<CustomMapWrapper> historicalList = new LinkedList<>();

    private final UserAssets userAssets = new UserAssets();
    private final ArrayList<UserAssets> userAssetsList = new ArrayList<>();
    private final TierManager tierManager = new TierManager();

    private SingleTransaction transaction;

    private long previousInterestTimestamp = 0;

    private boolean userAssetsUpdated = false;

    private final EventScheduler scheduler;

    private Event currentEvent;

    public BinanceHandler(double initialUSDTPortfolio, EventScheduler scheduler) {

        this.scheduler = scheduler;

        this.userAssets.setFreeUSDT(BigDecimal.valueOf(initialUSDTPortfolio));
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
                handleUserActionRequest(event.getActionRequest(), event.getOrder());
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
            userAssets.setSavePrice(transaction.price());

            scheduler.addEvent(new Event(currentEvent.getDelayedTimestamp(), EventDestination.LOCAL, new UserDataStream(userAssets, updatedOrders)));

            if (userAssetsUpdated) {
                userAssetsList.add(new UserAssets(userAssets));
            }

            updatedOrders.clear();

            userAssetsUpdated = false;
        }
    }

    private void handleUserActionRequest(OrderAction action, Order order) {

        if (action == null || order == null) {
            System.out.println(currentEvent.getId() + "Exchange error - EntryBlock in handleUserActionRequest is NULL which shouldn't be.");
            return;
        }

        switch (action) {
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
                    break;
                }

                // Immediate trigger?
                if (order.getType().equals(OrderType.LIMIT) &&
                        ((order.getDirection().equals(OrderSide.BUY) &&
                                ((order.isStopLoss() && transaction.price() >= order.getOpenPrice().doubleValue()) ||
                                        (!order.isStopLoss() && transaction.price() <= order.getOpenPrice().doubleValue()))) ||
                        (order.getDirection().equals(OrderSide.SELL) &&
                                ((order.isStopLoss() && transaction.price() <= order.getOpenPrice().doubleValue()) ||
                                        (!order.isStopLoss() && transaction.price() >= order.getOpenPrice().doubleValue()))))) {

                    order.setStatus(OrderStatus.REJECTED);
                    order.setRejectionReason(RejectionReason.WOULD_TRIGGER_IMMEDIATELY);
                    createActionResponse(ActionResponse.ORDER_REJECTED, order);
                    break;
                }

                // "If you use MARGIN_BUY, the amount necessary to borrow in order to execute your order later will be borrowed at the creation time, regardless of the order type (whether it's limit or stop-limit)."
                if (order.isAutomaticBorrow() && order.getType().equals(OrderType.LIMIT)) {
                    //We checked for already available funds in the checkFills method for market orders

                    boolean isLong = order.getDirection().equals(OrderSide.BUY);

                    //Hacky as hell here - binance usually borrows extra funds I already see we're going to have issues with automatic borrowings.
//                    BigDecimal fillPrice = SlippageHandler.getSlippageFillPrice(
//                            order.getOpenPrice(),
//                            order.getSize(),
//                            order.getDirection()
//                    );

                    BigDecimal fillPrice = SlippageHandler.getSlippageFillFromBook(
                            order.getOpenPrice(),
                            order.getSize(),
                            order.getDirection()
                    );

                    if (isLong) {
                        order.setAppropriateUnitPositionValue(order.getSize().multiply(fillPrice));
                    }

                    if ((isLong && userAssets.getFreeUSDT().compareTo(order.getSize().multiply(fillPrice)) < 0)
                            || (!isLong && userAssets.getFreeBTC().compareTo(order.getSize()) < 0)) { //Unless we have enough funds.

                        RejectionReason rejectionReason = borrowFunds(order, fillPrice);
                        if (rejectionReason != null) {
                            order.setStatus(OrderStatus.REJECTED);
                            order.setRejectionReason(rejectionReason);
                            createActionResponse(ActionResponse.ORDER_REJECTED, order);
                            break;
                        }
                    }
                }

                logToHistory(order, userAssets, "Created order " + order.getId() + ", position " + order.getPositionId());

                order.setStatus(OrderStatus.NEW);
                newOrders.add(order);

                createActionResponse(ActionResponse.ORDER_CREATED, order);
                updatedOrders.add(order);
            }
            case CANCEL_ORDER -> {

                if(order.getPositionId() == 9792){
                    boolean point = true;
                }

                boolean newOrdersContainsOrder = newOrders.stream().anyMatch(o -> o.getId() == order.getId());

                if (!newOrdersContainsOrder || !order.getType().equals(OrderType.LIMIT)) {
                    order.setStatus(OrderStatus.REJECTED);
                    order.setRejectionReason(RejectionReason.INVALID_ORDER_STATE);
                    createActionResponse(ActionResponse.ACTION_REJECTED, order);
                    break;
                }
                if (order.isAutoRepayAtCancel() && order.getMarginBuyBorrowAmount().doubleValue() > 0e-7) {

                    RejectionReason rejectionReason = repayFunds(order);

                    if (rejectionReason != null) {
                        order.setStatus(OrderStatus.REJECTED);
                        order.setRejectionReason(rejectionReason);
                        createActionResponse(ActionResponse.ACTION_REJECTED, order);
                        logToHistory(order, userAssets, "Rejected repay on cancel order " + order.getId() + ", position " + order.getPositionId());
                        break;
                    }
                }

                logToHistory(order, userAssets, "Cancelled order " + order.getId() + ", position " + order.getPositionId());

                order.setStatus(OrderStatus.CANCELED);
                cancelledOrders.add(order);
                newOrders.removeIf(o -> o.getId() == order.getId());

                createActionResponse(ActionResponse.ORDER_CANCELLED, order);
                updatedOrders.add(order);
            }
            case REPAY_FUNDS -> {
                RejectionReason rejectionReason = repayFunds(order);
                if (rejectionReason != null) {
                    order.setStatus(OrderStatus.REJECTED);
                    order.setRejectionReason(rejectionReason);
                    createActionResponse(ActionResponse.ACTION_REJECTED, order);
                    logToHistory(order, userAssets, "Rejected repay order " + order.getId() + ", position " + order.getPositionId());
                    break;
                }
                createActionResponse(ActionResponse.FUNDS_REPAID, order);
            }
        }
    }

    private void checkFills() {
        ArrayList<Order> tempRejectedOrder = new ArrayList<>();

        for (Order order : newOrders) {
            if (order.getStatus().equals(OrderStatus.NEW)) { //FIXME: Status field is mutable by the client. We're not editing it too much but still not good practice

                boolean isMarketOrder = order.getType().equals(OrderType.MARKET);
                boolean isLong = order.getDirection().equals(OrderSide.BUY);

                if (isMarketOrder ||
                        ((isLong &&
                                ((order.isStopLoss() && transaction.price() >= order.getOpenPrice().doubleValue()) ||
                                        (!order.isStopLoss() && transaction.price() <= order.getOpenPrice().doubleValue()))) ||
                                (!isLong &&
                                        ((order.isStopLoss() && transaction.price() <= order.getOpenPrice().doubleValue()) ||
                                                (!order.isStopLoss() && transaction.price() >= order.getOpenPrice().doubleValue()))))) {


                    if(order.getPositionId() == 8224 || order.getPositionId() == 8225 || order.getPositionId() == 8229){
                        boolean point = true;
                    }

//                    BigDecimal fillPrice = SlippageHandler.getSlippageFillPrice(
//                            isMarketOrder ? BigDecimal.valueOf(transaction.price()) : order.getOpenPrice(),
//                            order.getSize(),
//                            order.getDirection()
//                    );

                    BigDecimal fillPrice = SlippageHandler.getSlippageFillFromBook(
                            order.getOpenPrice(),
                            order.getSize(),
                            order.getDirection()
                    );

                    if (isLong) {
                        order.setAppropriateUnitPositionValue(order.getSize().multiply(fillPrice));
                    }

                    if (isMarketOrder && order.isAutomaticBorrow()) {

                        //FIXME: BinanceHandler only checks for asset we must borrow
                        if ((isLong && userAssets.getFreeUSDT().compareTo(order.getSize().multiply(fillPrice)) < 0)
                                || (!isLong && userAssets.getFreeBTC().compareTo(order.getSize()) < 0)) {

                            RejectionReason rejectionReason = borrowFunds(order, fillPrice);
                            if (rejectionReason != null) {
                                order.setStatus(OrderStatus.REJECTED);
                                order.setRejectionReason(rejectionReason);
                                createActionResponse(ActionResponse.ORDER_REJECTED, order);
                                tempRejectedOrder.add(order);
                                rejectedOrders.add(order); //For removal at the end of the method
                                logToHistory(order, userAssets, "Rejected "
                                        + (order.getDirection().equals(OrderSide.BUY) ? "USDT" : "BTC")
                                        + " borrow order " + order.getId() + ", position " + order.getPositionId());
                                continue;
                            }
                        }
                    }

                    boolean payFee = order.getPurpose().equals(OrderPurpose.STOP) || order.getPurpose().equals(OrderPurpose.CLOSE);

                    payFee = false;

                    if (isLong) {
                        if (userAssets.getFreeUSDT().compareTo(order.getAppropriateUnitPositionValue()) < 0) {
                            if (isMarketOrder) { // Action response
                                order.setStatus(OrderStatus.REJECTED);
                                order.setRejectionReason(RejectionReason.INSUFFICIENT_FUNDS);
                                createActionResponse(ActionResponse.ORDER_REJECTED, order);
                                tempRejectedOrder.add(order);
                                rejectedOrders.add(order);
                            } else { // Rejected order
                                rejectOrder(RejectionReason.INSUFFICIENT_FUNDS, order); //This one for example, reports over a user stream.
                            }
                            logToHistory(order, userAssets, "Rejected long order " + order.getId() + ", position " + order.getPositionId());
                            continue;
                        }

                        //Buy BTC

                        userAssets.setFreeUSDT(
                                userAssets.getFreeUSDT()
                                        .subtract(order.getSize().multiply(fillPrice)
                                                .multiply(payFee ? BigDecimal.valueOf(1.00016) : BigDecimal.ONE))
                        );

                        userAssets.setFreeBTC(
                                userAssets.getFreeBTC()
                                        .add(order.getSize())
                        );

                        logToHistory(order, userAssets, "Longed order " + order.getId() + ", position " + order.getPositionId());

                    } else { //Short
                        if (userAssets.getFreeBTC().compareTo(order.getSize()) < 0) {
                            if (isMarketOrder) {
                                order.setStatus(OrderStatus.REJECTED);
                                order.setRejectionReason(RejectionReason.INSUFFICIENT_FUNDS);
                                createActionResponse(ActionResponse.ORDER_REJECTED, order);
                                tempRejectedOrder.add(order);
                                rejectedOrders.add(order);
                            } else {
                                rejectOrder(RejectionReason.INSUFFICIENT_FUNDS, order); //FIXME: This one for example, would report over a user stream.
                            }
                            logToHistory(order, userAssets, "Rejected short order " + order.getId() + ", position " + order.getPositionId());
                            continue;
                        }
                        //Sell BTC

                        userAssets.setFreeBTC(
                                userAssets.getFreeBTC()
                                        .subtract(order.getSize())
                        );
                        userAssets.setFreeUSDT(
                                userAssets.getFreeUSDT()
                                        .add(order.getSize().multiply(fillPrice)
                                                .multiply(payFee ? BigDecimal.valueOf(0.99984) : BigDecimal.ONE))
                        );



                        logToHistory(order, userAssets, "Shorted order " + order.getId() + ", position " + order.getPositionId());
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

        for (Order order : tempRejectedOrder) {
            newOrders.removeIf(o -> o.getId() == order.getId());
        }
    }

    private void increaseInterest() {
        if (currentEvent.getDelayedTimestamp() - previousInterestTimestamp >= 1000 * 60 * 60) {

            previousInterestTimestamp = currentEvent.getDelayedTimestamp();
            previousInterestTimestamp -= currentEvent.getDelayedTimestamp() % (1000 * 60 * 60);

            if (userAssets.getTotalBorrowedBTC().compareTo(BigDecimal.ZERO) != 0 || userAssets.getTotalBorrowedUSDT().compareTo(BigDecimal.ZERO) != 0) {

                userAssets.setRemainingInterestBTC(
                        userAssets.getRemainingInterestBTC()
                                .add(userAssets.getTotalBorrowedBTC().multiply(TierManager.HOURLY_BTC_INTEREST_RATE_PCT)
                                        .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP))
                );

                userAssets.setRemainingInterestUSDT(
                        userAssets.getRemainingInterestUSDT()
                                .add(userAssets.getTotalBorrowedUSDT().multiply(TierManager.HOURLY_USDT_INTEREST_RATE_PCT)
                                        .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP))
                );

                userAssetsUpdated = true;
            }
        }
    }

    private RejectionReason borrowFunds(Order order, BigDecimal fillPrice) {

        if (order.getDirection().equals(OrderSide.BUY)) {

            BigDecimal positionValue = order.getSize().multiply(fillPrice);

            //Check maximum borrow amount
            if (positionValue.add(userAssets.getTotalBorrowedUSDT()).compareTo(TierManager.MAX_BORROW_USDT) > 0) {
                return RejectionReason.EXCESS_BORROW;
            }

            //Calculate leverage

            tierManager.checkAndUpdateTier(positionValue.add(userAssets.getTotalBorrowedUSDT()).doubleValue(), userAssets.getTotalBorrowedBTC().doubleValue());

            //Calculate required margin
            order.setBorrowCollateral(order.getSize().multiply(fillPrice).divide(BigDecimal.valueOf(tierManager.getCurrentLeverage()), 8, RoundingMode.HALF_UP));

            if (userAssets.getFreeUSDT().compareTo(order.getBorrowCollateral()) < 0) {
                return RejectionReason.INSUFFICIENT_MARGIN;
            }

            order.setMarginBuyBorrowAmount(positionValue);

            //Lock funds
            userAssets.setFreeUSDT(
                    userAssets.getFreeUSDT()
                            .subtract(order.getBorrowCollateral())
            );

            userAssets.setLockedUSDT(
                    userAssets.getLockedUSDT()
                            .add(order.getBorrowCollateral())
            );

            //Receive asset
            userAssets.setFreeUSDT(
                    userAssets.getFreeUSDT()
                            .add(positionValue)
            );

            userAssets.setTotalBorrowedUSDT(
                    userAssets.getTotalBorrowedUSDT()
                            .add(positionValue)
            );

            order.initializeInterest();

            userAssets.setRemainingInterestUSDT(
                    userAssets.getRemainingInterestUSDT()
                            .add(order.getTotalUnpaidInterest())
            );

            logToHistory(order, userAssets, "Borrowed USDT order " + order.getId() + ", position " + order.getPositionId());

        } else { //Short - borrow BTC

            //Check maximum borrow amount
            int MAX_BORROW_BTC = 72;
            if (order.getSize().add(userAssets.getTotalBorrowedBTC()).compareTo(TierManager.MAX_BORROW_BTC) > 0) {
                return RejectionReason.EXCESS_BORROW;
            }

            //Calculate leverage
            tierManager.checkAndUpdateTier(userAssets.getTotalBorrowedUSDT().doubleValue(), order.getSize().add(userAssets.getTotalBorrowedBTC()).doubleValue());

            order.setBorrowCollateral(order.getSize().multiply(fillPrice).divide(BigDecimal.valueOf(tierManager.getCurrentLeverage()), 8, RoundingMode.HALF_UP));

            //Check margin requirement
            if (userAssets.getFreeUSDT().compareTo(order.getBorrowCollateral()) < 0) {
                return RejectionReason.INSUFFICIENT_MARGIN;
            }

            order.setMarginBuyBorrowAmount(order.getSize());

            //Lock funds - USDT as collateral
            userAssets.setFreeUSDT(
                    userAssets.getFreeUSDT()
                            .subtract(order.getBorrowCollateral())
            );

            userAssets.setLockedUSDT(
                    userAssets.getLockedUSDT()
                            .add(order.getBorrowCollateral())
            );

            //Receive funds
            userAssets.setFreeBTC(
                    userAssets.getFreeBTC()
                            .add(order.getSize())
            );

            userAssets.setTotalBorrowedBTC(
                    userAssets.getTotalBorrowedBTC()
                            .add(order.getSize())
            );

            order.initializeInterest();

            userAssets.setRemainingInterestBTC(
                    userAssets.getRemainingInterestBTC()
                            .add(order.getTotalUnpaidInterest())
            );

            logToHistory(order, userAssets, "Borrowed BTC order " + order.getId() + ", position " + order.getPositionId());
        }

//        if(StrategyStarter.currentDay == 52){
//            if(order.getDirection().equals(OrderSide.BUY)){
//                System.out.printf("EXCHANGE: borrowed %.2f USDT.\n", order.getMarginBuyBorrowAmount().doubleValue());
//            } else {
//                System.out.printf("EXCHANGE: borrowed %.8f BTC.\n", order.getMarginBuyBorrowAmount().doubleValue());
//            }
//        }


        return null;
    }

    //TODO: Check and refactor
    //TODO: Fixme: BinanceHandler isn't calculating our order interest for us, we must do so locally.
    // Currently the repay amount is saved in an order, but we must locally add the unpaid interest amount to that which would offset other calculations.
    //FIXME: Convert the repayFunds so an amount is sent, not an order? Or an order along with the amount?
    private RejectionReason repayFunds(Order order) {

        if(!order.getPurpose().equals(OrderPurpose.ENTRY)){
            boolean point = true;
        }

        if(order.getPositionId() == 8224 || order.getPositionId() == 8225 || order.getPositionId() == 8229){
            boolean point = true;
        }

        //FIXME: Not functional when only repaying interest.
//        if (order.getMarginBuyBorrowAmount() == 0.0) {
//            return RejectionReason.INVALID_ORDER_STATE; // No funds were actually borrowed
//        }

        if (order.getDirection().equals(OrderSide.BUY)) {
            //Return USDT

            if (userAssets.getFreeUSDT().compareTo(order.getAppropriateUnitPositionValue().add(order.getTotalUnpaidInterest())) < 0) {
                return RejectionReason.INSUFFICIENT_FUNDS;
            }

            if (userAssets.getLockedUSDT().compareTo(order.getBorrowCollateral()) < 0) {
                System.out.println(currentEvent.getId() + "Exchange Error at repayFunds side BUY - locked USDT has insufficient funds to unlock margin?");
            }

            if (userAssets.getTotalBorrowedUSDT().compareTo(order.getAppropriateUnitPositionValue()) < 0) {
                System.out.println(currentEvent.getId() + "Exchange Error at repayFunds side BUY - borrowed USDT would go negative if we were to exclude the borrowed amount?");
            }

            userAssets.setFreeUSDT(
                    userAssets.getFreeUSDT()
                            .add(order.getBorrowCollateral()) // Margin
                            .subtract(order.getMarginBuyBorrowAmount()) // How much we borrowed if we borrowed
                            .subtract(order.getTotalUnpaidInterest())
            );

            userAssets.setLockedUSDT(
                    userAssets.getLockedUSDT()
                            .subtract(order.getBorrowCollateral())
            );

            userAssets.setTotalBorrowedUSDT(
                    userAssets.getTotalBorrowedUSDT()
                            .subtract(order.getMarginBuyBorrowAmount())
            );

            if (userAssets.getRemainingInterestUSDT().compareTo(order.getTotalUnpaidInterest()) < 0
                    && userAssets.getRemainingInterestUSDT().subtract(order.getTotalUnpaidInterest()).doubleValue() < 20) {
                System.out.println(currentEvent.getId() + "Exchange Error at repayFunds - unpaid USDT interest would go to "
                        + userAssets.getRemainingInterestUSDT().subtract(order.getTotalUnpaidInterest()).doubleValue() + " ?");
            }

            userAssets.setRemainingInterestUSDT(
                    userAssets.getRemainingInterestUSDT()
                            .subtract(order.getTotalUnpaidInterest())
            );

            logToHistory(order, userAssets, "Repaid USDT order " + order.getId() + ", position " + order.getPositionId());


        } else {
            //Return BTC

            if (userAssets.getFreeBTC().compareTo(order.getMarginBuyBorrowAmount()) < 0
                    || userAssets.getFreeUSDT().add(order.getBorrowCollateral()).compareTo(order.getTotalUnpaidInterest()) < 0) {
                return RejectionReason.INSUFFICIENT_FUNDS;
            }

            if (userAssets.getLockedUSDT().compareTo(order.getBorrowCollateral()) < 0) {
                System.out.println(currentEvent.getId() + "Exchange Error at repayFunds side SELL - locked USDT has insufficient funds to unlock margin?");
            }

            if (userAssets.getTotalBorrowedBTC().compareTo(order.getAppropriateUnitPositionValue()) < 0) {
                System.out.println(currentEvent.getId() + "Exchange Error at repayFunds side SELL - borrowed BTC would go negative if we were to exclude the borrowed amount?");
            }

            userAssets.setFreeBTC(
                    userAssets.getFreeBTC()
                            .subtract(order.getMarginBuyBorrowAmount())
                            .subtract(order.getTotalUnpaidInterest())
            );

            userAssets.setFreeUSDT(
                    userAssets.getFreeUSDT()
                            .add(order.getBorrowCollateral())
            );

            userAssets.setLockedUSDT(
                    userAssets.getLockedUSDT()
                            .subtract(order.getBorrowCollateral())
            );

            userAssets.setTotalBorrowedBTC(
                    userAssets.getTotalBorrowedBTC()
                            .subtract(order.getMarginBuyBorrowAmount())
            );

            if (userAssets.getRemainingInterestBTC().compareTo(order.getTotalUnpaidInterest()) < 0
                    && userAssets.getRemainingInterestBTC().subtract(order.getTotalUnpaidInterest()).doubleValue() * transaction.price() < 20) {
                System.out.println(currentEvent.getId() + "Exchange Error at repayFunds - unpaid BTC interest would go to "
                        + userAssets.getRemainingInterestBTC().subtract(order.getTotalUnpaidInterest()).doubleValue() + " ?");
            }

            userAssets.setRemainingInterestBTC(
                    userAssets.getRemainingInterestBTC()
                            .subtract(order.getTotalUnpaidInterest())
            );

            logToHistory(order, userAssets, "Repaid BTC order " + order.getId() + ", position " + order.getPositionId());

        }

        userAssetsUpdated = true;

        return null;
    }

    private double checkMarginLevel() {
        double freeUSDT = userAssets.getFreeUSDT().doubleValue();
        double lockedUSDT = userAssets.getLockedUSDT().doubleValue();
        double freeBTC = userAssets.getFreeBTC().doubleValue();
        double lockedBTC = userAssets.getLockedBTC().doubleValue();
        double totalBorrowedUSDT = userAssets.getTotalBorrowedUSDT().doubleValue();
        double totalBorrowedBTC = userAssets.getTotalBorrowedBTC().doubleValue();
        double remainingInterestUSDT = userAssets.getRemainingInterestUSDT().doubleValue();
        double remainingInterestBTC = userAssets.getRemainingInterestBTC().doubleValue();

        double price = transaction.price();

        double totalAssetValue = (freeUSDT + lockedUSDT + (freeBTC + lockedBTC) * price);
        double totalBorrowAndInterestValue = (totalBorrowedUSDT + totalBorrowedBTC * price +
                remainingInterestUSDT + remainingInterestBTC * price);

        double marginLevel = (totalBorrowAndInterestValue <= 0) ? 999 : Math.min(999, totalAssetValue / totalBorrowAndInterestValue);

        if (marginLevel <= 1.05) {
            System.out.println(currentEvent.getId() + " marginLevel = " + marginLevel);
            filledOrders.forEach(this::liquidateOrder);
        }

        userAssets.setMarginLevel(marginLevel);
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

    public BigDecimal getTotalAssetsValue() {
        if (transaction != null) {
            return userAssets.getTotalAssetValue(BigDecimal.valueOf(transaction.price()));
        }
        return BigDecimal.valueOf(-1);
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

    public ArrayList<UserAssets> getUserAssetsList(){
        return this.userAssetsList;
    }

    private void logToHistory(Order order, UserAssets assets, String message){
        if(LOG_KEY_EVENTS){
            historicalList.addFirst(new CustomMapWrapper(order, assets, message));
        }
    }
}
