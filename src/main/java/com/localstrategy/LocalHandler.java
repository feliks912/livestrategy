package com.localstrategy;

import com.localstrategy.util.enums.*;
import com.localstrategy.util.helper.CandleConstructor;
import com.localstrategy.util.helper.EventScheduler;
import com.localstrategy.util.helper.OrderRequest;
import com.localstrategy.util.helper.TierManager;
import com.localstrategy.util.types.*;
import org.apache.commons.collections4.map.SingletonMap;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;

public class LocalHandler {

    private static final int CANDLE_VOLUME = 500_000;
    private static final double RISK_PCT = 0.1;

    //In relation to the difference between our entry and stop-loss price difference, how much in percentage of slippage are we ready to accept (total, not the average fill price)
    private static final int SLIPPAGE_PCT = 15;

    private final EventScheduler scheduler;
    private final CandleConstructor candleConstructor = new CandleConstructor(CANDLE_VOLUME);

    private final ArrayList<Position> activePositions = new ArrayList<>();
    private final LinkedList<Position> inactivePositions = new LinkedList<>();

    private UserAssets userAssets = new UserAssets();
    private final OrderRequest orderRequest;
    private Candle lastCandle;

    private long previousInterestTimestamp = 0;

    private ArrayList<Candle> candles = candleConstructor.getCandles();

    private ArrayList<SingletonMap<Long, Double>> candleCloseList = new ArrayList<>();

    private Event currentEvent;
    private SingleTransaction transaction;

    private boolean closeRequestSent = false;

    private boolean printedInactivePositions = false;

    private boolean repayUSDTRequestSent = false;
    private boolean repayBTCRequestSent = false;

    private long buyBackOrderId = -1;
    private long reattemptRepayOrderId = -1;

    private long BTCInterestRepayOrderId = -1;

    private long USDTInterestRepayOrderId = -1;


    private final TreeMap<Long, ArrayList<Map<Order, OrderAction>>> waitForRepayToRepeatActionsList = new TreeMap<>();
    private final TreeMap<Long, ArrayList<Map<Order, OrderAction>>> closeFilledAfterStopFilledCorrectionActionsList = new TreeMap<>();
    private final TreeMap<Long, ArrayList<Map<Order, OrderAction>>> illegalExecutionCorrectionActionsList = new TreeMap<>();


    private boolean marginCallPositionCloseRequestSent = false;
    private long marginCallPositionId = -1;


    private final ArrayList<Map<Order, OrderAction>> waitForRepayToRepeatActionList = new ArrayList<>();


    private long closeFilledAfterStopFilledOrderId = -1;
    private final ArrayList<Map<Order, OrderAction>> closeFilledAfterStopFilledCorrectionActionList = new ArrayList<>();


    private boolean illegalExecutionCorrectionInProgress = false;
    private long illegalExecutionCorrectionOrderId = -1;
    private final ArrayList<Map<Order, OrderAction>> illegalExecutionCorrectionActionStorageList = new ArrayList<>();


    Strategy2 strategy;

    public LocalHandler(double initialFreeUSDT, EventScheduler scheduler) {

        this.scheduler = scheduler;

        userAssets.setFreeUSDT(BigDecimal.valueOf(initialFreeUSDT));

        orderRequest = new OrderRequest(
                activePositions,
                new TierManager(),
                RISK_PCT,
                BinanceHandler.ALGO_ORDER_LIMIT,
                SLIPPAGE_PCT);
        orderRequest.setUserAssets(userAssets);

        //TODO: Print parameters on strategy start

        strategy = new Strategy2(this, candles, activePositions, inactivePositions);
    }

    private void priceUpdate(SingleTransaction transaction) {
        strategy.priceUpdate(transaction);
    }

    private void newCandle(Candle lastCandle) {
        strategy.candleUpdate(lastCandle);
    }

    public Position executeMarketOrder(double stopPrice) {
        Position newMarketPosition = orderRequest.newMarketPosition(transaction, BigDecimal.valueOf(stopPrice));

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

    public Position executeLimitOrder(double entryPrice, double stopPrice) {
        Position newLimitPosition = orderRequest.newLimitPosition(BigDecimal.valueOf(entryPrice), BigDecimal.valueOf(stopPrice), transaction);

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
        if (position == null || position.isStopLossRequestSent() || position.isStopLossActive()) {
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

    public boolean updateStopLoss(double newStopPrice, Position position) {
        if (position == null || !position.isStopLossActive() || !position.getGroup().equals(PositionGroup.FILLED)) { //Must be a better way
            return true;
        }

        scheduler.addEvent(new Event(
                currentEvent.getDelayedTimestamp(),
                EventDestination.EXCHANGE,
                OrderAction.CANCEL_ORDER,
                position.getStopOrder().clone()
        ));

        position.getStopOrder().setOpenPrice(BigDecimal.valueOf(newStopPrice));
        position.setOpenTimestamp(currentEvent.getDelayedTimestamp());

        scheduler.addEvent(new Event(
                currentEvent.getDelayedTimestamp(),
                EventDestination.EXCHANGE,
                OrderAction.CREATE_ORDER,
                position.getStopOrder().clone()
        ));

        position.setBreakEvenStatus(true);

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

        if (position == null || position.isRepayRequestSent()) {
            return true;
        }

        if (position.isClosedBeforeStopLoss() || !position.getGroup().equals(PositionGroup.FILLED) || (position.getCloseOrder() != null && position.getCloseOrder().getRejectionReason() == null)) {
            return true;
        }

        Order closeOrder = position.createCloseOrder(transaction);

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
                //increaseInterest();
                onTransaction(transaction);
            }
            case USER_DATA_STREAM -> { // DONE
                UserDataStream userDataStream = event.getUserDataStream();

                //Update local user assets to the latest snapshot
                userAssets = new UserAssets(userDataStream.userAssets());
                orderRequest.setUserAssets(userAssets);

                //Repay interest and handle the response, we do that by setting total Unpaid interest and everything else to 0
                if (!repayUSDTRequestSent
                        && userAssets.getRemainingInterestUSDT().compareTo(BigDecimal.valueOf(10)) >= 0) {
                    // Repay 10 USDT

                    Order repayOrderUSDT = new Order(
                            BigDecimal.valueOf(transaction.price()),
                            OrderSide.BUY,
                            false,
                            false,
                            BigDecimal.ZERO,
                            OrderType.MARKET,
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            event.getDelayedTimestamp(),
                            OrderPurpose.REPAY,
                            -1
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

                if (!repayBTCRequestSent
                        && userAssets.getRemainingInterestBTC().multiply(BigDecimal.valueOf(transaction.price())).compareTo(BigDecimal.valueOf(10)) >= 0
                        && userAssets.getRemainingInterestBTC().compareTo(BigDecimal.valueOf(0.00001)) >= 0) {
                    // Repay remaining BTC by first buying them on market, handle repay response on order fill

                    Order rebuyOrderBTC = new Order(
                            BigDecimal.valueOf(transaction.price()),
                            OrderSide.BUY,
                            false,
                            false,
                            userAssets.getRemainingInterestBTC(),
                            OrderType.MARKET,
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            event.getDelayedTimestamp(),
                            OrderPurpose.REPAY,
                            -1
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

                if (userAssets.getMarginLevel() <= 1.05) {
                    System.out.println(currentEvent.getId() + "LocalHandler Error - Liquidation. Should have been called in exchange first.");
                } else if (userAssets.getMarginLevel() <= 1.1 && !marginCallPositionCloseRequestSent) { //TODO: Handle margin level report - close positions and repay funds
                    //Margin call
                    System.out.println(currentEvent.getId() + "LocalHandler Margin call");

                    Position maximumBorrowPosition = null;
                    double maximumNotionalBorrowValue = 0;

                    for (Position position : activePositions) {
                        if (position.getGroup().equals(PositionGroup.FILLED)) {
                            if (position.getDirection().equals(OrderSide.BUY) && position.getMarginBuyBorrowAmount().doubleValue() > maximumNotionalBorrowValue) {
                                maximumNotionalBorrowValue = position.getMarginBuyBorrowAmount().doubleValue();
                                maximumBorrowPosition = position;
                                break;
                            } else if (position.getDirection().equals(OrderSide.SELL)
                                    && position.getMarginBuyBorrowAmount().multiply(BigDecimal.valueOf(transaction.price())).doubleValue() > maximumNotionalBorrowValue) {
                                maximumNotionalBorrowValue = position.getMarginBuyBorrowAmount().multiply(BigDecimal.valueOf(transaction.price())).doubleValue();
                                maximumBorrowPosition = position;
                                break;
                            }
                        }
                    }

                    if (maximumBorrowPosition != null) {
                        closePosition(maximumBorrowPosition);
                        marginCallPositionCloseRequestSent = true;
                        marginCallPositionId = maximumBorrowPosition.getId();
                    } else {
                        System.out.println("Local Error - margin call but no positions to liquidate?");
                    }

                }

                for (Order order : userDataStream.updatedOrders()) {
                    switch (order.getStatus()) {
                        case REJECTED -> { //DONE
                            switch (order.getRejectionReason()) {
                                case INSUFFICIENT_FUNDS, INSUFFICIENT_MARGIN -> {

                                    //Insufficient funds? Discard the position.
                                    if (!order.getPurpose().equals(OrderPurpose.ENTRY)) {
                                        //System.out.println(currentEvent.getId() + "Local Error - insufficient funds | margin action rejection but it's not an entry order - pls fix");
                                    }

                                    boolean isInActivePositions = false;
                                    boolean isInInactivePositions = false;

                                    loop: for (Position position : activePositions) {
                                        switch (position.getGroup()) {
                                            case PENDING, NEW, FILLED -> {
                                                if (position.getEntryOrder().getId() == order.getId()) {
                                                    position.setEntryOrder(order.clone());

                                                    if (position.isStopLossRequestSent()) {
                                                        scheduler.addEvent(new Event(
                                                                currentEvent.getDelayedTimestamp(),
                                                                EventDestination.EXCHANGE,
                                                                OrderAction.CANCEL_ORDER,
                                                                position.getStopOrder().clone()
                                                        ));
                                                    }

                                                    isInActivePositions = true;
                                                } else if (position.getStopOrder().getId() == order.getId()) { // Might focus on this as it's handling stoplosses
                                                    position.setStopOrder(order.clone());

                                                    isInActivePositions = true;
                                                } else if (position.getCloseOrder() != null && position.getCloseOrder().getId() == order.getId()) {

                                                    delayedRepayOrderRejectionReasonMethod(order, position);

                                                    position.setCloseOrder(order.clone());
                                                    isInActivePositions = true;
                                                }

                                                if(isInActivePositions){
                                                    break loop;
                                                }
                                            }
                                        }
                                    }

                                    if (!isInActivePositions) {
                                        for (Position position : inactivePositions) {
                                            if (position.getStopOrder().getId() == order.getId()) {
                                                isInInactivePositions = true;
                                                if (position.getCloseOrder() != null && position.getCloseOrder().getStatus().equals(OrderStatus.FILLED)) {
                                                    position.setStopOrder(order.clone());
                                                } else {
                                                    System.out.println("Local Error - stop order rejected, position is in inactive positions, close order is sent but not filled.");
                                                }
                                                break;
                                            }
                                        }
                                    }

                                    if (!isInActivePositions && !isInInactivePositions) {
                                        System.out.println("Local Error - rejected order (not action) is not in active nor inactive orders.");
                                    }
                                }
                                case EXCESS_BORROW, INVALID_ORDER_STATE, WOULD_TRIGGER_IMMEDIATELY, MAX_NUM_ALGO_ORDERS -> {
                                    System.out.println(currentEvent.getId() + "Local Error - Order rejected due to Excess borrow, invalid order state, would trigger immediately, or max num algo orders.");
                                }
                            }
                        } // DONE

                        case FILLED -> { // DONE
                            ArrayList<Position> tempPositions = new ArrayList<>();
                            boolean isInActivePositions = false;
                            loop: for (Position position : activePositions) {
                                switch (position.getGroup()) {
                                    case PENDING, NEW, FILLED -> {
                                        if (position.getEntryOrder().getId() == order.getId()) {

                                            isInActivePositions = true;

//                                            if(position.getDirection().equals(OrderSide.BUY) && order.getFillPrice().doubleValue() <= position.getStopOrder().getOpenPrice().doubleValue()){
//                                                System.out.println("fucked on BUY. Difference " + (order.getFillPrice().doubleValue() - position.getStopOrder().getOpenPrice().doubleValue()));
//                                            } else if(position.getDirection().equals(OrderSide.SELL) && order.getFillPrice().doubleValue() >= position.getStopOrder().getOpenPrice().doubleValue()){
//                                                System.out.println("fucked on SELL. Difference " + (order.getFillPrice().doubleValue() - position.getStopOrder().getOpenPrice().doubleValue()));
//                                            }

                                            if (position.getGroup().equals(PositionGroup.FILLED)) {
                                                System.out.println(currentEvent.getId() + "Local Error - double filled the position entry order.");
                                            }

                                            position.setFillPrice(order.getFillPrice());
                                            position.setGroup(PositionGroup.FILLED);
                                            position.setEntryOrder(order.clone());

                                            if (order.getType().equals(OrderType.MARKET)) {
                                                position.setMarginBuyBorrowAmount(order.getMarginBuyBorrowAmount());

                                                illegalExecutionRoutine(order, position);
                                            }

                                            break loop;

                                        } else if (position.getStopOrder().getId() == order.getId()) {

                                            isInActivePositions = true;

                                            position.setStopOrder(order.clone());

                                            //FIXME: ABCD
                                            if (position.getEntryOrder().getStatus().equals(OrderStatus.REJECTED)) {
                                                break;
                                            }

                                            if (position.getEntryOrder().getStatus().equals(OrderStatus.NEW)) {
                                                //TODO: Stoplossed before entry trigger - shouldn't be possible since funds weren't borrowed. This fucked our asset management.
                                                System.out.println(currentEvent.getId() + "Local Error - stoploss filled before entry order.");
                                            }

                                            //If we borrowed for the position, but the stop order isn't a market compensation for immediate trigger (
                                            if (position.getMarginBuyBorrowAmount().doubleValue() > 0e-7) {

                                                position.getEntryOrder().setTotalUnpaidInterest(BigDecimal.ZERO); // Null the unpaid interest we do that elsewhere

                                                scheduler.addEvent(new Event(
                                                        currentEvent.getDelayedTimestamp(),
                                                        EventDestination.EXCHANGE,
                                                        OrderAction.REPAY_FUNDS,
                                                        position.getEntryOrder().clone()));

                                                position.setRepayRequestSent(true);

                                            } else {
                                                position.closePosition(currentEvent.getDelayedTimestamp());

                                                if (position.getGroup().equals(PositionGroup.FILLED)) {
                                                    position.setGroup(PositionGroup.CLOSED);
                                                    checkFuckedness(position);
                                                    if (position.getId() == marginCallPositionId) {
                                                        marginCallPositionCloseRequestSent = false;
                                                    }
                                                } else {
                                                    position.setGroup(PositionGroup.DISCARDED);
                                                }

                                                tempPositions.add(position);
                                            }

                                            break loop;

                                        } else if (position.getCloseOrder() != null && position.getCloseOrder().getId() == order.getId()) {

                                            isInActivePositions = true;

                                            if (order.getId() == 22165) {
                                                boolean point = true;
                                            }

                                            position.setCloseOrder(order.clone());

                                            if (position.getStopOrder().getStatus().equals(OrderStatus.FILLED)) {
                                                System.out.println("closeFilledAfterStopFilled Problem begun.");

                                                order.setStatus(OrderStatus.NEW);
                                                order.setDirection(order.getDirection().equals(OrderSide.BUY) ? OrderSide.SELL : OrderSide.BUY);
                                                order.setMarginBuyBorrowAmount(BigDecimal.ZERO);
                                                order.setFillTimestamp(0);
                                                order.setRejectionReason(null);
                                                order.setOpenPrice(BigDecimal.valueOf(transaction.price()));
                                                order.setOpenTimestamp(transaction.timestamp());

                                                scheduler.addEvent(new Event(
                                                        currentEvent.getDelayedTimestamp(),
                                                        EventDestination.EXCHANGE,
                                                        OrderAction.CREATE_ORDER,
                                                        order.clone()
                                                ));

                                                //This is a new key which must be added to the respective map for delayed orders to be stored in.
                                                addToDelayedActionMap(closeFilledAfterStopFilledCorrectionActionsList, order.getId(), null, null);
                                            }

                                            if (position.getMarginBuyBorrowAmount().doubleValue() > 0e-7) { //If position has borrowed

                                                position.getEntryOrder().setTotalUnpaidInterest(BigDecimal.ZERO);

                                                scheduler.addEvent(new Event(
                                                        currentEvent.getDelayedTimestamp(),
                                                        EventDestination.EXCHANGE,
                                                        OrderAction.REPAY_FUNDS,
                                                        position.getEntryOrder().clone()));

                                                position.setRepayRequestSent(true);

                                            } else {

                                                position.closePosition(currentEvent.getDelayedTimestamp());

                                                if (position.getGroup().equals(PositionGroup.FILLED)) {
                                                    position.setGroup(PositionGroup.CLOSED);
                                                    checkFuckedness(position);
                                                    if (position.getId() == marginCallPositionId) {
                                                        marginCallPositionCloseRequestSent = false;
                                                    }
                                                } else {
                                                    position.setGroup(PositionGroup.DISCARDED);
                                                }

                                                tempPositions.add(position); //Add to inactive positions
                                            }

                                            if (position.getStopOrder().getStatus().equals(OrderStatus.NEW)) { //If stoploss is still active cancel it
                                                scheduler.addEvent(new Event(
                                                        currentEvent.getDelayedTimestamp(),
                                                        EventDestination.EXCHANGE,
                                                        OrderAction.CANCEL_ORDER,
                                                        position.getStopOrder().clone()
                                                ));
                                            }

                                            illegalExecutionRoutine(order, position);

                                            break loop;
                                        }
                                    }
                                }
                            }

                            if (order.getId() == BTCInterestRepayOrderId) { // If it's done for repaying purposes
                                order.setAppropriateUnitPositionValue(BigDecimal.ZERO); // Don't know if this is required
                                order.setDirection(OrderSide.SELL); // We repay BTC

                                order.setMarginBuyBorrowAmount(BigDecimal.ZERO);

                                scheduler.addEvent(new Event(
                                        currentEvent.getDelayedTimestamp(),
                                        EventDestination.EXCHANGE,
                                        OrderAction.REPAY_FUNDS,
                                        order.clone()
                                ));
                            } else if (order.getId() == buyBackOrderId) {
                                //Reattempt repay

                                for (Position position : activePositions) {
                                    if (position.getEntryOrder().getId() == reattemptRepayOrderId) {

                                        order.setMarginBuyBorrowAmount(BigDecimal.ZERO);

                                        scheduler.addEvent(new Event(
                                                currentEvent.getDelayedTimestamp(),
                                                EventDestination.EXCHANGE,
                                                OrderAction.REPAY_FUNDS,
                                                position.getEntryOrder().clone()
                                        ));

                                        position.setRepayRequestSent(true);

                                        break;
                                    }
                                }
                            } else if(!isInActivePositions){
                                for(Position position : inactivePositions){
                                    if(position.getCloseOrder() != null && position.getCloseOrder().getId() == order.getId()){

                                        position.setCloseOrder(order.clone());

                                        if(position.isBuyBackExecuted()){
                                            break;
                                        }

                                        if(position.getStopOrder().getStatus().equals(OrderStatus.FILLED)){
                                            //Meaning, the close order filled in the same transaction as stop order and we had enough funds to fill it

                                            //TODO Recouperate close order funds
                                            Order buyBackOrder = order.clone();
                                            buyBackOrder.setType(OrderType.MARKET);
                                            buyBackOrder.setStatus(OrderStatus.NEW);
                                            buyBackOrder.setDirection(order.getDirection().equals(OrderSide.BUY) ? OrderSide.SELL : OrderSide.BUY);
                                            buyBackOrder.setOpenPrice(BigDecimal.valueOf(transaction.price()));

                                            scheduler.addEvent(new Event(
                                                    currentEvent.getDelayedTimestamp(),
                                                    EventDestination.EXCHANGE,
                                                    OrderAction.CREATE_ORDER,
                                                    buyBackOrder
                                            ));
                                        }

                                        illegalExecutionRoutine(order, position);

                                        if(!position.getGroup().equals(PositionGroup.CLOSED)){
                                            position.setGroup(PositionGroup.CLOSED);
                                        }

                                        position.setBuyBackExecuted(true);

                                    } else if(position.getStopOrder().getId() == order.getId()){

                                        position.setStopOrder(order.clone());

                                        if(position.isBuyBackExecuted()){
                                            break;
                                        }

                                        if(position.getCloseOrder() != null && position.getCloseOrder().getStatus().equals(OrderStatus.FILLED)){

                                            position.setStopOrder(order.clone());

                                            //Meaning, the stop order filled in the same transaction as close order and we had enough funds to fill it

                                            //TODO Recouperate stop order funds
                                            Order buyBackOrder = order.clone();
                                            buyBackOrder.setType(OrderType.MARKET);
                                            buyBackOrder.setStatus(OrderStatus.NEW);
                                            buyBackOrder.setDirection(order.getDirection().equals(OrderSide.BUY) ? OrderSide.SELL : OrderSide.BUY);
                                            buyBackOrder.setOpenPrice(BigDecimal.valueOf(transaction.price()));

                                            scheduler.addEvent(new Event(
                                                    currentEvent.getDelayedTimestamp(),
                                                    EventDestination.EXCHANGE,
                                                    OrderAction.CREATE_ORDER,
                                                    buyBackOrder
                                            ));
                                        }

                                        if(!position.getGroup().equals(PositionGroup.CLOSED)){
                                            position.setGroup(PositionGroup.CLOSED);
                                        }

                                        position.setBuyBackExecuted(true);
                                    }
                                }
                            }

                            executeDelayedActionsFromMap(closeFilledAfterStopFilledCorrectionActionsList, order.getId());

                            for (Position p : tempPositions) {
                                inactivePositions.addFirst(p);
                            }
                            activePositions.removeAll(tempPositions);
                        } // DONE
                    }
                }
                //increaseInterest();
            } // DONE
            case ACTION_RESPONSE -> {

                ActionResponse actionResponse = event.getActionResponse();
                Order order = event.getOrder();

                switch (actionResponse) {
                    case ORDER_REJECTED -> {
                        switch (order.getRejectionReason()) {
                            case WOULD_TRIGGER_IMMEDIATELY -> {
                                loop: for (Position position : activePositions) {
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

                                                break loop;

                                            } else if (position.getStopOrder().getId() == order.getId()) {

                                                // When we send market + stop combination the stop limit might be rejected before the market order is filled.
                                                // The market order will get filled on the next cycle, so we cannot cancel it.
                                                // Instead, send close request and handle fund repaying and position cancellation locally.

                                                position.setStopOrder(order.clone());

                                                order.setType(OrderType.MARKET);
                                                order.setOpenPrice(BigDecimal.valueOf(transaction.price()));
                                                order.setOpenTimestamp(currentEvent.getTimestamp());
                                                order.setStatus(OrderStatus.NEW);

                                                scheduler.addEvent(new Event(
                                                        currentEvent.getDelayedTimestamp(),
                                                        EventDestination.EXCHANGE,
                                                        OrderAction.CREATE_ORDER,
                                                        order.clone()));

                                                position.setClosedBeforeStopLoss(true);

                                                break loop;
                                            }
                                        }
                                        case FILLED -> {
                                            if (position.getEntryOrder().getId() == order.getId()) {

                                                System.out.println(currentEvent.getId() + "Local Error - rejected already filled position entry order?");
                                                position.setEntryOrder(order.clone());

                                                break loop;

                                            } else if (position.getStopOrder().getId() == order.getId()) {

                                                position.setStopOrder(order.clone());

                                                order.setType(OrderType.MARKET);
                                                order.setOpenPrice(BigDecimal.valueOf(transaction.price()));
                                                order.setOpenTimestamp(currentEvent.getTimestamp());
                                                order.setStatus(OrderStatus.NEW);

                                                scheduler.addEvent(new Event(
                                                        currentEvent.getDelayedTimestamp(),
                                                        EventDestination.EXCHANGE,
                                                        OrderAction.CREATE_ORDER,
                                                        order.clone()));

                                                position.setClosedBeforeStopLoss(true);

                                                break loop;

                                            } else if (position.getCloseOrder() != null && position.getCloseOrder().getId() == order.getId()) {
                                                //For programmatic take profits, not usable for now
                                                position.setCloseOrder(order.clone());

                                                break loop;
                                            }
                                        }
                                    }
                                }
                            }
                            case INSUFFICIENT_FUNDS, INSUFFICIENT_MARGIN -> {
                                //Insufficient funds? Discard the position.
                                if (!order.getPurpose().equals(OrderPurpose.ENTRY)) {
                                    //System.out.println(currentEvent.getId() + "Local Error - insufficient funds | margin action rejection but it's not an entry order - pls fix");
                                }

                                boolean isInActivePositions = false;
                                boolean isInInactivePositions = false;

                                ArrayList<Position> tempPositions = new ArrayList<>();

                                loop: for (Position position : activePositions) {
                                    if (position.getEntryOrder().getId() == order.getId()) {
                                        isInActivePositions = true;

                                        if (position.isStopLossRequestSent()) {
                                            if (!position.getStopOrder().getStatus().equals(OrderStatus.FILLED)) {

                                                // What if it fills in the meantime? Illegal order state? Do we have to handle that separately (will it remove the position automatically?)
                                                // Yes illegal order state must be handler appropriately and it seems to be.

                                                scheduler.addEvent(new Event(
                                                        currentEvent.getDelayedTimestamp(),
                                                        EventDestination.EXCHANGE,
                                                        OrderAction.CANCEL_ORDER,
                                                        position.getStopOrder().clone()
                                                ));

                                                // When order cancelled response comes it must discard the position (already done since rejectionReason != null)? True.

                                            } else {
                                                //It's filled which is an issue because it didn't borrow funds.
                                                System.out.println("Local Error - ENTRY order rejected due to insufficient funds | margin but stoploss is filled.");
                                            }

                                            position.setEntryOrder(order.clone());
                                        } else {
                                            // Just discard the position.
                                            position.setGroup(PositionGroup.DISCARDED);
                                            tempPositions.add(position);
                                        }

                                        break;

                                    } else if (position.getStopOrder().getId() == order.getId()) {
                                        isInActivePositions = true;
                                        System.out.println("Local Error - STOP order rejected due to insufficient funds | margin");
                                        position.setStopOrder(order.clone());

                                        break;
                                    } else if (position.getCloseOrder() != null && position.getCloseOrder().getId() == order.getId()) {
                                        isInActivePositions = true;

                                        position.setCloseOrder(order.clone());

                                        if (!position.getStopOrder().getStatus().equals(OrderStatus.FILLED)) {

                                            if(!waitForRepayToRepeatActionsList.isEmpty()){
                                                delayedRepayOrderRejectionReasonMethod(order, position);
                                            }


                                            if (!closeFilledAfterStopFilledCorrectionActionsList.isEmpty()) { //Another position executed a close after stop, blocking this one from executing.
                                                //TODOOO

                                                addToDelayedActionMap(closeFilledAfterStopFilledCorrectionActionsList, null, order, OrderAction.CREATE_ORDER);
                                            } else if(!order.isCloseReattempted()) {
                                                order.setCloseReattempted(true);
                                                order.setStatus(OrderStatus.NEW);
                                                scheduler.addEvent(new Event(
                                                        currentEvent.getDelayedTimestamp(),
                                                        EventDestination.EXCHANGE,
                                                        OrderAction.CREATE_ORDER,
                                                        order.clone()
                                                ));
                                            } else {
                                                System.out.println("Local Error - can't close order twice.");
                                            }
                                        }

                                        break;
                                    }
                                }

                                if (!isInActivePositions) {
                                    for (Position position : inactivePositions) {
                                        if (position.getCloseOrder() != null && position.getCloseOrder().getId() == order.getId()) {
                                            isInInactivePositions = true;
                                            if (position.getStopOrder().getStatus().equals(OrderStatus.FILLED)) {
                                                position.setCloseOrder(null);
                                            } else {
                                                System.out.println("Rejected close order from a position which stoploss is NOT filled found in inactive positions - bug.");
                                            }

                                            break;
                                        } else if (position.getStopOrder().getId() == order.getId()) {
                                            isInInactivePositions = true;
                                            System.out.println("Rejected stop order from inactive position???");

                                            position.setStopOrder(order.clone());

                                            break;
                                        } else if (position.getEntryOrder().getId() == order.getId()) {
                                            isInInactivePositions = true;
                                            System.out.println("Rejected entry order from inactive position???");

                                            position.setEntryOrder(order.clone());

                                            break;
                                        }
                                    }
                                }

                                if (!isInActivePositions && !isInInactivePositions) {
                                    System.out.println("Local Error - order rejected due to insufficient funds | margin, but not in any active OR INACTIVE positions.");
                                }

                                if (!tempPositions.isEmpty()) {
                                    for (Position p : tempPositions) {
                                        inactivePositions.addFirst(p);
                                    }
                                    activePositions.removeAll(tempPositions);
                                    tempPositions.clear();
                                }
                            }
                            case EXCESS_BORROW -> {
                                //System.out.println(currentEvent.getId() + "Local Error - excess borrow action rejection - should have been tested during order creation.");

                                ArrayList<Position> tempPositions = new ArrayList<>();

                                for (Position position : activePositions) {
                                    if (position.getEntryOrder().getId() == order.getId()) {
                                        if (position.isStopLossRequestSent()) {
                                            if (position.getStopOrder().getStatus().equals(OrderStatus.FILLED)) {
                                                System.out.println("Local Error - excess borrow order rejected but it's stoploss is triggered.");
                                            } else {
                                                scheduler.addEvent(new Event(
                                                        currentEvent.getDelayedTimestamp(),
                                                        EventDestination.EXCHANGE,
                                                        OrderAction.CANCEL_ORDER,
                                                        position.getStopOrder().clone()
                                                ));
                                            }
                                        } else {
                                            // Discard the position
                                            position.setGroup(PositionGroup.DISCARDED);
                                            tempPositions.add(position);
                                        }

                                        break;
                                    }
                                }

                                if (!tempPositions.isEmpty()) {
                                    for (Position p : tempPositions) {
                                        inactivePositions.addFirst(p);
                                    }
                                    activePositions.removeAll(tempPositions);
                                    tempPositions.clear();
                                }
                            }
                            case MAX_NUM_ALGO_ORDERS ->
                                    System.out.println(currentEvent.getId() + "Local Error - maximum number of algo orders action rejection - should have been tested during order creation.");
                            case INVALID_ORDER_STATE ->
                                    System.out.println(currentEvent.getId() + "Local Error - invalid order state action rejection");
                        }
                    } // "Done"
                    case ACTION_REJECTED -> { // Cancel order or repay funds - invalid order state (non-existent order), or general repay rejection reasons
                        switch (order.getRejectionReason()) {
                            case INSUFFICIENT_FUNDS -> {

                                // Insufficient funds to repay a loan means we took the borrowed funds and executed a trade with them.
                                // Yes I can create a map of last order actions, or I can hope this is the only case I'll need it for.

                                for (Position position : activePositions) {
                                    if (position.getEntryOrder().getId() == order.getId()) {
                                        if (position.isRepayRequestSent() && !position.isRepaid()) {
                                            if (!illegalExecutionCorrectionActionsList.isEmpty()) {
                                                addToDelayedActionMap(illegalExecutionCorrectionActionsList, null, order, OrderAction.REPAY_FUNDS);

                                            } else if (!order.isRepayReattempted()) {

                                                order.setRepayReattempted(true);

                                                scheduler.addEvent(new Event(
                                                        currentEvent.getDelayedTimestamp(),
                                                        EventDestination.EXCHANGE,
                                                        OrderAction.REPAY_FUNDS,
                                                        order.clone()
                                                ));
                                            } else {
                                                System.out.println("Local Error - action rejected due to insufficient funds but there is no illegal execution and repay is reattempted.");
                                            }

                                            break;
                                        }
                                    }
                                }
                            }

                            case INVALID_ORDER_STATE -> {
                                // order.borrowAmount == 0 or stop loss limit order triggered after sending the cancel request, and we had enough funds to execute it.
                                // Now we must buy back on market.

                                //FIXME: BUT WE'RE GETTING A LOT OF IT WHAT THE FUCK (not anymore but don't know if this works.)
                                //Todo: Don't know if this is necessary.

                                ArrayList<Position> tempPositions = new ArrayList<>();

                                boolean isInActivePositions = false;
                                boolean isInInactivePositions = false;

                                for (Position position : activePositions) {
                                    if (position.getStopOrder().getId() == order.getId()) {
                                        isInActivePositions = true;

                                        Order stopOrder = order.clone();

                                        if (!position.getStopOrder().getStatus().equals(OrderStatus.REJECTED)) {
                                            stopOrder.setDirection(order.getDirection().equals(OrderSide.BUY) ? OrderSide.SELL : OrderSide.BUY);
                                            stopOrder.setType(OrderType.MARKET);

                                            scheduler.addEvent(new Event(
                                                    currentEvent.getDelayedTimestamp(),
                                                    EventDestination.EXCHANGE,
                                                    OrderAction.CREATE_ORDER,
                                                    stopOrder.clone()
                                            ));

                                            position.setStopOrder(stopOrder.clone());
                                        } else if (position.getCloseOrder() != null && position.getCloseOrder().getStatus().equals(OrderStatus.FILLED)) {
                                            position.setGroup(PositionGroup.CANCELLED);
                                            tempPositions.add(position);
                                        }

                                        break;
                                    }
                                }

                                if (!isInActivePositions) {
                                    for (int i = inactivePositions.size() - 1; i >= 0; i--) {
                                        Position position = inactivePositions.get(i);

                                        if (position.getStopOrder().getId() == order.getId()) {
                                            isInInactivePositions = true;

                                            Order stopOrder = order.clone();

                                            if (!position.getStopOrder().getStatus().equals(OrderStatus.REJECTED)) {
                                                stopOrder.setDirection(order.getDirection().equals(OrderSide.BUY) ? OrderSide.SELL : OrderSide.BUY);
                                                stopOrder.setType(OrderType.MARKET);

                                                scheduler.addEvent(new Event(
                                                        currentEvent.getDelayedTimestamp(),
                                                        EventDestination.EXCHANGE,
                                                        OrderAction.CREATE_ORDER,
                                                        stopOrder.clone()
                                                ));

                                                position.setStopOrder(stopOrder.clone());
                                            } else if (position.getCloseOrder() != null && position.getCloseOrder().getStatus().equals(OrderStatus.FILLED)) {
                                                position.setGroup(PositionGroup.CANCELLED);
                                                tempPositions.add(position);
                                            }

                                            break;
                                        }
                                    }
                                }

                                if (!isInActivePositions && !isInInactivePositions) {
                                    System.out.println("???????????????????????????");
                                }

                                if (!tempPositions.isEmpty()) {
                                    for (Position p : tempPositions) {
                                        inactivePositions.addFirst(p);
                                    }
                                    activePositions.removeAll(tempPositions);
                                }

                            }
                        }
                    } // DONE
                    case FUNDS_REPAID -> {
                        ArrayList<Position> tempPositions = new ArrayList<>();
                        loop: for (Position position : activePositions) {
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
                                            checkFuckedness(position);
                                            if (position.getId() == marginCallPositionId) {
                                                marginCallPositionCloseRequestSent = false;
                                            }
                                        } else {
                                            position.setGroup(PositionGroup.DISCARDED);
                                        }

                                        position.setRepaid(true);

                                        executeDelayedActionsFromMap(waitForRepayToRepeatActionsList, order.getId());

                                        tempPositions.add(position);

                                        break loop;
                                    }
                                }
                            }
                        }

                        if (order.getId() == BTCInterestRepayOrderId) { // If it's done for repaying purposes
                            repayBTCRequestSent = false;
                            BTCInterestRepayOrderId = -1;
                        } else if (order.getId() == USDTInterestRepayOrderId) {
                            repayUSDTRequestSent = false;
                            USDTInterestRepayOrderId = -1;
                        }

                        for (Position p : tempPositions) {
                            inactivePositions.addFirst(p);
                        }
                        activePositions.removeAll(tempPositions);

                    } // DONE
                    case ORDER_CANCELLED -> { //DONE?

                        if (order.getType().equals(OrderType.MARKET)) {
                            System.out.println(currentEvent.getId() + "Local Error - Cancelling a market order.");
                        }

                        ArrayList<Position> tempPositions = new ArrayList<>();

                        boolean isInActivePositions = false;
                        boolean isInInactivePositions = false;

                        loop: for (Position position : activePositions) {
                            switch (position.getGroup()) {
                                case PENDING, NEW -> {
                                    if (position.getEntryOrder().getId() == order.getId()) {
                                        isInActivePositions = true;

                                        //Repay funds if stop order is cancelled - IF it's not automatic repay at cancel.
                                        //FIXME: Currently only supporting auto repay on cancel. What happens if we don't borrow here? We have that check in binance therefore position will only be cancelled
                                        if (position.getStopOrder().getStatus().equals(OrderStatus.CANCELED)) {
                                            position.setGroup(PositionGroup.CANCELLED);
                                            tempPositions.add(position);
                                        } else if (position.getStopOrder().getType().equals(OrderType.LIMIT)) {
                                            //Send request for stoploss order cancellation? Or will that be done manually for now let's do it automatic.
                                            scheduler.addEvent(new Event(
                                                    currentEvent.getDelayedTimestamp(),
                                                    EventDestination.EXCHANGE,
                                                    OrderAction.CANCEL_ORDER,
                                                    position.getStopOrder().clone()));
                                        }

                                        illegalExecutionRoutine(order, position);

                                        position.setEntryOrder(order.clone());

                                        break loop;

                                    } else if (position.getStopOrder().getId() == order.getId()) {
                                        isInActivePositions = true;

                                        //Repay funds if entry order is cancelled - IF it's not automatic repay at cancel
                                        if (position.getEntryOrder().getStatus().equals(OrderStatus.CANCELED)) {
                                            position.setGroup(PositionGroup.CANCELLED);
                                            tempPositions.add(position);

                                            //illegalExecutionRoutine(order, position); // In case of canceling

                                        } else if (position.getEntryOrder().getStatus().equals(OrderStatus.REJECTED)) { //If the entry order got rejected?
                                            //Funds weren't borrowed, discard the position
                                            position.setGroup(PositionGroup.DISCARDED);
                                            tempPositions.add(position);
                                        }

                                        position.setStopOrder(order.clone());

                                        break loop;
                                    }
                                }
                                case FILLED -> {
                                    if (position.getEntryOrder().getId() == order.getId()) {
                                        isInActivePositions = true;
                                        System.out.println(currentEvent.getId() + "Local Error - cancelling filled position's entry order");
                                        position.setEntryOrder(order.clone());

                                        break loop;
                                    } else if (position.getStopOrder().getId() == order.getId()) {
                                        isInActivePositions = true;
                                        //Most likely a breakeven / move stoploss request
                                        position.setStopOrder(order.clone());
                                        position.setActiveStopLoss(false); //Temporarily

                                        break loop;
                                    }
                                }
                                case CLOSED -> { //Cancelling stoploss order after closing the order
                                    if (position.getStopOrder().getId() == order.getId()) {
                                        isInActivePositions = true;
                                        position.setStopOrder(order.clone());
                                        tempPositions.add(position);

                                        break loop;
                                    }
                                }
                            }
                        }

                        if (!isInActivePositions) {
                            for (Position position : inactivePositions) {
                                if (position.getStopOrder().getId() == order.getId()) {
                                    isInInactivePositions = true;
                                    position.setStopOrder(order.clone());
                                }
                            }
                        }

                        if (!isInActivePositions && !isInInactivePositions) {
                            System.out.println("Order cancelled but it's not in active nor inactive positions");
                        }

                        for (Position p : tempPositions) {
                            inactivePositions.addFirst(p);
                        }
                        activePositions.removeAll(tempPositions);

                    } // DONE?
                    case ORDER_CREATED -> { // DONE
                        loop: for (Position position : activePositions) {
                            switch (position.getGroup()) {
                                case PENDING -> {
                                    if (position.getEntryOrder().getId() == order.getId()) {

                                        position.setEntryOrder(order.clone());
                                        position.setGroup(PositionGroup.NEW);

                                        if (order.getType().equals(OrderType.LIMIT)) {
                                            position.setMarginBuyBorrowAmount(order.getMarginBuyBorrowAmount());

                                            illegalExecutionRoutine(order, position);
                                        }

                                        break loop;

                                    } else if (position.getStopOrder().getId() == order.getId()) {
                                        position.setStopOrder(order.clone());
                                        System.out.println(currentEvent.getId() + "Local Error - created a stop order for a pending position before it became new");

                                        break loop;
                                    }
                                }
                                case NEW -> {
                                    if (position.getEntryOrder().getId() == order.getId()) {
                                        position.setEntryOrder(order.clone());
                                        System.out.println(currentEvent.getId() + "Local Error - created a new entry order for a position of status new?");

                                        break loop;
                                    } else if (position.getStopOrder().getId() == order.getId()) {
                                        position.setStopOrder(order.clone());
                                        position.setActiveStopLoss(true);

                                        break loop;
                                    }
                                }
                                case FILLED -> {
                                    if (position.getStopOrder().getId() == order.getId()) {
                                        position.setStopOrder(order.clone());
                                        position.setActiveStopLoss(true);

                                        break loop;
                                    } else if (position.getCloseOrder() != null && position.getCloseOrder().getId() == order.getId()) {
                                        // If we made market + stop request and stop wasn't placed before market order fill but didn't get placed because it would trigger immediately,
                                        // We get a would trigger immediately action rejection for a new position. We send a market close request because the order will be filled in the next cycle
                                        // Here we handle the response from the cancel order request when it's created.
                                        position.setCloseOrder(order.clone());
                                        // Handle order filling and repaying in user data stream

                                        break loop;
                                    }
                                }
                            }
                        }
                    } // DONE
                }

                //increaseInterest();
            }
            default ->
                    System.out.println(currentEvent.getId() + "Unexplained event found its way into our local strategy function.");
        }
    }

    private void checkFuckedness(Position position) {
        if (position.getCloseOrder() != null && position.getEntryOrder().getFillPrice() != null && position.getCloseOrder().getFillPrice() != null) {
            boolean fucked = false;

            if (position.getDirection().equals(OrderSide.BUY)) {
                if (position.isFilled()) {
                    if (position.getStopOrder().getOpenPrice().compareTo(position.getEntryOrder().getFillPrice()) >= 0) {
                        if (position.getProfit().doubleValue() > 0) {
                            fucked = true;
                        }
                    }
                }
            } else {
                if (position.isFilled()) {
                    if (position.getStopOrder().getOpenPrice().compareTo(position.getEntryOrder().getFillPrice()) <= 0) {
                        if (position.getProfit().doubleValue() > 0) {
                            fucked = true;
                        }
                    }
                }
            }

            if (fucked) {
                System.out.println("Position " + position.getId() + " is fucked. Resulted in $" + position.getProfit() + ", but should have been -$" + (position.getProfit().doubleValue() / position.getRR()));
            }
        }
    }

    private void delayedRepayOrderRejectionReasonMethod(Order order, Position position) {
        boolean remainingUnpaidPositions = false;

        for (Position pos : activePositions) {
            if (!pos.equals(position)) {
                if (!position.isRepaid()) {
                    remainingUnpaidPositions = true;
                    break;
                }
            }
        }

        if (!remainingUnpaidPositions) {
            if (position.getEntryOrder().getStatus().equals(OrderStatus.FILLED)) {
                closePosition(position);
            } else {
                cancelPosition(position);
            }
        } else {
            addToDelayedActionMap(waitForRepayToRepeatActionsList, null, order, OrderAction.CREATE_ORDER);
        }
    }

    private void addToDelayedActionMap(TreeMap<Long, ArrayList<Map<Order, OrderAction>>> map, Long orderId, Order order, OrderAction action) {
        if (orderId != null && map.get(orderId) == null) {
            if (map.lastEntry() != null && map.lastEntry().getValue().isEmpty()) {
                map.remove(map.lastKey());
            }
            map.put(orderId, new ArrayList<>());
            return;
        }

        if (map.isEmpty()) {
            System.out.println("Error - adding action events to a map before identifying the first key");
            return;
        }

        ArrayList<Map<Order, OrderAction>> subMap = map.lastEntry().getValue();
        subMap.add(new SingletonMap<>(order.clone(), action));
        map.put(map.lastKey(), new ArrayList<>(subMap));
    }

    private void executeDelayedActionsFromMap(TreeMap<Long, ArrayList<Map<Order, OrderAction>>> map, long orderId) {

        if (map.containsKey(orderId)) {
            ArrayList<Map<Order, OrderAction>> list = map.get(orderId);
            for (Map<Order, OrderAction> orderAction : list) {

                Map.Entry<Order, OrderAction> entry = orderAction.entrySet().iterator().next();

                scheduler.addEvent(new Event(
                        currentEvent.getDelayedTimestamp(),
                        EventDestination.EXCHANGE,
                        entry.getValue(),
                        entry.getKey().clone()
                ));

                System.out.println("Executed delayed action: " + entry.getValue());
            }
        }

        map.remove(orderId);
    }

    /**
     * FIXME: If the first order is a long it
     *
     * @param order
     * @param position
     */
    private void illegalExecutionRoutine(Order order, Position position) {

        if (illegalExecutionCorrectionActionsList.get(order.getId()) == null) {
            checkIllegalExecution(order, position);
        } else {
            if (order.getMarginBuyBorrowAmount().doubleValue() <= 0e-7 && order.isAutomaticBorrow()) {
                System.out.println("Local Error - illegalLackOfBorrowCorrectionInProgress true while another entry order is filled. 2 potential events collide. Probably the rebuy one.");
            } else {
                executeDelayedActionsFromMap(illegalExecutionCorrectionActionsList, order.getId());
            }
//            if (checkIllegalExecution(order, position)) {
//                System.out.println("Local Error - illegalLackOfBorrowCorrectionInProgress true while another entry order is filled. 2 potential events collide. Probably the rebuy one.");
//            } else {
//                // Position is closed, we can reattempt the request we made unsuccessfully before.
//
//                executeDelayedActionsFromMap(illegalExecutionCorrectionActionsList, order.getId());
//            }
        }
    }

    /**
     * Checks for illegal executions and sends a disassociated order to get them back, either by selling or buying the underlying asset
     * <br><br>
     * 'Illegal execution' refers to an execution of an order without borrowing funds.
     * <br><br>
     * When using Automatic Margin Borrowing, Binance will bypass borrowing if there's enough free asset to cover the entire position.
     * <br>In that case we must buy back the reduced asset immediately so other orders can properly return them upon cancelling.
     *
     * <br><br>
     * TODO: If we lack the funds both return funds and keep the new position, we'll discard the position
     * <br>
     * TODO: There might be a better way of handling this but this seems reasonable to keep all things in order.
     *
     * @param order the order we're testing for
     * @return true if order executed illegally (executed without borrowing), false otherwise.
     */
    private boolean checkIllegalExecution(Order order, Position position) {

        // If the first order is a long it's okay if it executes without borrowing
        if (order.getId() == 0 || activePositions.size() == 1) {
            return false;
        }

        // Close the position

        //IGNORE:
        //FIXME: We don't know the assets state on limit orders because we don't get the User Data yet, therefore we need to execute this on the newest user data, not the action response
        // Market orders are checked during filling
        // If we notice the flag on an action response, execute this on next user assets stream
        // This will cancel the...

        if (order.getPositionId() == 8224 || order.getPositionId() == 8225 || order.getPositionId() == 8229) {
            boolean point = true;
        }

        if (order.isAutomaticBorrow() && order.getMarginBuyBorrowAmount().doubleValue() <= 0e-7) {

            // Just close the position for now
            if ((order.getType().equals(OrderType.MARKET) || order.getStatus().equals(OrderStatus.FILLED))) {
                if (closePosition(position)) {
                    if (!position.isClosedBeforeStopLoss()) {
                        System.out.println("Local Error - Closing the position returned true meaning we can't close it that's just fucked.");
                    } else {
                        System.out.println("huh...");
                    }
                } else {
                    addToDelayedActionMap(illegalExecutionCorrectionActionsList, position.getCloseOrder().getId(), null, null);
                }
            } else if (order.getType().equals(OrderType.LIMIT)) {
                if (cancelPosition(position)) {
                    System.out.println("Local Error - Cancelling the position returned true meaning we can't close it that's just fucked.");
                } else {
                    addToDelayedActionMap(illegalExecutionCorrectionActionsList, position.getEntryOrder().getId(), null, null);
                }
            }

            return true;

//            BigDecimal doubleOrderSize = order.getSize().multiply(BigDecimal.TWO);
//            if ((!order.getDirection().equals(OrderSide.BUY) || doubleOrderSize.multiply(transaction.price()).compareTo(userAssets.getFreeUSDT()) > 0)
//                    && (!order.getDirection().equals(OrderSide.SELL) || doubleOrderSize.compareTo(userAssets.getFreeBTC()) > 0)) {
//                // We have to close the position first, no repaying necessary because we didn't borrow.
//
//                //FIXME: Currently we hope to God this works with no issue. Exchange state can change during execution, so we would have to handle these cases separately.
//                if(closePosition(position)){
//                    System.out.println("Local Error - Closing the position returned true meaning we can't close it that's just fucked.");
//                }
//            }
//            // Buy back the asset
//            Order buyBackOrder = new Order(
//                    transaction.price(),
//                    order.getDirection().equals(OrderSide.BUY) ? OrderSide.SELL : OrderSide.BUY,
//                    false,
//                    false,
//                    order.getSize(),
//                    OrderType.MARKET,
//                    BigDecimal.ZERO,
//                    BigDecimal.ZERO,
//                    currentEvent.getDelayedTimestamp(),
//                    OrderPurpose.BUYBACK,
//                    -1
//            );
//
//            scheduler.addEvent(new Event(
//                    currentEvent.getDelayedTimestamp(),
//                    EventDestination.EXCHANGE,
//                    OrderAction.CREATE_ORDER,
//                    buyBackOrder.clone()
//            ));

            // After receiving the disassociated order fill response, we must repeat the previously failed action (probably repaying)
        }
        return false;
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

                if (order.getMarginBuyBorrowAmount().doubleValue() > 0e-7) {

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
            candleCloseList.add(new SingletonMap<>(candle.timestamp(), candle.close()));
            newCandle(lastCandle);
        }

        priceUpdate(transaction);
    }

    public ArrayList<SingletonMap<Long, Double>> getCandleCloseList(){
        return this.candleCloseList;
    }

    public UserAssets getUserAssets() {
        return this.userAssets;
    }

    public LinkedList<Position> getInactivePositions() {
        return this.inactivePositions;
    }

    public ArrayList<Position> getActivePositions() {
        return this.activePositions;
    }
}
