package com.localstrategy;

import com.localstrategy.util.enums.*;
import com.localstrategy.util.helper.CandleConstructor;
import com.localstrategy.util.helper.EventScheduler;
import com.localstrategy.util.helper.OrderRequest;
import com.localstrategy.util.helper.TierManager;
import com.localstrategy.util.types.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;

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

    private long previousInterestTimestamp = 0;

    private ArrayList<Candle> candles = candleConstructor.getCandles();

    private Event currentEvent;
    private SingleTransaction transaction;

    private boolean closeRequestSent = false;

    private boolean printedInactivePositions = false;

    private boolean repayUSDTRequestSent = false;
    private boolean repayBTCRequestSent = false;

    private long buyBackOrderId = Long.MAX_VALUE;
    private long reattemptRepayOrderId = Long.MAX_VALUE;

    private long BTCInterestRepayOrderId = Long.MAX_VALUE;

    private long USDTInterestRepayOrderId = Long.MAX_VALUE;

    Strategy2 strategy;

    public LocalHandler(double initialFreeUSDT, EventScheduler scheduler) {

        this.scheduler = scheduler;

        userAssets.setFreeUSDT(BigDecimal.valueOf(initialFreeUSDT));

        orderRequest = new OrderRequest(
                activePositions,
                new TierManager(),
                userAssets,
                RISK_PCT,
                BinanceHandler.ALGO_ORDER_LIMIT,
                SLIPPAGE_PCT);

        //TODO: Print parameters on strategy start

        strategy = new Strategy2(this, candles, activePositions, inactivePositions);
    }

    private void priceUpdate(SingleTransaction transaction) {
        strategy.priceUpdate(transaction);
    }

    private void newCandle(Candle lastCandle) {
        strategy.candleUpdate(lastCandle);
    }

    public Position executeMarketOrder(BigDecimal stopPrice) {
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

    public Position executeLimitOrder(BigDecimal entryPrice, BigDecimal stopPrice) {
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

    public boolean activateStopLoss(Position position) {
        if (position == null || position.isStopLossRequestSent() || position.isActiveStopLoss()) {
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

    public boolean updateStopLoss(BigDecimal newStopPrice, Position position) {
        if (position == null || !position.isActiveStopLoss() || !position.getGroup().equals(PositionGroup.FILLED)) { //Must be a better way
            return true;
        }

        scheduler.addEvent(new Event(
                currentEvent.getDelayedTimestamp(),
                EventDestination.EXCHANGE,
                OrderAction.CANCEL_ORDER,
                position.getStopOrder().clone()
        ));

        position.getStopOrder().setOpenPrice(newStopPrice);
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

    public boolean cancelPosition(Position position) {
        if (position == null || !position.getGroup().equals(PositionGroup.NEW) || !position.getGroup().equals(PositionGroup.FILLED)) {
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

    public boolean closePosition(Position position) {
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


    public void onEvent(Event event) {


        this.currentEvent = event;

        switch (event.getType()) {
            case TRANSACTION -> {
                this.transaction = event.getTransaction();
                increaseInterest();
                onTransaction(transaction);
            }
            case USER_DATA_STREAM -> { // DONE
                UserDataStream userDataStream = event.getUserDataStream();

                //Update local user assets to the latest snapshot
                userAssets = new UserAssets(userDataStream.userAssets());

                //Repay interest and handle the response, we do that by setting total Unpaid interest and everything else to 0
                if(!repayUSDTRequestSent
                        && userAssets.getRemainingInterestUSDT().compareTo(BigDecimal.valueOf(10)) >= 0){
                    // Repay 10 USDT

                    Order repayOrderUSDT = new Order(
                        transaction.price(),
                        OrderSide.BUY,
                        false,
                        false,
                            BigDecimal.ZERO,
                        OrderType.MARKET,
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                        event.getDelayedTimestamp(),
                        OrderPurpose.REPAY
                    );

                    repayOrderUSDT.setMarginBuyBorrowAmount(BigDecimal.ZERO);
                    repayOrderUSDT.setTotalUnpaidInterest(userAssets.getRemainingInterestUSDT());

                    USDTInterestRepayOrderId = repayOrderUSDT.getId();

                    scheduler.addEvent(new Event(
                            currentEvent.getDelayedTimestamp(),
                            EventDestination.EXCHANGE,
                            OrderAction.REPAY_FUNDS,
                            repayOrderUSDT.clone()
                    ));

                    repayUSDTRequestSent = true;
                }

                if(!repayBTCRequestSent
                        && userAssets.getRemainingInterestBTC().multiply(transaction.price()).compareTo(BigDecimal.valueOf(10)) >= 0
                        && userAssets.getRemainingInterestBTC().compareTo(BigDecimal.valueOf(0.00001)) >= 0){
                    // Repay remaining BTC by first buying them on market, handle repay response on order fill

                    Order rebuyOrderBTC = new Order(
                        transaction.price(),
                        OrderSide.BUY,
                        false,
                        false,
                        userAssets.getRemainingInterestBTC(),
                        OrderType.MARKET,
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                        event.getDelayedTimestamp(),
                        OrderPurpose.REPAY
                    );

                    rebuyOrderBTC.setMarginBuyBorrowAmount(BigDecimal.ZERO);
                    rebuyOrderBTC.setTotalUnpaidInterest(userAssets.getRemainingInterestBTC());

                    BTCInterestRepayOrderId = rebuyOrderBTC.getId();

                    scheduler.addEvent(new Event(
                            currentEvent.getDelayedTimestamp(),
                            EventDestination.EXCHANGE,
                            OrderAction.CREATE_ORDER,
                            rebuyOrderBTC.clone()
                    ));

                    repayBTCRequestSent = true;
                }

                if (userAssets.getMarginLevel().compareTo(BigDecimal.valueOf(1.05)) <= 0) {
                    System.out.println(currentEvent.getId() + "LocalHandler Error - Liquidation. Should have been called in exchange first.");
                } else if (userAssets.getMarginLevel().compareTo(BigDecimal.valueOf(1.1)) <= 0) { //TODO: Handle margin level report - close positions and repay funds
                    //Margin call
                    System.out.println(currentEvent.getId() + "LocalHandler Margin call");
                }

                for (Order order : userDataStream.updatedOrders()) {
                    switch (order.getStatus()) {
                        case REJECTED -> { //DONE
                            switch (order.getRejectionReason()) {
                                case INSUFFICIENT_FUNDS, INSUFFICIENT_MARGIN -> {
                                    System.out.println(currentEvent.getId() + "LocalHandler Error - Market position rejected for insufficient margin or insufficient funds - should have been checked during order creation?");
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
                                        System.out.println(currentEvent.getId() + "Local Error - it's not even in active positions.");
                                    }
                                }
                                case EXCESS_BORROW, INVALID_ORDER_STATE, WOULD_TRIGGER_IMMEDIATELY, MAX_NUM_ALGO_ORDERS -> {
                                    System.out.println(currentEvent.getId() + "Local Error - Order rejected due to Excess borrow, invalid order state, would trigger immediately, or max num algo orders.");
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

//                                                if (order.isAutomaticBorrow() && order.getMarginBuyBorrowAmount() != 0.0 && order.getDirection().equals(OrderSide.SELL)) {
//                                                    shortStopOrderInitialInterestSizeIncrease(order, position);
//                                                }

                                            }
                                            if (position.getGroup().equals(PositionGroup.FILLED)) {
                                                System.out.println(currentEvent.getId() + "Local Error - double filled the position entry order.");
                                            }
                                            position.setEntryOrder(order.clone());
                                            position.setGroup(PositionGroup.FILLED);

                                        } else if (position.getStopOrder().getId() == order.getId()) {

                                            position.setStopOrder(order.clone());

                                            if (position.getEntryOrder().getStatus().equals(OrderStatus.NEW)) {
                                                //TODO: Stoplossed before entry trigger - shouldn't be possible since funds weren't borrowed. This fucked our asset management.
                                                System.out.println(currentEvent.getId() + "Local Error - stoploss filled before entry order.");
                                            }

                                            if (position.getMarginBuyBorrowAmount().compareTo(BigDecimal.ZERO) != 0) {

                                                position.getEntryOrder().setTotalUnpaidInterest(BigDecimal.ZERO); // Null the unpaid interest we do that elsewhere

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

                                            if (position.getMarginBuyBorrowAmount().compareTo(BigDecimal.ZERO) != 0) {

                                                position.getEntryOrder().setTotalUnpaidInterest(BigDecimal.ZERO);

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

                            if(order.getId() == BTCInterestRepayOrderId){ // If it's done for repaying purposes
                                order.setAppropriateUnitPositionValue(BigDecimal.ZERO); // Don't know if this is required
                                order.setDirection(OrderSide.SELL); // We repay BTC

                                scheduler.addEvent(new Event(
                                        currentEvent.getDelayedTimestamp(),
                                        EventDestination.EXCHANGE,
                                        OrderAction.REPAY_FUNDS,
                                        order.clone()
                                ));
                            } else if(order.getId() == buyBackOrderId){
                                //Reattempt repay

                                for(Position position : activePositions){
                                    if(position.getEntryOrder().getId() == reattemptRepayOrderId){
                                        scheduler.addEvent(new Event(
                                                currentEvent.getDelayedTimestamp(),
                                                EventDestination.EXCHANGE,
                                                OrderAction.REPAY_FUNDS,
                                                position.getEntryOrder().clone()
                                        ));
                                    }
                                }
                            }

                            inactivePositions.addAll(tempPositions);
                            activePositions.removeAll(tempPositions);
                        } // DONE
                    }
                }
                increaseInterest();
            } // DONE
            case ACTION_RESPONSE -> {

                ActionResponse actionResponse = event.getActionResponse();
                Order order = event.getOrder();

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

                                                // When we send market + stop combination the stop limit might be rejected before the market order is filled.
                                                // The market order will get filled on the next cycle, so we cannot cancel it.
                                                // Instead, send close request and handle fund repaying and position cancellation locally.

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
                                            }
                                        }
                                        case FILLED -> {
                                            if (position.getEntryOrder().getId() == order.getId()) {

                                                System.out.println(currentEvent.getId() + "Local Error - rejected already filled position entry order?");
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
                                    System.out.println(currentEvent.getId() + "Local Error - insufficient margin action rejection - should be tested during production.");
                            case INSUFFICIENT_FUNDS ->
                                    System.out.println(currentEvent.getId() + "Local Error - insufficient funds action rejection - pls fix");
                            case EXCESS_BORROW ->
                                    System.out.println(currentEvent.getId() + "Local Error - excess borrow action rejection - should have been tested during order creation.");
                            case MAX_NUM_ALGO_ORDERS ->
                                    System.out.println(currentEvent.getId() + "Local Error - maximum number of algo orders action rejection - should have been tested during order creation.");
                            case INVALID_ORDER_STATE -> System.out.println(currentEvent.getId() + "Local Error - invalid order state action rejection");
                        }
                    } // "Done"
                    case ACTION_REJECTED -> { // Cancel order or repay funds - invalid order state (non-existent order), or general repay rejection reasons
                        switch (order.getRejectionReason()) {
                            case INSUFFICIENT_FUNDS -> {

                                // Can only

                                // Insufficient funds to repay a loan means we took the borrowed funds and executed a trade with them? Can only happen with insufficient USDT because we only use that for collateral.

                                //Fixme: That is off and should never happen. Let's sell off some assets we own and repay the loan.

                                //ASSUMPTION: It's probably in our Free BTC asset.
                                if(!order.getDirection().equals(OrderSide.BUY)){
                                    System.out.println("SELL order repay action rejected due to insufficient funds - we lack BTC to repay funds - impossible.");
                                    break;
                                }

                                if(userAssets.getFreeBTC().multiply(transaction.price()).add(userAssets.getFreeUSDT())
                                        .compareTo(order.getMarginBuyBorrowAmount().multiply(
                                                order.getDirection().equals(OrderSide.BUY) ? BigDecimal.ONE : transaction.price()
                                        )) > 0){

                                    for(Position position : activePositions){
                                        if(position.getGroup().equals(PositionGroup.FILLED) && position.getCloseOrder() == null){
                                            //Lower the position value and convert back to USDT required to pay off the loan

                                            reattemptRepayOrderId = order.getId();

                                            BigDecimal previousSize = position.getSize().setScale(position.getSize().scale(), RoundingMode.HALF_UP);

                                            BigDecimal adjustedSize = position.getSize().subtract(
                                                    (userAssets.getFreeBTC().multiply(transaction.price()).add(userAssets.getFreeUSDT())
                                                            .subtract(order.getMarginBuyBorrowAmount().multiply(
                                                                    (order.getDirection().equals(OrderSide.BUY) ? BigDecimal.ONE : transaction.price())
                                                            )))
                                                    .divide(transaction.price(), 8, RoundingMode.HALF_UP)
                                            );

                                            BigDecimal buyBackSize = previousSize.subtract(adjustedSize).setScale(8, RoundingMode.HALF_UP);

                                            position.setSize(adjustedSize);
                                            position.getEntryOrder().setSize(adjustedSize);

                                            //Fixme: I'll assume this happens on with filled, not with cancelled positions?
                                            position.getEntryOrder().setStatus(OrderStatus.FILLED);

                                            scheduler.addEvent(new Event(
                                                    currentEvent.getDelayedTimestamp(),
                                                    EventDestination.EXCHANGE,
                                                    OrderAction.CANCEL_ORDER,
                                                    position.getStopOrder().clone()
                                            ));

                                            position.getStopOrder().setSize(adjustedSize);

                                            scheduler.addEvent(new Event(
                                                    currentEvent.getDelayedTimestamp(),
                                                    EventDestination.EXCHANGE,
                                                    OrderAction.CREATE_ORDER,
                                                    position.getStopOrder().clone()
                                            ));

                                            Order buyBackOrder = new Order(
                                                    transaction.price(),
                                                    OrderSide.SELL,
                                                    false,
                                                    false,
                                                    buyBackSize,
                                                    OrderType.MARKET,
                                                    BigDecimal.ZERO,
                                                    BigDecimal.ZERO,
                                                    currentEvent.getDelayedTimestamp(),
                                                    OrderPurpose.BUYBACK
                                            );

                                            buyBackOrder.setMarginBuyBorrowAmount(BigDecimal.ZERO);

                                            buyBackOrderId = buyBackOrder.getId();

                                            scheduler.addEvent(new Event( // Reattempt the loan after this
                                                currentEvent.getDelayedTimestamp(),
                                                    EventDestination.EXCHANGE,
                                                    OrderAction.CREATE_ORDER,
                                                    buyBackOrder.clone()
                                            ));
                                        }
                                    }
                                }
                            }

                            case INVALID_ORDER_STATE -> {
                                // order.borrowAmount == 0 or stop loss limit order triggered after sending the cancel request, and we had enough funds to execute it.
                                // Now we must buy back on market.

                                //FIXME: BUT WE'RE GETTING A LOT OF IT WHAT THE FUCK
                                //Todo: Don't know if this is necessary.

                                for (Position position : activePositions) {
                                    if (position.getStopOrder().getId() == order.getId()) {

                                        Order closeOrder = order.clone();

                                        closeOrder.setDirection(order.getDirection().equals(OrderSide.BUY) ? OrderSide.SELL : OrderSide.BUY);
                                        closeOrder.setType(OrderType.MARKET);

                                        scheduler.addEvent(new Event(
                                                currentEvent.getDelayedTimestamp(),
                                                EventDestination.EXCHANGE,
                                                OrderAction.CREATE_ORDER,
                                                closeOrder.clone()
                                        ));

                                        position.setStopOrder(closeOrder);
                                    }
                                }
                            }
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

                        if(order.getId() == BTCInterestRepayOrderId){ // If it's done for repaying purposes
                            repayBTCRequestSent = false;
                        } else if (order.getId() == USDTInterestRepayOrderId){
                            repayUSDTRequestSent = false;
                        }

                        inactivePositions.addAll(tempPositions);
                        activePositions.removeAll(tempPositions);
                    } // DONE
                    case ORDER_CANCELLED -> { //DONE?

                        if (order.getType().equals(OrderType.MARKET)) {
                            System.out.println(currentEvent.getId() + "Local Error - Cancelling a market order.");
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
                                        System.out.println(currentEvent.getId() + "Local Error - cancelling filled position's entry order");
                                        position.setEntryOrder(order.clone());
                                    } else if (position.getStopOrder().getId() == order.getId()) {
                                        //Most likely a breakeven / move stoploss request
                                        position.setStopOrder(order.clone());
                                        position.setActiveStopLoss(false);
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

                                        if (order.getType().equals(OrderType.LIMIT)) {

                                            position.setMarginBuyBorrowAmount(order.getMarginBuyBorrowAmount());

                                            // We executed a short order without borrowings funds now we got to recuperate
                                            if(order.getMarginBuyBorrowAmount().compareTo(BigDecimal.ZERO) == 0
                                                    && order.getDirection().equals(OrderSide.SELL)){
                                                Order buyBackOrder = new Order(
                                                        transaction.price(),
                                                        OrderSide.BUY,
                                                        false,
                                                        false,
                                                        order.getSize(),
                                                        OrderType.MARKET,
                                                        BigDecimal.ZERO,
                                                        BigDecimal.ZERO,
                                                        currentEvent.getDelayedTimestamp(),
                                                        OrderPurpose.BUYBACK
                                                );

                                                scheduler.addEvent(new Event(
                                                        currentEvent.getDelayedTimestamp(),
                                                        EventDestination.EXCHANGE,
                                                        OrderAction.CREATE_ORDER,
                                                        buyBackOrder.clone()
                                                ));
                                            }

//                                            if (order.isAutomaticBorrow() && order.getMarginBuyBorrowAmount() != 0.0 && order.getDirection().equals(OrderSide.SELL)) {
//                                                shortStopOrderInitialInterestSizeIncrease(order, position);
//                                            }
                                        }
                                        position.setEntryOrder(order.clone());

                                        position.setGroup(PositionGroup.NEW);
                                    } else if (position.getStopOrder().getId() == order.getId()) {
                                        position.setEntryOrder(order.clone());
                                        System.out.println(currentEvent.getId() + "Local Error - created a stop order for a pending position before it became new");
                                    }
                                }
                                case NEW -> {
                                    if (position.getEntryOrder().getId() == order.getId()) {
                                        position.setEntryOrder(order.clone());
                                        System.out.println(currentEvent.getId() + "Local Error - created a new entry order for a position of status new?");
                                    } else if (position.getStopOrder().getId() == order.getId()) {
                                        position.setStopOrder(order.clone());
                                        position.setActiveStopLoss(true);
                                    }
                                }
                                case FILLED -> {
                                    if (position.getStopOrder().getId() == order.getId()) {
                                        position.setStopOrder(order.clone());
                                        position.setActiveStopLoss(true);
                                    } else if (position.getCloseOrder() != null && position.getCloseOrder().getId() == order.getId()) {
                                        // If we made market + stop request and stop wasn't placed before market order fill but didn't get placed because it would trigger immediately,
                                        // We get a would trigger immediately action rejection for a new position. We send a market close request because the order will be filled in the next cycle
                                        // Here we handle the response from the cancel order request when it's created.
                                        position.setCloseOrder(order.clone());
                                        // Handle order filling and repaying in user data stream
                                    }
                                }
                            }
                        }
                    } // DONE
                }

                increaseInterest();
            }
            default ->
                    System.out.println(currentEvent.getId() + "Unexplained event found its way into our local strategy function.");
        }
    }

    private void shortStopOrderInitialInterestSizeIncrease(Order order, Position position) {

        Order stopOrder = position.getStopOrder();

        if (position.isStopLossRequestSent()) {
            // Update stoploss

            scheduler.addEvent(new Event(
                    currentEvent.getDelayedTimestamp(),
                    EventDestination.EXCHANGE,
                    OrderAction.CANCEL_ORDER,
                    stopOrder.clone()
            ));

            stopOrder.setSize(order.getSize().add(order.getTotalUnpaidInterest()));

            scheduler.addEvent(new Event(
                    currentEvent.getDelayedTimestamp(),
                    EventDestination.EXCHANGE,
                    OrderAction.CREATE_ORDER,
                    stopOrder.clone()
            ));


        } else {
            // Increase stoploss size

            stopOrder.setSize(stopOrder.getSize().add(order.getTotalUnpaidInterest()));
        }
    }

    private void increaseInterest() {
        if (currentEvent.getDelayedTimestamp() - previousInterestTimestamp >= 1000 * 60 * 60) {

            previousInterestTimestamp = currentEvent.getDelayedTimestamp();
            previousInterestTimestamp -= currentEvent.getDelayedTimestamp() % (1000 * 60 * 60);

            for (Position position : activePositions) {
                Order order = position.getEntryOrder();

                if (order.getMarginBuyBorrowAmount().compareTo(BigDecimal.ZERO) != 0) {

                    order.increaseUnpaidInterest();
//                    if (order.getDirection().equals(OrderSide.SELL)) { // Every time BTC interest increases in a short order we must update the stoploss to buy more bitcoin when triggered
//                        //Now we're removing the stoploss, then adding on instantly. IRL it would be better to keep the stoploss and accumulate interest until we can just buy $10 on market
//
//                        Order stopOrder = position.getStopOrder();
//
//                        scheduler.addEvent(new Event(
//                                currentEvent.getDelayedTimestamp(),
//                                EventDestination.EXCHANGE,
//                                OrderAction.CANCEL_ORDER,
//                                stopOrder.clone()
//                        ));
//
//                        stopOrder.setSize(order.getSize() + order.getTotalUnpaidInterest());
//
//                        scheduler.addEvent(new Event(
//                                currentEvent.getDelayedTimestamp(),
//                                EventDestination.EXCHANGE,
//                                OrderAction.CREATE_ORDER,
//                                stopOrder.clone()
//                        ));
//                    }
                }
            }
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

    public UserAssets getUserAssets() {
        return this.userAssets;
    }
}
