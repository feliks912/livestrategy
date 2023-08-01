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

    private final ArrayList<Position> pendingPositions = new ArrayList<>();
    private final ArrayList<Position> newPositions = new ArrayList<>();
    private final ArrayList<Position> filledPositions = new ArrayList<>();
    private final ArrayList<Position> cancelledPositions = new ArrayList<>();
    private final ArrayList<Position> closedPositions = new ArrayList<>();


    private final ArrayList<Position> rejectedOrders = new ArrayList<>();

    private final ArrayList<Map<RejectionReason, Position>> rejectedActions = new ArrayList<>();

    private UserAssets userAssets = new UserAssets();
    private OrderRequest orderRequest;
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
        for (Position position : filledPositions) {
            if (position.getEntryOrder().getStatus().equals(OrderStatus.FILLED)) {
                if (!position.isStopLossRequestSent()) {
                    scheduler.addEvent(new Event(
                            currentEvent.getDelayedTimestamp(),
                            EventDestination.EXCHANGE,
                            OrderAction.CREATE_ORDER,
                            position.getStopOrder()
                    ));
                    position.setStopLossRequestSent(true);
                }
            }
        }
    }

    private void newCandle(SingleTransaction transaction, ArrayList<Candle> candles) {

        if (candles.size() > 1) {
            Candle currentCandle = candles.get(candles.size() - 1);
            Candle previousCandle = candles.get(candles.size() - 2);

            if (currentCandle.open() != previousCandle.close()) {
                orderRequest.newMarketPosition(transaction, transaction.price() - 100);
                scheduler.addEvent(new Event(
                        currentEvent.getDelayedTimestamp(),
                        EventDestination.EXCHANGE,
                        OrderAction.CREATE_ORDER,
                        pendingPositions.get(pendingPositions.size() - 1).getEntryOrder()
                ));

                for (Position position : filledPositions) {
                    if (!position.isClosed()) {
                        Order closeOrder = new Order(position.getEntryOrder());

                        closeOrder.setDirection(closeOrder.getDirection() == OrderSide.BUY ? OrderSide.SELL : OrderSide.BUY);
                        closeOrder.setType(OrderType.MARKET);

                        position.setCloseOrder(closeOrder);

                        scheduler.addEvent(new Event(
                                currentEvent.getDelayedTimestamp(),
                                EventDestination.EXCHANGE,
                                OrderAction.CREATE_ORDER,
                                closeOrder
                        ));
                    }
                }
            }
        }
    }


    public void onEvent(Event event) {
        this.currentEvent = event;

        switch (event.getType()) {
            case TRANSACTION -> {
                this.transaction = event.getTransaction();
                onTransaction(transaction);
            }
            case USER_DATA_STREAM -> {
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
                        case REJECTED -> {
                            switch (order.getRejectionReason()) {
                                case INSUFFICIENT_MARGIN -> { // Couldn't borrow for a market order. Borrowings for a limit order are handled in the action response.
                                    // Free the margin on discard the position. Currently, we choose to discard the position, but this shouldn't happen as we check for available borrowings during order creation. Variable leverage isn't implemented yet.
                                    System.out.println("LocalStrategy Error - Market position rejected for insufficient margin - should have been checked during order creation?");

                                    boolean isInNewPositions = false;
                                    //This market order should be in new positions since it's put there by an action response.
                                    for (Position position : newPositions) { //Update order in respective position - would be cool if we could iterate.
                                        if (position.getEntryOrder().getId() == order.getId()) {
                                            position.setEntryOrder(new Order(order));
                                            isInNewPositions = true;
                                            break;
                                        } else if (position.getStopOrder().getId() == order.getId()) { // Might focus on this as it's handling stoplosses
                                            position.setStopOrder(new Order(order));
                                            isInNewPositions = true;
                                            break;
                                        } else if (position.getCloseOrder() != null && position.getCloseOrder().getId() == order.getId()) {
                                            position.setCloseOrder(new Order(order));
                                            isInNewPositions = true;
                                            break;
                                        }
                                    }

                                    if (!isInNewPositions) {
                                        System.out.println("Local Error - it's not even in new positions.");
                                    }
                                }
                                case INSUFFICIENT_FUNDS -> {
                                    //Not enough funds at the time of filling a limit position - shouldn't happen as funds should have already been borrowed. Implies incorrect funds handling by other positions.
                                    System.out.println("LocalStrategy Error - Insufficient free funds during filling of limit position. Since it's always borrowed up-front it should have been filled.");


                                    boolean isInNewPositions = false;
                                    //This limit order should be in new positions since it's put there by an action response.
                                    for (Position position : newPositions) { //Update order in respective position - would be cool if we could iterate.
                                        if (position.getEntryOrder().getId() == order.getId()) {
                                            position.setEntryOrder(new Order(order));
                                            isInNewPositions = true;
                                            break;
                                        } else if (position.getStopOrder().getId() == order.getId()) { // Might focus on this as it's handling stoplosses
                                            position.setStopOrder(new Order(order));
                                            isInNewPositions = true;
                                            break;
                                        } else if (position.getCloseOrder() != null && position.getCloseOrder().getId() == order.getId()) {
                                            position.setCloseOrder(new Order(order));
                                            isInNewPositions = true;
                                            break;
                                        }
                                    }

                                    if (!isInNewPositions) {
                                        System.out.println("Local Error - it's not even in new positions.");
                                    }
                                }
                                case WOULD_TRIGGER_IMMEDIATELY -> {
                                } // N/A - handled in action response
                                case EXCESS_BORROW -> {
                                } // N/A - Excess borrow is sent only in the borrowFunds method, which is currently only implemented as an action response
                                case INVALID_ORDER_STATE -> {
                                } // N/A - Immediately checked, therefore handled in action response
                                case MAX_NUM_ALGO_ORDERS -> {
                                } // N/A - Immediately checked, handled in action response
                            }
                        } // "Done"
                        case FILLED -> { // New filled order (market or limit)
                            Position tempPosition = null; //Something other than null pls?
                            for (Position position : pendingPositions) {
                                if (position.getEntryOrder().getId() == order.getId()) {
                                    position.setEntryOrder(new Order(order));
                                    tempPosition = position;

                                } else if (position.getStopOrder().getId() == order.getId()) {
                                    position.setStopOrder(new Order(order));

                                    //Shouldn't be here because the stop order shouldn't be set until the position is filled (for our specific algorithm)
                                    System.out.println("Local Error - Stop Order triggered for an unfilled position.");
                                } else if (position.getCloseOrder() != null && position.getCloseOrder().getId() == order.getId()) {
                                    //Shouldn't be here because the close order shouldn't be sent!
                                    System.out.println("Local Error - Sent close request for an order which wasn't filled!");
                                }
                            }
                            if (tempPosition == null) {
                                boolean isInFilledPositions = false;
                                for (Position position : filledPositions) {
                                    if (position.getEntryOrder().getId() == order.getId()) {
                                        isInFilledPositions = true;
                                    } else if (position.getStopOrder().getId() == order.getId()) {
                                        isInFilledPositions = true;
                                        position.setStopOrder(new Order(order));

                                        if (order.getType().equals(OrderType.MARKET) && position.isClosedBeforeStopLoss()) {
                                            //TODO: Closed market after unsuccessful stop-loss creation - repay the funds + more ?
                                            // The same amount of funds is repaid every time, but it costs us more than the original position value.
                                        }

                                        scheduler.addEvent(new Event(
                                                currentEvent.getDelayedTimestamp(),
                                                EventDestination.EXCHANGE,
                                                OrderAction.REPAY_FUNDS, position.getEntryOrder()));

                                        break;
                                    } else if (position.getCloseOrder() != null && position.getCloseOrder().getId() == order.getId()) {
                                        isInFilledPositions = true;
                                        position.setCloseOrder(new Order(order));
                                        // Position is closed - repay the funds and set it as closed with profit in funds repaid action response
                                        scheduler.addEvent(new Event(currentEvent.getDelayedTimestamp(), EventDestination.EXCHANGE, OrderAction.REPAY_FUNDS, position.getEntryOrder()));
                                        break;
                                    }
                                }
                                if (!isInFilledPositions) {
                                    System.out.println("Local Error - filled order not in new or filled positions?");
                                }
                            } else {
                                pendingPositions.remove(tempPosition);
                                newPositions.add(tempPosition);
                            }
                        }
                        case NEW -> {
                        } // Gets handled in action response
                    }
                }
            }
            case ACTION_RESPONSE -> {
                Map<ActionResponse, Order> response = event.getActionResponse();

                ActionResponse actionResponse = response.keySet().iterator().next();
                Order order = response.values().iterator().next();

                switch (actionResponse) {
                    case ORDER_REJECTED -> {
                        switch (order.getRejectionReason()) {
                            case WOULD_TRIGGER_IMMEDIATELY -> {

                                //TODO: Special care for rejected limit stop-loss orders
                                //Find whether the order is a stop-loss. Orders themselves don't hold that information
                                //Check filled positions for this order

                                for (Position position : filledPositions) { //Because we send a stop-loss order when the position is filled, and is transferred into the filled position group.
                                    if (position.getStopOrder().getId() == order.getId()) {
                                        //It's a rejected stop order! Create a market exit order.
                                        //TODO: Then repay the funds on a response if we used automatic borrowings (so far we must always use it)

                                        //TODO: Check if this is correct
                                        Order immediateOrder = new Order(order);
                                        immediateOrder.setType(OrderType.MARKET);
                                        immediateOrder.setOpenPrice(transaction.price());
                                        immediateOrder.setOpenTimestamp(currentEvent.getTimestamp());
                                        immediateOrder.setStatus(OrderStatus.NEW); //Bad practice to change values only exchange can change

                                        //Send an order request?
                                        scheduler.addEvent(new Event(
                                                currentEvent.getDelayedTimestamp(),
                                                EventDestination.EXCHANGE,
                                                OrderAction.CREATE_ORDER, immediateOrder));

                                        position.setStopOrder(immediateOrder);

                                        position.setClosedBeforeStopLoss(true);

                                        break;
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
                    } // "Done"
                    case FUNDS_REPAID -> { //Please send only entry orders when sending repay requests :)
                        Position newClosedPosition = null;
                        Position newCancelledPosition = null;
                        //Can be a filled order (closing the position) or a new order (cancelling a position)
                        for (Position position : filledPositions) {
                            if (position.getEntryOrder().getId() == order.getId()) {
                                position.setEntryOrder(new Order(order));

                                if (position.getStopOrder().getStatus().equals(OrderStatus.FILLED)) {
                                    //Repaid after stoploss
                                }

                                position.closePosition(currentEvent.getDelayedTimestamp()); //TODO: Focus here
                                newClosedPosition = position;
                            }
                        }
                        if (newClosedPosition == null) {
                            for (Position position : newPositions) {
                                if (position.getEntryOrder().getId() == order.getId()) {
                                    position.setEntryOrder(new Order(order));
                                    newCancelledPosition = position;
                                }
                            }
                            if (newCancelledPosition != null) {
                                cancelledPositions.add(newCancelledPosition);
                                newPositions.remove(newCancelledPosition);
                            } else {
                                System.out.println("Local Error - repaid order isn't in filled positions nor new positions?");
                            }
                        } else {
                            closedPositions.add(newClosedPosition);
                            filledPositions.remove(newClosedPosition);
                        }
                    } // "Done"
                    case ORDER_CANCELLED -> { //FIXME - could be in new or filled positions

                        if (order.getType().equals(OrderType.MARKET)) {
                            System.out.println("Local Error - Cancelling a market order.");
                        }

                        boolean isInNewPositions = false;
                        Position newCancelledPosition = null;

                        for (Position position : newPositions) {
                            //FIXME: There are bugs here regarding checking which order is closed first
                            if (position.getEntryOrder().getId() == order.getId()) { // We cancelled a complete limit order
                                position.setEntryOrder(new Order(order));
                                isInNewPositions = true;

                                if (position.getStopOrder().getStatus().equals(OrderStatus.CANCELED)) { //And the stop order is already cancelled
                                    position.setCancelled(true); //TODO: What does this imply? What about positions which are already cancelled?
                                    newCancelledPosition = position;
                                }
                                if (!position.getEntryOrder().isAutoRepayAtCancel()) { // We must repay funds before setting the position as cancelled
                                    //We must manually repay before setting the position as cancelled but that shouldn't be for now.
                                    System.out.println("Local Error - cancelling a position without auto repay at cancel.");
                                }

                            } else if (position.getStopOrder().getId() == order.getId()) { // Cancelled stoploss - could be the whole position or just the stoploss (for BE for example)
                                position.setStopOrder(new Order(order));
                                isInNewPositions = true;

                                if (position.getEntryOrder().getStatus().equals(OrderStatus.CANCELED)) { //And the entry order is already cancelled
                                    position.setCancelled(true); //TODO: What does this imply? What about positions which are already cancelled?
                                    newCancelledPosition = position;
                                }
                                if (position.getEntryOrder().getStatus().equals(OrderStatus.NEW)) {
                                    // We cancelled the whole position.
                                } else {
                                    //We cancelled only the stop position (entry is filled)
                                }
                            } else if (position.getCloseOrder() != null && position.getCloseOrder().getId() == order.getId()) { //FIXME: closeOrder is null at the beginning could be an issue
                                System.out.println("Local Error - cancelling an order which isn't entry nor stop.");
                            }
                        }
                        if (newCancelledPosition == null) {
                            boolean isInFilledPositions = false;
                            for (Position position : filledPositions) {
                                if (position.getStopOrder().getId() == order.getId()) { // We're moving the stoploss
                                    position.setStopOrder(new Order(order));
                                } //TODO: Add checks for entry and close orders here
                            }
                            if (!isInFilledPositions) {
                                System.out.println("Local Error - cancelled order isn't in new or filled positions");
                            }
                        } else {
                            newPositions.remove(newCancelledPosition);
                            cancelledPositions.add(newCancelledPosition);
                        }

                        if (!isInNewPositions) {
                            System.out.println("Local Error - cancelling an order which isn't in new positions");
                        }
                    }
                    case ORDER_CREATED -> {
                        //Means no errors in order creation. Filled orders get updated in UserStream response
                        Position newPosition = null;
                        for (Position position : pendingPositions) { //DISGUSTIN
                            if (position.getEntryOrder().getId() == order.getId()) {
                                position.setEntryOrder(new Order(order));
                                newPosition = new Position(position);
                                break;
                            } else if (position.getStopOrder().getId() == order.getId()) { // Might focus on this as it's handling stoplosses
                                position.setStopOrder(new Order(order));
                                newPosition = new Position(position);
                                break;
                            } else if (position.getCloseOrder() != null && position.getCloseOrder().getId() == order.getId()) {
                                position.setCloseOrder(new Order(order));
                                newPosition = new Position(position);
                                break;
                            }
                        }
                        if (newPosition != null) {
                            pendingPositions.remove(newPosition);
                            newPositions.add(newPosition);
                        } else {
                            System.out.println("Local Error - created order isn't in any pending position?");
                        }
                    }
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
