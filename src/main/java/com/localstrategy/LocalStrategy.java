package com.localstrategy;

import com.localstrategy.util.enums.*;
import com.localstrategy.util.helper.CandleConstructor;
import com.localstrategy.util.types.Candle;
import com.localstrategy.util.types.Event;
import com.localstrategy.util.types.SingleTransaction;
import com.localstrategy.util.types.UserDataStream;

import java.util.ArrayList;
import java.util.Map;

public class LocalStrategy {

    private static final int CANDLE_VOLUME = 2_000_000;
    private static final double RISK_PCT = 0.1;
    private static final int SLIPPAGE_PCT = 15; //In relation to the difference between our entry and stop-loss price difference, how much in percentage of slippage are we ready to accept (total, not the average fill price)

    private final EventScheduler scheduler;
    private final CandleConstructor candleConstructor = new CandleConstructor(CANDLE_VOLUME);

    private final ArrayList<Position> activePositions = new ArrayList<>();
    private final ArrayList<Position> inactivePositions = new ArrayList<>();
    private final ArrayList<Position> tempPositions = new ArrayList<>();

    private final ArrayList<Position> pendingPositions = new ArrayList<>();
    private final ArrayList<Position> newPositions = new ArrayList<>();
    private final ArrayList<Position> filledPositions = new ArrayList<>();
    private final ArrayList<Position> cancelledPositions = new ArrayList<>();
    private final ArrayList<Position> closedPositions = new ArrayList<>();


    private final ArrayList<Position> rejectedOrders = new ArrayList<>();

    private final ArrayList<Map<RejectionReason, Position>> rejectedActions = new ArrayList<>();

    private UserAssets userAssets = new UserAssets();
    private final OrderRequest orderRequest;
    private Candle lastCandle;

    private Event currentEvent;
    private SingleTransaction transaction;

    public LocalStrategy(double initialFreeUSDT, EventScheduler scheduler) {

        this.scheduler = scheduler;

        userAssets.setFreeUSDT(initialFreeUSDT);

        orderRequest = new OrderRequest(
                pendingPositions,
                newPositions,
                filledPositions,
                new TierManager(),
                userAssets,
                RISK_PCT,
                Binance.ALGO_ORDER_LIMIT,
                SLIPPAGE_PCT);

        //TODO: Print parameters on strategy start
    }


    private void priceUpdate(SingleTransaction transaction) {
        if(pendingPositions.isEmpty() && newPositions.isEmpty() && filledPositions.isEmpty()){
            //Open position
            Position newPosition = orderRequest.newMarketPosition(transaction, transaction.price() - 50);
            scheduler.addEvent(new Event(currentEvent.getDelayedTimestamp(), EventDestination.EXCHANGE, OrderAction.CREATE_ORDER, newPosition.getEntryOrder()));
            scheduler.addEvent(new Event(currentEvent.getDelayedTimestamp(), EventDestination.EXCHANGE, OrderAction.CREATE_ORDER, newPosition.getStopOrder()));
        } else {
            if(closedPositions.isEmpty()){
                if(!filledPositions.isEmpty()){
                    if(transaction.price() > filledPositions.get(0).getOpenPrice() + 100){
                        if(filledPositions.get(0).getCloseOrder() == null){
                            Order closeOrder = new Order(filledPositions.get(0).getEntryOrder());

                            closeOrder.setStatus(OrderStatus.NEW);
                            closeOrder.setDirection(closeOrder.getDirection().equals(OrderSide.BUY) ? OrderSide.SELL : OrderSide.BUY);
                            closeOrder.setType(OrderType.MARKET);

                            filledPositions.get(0).setCloseOrder(closeOrder);

                            scheduler.addEvent(new Event(currentEvent.getDelayedTimestamp(), EventDestination.EXCHANGE, OrderAction.CREATE_ORDER, closeOrder));
                        }
                    }
                }
            } else {
                if(closedPositions.get(0).getStopOrder().getStatus().equals(OrderStatus.FILLED)){ //Stoplossed
                    System.out.println("Stoplossed");
                } else { //Closed
                    System.out.println("closed");
                }
            }
        }
    }

    private void newCandle(SingleTransaction transaction, ArrayList<Candle> candles) {

    }


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
                    System.out.println("LocalStrategy Error - Liquidation. Should have been called in exchange first.");
                } else if (userAssets.getMarginLevel() <= 1.1) { //TODO: Handle margin level report - close positions and repay funds
                    //Margin call
                    System.out.println("LocalStrategy Margin call");
                }

                for (Order order : userDataStream.updatedOrders()) {
                    switch (order.getStatus()) {
                        case REJECTED -> { //DONE
                            switch (order.getRejectionReason()) {
                                case INSUFFICIENT_FUNDS, INSUFFICIENT_MARGIN -> {
                                    System.out.println("LocalStrategy Error - Market position rejected for insufficient margin or insufficient funds - should have been checked during order creation?");
                                    boolean issue = true;
                                    for (Position position : activePositions) {
                                        switch (position.getGroup()) {
                                            case PENDING:
                                            case NEW:
                                            case FILLED:
                                                if (position.getEntryOrder().getId() == order.getId()) {
                                                    position.setEntryOrder(new Order(order));
                                                    issue = false;
                                                } else if (position.getStopOrder().getId() == order.getId()) { // Might focus on this as it's handling stoplosses
                                                    position.setStopOrder(new Order(order));
                                                    issue = false;
                                                } else if (position.getCloseOrder().getId() == order.getId()) {
                                                    position.setCloseOrder(new Order(order));
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
                            for(Position position : activePositions){
                                switch (position.getGroup()) {
                                    case PENDING, NEW, FILLED -> {
                                        if (position.getEntryOrder().getId() == order.getId()) {
                                            if(position.getGroup().equals(PositionGroup.FILLED)){
                                                System.out.println("Local Error - double filled the position entry order.");
                                            }
                                            position.setEntryOrder(new Order(order));
                                            position.setGroup(PositionGroup.FILLED);

                                        } else if (position.getStopOrder().getId() == order.getId()) {

                                            if(position.getEntryOrder().getStatus().equals(OrderStatus.NEW)){
                                                //TODO: Stoplossed before entry trigger - shouldn't be possible since funds weren't borrowed. This fucked our asset management.
                                                System.out.println("Local Error - stoploss filled before entry order.");
                                            }

                                            scheduler.addEvent(new Event(
                                                    currentEvent.getDelayedTimestamp(),
                                                    EventDestination.EXCHANGE,
                                                    OrderAction.REPAY_FUNDS,
                                                    new Order(position.getEntryOrder())));

                                            position.setStopOrder(new Order(order));

                                        } else if (position.getCloseOrder() != null && position.getCloseOrder().getId() == order.getId()) {

                                            scheduler.addEvent(new Event(
                                                    currentEvent.getDelayedTimestamp(),
                                                    EventDestination.EXCHANGE,
                                                    OrderAction.REPAY_FUNDS,
                                                    new Order(position.getEntryOrder())));

                                            position.setCloseOrder(new Order(order));
                                        }
                                    }
                                }
                            }
                        } // DONE

                        case NEW -> {
                        } // Handled in action response
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
                                for(Position position : activePositions){
                                    switch (position.getGroup()){
                                        case PENDING, NEW -> {
                                            if(position.getEntryOrder().getId() == order.getId()){
                                                position.setEntryOrder(new Order(order));

                                                //TODO: Discard position, cancel stoploss if it's already set
                                                if(position.isStopLossRequestSent()){
                                                    scheduler.addEvent(new Event(currentEvent.getDelayedTimestamp(),
                                                            EventDestination.EXCHANGE,
                                                            OrderAction.CANCEL_ORDER,
                                                            new Order(position.getStopOrder())));
                                                } else {
                                                    //Discard
                                                    position.setGroup(PositionGroup.DISCARDED);
                                                }

                                            } else if(position.getStopOrder().getId() == order.getId()){

                                                System.out.println("Local Error - pending or new position would trigger immediately.");

                                                position.setStopOrder(new Order(order));
                                            }
                                        }
                                        case FILLED -> {
                                            if(position.getEntryOrder().getId() == order.getId()){

                                                System.out.println("Local Error - rejected already filled position entry order?");
                                                position.setEntryOrder(new Order(order));

                                            } else if (position.getStopOrder().getId() == order.getId()) {

                                                order.setType(OrderType.MARKET);
                                                order.setOpenPrice(transaction.price());
                                                order.setOpenTimestamp(currentEvent.getTimestamp());
                                                order.setStatus(OrderStatus.NEW);

                                                scheduler.addEvent(new Event(
                                                        currentEvent.getDelayedTimestamp(),
                                                        EventDestination.EXCHANGE,
                                                        OrderAction.CREATE_ORDER,
                                                        new Order(order)));

                                                position.setStopOrder(new Order(order));

                                                position.setClosedBeforeStopLoss(true);
                                            } else if(position.getCloseOrder() != null && position.getCloseOrder().getId() == order.getId()){
                                                //For programmatic take profits, not usable for now
                                                position.setCloseOrder(new Order(order));
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
                    case FUNDS_REPAID -> { // DONE
                        for(Position position : activePositions){
                            switch (position.getGroup()){
                                case FILLED -> {
                                    if(position.getEntryOrder().getId() == order.getId()){

                                        if(position.getCloseOrder() != null){
                                            // We closed the order manually
                                        } else {
                                            // We got stoplossed
                                        }

                                        position.setEntryOrder(new Order(order));

                                        position.closePosition(currentEvent.getDelayedTimestamp());
                                        position.setGroup(PositionGroup.CLOSED);
                                    }
                                }
                            }
                        }

                        Position newClosedPosition = null;
                        Position newCancelledPosition = null;
                        //Can be a filled order (closing the position) or a new order (cancelling a position)
                        for (Position position : filledPositions) {
                            if (position.getEntryOrder().getId() == order.getId()) {
                                position.setEntryOrder(order);

                                if (position.getStopOrder().getStatus().equals(OrderStatus.FILLED)) {
                                    //Repaid after stoploss
                                }

                                position.closePosition(currentEvent.getDelayedTimestamp()); //TODO: Focus here
                                newClosedPosition = position;

                                break;
                            }
                        }
                        if (newClosedPosition == null) {
                            for (Position position : newPositions) {
                                if (position.getEntryOrder().getId() == order.getId()) {
                                    position.setEntryOrder(order);
                                    newCancelledPosition = position;

                                    break;
                                }
                            }
                            if (newCancelledPosition != null) {
                                Position finalNewCancelledPosition = newCancelledPosition;
                                cancelledPositions.add(finalNewCancelledPosition);
                                newPositions.removeIf(position -> position.getId() == finalNewCancelledPosition.getId());
                            } else {
                                System.out.println("Local Error - repaid order isn't in filled positions nor new positions?");
                            }
                        } else {
                            Position finalNewClosedPosition = newClosedPosition;
                            closedPositions.add(new Position(finalNewClosedPosition));
                            filledPositions.removeIf(position -> position.getId() == finalNewClosedPosition.getId());
                        }
                    } // DONE
                    case ORDER_CANCELLED -> { //DONE?

                        if (order.getType().equals(OrderType.MARKET)) {
                            System.out.println("Local Error - Cancelling a market order.");
                        }

                        for(Position position : activePositions){
                            switch (position.getGroup()){
                                case PENDING, NEW -> {
                                    if(position.getEntryOrder().getId() == order.getId()){

                                        //Repay funds if stop order is cancelled - IF it's not automatic repay at cancel.
                                        if(position.getStopOrder().getStatus().equals(OrderStatus.CANCELED)){

                                            position.setGroup(PositionGroup.CANCELLED);
                                        }

                                        position.setEntryOrder(new Order(order));

                                    } else if(position.getStopOrder().getId() == order.getId()){

                                        //Repay funds if entry order is cancelled - IF it's not automatic repay at cancel
                                        if(position.getEntryOrder().getStatus().equals(OrderStatus.CANCELED)){
                                            position.setGroup(PositionGroup.CANCELLED);

                                        } else if(position.getEntryOrder().getStatus().equals(OrderStatus.NEW) && position.getEntryOrder().getRejectionReason() != null){ //If the entry order got rejected?
                                            //Funds weren't borrowed, discard the position
                                            position.setGroup(PositionGroup.DISCARDED);
                                        }

                                        position.setStopOrder(new Order(order));
                                    }
                                }
                                case FILLED -> {
                                    if(position.getEntryOrder().getId() == order.getId()){
                                        System.out.println("Local Error - cancelling filled position's entry order");
                                        position.setEntryOrder(new Order(order));
                                    } else if(position.getStopOrder().getId() == order.getId()){
                                        //Most likely a breakeven / move stoploss request
                                        position.setStopOrder(new Order(order));
                                    }
                                }
                            }
                        }
                    } // DONE?
                    case ORDER_CREATED -> { // DONE
                        for(Position position : activePositions){
                            switch (position.getGroup()){
                                case PENDING -> {
                                    if(position.getEntryOrder().getId() == order.getId()){
                                        position.setEntryOrder(new Order(order));
                                        position.setGroup(PositionGroup.NEW);
                                    } else if(position.getStopOrder().getId() == order.getId()){
                                        position.setEntryOrder(new Order(order));
                                        System.out.println("Local Error - created a stop order for a pending position before it became new");
                                    }
                                }
                                case NEW -> {
                                    if(position.getEntryOrder().getId() == order.getId()){
                                        position.setEntryOrder(new Order(order));
                                        System.out.println("Local Error - created a new entry order for a position of status new?");
                                    } else if(position.getStopOrder().getId() == order.getId()){
                                        position.setStopOrder(new Order(order));
                                        position.setActiveStopLoss(true);
                                    }
                                }
                                case FILLED -> {
                                    if(position.getStopOrder().getId() == order.getId()){
                                        position.setStopOrder(new Order(order));
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
    public void onTransaction(SingleTransaction transaction) {
        Candle candle = candleConstructor.processTradeEvent(transaction);

        if (candle != null) { // New candle
            lastCandle = candle;
            newCandle(transaction, candleConstructor.getCandles());
        }

        priceUpdate(transaction);
    }
}
