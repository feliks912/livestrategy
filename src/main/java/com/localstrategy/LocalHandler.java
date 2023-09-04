package com.localstrategy;

import com.localstrategy.util.enums.*;
import com.localstrategy.util.helper.CandleConstructor;
import com.localstrategy.util.helper.EventScheduler;
import com.localstrategy.util.helper.OrderRequest;
import com.localstrategy.util.helper.TierManager;
import com.localstrategy.util.types.*;
import org.apache.commons.collections4.map.SingletonMap;

import java.math.BigDecimal;
import java.util.*;

public class LocalHandler {

    private static final int CANDLE_VOLUME = Params.volume;
    private static final double RISK_PCT = Params.risk;

    //In relation to the difference between our entry and stop-loss price difference, how much in percentage of slippage are we ready to accept (total, not the average fill price)
    private static final int SLIPPAGE_PCT = Params.perc;

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

    private final Map<Order, OrderAction> repeatActionList = new HashMap<>();

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
        System.out.println("Local handler settings: candle volume " + CANDLE_VOLUME + ", risk " + RISK_PCT + "%, slippage pct: " + SLIPPAGE_PCT + "%");

        strategy = new Strategy2(this, candles, activePositions, inactivePositions);
    }

    private void priceUpdate(SingleTransaction transaction) {
        strategy.priceUpdate(transaction);
    }

    private void newCandle(Candle lastCandle) {
        strategy.candleUpdate(lastCandle);
    }

    public Position executeMarketOrder(double stopPrice, boolean adjustStop) {
        Position newMarketPosition = orderRequest.newMarketPosition(transaction, BigDecimal.valueOf(stopPrice), adjustStop);

        if (newMarketPosition != null) {
            sendAction(OrderAction.CREATE_ORDER, newMarketPosition.getEntryOrder());
        }

        return newMarketPosition;
    }

    public Position executeLimitOrder(double entryPrice, double stopPrice, boolean adjustStop) {
        Position newLimitPosition = orderRequest.newLimitPosition(BigDecimal.valueOf(entryPrice), BigDecimal.valueOf(stopPrice), transaction, adjustStop);

        if (newLimitPosition != null) {
            sendAction(OrderAction.CREATE_ORDER, newLimitPosition.getEntryOrder());
        }

        return newLimitPosition;
    }

    public boolean activateStopLoss(Position position) {
        if (position == null || position.isStopLossRequestSent() || position.isStopLossActive()) {
            return true;
        }

        sendAction(OrderAction.CREATE_ORDER, position.getStopOrder());

        position.setStopLossRequestSent(true);

        return false;
    }

    public boolean updateStopLoss(double newStopPrice, Position position) {
        if (position == null || !position.isStopLossActive() || !position.getGroup().equals(PositionGroup.FILLED)) { //Must be a better way
            return true;
        }

        sendAction(OrderAction.CANCEL_ORDER, position.getStopOrder());

        position.getStopOrder().setOpenPrice(BigDecimal.valueOf(newStopPrice));
        position.setOpenTimestamp(currentEvent.getDelayedTimestamp());

        sendAction(OrderAction.CREATE_ORDER, position.getStopOrder());

        position.setBreakEvenStatus(true);

        return false;
    }

    public boolean cancelPosition(Position position) {
        if (position == null || !position.getGroup().equals(PositionGroup.NEW)) {
            return true;
        }

        sendAction(OrderAction.CANCEL_ORDER, position.getEntryOrder());

        return false;
    }

    public boolean closePosition(Position position) {

        if (position == null || position.isRepayRequestSent()) {
            return true;
        }

        if (position.isClosedBeforeStopLoss()
                || !position.getGroup().equals(PositionGroup.FILLED)
                || position.getCloseOrder() != null) {
            return true;
        }

        if(position.getId() == 107){
            boolean stop = true;
        }

        sendAction(OrderAction.CREATE_ORDER, position.createCloseOrder(transaction));

        return false;
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
                orderRequest.setUserAssets(userAssets);

                if (userAssets.getMarginLevel() <= 1.05) {
                    System.out.println(currentEvent.getId() + "LocalHandler Error - Liquidation. Should have been called in exchange first.");
                } else if (userAssets.getMarginLevel() <= 1.1 && !marginCallPositionCloseRequestSent) { //TODO: Handle margin level report - close positions and repay funds

                    Position maximumBorrowPosition = null;
                    double maximumNotionalBorrowValue = 0;

                    for (Position position : activePositions) {
                        if (position.getGroup().equals(PositionGroup.FILLED)) {
                            if (position.getDirection().equals(OrderSide.BUY) && position.getMarginBuyBorrowAmount().doubleValue() > maximumNotionalBorrowValue) {
                                maximumNotionalBorrowValue = position.getMarginBuyBorrowAmount().doubleValue();
                                maximumBorrowPosition = position;
                            } else if (position.getDirection().equals(OrderSide.SELL)
                                    && position.getMarginBuyBorrowAmount().multiply(BigDecimal.valueOf(transaction.price())).doubleValue() > maximumNotionalBorrowValue) {
                                maximumNotionalBorrowValue = position.getMarginBuyBorrowAmount().multiply(BigDecimal.valueOf(transaction.price())).doubleValue();
                                maximumBorrowPosition = position;
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

                for(Order order : event.getUserDataStream().updatedOrders()){

                    Position pos = findPosition(order);

                    if(pos == null){
                        return;
                    }

                    switch(order.getStatus()){
                        case FILLED -> {
                            if(order.getType().equals(OrderType.LIMIT)){
                                switch(order.getPurpose()){
                                    case ENTRY -> {
                                        pos.setMarginBuyBorrowAmount(order.getMarginBuyBorrowAmount());
                                        pos.setGroup(PositionGroup.FILLED);
                                        pos.setFilled(true);
                                    }
                                    case STOP -> {
                                        if(pos.getEntryOrder().isAutomaticBorrow() && pos.getEntryOrder().getMarginBuyBorrowAmount().compareTo(BigDecimal.ZERO) != 0){
                                            sendAction(OrderAction.REPAY_FUNDS, pos.getEntryOrder());
                                        } else if(!pos.getEntryOrder().getStatus().equals(OrderStatus.FILLED)) {
                                            System.out.println("Local Error - stop filled before entry");
                                        } else if(!pos.isClosed()) {
                                            pos.setGroup(PositionGroup.CLOSED);
                                            pos.closePosition(currentEvent.getDelayedTimestamp());
                                            activePositions.remove(pos);
                                            inactivePositions.addFirst(pos);
                                        }
                                    }
                                    case CLOSE -> {
                                        if(pos.getEntryOrder().isAutomaticBorrow() && pos.getEntryOrder().getMarginBuyBorrowAmount().compareTo(BigDecimal.ZERO) != 0){
                                            sendAction(OrderAction.REPAY_FUNDS, pos.getEntryOrder());
                                        } else if(!pos.isClosed()) {
                                            pos.setGroup(PositionGroup.CLOSED);
                                            pos.closePosition(currentEvent.getDelayedTimestamp());
                                            activePositions.remove(pos);
                                            inactivePositions.addFirst(pos);
                                        }
                                    }
                                }
                            }
                        }
                        case REJECTED -> {
                            switch(order.getRejectionReason()){
                                case INSUFFICIENT_FUNDS -> {
                                    switch(order.getPurpose()){
                                        case CLOSE -> {
                                            if(!pos.getStopOrder().getStatus().equals(OrderStatus.FILLED)){
                                                System.out.println("Local Error - close order rejected due to insufficient funds");
                                            }
                                        }
                                        case STOP -> {
                                            if(pos.getCloseOrder() == null){
                                                System.out.println("Local Error - stop order rejected due to insufficient funds");
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            case ACTION_RESPONSE -> {

                Order order = event.getOrder();
                Position pos = findPosition(order);

                if(pos == null){
                    return;
                }

                switch (event.getActionResponse()){
                    case ORDER_FILLED -> {
                        switch(order.getPurpose()){
                            case ENTRY -> {
                                pos.setMarginBuyBorrowAmount(order.getMarginBuyBorrowAmount());
                                pos.setGroup(PositionGroup.FILLED);
                                pos.setFillPrice(order.getFillPrice());
                            }
                            case STOP -> {
                                if(!pos.isRepaid() && pos.getEntryOrder().isAutomaticBorrow() && pos.getEntryOrder().getMarginBuyBorrowAmount().compareTo(BigDecimal.ZERO) != 0){
                                    sendAction(OrderAction.REPAY_FUNDS, pos.getEntryOrder());
                                } else {
                                    if(!pos.getEntryOrder().getStatus().equals(OrderStatus.FILLED)){
                                        sendAction(OrderAction.CANCEL_ORDER, pos.getEntryOrder());
                                    }

                                    pos.closePosition(currentEvent.getDelayedTimestamp());
                                    pos.setGroup(PositionGroup.CLOSED);
                                    activePositions.remove(pos);
                                    inactivePositions.addFirst(pos);
                                }
                            }
                            case CLOSE -> {

                                if(!pos.getStopOrder().getStatus().equals(OrderStatus.FILLED)) {
                                    sendAction(OrderAction.CANCEL_ORDER, pos.getStopOrder());

                                    if(!pos.isRepaid() && pos.getEntryOrder().isAutomaticBorrow() && pos.getEntryOrder().getMarginBuyBorrowAmount().compareTo(BigDecimal.ZERO) != 0){
                                        sendAction(OrderAction.REPAY_FUNDS, pos.getEntryOrder());
                                    } else {
                                        pos.setGroup(PositionGroup.CLOSED);
                                        pos.closePosition(currentEvent.getDelayedTimestamp());
                                        activePositions.remove(pos);
                                        inactivePositions.addFirst(pos);
                                    }

                                } else {
                                    //Stop order triggered after closing the position
                                    Order stopOrder = pos.getStopOrder();
                                    stopOrder.setType(OrderType.MARKET);
                                    stopOrder.setStatus(OrderStatus.NEW);
                                    stopOrder.setDirection(stopOrder.getDirection().equals(OrderSide.BUY) ? OrderSide.SELL : OrderSide.BUY);

                                    sendAction(OrderAction.CREATE_ORDER, stopOrder);
                                }

                                if(!pos.getEntryOrder().getStatus().equals(OrderStatus.FILLED)){
                                    sendAction(OrderAction.CANCEL_ORDER, pos.getEntryOrder());
                                }
                            }
                        }
                    }
                    case ORDER_CREATED -> {

                        if(order.getPurpose().equals(OrderPurpose.STOP)){
                            pos.setStopLossActive(true);
                        }

                        if(pos.getEntryOrder().isAutomaticBorrow() && order.getMarginBuyBorrowAmount().doubleValue() == 0){
                            //System.out.println("Local Error - illegal borrow");
                        }
                    }
                    case ACTION_REJECTED -> {
                        switch(order.getRejectionReason()){
                            case INVALID_ORDER_STATE -> {
                                //order filled during cancel
                                if(order.getStatus().equals(OrderStatus.FILLED)){
                                    order.setType(OrderType.MARKET);
                                    order.setStatus(OrderStatus.NEW);
                                    order.setDirection(order.getDirection().equals(OrderSide.BUY) ? OrderSide.SELL : OrderSide.BUY);
                                    order.setAppropriateUnitPositionValue(order.getSize()); //FIXME: This necessary?

                                    sendAction(OrderAction.CREATE_ORDER, order);

                                    System.out.println("Local Error - order filled during cancel request");
                                }
                            }
                            case INSUFFICIENT_FUNDS -> {
                                System.out.println("Local error - ACTION rejected because insufficient FUNDS");
                            }
                            case INSUFFICIENT_MARGIN -> {
                                System.out.println("Local error - BORROW ACTION rejected because insufficient MARGIN");
                            }
                        }
                    }
                    case ORDER_REJECTED -> {
                        switch(order.getRejectionReason()){
                            case WOULD_TRIGGER_IMMEDIATELY -> {
                                switch(order.getPurpose()) {
                                    case STOP -> {
                                        if (pos.getEntryOrder().getStatus().equals(OrderStatus.FILLED)) {

                                            order.setStatus(OrderStatus.NEW);
                                            order.setType(OrderType.MARKET);
                                            order.setRejectionReason(null);

                                            pos.setClosedBeforeStopLoss(true);

                                            sendAction(OrderAction.CREATE_ORDER, order);
                                        }
                                    }
                                    case ENTRY -> {
                                        if(pos.isStopLossRequestSent()){
                                            sendAction(OrderAction.CANCEL_ORDER, pos.getStopOrder());
                                        }
                                    }
                                    case CLOSE -> {
                                        order.setStatus(OrderStatus.NEW);
                                        order.setType(OrderType.MARKET);
                                        order.setRejectionReason(null);

                                        sendAction(OrderAction.CREATE_ORDER, order);
                                    }
                                }
                            }
                            case INSUFFICIENT_FUNDS -> {
                                switch(order.getPurpose()){

                                    case CLOSE -> {
                                        if(!pos.getStopOrder().getStatus().equals(OrderStatus.FILLED)){
                                            System.out.println("Local Error - close order rejected due to insufficient funds");
                                        }
                                    }
                                    case STOP -> {
                                        if(pos.getCloseOrder() == null){
                                            System.out.println("Local Error - stop order rejected due to insufficient funds");
                                        }
                                    }
                                }
                            }
                            case INSUFFICIENT_MARGIN -> {
                                System.out.println("Local Error - BORROW rejected because insufficient MARGIN in market position");
                            }
                            case EXCESS_BORROW -> {
                                System.out.println("Local Error - BORROW rejected due to EXCESS BORROW");
                            }
                            case MAX_NUM_ALGO_ORDERS -> {
                                System.out.println("Local Error - max num algo orders");
                            }
                        }
                    }
                    case ORDER_CANCELLED -> {
                        if(order.getPurpose().equals(OrderPurpose.ENTRY) && !pos.getEntryOrder().isAutoRepayAtCancel()){
                            sendAction(OrderAction.REPAY_FUNDS, order);
                        } else if (pos.getEntryOrder().getStatus().equals(OrderStatus.CANCELED)
                                && pos.getStopOrder().getStatus().equals(OrderStatus.CANCELED)) {
                            pos.setGroup(PositionGroup.CANCELLED);
                            activePositions.remove(pos);
                            inactivePositions.addFirst(pos);
                        } else if(order.getPurpose().equals(OrderPurpose.STOP)){
                            if(pos.getEntryOrder().getStatus().equals(OrderStatus.REJECTED)){
                                pos.setGroup(PositionGroup.DISCARDED);
                                activePositions.remove(pos);
                                inactivePositions.addFirst(pos);
                            } else {
                                pos.setStopLossActive(false);
                            }
                        }
                    }
                    case FUNDS_REPAID -> {

                        pos.setRepaid(true);

                        if(order.getStatus().equals(OrderStatus.CANCELED)){
                            pos.setGroup(PositionGroup.CANCELLED);
                        } else {
                            pos.closePosition(currentEvent.getDelayedTimestamp());
                        }

                        ArrayList<Order> orderList = new ArrayList<>();
                        for(Map.Entry<Order, OrderAction> entry : repeatActionList.entrySet()){
                            if(entry.getValue().equals(OrderAction.CREATE_ORDER)){
                                sendAction(entry.getValue(), entry.getKey());
                                orderList.add(entry.getKey());
                            }
                        }
                        for(Order o : orderList){
                            repeatActionList.remove(o);
                        }

                        activePositions.remove(pos);
                        inactivePositions.addFirst(pos);
                    }
                }
            }
            default -> System.out.println(currentEvent.getId() + "Unexplained event found its way into our local strategy function.");
        }
    }

    private Position findPosition(Order order){

        long orderId = order.getId();

        for(Position pos : activePositions){
            if(pos.getEntryOrder().getId() == orderId){
                pos.setEntryOrder(order.clone());
                return pos;
            } else if(pos.getStopOrder().getId() == orderId){
                pos.setStopOrder(order.clone());
                return pos;
            } else if(pos.getCloseOrder() != null && pos.getCloseOrder().getId() == orderId){
                pos.setCloseOrder(order.clone());
                return pos;
            }
        }

        for(Position pos : inactivePositions){
            if(pos.getEntryOrder().getId() == orderId){
                pos.setEntryOrder(order.clone());
                return pos;
            } else if(pos.getStopOrder().getId() == orderId){
                pos.setStopOrder(order.clone());
                return pos;
            } else if(pos.getCloseOrder() != null && pos.getCloseOrder().getId() == orderId){
                pos.setCloseOrder(order.clone());
                return pos;
            }
        }

        System.out.println("Local Error - Order not present in positions");
        return null;
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

    private void sendAction(OrderAction action, Order order){
        scheduler.addEvent(new Event(currentEvent.getDelayedTimestamp(), EventDestination.EXCHANGE,
                action,
                order.clone()
        ));
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
