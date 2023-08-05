package com.localstrategy;

import com.localstrategy.util.enums.*;
import com.localstrategy.util.helper.*;
import com.localstrategy.util.types.Candle;
import com.localstrategy.util.types.Event;
import com.localstrategy.util.types.SingleTransaction;
import com.localstrategy.util.types.UserDataStream;

import java.util.ArrayList;
import java.util.Map;

public class LocalHandler {

    private static final int CANDLE_VOLUME = 2_000_000;
    private static final double RISK_PCT = 0.1;
    private static final int SLIPPAGE_PCT = 15; //In relation to the difference between our entry and stop-loss price difference, how much in percentage of slippage are we ready to accept (total, not the average fill price)

    private final EventScheduler scheduler;
    private final CandleConstructor candleConstructor = new CandleConstructor(CANDLE_VOLUME);

    private final ArrayList<Position> activePositions = new ArrayList<>();
    private final ArrayList<Position> inactivePositions = new ArrayList<>();

    private UserAssets userAssets = new UserAssets();
    private final OrderRequest orderRequest;
    private Candle lastCandle;

    private ArrayList<Candle> candles = new ArrayList<>();

    private Event currentEvent;
    private SingleTransaction transaction;

    private boolean closeRequestSent = false;

    private boolean printedInactivePositions = false;

    UserStrategy strategy;

    public LocalHandler(double initialFreeUSDT, EventScheduler scheduler) {

        this.scheduler = scheduler;

        userAssets.setFreeUSDT(initialFreeUSDT);

        orderRequest = new OrderRequest(
                activePositions,
                new TierManager(),
                userAssets,
                RISK_PCT,
                BinanceHandler.ALGO_ORDER_LIMIT,
                SLIPPAGE_PCT);

        //TODO: Print parameters on strategy start

        strategy = new UserStrategy(this, transaction, candles, activePositions, inactivePositions);
    }

    private void priceUpdate(SingleTransaction transaction) {
        strategy.priceUpdate(transaction);
    }

    private void newCandle(Candle lastCandle) {
        strategy.candleUpdate(lastCandle);
    }

    public Position executeMarketOrder(double stopPrice){
        Position newMarketPosition = orderRequest.newMarketPosition(transaction, stopPrice);

        if (newMarketPosition != null) {
            scheduler.addEvent(new Event(
                    currentEvent.getDelayedTimestamp(),
                    EventDestination.EXCHANGE,
                    OrderAction.CREATE_ORDER,
                    newMarketPosition.getEntryOrder().clone()
            ));
        }

        return newMarketPosition;
    }

    public Position executeLimitOrder(double entryPrice, double stopPrice){
        Position newLimitPosition = orderRequest.newLimitPosition(entryPrice, stopPrice, transaction);

        if (newLimitPosition != null) {
            scheduler.addEvent(new Event(
                    currentEvent.getDelayedTimestamp(),
                    EventDestination.EXCHANGE,
                    OrderAction.CREATE_ORDER,
                    newLimitPosition.getEntryOrder().clone()
            ));
        }

        return newLimitPosition;
    }

    public boolean activateStopLoss(Position position){
        if(position == null || position.isStopLossRequestSent() || position.isActiveStopLoss()){
            return true;
        }

        scheduler.addEvent(new Event(
                currentEvent.getDelayedTimestamp(),
                EventDestination.EXCHANGE,
                OrderAction.CREATE_ORDER,
                position.getStopOrder().clone()
        ));

        position.setStopLossRequestSent(true);

        return false;
    }

    public boolean updateStopLoss(double newStopPrice, Position position){
        if (position == null || !position.isActiveStopLoss() || !position.getGroup().equals(PositionGroup.FILLED)) { //Must be a better way
            return true;
        }

        scheduler.addEvent(new Event(
                currentEvent.getDelayedTimestamp(),
                EventDestination.EXCHANGE,
                OrderAction.CANCEL_ORDER,
                position.getStopOrder().clone()
        ));

        position.getStopOrder().setOpenPrice(46400);
        position.setOpenTimestamp(currentEvent.getDelayedTimestamp());

        scheduler.addEvent(new Event(
                currentEvent.getDelayedTimestamp(),
                EventDestination.EXCHANGE,
                OrderAction.CREATE_ORDER,
                position.getStopOrder().clone()
        ));

        position.setBreakEven(true);

        return false;
    }

    public boolean cancelPosition(Position position){
        if(position == null || !position.getGroup().equals(PositionGroup.NEW) || !position.getGroup().equals(PositionGroup.FILLED)){
            return true;
        }

        scheduler.addEvent(new Event(
                currentEvent.getDelayedTimestamp(),
                EventDestination.EXCHANGE,
                OrderAction.CANCEL_ORDER,
                position.getEntryOrder().clone()
        ));

        return false;
    }

    public boolean closePosition(Position position){
        if (position == null || position.getCloseOrder() != null || !position.getGroup().equals(PositionGroup.FILLED)) {
            return true;
        }

        Order closeOrder = position.createCloseOrder(transaction);
        position.setCloseOrder(closeOrder);

        scheduler.addEvent(new Event(
                currentEvent.getDelayedTimestamp(),
                EventDestination.EXCHANGE,
                OrderAction.CREATE_ORDER,
                closeOrder.clone()
        ));

        return false;
    }


    private void test4(){
        if (activePositions.isEmpty()) {
            Position newMarketPosition = orderRequest.newLimitPosition(46560, 46580, transaction);

            if (newMarketPosition != null) {
                scheduler.addEvent(new Event(
                        currentEvent.getDelayedTimestamp(),
                        EventDestination.EXCHANGE,
                        OrderAction.CREATE_ORDER,
                        newMarketPosition.getEntryOrder().clone()
                ));
            }
        } else {
            for (Position position : activePositions) {
                switch (position.getGroup()){
                    case NEW -> {
                        if (!position.isStopLossRequestSent()) {
                            scheduler.addEvent(new Event(
                                    currentEvent.getDelayedTimestamp(),
                                    EventDestination.EXCHANGE,
                                    OrderAction.CREATE_ORDER,
                                    position.getStopOrder().clone()
                            ));
                            position.setStopLossRequestSent(true);
                        }
                    }
                    case FILLED -> {
                        if (!position.isBreakEven() && position.isActiveStopLoss() && transaction.price() <= 46300) { //Must be a better way

                            scheduler.addEvent(new Event(
                                    currentEvent.getDelayedTimestamp(),
                                    EventDestination.EXCHANGE,
                                    OrderAction.CANCEL_ORDER,
                                    position.getStopOrder().clone()
                            ));

                            position.getStopOrder().setOpenPrice(46400);
                            position.setOpenTimestamp(currentEvent.getDelayedTimestamp());

                            scheduler.addEvent(new Event(
                                    currentEvent.getDelayedTimestamp(),
                                    EventDestination.EXCHANGE,
                                    OrderAction.CREATE_ORDER,
                                    position.getStopOrder().clone()
                            ));

                            position.setBreakEven(true);
                        }
                    }
                }
            }
        }
        if (!inactivePositions.isEmpty() && !printedInactivePositions) {
            for (Position position : inactivePositions) {
                if (position.getGroup().equals(PositionGroup.CLOSED)) {
                    System.out.printf("Position SL'd successfully with a profit is $%.2f\n", position.getProfit());

                    printedInactivePositions = true;
                }
            }
        }
    } // Limit with BE

    private void test3(){
        if (activePositions.isEmpty() && inactivePositions.isEmpty()) {
            Position newMarketPosition = orderRequest.newLimitPosition(45000, 44900, transaction);

            if (newMarketPosition != null) {
                scheduler.addEvent(new Event(
                        currentEvent.getDelayedTimestamp(),
                        EventDestination.EXCHANGE,
                        OrderAction.CREATE_ORDER,
                        newMarketPosition.getEntryOrder().clone()
                ));
            }
        } else {
            for (Position position : activePositions) {
                switch (position.getGroup()){
                    case NEW -> {
                        if (!position.isStopLossRequestSent()) {
                            scheduler.addEvent(new Event(
                                    currentEvent.getDelayedTimestamp(),
                                    EventDestination.EXCHANGE,
                                    OrderAction.CREATE_ORDER,
                                    position.getStopOrder().clone()
                            ));
                            position.setStopLossRequestSent(true);
                        }
                    }
                    case FILLED -> {
                        if (position.getCloseOrder() == null && position.isActiveStopLoss() && transaction.price() >= 46200) { //Must be a better way

                            Order closeOrder = position.createCloseOrder(transaction);

                            scheduler.addEvent(new Event(
                                    currentEvent.getDelayedTimestamp(),
                                    EventDestination.EXCHANGE,
                                    OrderAction.CREATE_ORDER,
                                    closeOrder.clone()
                            ));

                            position.setCloseOrder(closeOrder);
                        }
                    }
                }
            }
        }
        if (!inactivePositions.isEmpty() && !printedInactivePositions) {
            for (Position position : inactivePositions) {
                if (position.getGroup().equals(PositionGroup.CLOSED)) {
                    System.out.printf("Position TP'd successfully. Profit is $%.2f\n", position.getProfit());
                    printedInactivePositions = true;
                }
            }
        }
    } // Limit buy and TP

    private void test2(){
        if (activePositions.isEmpty()) {
            Position newMarketPosition = orderRequest.newLimitPosition(transaction.price() - 100, transaction.price() - 110, transaction);

            if (newMarketPosition != null) {
                scheduler.addEvent(new Event(
                        currentEvent.getDelayedTimestamp(),
                        EventDestination.EXCHANGE,
                        OrderAction.CREATE_ORDER,
                        newMarketPosition.getEntryOrder().clone()
                ));
            }
        } else {
            for (Position position : activePositions) {
                if (position.getGroup().equals(PositionGroup.NEW)) {
                    if (!position.isStopLossRequestSent()) {
                        scheduler.addEvent(new Event(
                                currentEvent.getDelayedTimestamp(),
                                EventDestination.EXCHANGE,
                                OrderAction.CREATE_ORDER,
                                position.getStopOrder().clone()
                        ));
                        position.setStopLossRequestSent(true);
                    } else if (!closeRequestSent && position.isActiveStopLoss()) { //Must be a better way
                        scheduler.addEvent(new Event(
                                currentEvent.getDelayedTimestamp(),
                                EventDestination.EXCHANGE,
                                OrderAction.CANCEL_ORDER,
                                position.getEntryOrder().clone()
                        ));
                        closeRequestSent = true;
                    }
                }
            }
        }
        if (!inactivePositions.isEmpty() && !printedInactivePositions) {
            for (Position position : inactivePositions) {
                if (position.getGroup().equals(PositionGroup.CANCELLED)) {
                    System.out.println("Position cancelled successfully.");

                    printedInactivePositions = true;
                }
            }
        }
    } // Limit order cancelling

    private void test1() {
        if (activePositions.isEmpty() && inactivePositions.isEmpty()) {
            Position newMarketPosition = orderRequest.newMarketPosition(transaction, transaction.price() + 100);

            if (newMarketPosition != null) {
                scheduler.addEvent(new Event(
                        currentEvent.getDelayedTimestamp(),
                        EventDestination.EXCHANGE,
                        OrderAction.CREATE_ORDER,
                        newMarketPosition.getEntryOrder().clone()
                ));
            }
        } else {
            for (Position position : activePositions) {
                if (position.getGroup().equals(PositionGroup.FILLED)) {
                    if (!position.isStopLossRequestSent()) {
                        scheduler.addEvent(new Event(
                                currentEvent.getDelayedTimestamp(),
                                EventDestination.EXCHANGE,
                                OrderAction.CREATE_ORDER,
                                position.getStopOrder().clone()
                        ));
                        position.setStopLossRequestSent(true);
                    } else if (position.isActiveStopLoss()) { //Must be a better way
                        if (position.getCloseOrder() == null && transaction.price() < position.getEntryOrder().getFillPrice() - 100) {

                            Order closeOrder = position.createCloseOrder(transaction);
                            position.setCloseOrder(closeOrder);

                            scheduler.addEvent(new Event(
                                    currentEvent.getDelayedTimestamp(),
                                    EventDestination.EXCHANGE,
                                    OrderAction.CREATE_ORDER,
                                    closeOrder.clone()
                            ));
                        }
                    }
                }
            }
        }
        if (!inactivePositions.isEmpty() && !printedInactivePositions) {
            for (Position position : inactivePositions) {
                if (position.getGroup().equals(PositionGroup.CLOSED)) {
                    if (position.getProfit() > 0) {
                        System.out.printf("Ayy caramba we got some $%.2f\n", position.getProfit());
                    } else {
                        System.out.printf("My lord, we lost some $%.2f\n", position.getProfit());
                    }

                    printedInactivePositions = true;
                }
            }
        }
    } // 1RR market order




    public void onEvent(Event event) {
        this.currentEvent = event;

        switch (event.getType()) {
            case TRANSACTION -> {
                this.transaction = event.getTransaction();
                onTransaction(transaction);
            }
            case USER_DATA_STREAM -> { // DONE
                UserDataStream userDataStream = event.getUserDataStream();

                //Update local user assets to the latest snapshot
                userAssets = new UserAssets(userDataStream.userAssets());

                if (userAssets.getMarginLevel() <= 1.05) {
                    System.out.println("LocalHandler Error - Liquidation. Should have been called in exchange first.");
                } else if (userAssets.getMarginLevel() <= 1.1) { //TODO: Handle margin level report - close positions and repay funds
                    //Margin call
                    System.out.println("LocalHandler Margin call");
                }

                for (Order order : userDataStream.updatedOrders()) {
                    switch (order.getStatus()) {
                        case REJECTED -> { //DONE
                            switch (order.getRejectionReason()) {
                                case INSUFFICIENT_FUNDS, INSUFFICIENT_MARGIN -> {
                                    System.out.println("LocalHandler Error - Market position rejected for insufficient margin or insufficient funds - should have been checked during order creation?");
                                    boolean issue = true;
                                    for (Position position : activePositions) {
                                        switch (position.getGroup()) {
                                            case PENDING:
                                            case NEW:
                                            case FILLED:
                                                if (position.getEntryOrder().getId() == order.getId()) {
                                                    position.setEntryOrder(order.clone());
                                                    issue = false;
                                                } else if (position.getStopOrder().getId() == order.getId()) { // Might focus on this as it's handling stoplosses
                                                    position.setStopOrder(order.clone());
                                                    issue = false;
                                                } else if (position.getCloseOrder().getId() == order.getId()) {
                                                    position.setCloseOrder(order.clone());
                                                    issue = false;
                                                }
                                                break;
                                            default:
                                                break;
                                        }
                                    }
                                    if (issue) {
                                        System.out.println("Local Error - it's not even in active positions.");
                                    }
                                }
                                case EXCESS_BORROW, INVALID_ORDER_STATE, WOULD_TRIGGER_IMMEDIATELY, MAX_NUM_ALGO_ORDERS -> {
                                    System.out.println("Local Error - Order rejected due to Excess borrow, invalid order state, would trigger immediately, or max num algo orders.");
                                }
                            }
                        } // DONE

                        case FILLED -> { // DONE
                            ArrayList<Position> tempPositions = new ArrayList<>();
                            for (Position position : activePositions) {
                                switch (position.getGroup()) {
                                    case PENDING, NEW, FILLED -> {
                                        if (position.getEntryOrder().getId() == order.getId()) {
                                            if (order.getType().equals(OrderType.MARKET)) {
                                                position.setMarginBuyBorrowAmount(order.getMarginBuyBorrowAmount());
                                            }
                                            if (position.getGroup().equals(PositionGroup.FILLED)) {
                                                System.out.println("Local Error - double filled the position entry order.");
                                            }
                                            position.setEntryOrder(order.clone());
                                            position.setGroup(PositionGroup.FILLED);

                                        } else if (position.getStopOrder().getId() == order.getId()) {

                                            position.setStopOrder(order.clone());

                                            if (position.getEntryOrder().getStatus().equals(OrderStatus.NEW)) {
                                                //TODO: Stoplossed before entry trigger - shouldn't be possible since funds weren't borrowed. This fucked our asset management.
                                                System.out.println("Local Error - stoploss filled before entry order.");
                                            }

                                            if (position.getMarginBuyBorrowAmount() != 0.0) {
                                                scheduler.addEvent(new Event(
                                                        currentEvent.getDelayedTimestamp(),
                                                        EventDestination.EXCHANGE,
                                                        OrderAction.REPAY_FUNDS,
                                                        position.getEntryOrder().clone()));
                                            } else {
                                                position.closePosition(currentEvent.getDelayedTimestamp());

                                                if (position.getGroup().equals(PositionGroup.FILLED)) {
                                                    position.setGroup(PositionGroup.CLOSED);
                                                } else {
                                                    position.setGroup(PositionGroup.DISCARDED);
                                                }

                                                tempPositions.add(position);
                                            }

                                        } else if (position.getCloseOrder() != null && position.getCloseOrder().getId() == order.getId()) {

                                            position.setCloseOrder(order.clone());

                                            if (position.getMarginBuyBorrowAmount() != 0.0) {
                                                scheduler.addEvent(new Event(
                                                        currentEvent.getDelayedTimestamp(),
                                                        EventDestination.EXCHANGE,
                                                        OrderAction.REPAY_FUNDS,
                                                        position.getEntryOrder().clone()));
                                            } else {
                                                position.closePosition(currentEvent.getDelayedTimestamp());

                                                if (position.getGroup().equals(PositionGroup.FILLED)) {
                                                    position.setGroup(PositionGroup.CLOSED);
                                                } else {
                                                    position.setGroup(PositionGroup.DISCARDED);
                                                }
                                            }

                                            if (position.getStopOrder().getStatus().equals(OrderStatus.NEW)) { //If stoploss is still active cancel it
                                                scheduler.addEvent(new Event(
                                                        currentEvent.getDelayedTimestamp(),
                                                        EventDestination.EXCHANGE,
                                                        OrderAction.CANCEL_ORDER,
                                                        position.getStopOrder().clone()
                                                ));
                                            } else {
                                                tempPositions.add(position); //Add to inactive positions
                                            }
                                        }
                                    }
                                }
                            }
                            inactivePositions.addAll(tempPositions);
                            activePositions.removeAll(tempPositions);
                        } // DONE

                        case NEW -> {
                        } // Handled in action response

                        case CANCELED -> {
                            System.out.println("breakpoint");
                        }
                    }
                }
            } // DONE
            case ACTION_RESPONSE -> {
                Map<ActionResponse, Order> response = event.getActionResponse();

                ActionResponse actionResponse = response.keySet().iterator().next();
                Order order = response.values().iterator().next();

                switch (actionResponse) {
                    case ORDER_REJECTED -> {
                        switch (order.getRejectionReason()) {
                            case WOULD_TRIGGER_IMMEDIATELY -> {
                                for (Position position : activePositions) {
                                    switch (position.getGroup()) {
                                        case PENDING, NEW -> {
                                            if (position.getEntryOrder().getId() == order.getId()) {
                                                position.setEntryOrder(order.clone());

                                                //TODO: Discard position, cancel stoploss if it's already set
                                                if (position.isStopLossRequestSent()) {
                                                    scheduler.addEvent(new Event(currentEvent.getDelayedTimestamp(),
                                                            EventDestination.EXCHANGE,
                                                            OrderAction.CANCEL_ORDER,
                                                            position.getStopOrder().clone()));
                                                } else {
                                                    //Discard
                                                    position.setGroup(PositionGroup.DISCARDED);
                                                }

                                            } else if (position.getStopOrder().getId() == order.getId()) {

                                                System.out.println("Local Error - pending or new position would trigger immediately.");

                                                position.setStopOrder(order.clone());
                                            }
                                        }
                                        case FILLED -> {
                                            if (position.getEntryOrder().getId() == order.getId()) {

                                                System.out.println("Local Error - rejected already filled position entry order?");
                                                position.setEntryOrder(order.clone());

                                            } else if (position.getStopOrder().getId() == order.getId()) {

                                                order.setType(OrderType.MARKET);
                                                order.setOpenPrice(transaction.price());
                                                order.setOpenTimestamp(currentEvent.getTimestamp());
                                                order.setStatus(OrderStatus.NEW);

                                                scheduler.addEvent(new Event(
                                                        currentEvent.getDelayedTimestamp(),
                                                        EventDestination.EXCHANGE,
                                                        OrderAction.CREATE_ORDER,
                                                        order.clone()));

                                                position.setStopOrder(order.clone());

                                                position.setClosedBeforeStopLoss(true);
                                            } else if (position.getCloseOrder() != null && position.getCloseOrder().getId() == order.getId()) {
                                                //For programmatic take profits, not usable for now
                                                position.setCloseOrder(order.clone());
                                            }
                                        }
                                    }
                                }
                            }
                            case INSUFFICIENT_MARGIN -> //Insufficient margin during borrowing - discard the position
                                    System.out.println("Local Error - insufficient margin action rejection - should be tested during production.");
                            case INSUFFICIENT_FUNDS ->
                                    System.out.println("Local Error - insufficient funds action rejection - pls fix");
                            case EXCESS_BORROW ->
                                    System.out.println("Local Error - excess borrow action rejection - should have been tested during order creation.");
                            case MAX_NUM_ALGO_ORDERS ->
                                    System.out.println("Local Error - maximum number of algo orders action rejection - should have been tested during order creation.");
                            case INVALID_ORDER_STATE ->
                                    System.out.println("Local Error - invalid order state action rejection - repaying for 0 borrow? Impossible.");
                        }
                    } // "Done"
                    case ACTION_REJECTED -> { // Cancel order or repay funds - invalid order state (non-existent order), or general repay rejection reasons
                        switch (order.getRejectionReason()) {
                            case INSUFFICIENT_FUNDS -> //Not enough funds to repay a loan - theoretically shouldn't happen since we'd hit a margin call
                                    System.out.println("Local Error - Action rejected - insufficient funds to repay a loan - shouldn't we have received a margin call?");
                            case INVALID_ORDER_STATE -> // I implemented this as a failsafe when order.borrowAmount == 0. That shouldn't happen if we exclusively use automatic borrows.
                                    System.out.println("Local Error - Action rejected - invalid order state - borrow amount = 0?");
                        }
                    } // DONE
                    case FUNDS_REPAID -> {
                        ArrayList<Position> tempPositions = new ArrayList<>();
                        for (Position position : activePositions) {
                            switch (position.getGroup()) {
                                case NEW, FILLED -> {
                                    if (position.getEntryOrder().getId() == order.getId()) {
                                        if (position.getCloseOrder() != null) {
                                            // We closed the order manually
                                        } else {
                                            // We got stoplossed
                                        }

                                        position.setEntryOrder(order.clone());

                                        position.closePosition(currentEvent.getDelayedTimestamp());

                                        if (position.getGroup().equals(PositionGroup.FILLED)) {
                                            position.setGroup(PositionGroup.CLOSED);
                                        } else {
                                            position.setGroup(PositionGroup.DISCARDED);
                                        }

                                        tempPositions.add(position);
                                    }
                                }
                            }
                        }
                        inactivePositions.addAll(tempPositions);
                        activePositions.removeAll(tempPositions);
                    } // DONE
                    case ORDER_CANCELLED -> { //DONE?

                        if (order.getType().equals(OrderType.MARKET)) {
                            System.out.println("Local Error - Cancelling a market order.");
                        }

                        ArrayList<Position> tempPositions = new ArrayList<>();
                        for (Position position : activePositions) {
                            switch (position.getGroup()) {
                                case PENDING, NEW -> {
                                    if (position.getEntryOrder().getId() == order.getId()) {

                                        //Repay funds if stop order is cancelled - IF it's not automatic repay at cancel.
                                        if (position.getStopOrder().getStatus().equals(OrderStatus.CANCELED)) {
                                            position.setGroup(PositionGroup.CANCELLED);
                                            tempPositions.add(position);
                                        } else {
                                            //Send request for stoploss order cancellation? Or will that be done manually for now let's do it automatic.
                                            scheduler.addEvent(new Event(
                                                    currentEvent.getDelayedTimestamp(),
                                                    EventDestination.EXCHANGE,
                                                    OrderAction.CANCEL_ORDER,
                                                    position.getStopOrder().clone()));
                                        }

                                        position.setEntryOrder(order.clone());

                                    } else if (position.getStopOrder().getId() == order.getId()) {

                                        //Repay funds if entry order is cancelled - IF it's not automatic repay at cancel
                                        if (position.getEntryOrder().getStatus().equals(OrderStatus.CANCELED)) {
                                            position.setGroup(PositionGroup.CANCELLED);
                                            tempPositions.add(position);

                                        } else if (position.getEntryOrder().getStatus().equals(OrderStatus.NEW) && position.getEntryOrder().getRejectionReason() != null) { //If the entry order got rejected?
                                            //Funds weren't borrowed, discard the position
                                            position.setGroup(PositionGroup.DISCARDED);
                                            tempPositions.add(position);
                                        }

                                        position.setStopOrder(order.clone());
                                    }
                                }
                                case FILLED -> {
                                    if (position.getEntryOrder().getId() == order.getId()) {
                                        System.out.println("Local Error - cancelling filled position's entry order");
                                        position.setEntryOrder(order.clone());
                                    } else if (position.getStopOrder().getId() == order.getId()) {
                                        //Most likely a breakeven / move stoploss request
                                        position.setStopOrder(order.clone());
                                    }
                                }
                                case CLOSED -> { //Cancelling stoploss order after closing the order
                                    if (position.getStopOrder().getId() == order.getId()) {
                                        position.setStopOrder(order.clone());
                                        tempPositions.add(position);
                                    }
                                }
                            }
                        }
                        activePositions.removeAll(tempPositions);
                        inactivePositions.addAll(tempPositions);
                    } // DONE?
                    case ORDER_CREATED -> { // DONE
                        for (Position position : activePositions) {
                            switch (position.getGroup()) {
                                case PENDING -> {
                                    if (position.getEntryOrder().getId() == order.getId()) {
                                        position.setEntryOrder(order.clone());
                                        if (order.getType().equals(OrderType.LIMIT)) {
                                            position.setMarginBuyBorrowAmount(order.getMarginBuyBorrowAmount());
                                        }
                                        position.setGroup(PositionGroup.NEW);
                                    } else if (position.getStopOrder().getId() == order.getId()) {
                                        position.setEntryOrder(order.clone());
                                        System.out.println("Local Error - created a stop order for a pending position before it became new");
                                    }
                                }
                                case NEW -> {
                                    if (position.getEntryOrder().getId() == order.getId()) {
                                        position.setEntryOrder(order.clone());
                                        System.out.println("Local Error - created a new entry order for a position of status new?");
                                    } else if (position.getStopOrder().getId() == order.getId()) {
                                        position.setStopOrder(order.clone());
                                        position.setActiveStopLoss(true);
                                    }
                                }
                                case FILLED -> {
                                    if (position.getStopOrder().getId() == order.getId()) {
                                        position.setStopOrder(order.clone());
                                        position.setActiveStopLoss(true);
                                    }
                                }
                            }
                        }
                    } // DONE
                }
            }
            default -> System.out.println("Unexplained event found its way into our local strategy function.");
        }
    }

    //TODO: implement walls
    private void onTransaction(SingleTransaction transaction) {
        Candle candle = candleConstructor.processTradeEvent(transaction);

        if (candle != null) { // New candle
            lastCandle = candle;
            candles = candleConstructor.getCandles();
            newCandle(lastCandle);
        }

        priceUpdate(transaction);
    }
}
