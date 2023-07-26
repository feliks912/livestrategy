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

    private CandleConstructor candleConstructor = new CandleConstructor(CANDLE_VOLUME);

    private ArrayList<Position> pendingPositions = new ArrayList<>();

    private ArrayList<Position> newPositions = new ArrayList<>();
    private ArrayList<Position> filledPositions = new ArrayList<>();
    private ArrayList<Position> cancelledPositions = new ArrayList<>();
    private ArrayList<Position> rejectedOrders = new ArrayList<>();

    private ArrayList<Map<RejectionReason, Position>> rejectedActions = new ArrayList<>();

    private ArrayList<Position> closedPositions = new ArrayList<>();

    private UserAssets userAssets = new UserAssets();
    private TierManager tierManager = new TierManager();
    private OrderRequest orderRequest;
    private Binance exchangeHandler;
    private Candle lastCandle;
    private LatencyHandler latencyHandler;

    private final EventScheduler scheduler;

    private Event currentEvent;
    private SingleTransaction transaction;

    public LocalStrategy(double initialFreeUSDT, EventScheduler scheduler){

        this.scheduler = scheduler;

        //TODO: Any call to exchangeHandler has a response latency. Local variables are instant.

        userAssets.setFreeUSDT(initialFreeUSDT);

        orderRequest =  new OrderRequest(
            pendingPositions,
            newPositions,
            tierManager,
            userAssets,
            RISK_PCT,
            Binance.ALGO_ORDER_LIMIT,
            SLIPPAGE_PCT);

        //TODO: Print parameters on strategy start
    }


    private void priceUpdate(SingleTransaction transaction){
    }

    private void newCandle(SingleTransaction transaction, ArrayList<Candle> candles){

    }


    public void onEvent(Event event){
        this.currentEvent = event;

        switch(event.getType()){
            case TRANSACTION -> {
                 this.transaction = event.getTransaction();
                 onTransaction(transaction);
            }
            case USER_DATA_STREAM -> {
                UserDataStream userDataStream = event.getUserDataStream();

                //Update local user assets to the latest snapshot
                userAssets = new UserAssets(userDataStream.userAssets());

                if(userAssets.getMarginLevel() < 1.1){ //TODO: Handle margin level report - close positions and repay funds

                }

                for(Order order : userDataStream.updatedOrders()){
                    switch(order.getStatus()) {
                        case REJECTED -> {
                            switch (order.getRejectionReason()) {
                                case INSUFFICIENT_MARGIN -> { // Can't borrow for a market order. Borrowings for a limit order are handled in the action response.
                                    // Free the margin on discard the position. Currently, we choose to discard the position, but this shouldn't happen as we check for available borrowings during order creation. Variable leverage isn't implemented yet though.
                                }
                                case INSUFFICIENT_FUNDS -> {
                                    //Not enough funds at the time of filling a limit position - shouldn't happen as funds should have already been borrowed. Implies incorrect funds handling by other positions.
                                }
                                case WOULD_TRIGGER_IMMEDIATELY -> {} // N/A - handled in action response
                                case EXCESS_BORROW -> {} // N/A - Excess borrow is sent only in the borrowFunds method, which is currently only implemented as an action response
                                case INVALID_ORDER_STATE -> {} // N/A - Immediately checked, therefore handled in action response
                                case MAX_NUM_ALGO_ORDERS -> {} // N/A - Immediately checked, handled in action response
                            }
                        }
                        case FILLED -> { // New filled order

                        }
                        case NEW -> {} // New order but not filled, could be market (will get filled if no errors arise on next transaction) or a limit order. Both are handled in the action response ActionResponse.ORDER_CREATED
                    }
                }
            }
            case ACTION_RESPONSE -> {
                Map<ActionResponse, Order> response = event.getActionResponse();

                ActionResponse actionResponse = response.keySet().iterator().next();
                Order order = response.values().iterator().next();

                switch(actionResponse){
                    case ORDER_REJECTED -> {
                        switch(order.getRejectionReason()){
                            case WOULD_TRIGGER_IMMEDIATELY -> {

                                    //TODO: Special care for rejected limit stoploss orders
                                    //Find whether the order is a stoploss. Orders themselves don't hold that information
                                    //Check filled positions for this order

                                for(Position position : filledPositions){ //Because we send a stoploss order when the position is filled, and is transferred into the filled position group.
                                    if(position.getStopOrder().equals(order)){
                                        //It's a rejected stop order! Create a market exit order.
                                        //TODO: Then repay the funds on a response if we used automatic borrowings (so far we must always use it)

                                        //TODO: Check if this is correct
                                        Order immediateOrder = new Order(order);
                                        immediateOrder.setOrderType(OrderType.MARKET);
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
                                    }
                                }
                            }
                            //TODO: Add a bunch of stuff
                        }
                    }
                    case ACTION_REJECTED -> { // Cancel order or repay funds - invalid order state (non-existent order), or general repay rejection reasons
                        switch(order.getRejectionReason()){
                            case INSUFFICIENT_FUNDS -> { //Not enough funds to repay a loan - theoretically shouldn't happen since we'd hit a margin call
                                System.out.println("Local Error - Action rejected - insufficient funds to repay a loan - shouldn't we have received a margin call?");
                            }
                            case INVALID_ORDER_STATE -> { // I implemented this as a failsafe when order.borrowAmount == 0. That shouldn't happen if we exclusively use automatic borrows.
                                System.out.println("Local Error - Action rejected - invalid order state - borrow amount = 0?");
                            }
                        }
                    }
                    case FUNDS_REPAYED -> { //Please send only entry orders when sending repay requests :)
                        for(Position position : filledPositions){
                            if(position.getEntryOrder().equals(order)){
                                if(position.getStopOrder().getStatus().equals(OrderStatus.FILLED)){
                                    //Repay after stoploss
                                } else {
                                    // I'll assume I'll remember to send the entry order when sending requests
                                    //Set position as closed and move it to closed positions
                                    position.closePosition(currentEvent.getDelayedTimestamp());
                                    closedPositions.add(position);
                                }
                            }
                        }
                    }
                    case ORDER_CANCELLED -> {

                        if(order.getType().equals(OrderType.MARKET)){
                            System.out.println("Local Error - Cancelling a market order.");
                        }

                        boolean isInNewPositions = false;

                        for(Position position : newPositions){
                            //FIXME: There are bugs here regarding checking which order is closed first
                            if(position.getStopOrder().equals(order)){ // Cancelled stoploss - could be the whole position or just
                                isInNewPositions = true;

                                if(position.getEntryOrder().getStatus().equals(OrderStatus.NEW)){
                                    // We cancelled the whole position.
                                } else {
                                    position.setStopOrder(order);
                                }
                            } else if(position.getEntryOrder().equals(order)){ // We cancelled a complete limit order
                                isInNewPositions = true;
                                if(position.getStopOrder().getStatus().equals(OrderStatus.CANCELED)){
                                    //And the stop order is already cancelled
                                }
                                if(position.getEntryOrder().isAutoRepayAtCancel()){ // We must repay funds before setting the position as cancelled
                                    //Funds are already repaid.
                                } else {
                                    //We must manually repay before setting the position as cancelled but that shouldn't be for now.
                                    System.out.println("Local Error - cancelling a position without auto repay at cancel.");
                                }
                                position.setCancelled(true); //TODO: What does this imply? What about positions which are already cancelled?
                            } else if(position.getCloseOrder().equals(order)) { //FIXME: closeOrder is null at the beginning could be an issue
                                System.out.println("Local Error - cancelling an order which isn't entry nor stop.");
                            }
                        }

                        if(!isInNewPositions){
                            System.out.println("Local Error - cancelling an order which isn't in new positions");
                        }
                    }
                    case ORDER_CREATED -> {
                        Position tempPosition = null; //Something other than null pls?
                        for(Position position : pendingPositions){
                            if(position.getStopOrder().equals(order)){
                                if(order.getType().equals(OrderType.MARKET) && position.isClosedBeforeStopLoss()){
                                    //Closed market after unsuccessful stoploss creation, now repay the funds and close the position
                                    scheduler.addEvent(new Event(
                                            currentEvent.getDelayedTimestamp(),
                                            EventDestination.EXCHANGE,
                                            OrderAction.REPAY_FUNDS, position.getEntryOrder()));
                                }
                            }
                            if(position.getEntryOrder().equals(order)){
                                position.setEntryOrder(order);
                                tempPosition = position;

                                if(order.getType().equals(OrderType.MARKET)){
                                    if(position.isClosedBeforeStopLoss()){ //This was our market stoploss filling
                                        //TODO: Close the position

                                    } else {

                                    }
                                }
                            }
                        }
                        if(tempPosition != null){
                            pendingPositions.remove(tempPosition);
                            newPositions.add(tempPosition);
                        }
                    }
                }
            }
            default -> {
                System.out.println("Unexplained event found its way into our local strategy function.");
            }
        }
    }

    //TODO: implement walls
    public void onTransaction(SingleTransaction transaction){
        Candle candle = candleConstructor.processTradeEvent(transaction);

        priceUpdate(transaction);

        if(candle != null){ // New candle
            lastCandle = candle;
            newCandle(transaction, candleConstructor.getCandles());
        }
    }
}
