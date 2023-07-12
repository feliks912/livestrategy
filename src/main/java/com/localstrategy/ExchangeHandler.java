package com.localstrategy;

import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

//TODO: Add locking BTC on short and general transfer of borrowed funds into free categories
//TODO: Add proper AssetHandler initialization
//TODO: Check userAssetsUpdate and positionsUpdate
//TODO: Check if we're properly clearing arrays
//TODO: Currently I'm handling margin incorrently. During order creation I bind it to the order and calculate it during order execution. Instead what I should be doing is using margin only to borrow funds, and then lock the entire position value during opening.

//FIXME: Test userDataStream latency. It shouldn't be (lol shouldn't...) much different than order execution reponse latency

//Note - we don't use borrowed funds when calculating or positons sizes. That's important during live trading. handle it properly that's why I made the request to the Binance API team

//TODO: Spin a riskManager instance here to double check if the requested leverage is what it's supposed to be. Also edit the logic

//TODO: Parse strategy as parameter from App instead of hardcoding it into TempStrategyExecutor, unless TempStrategyExecutor is the strategy (later)
//TODO: Implement order rejections (later)
//TODO: Implement borrow requests (later)
//TODO: Add symbol rules (later)

public class ExchangeHandler {

    private int MAX_PROG_ORDERS = 5;

    private ArrayList<Position> unfilledPositions = new ArrayList<Position>();
    private ArrayList<Position> filledPositions = new ArrayList<Position>();
    private ArrayList<Position> closedPositions = new ArrayList<Position>();
    private ArrayList<Position> cancelledPositions = new ArrayList<Position>();

    private ArrayList<Map<RejectionReason, Position>> rejectedPositionsActions = new ArrayList<Map<RejectionReason, Position>>();


    private ArrayList<Position> tempPositionList = new ArrayList<Position>();

    private AssetHandler userAssets = new AssetHandler();
    private ExchangeLatencyHandler exchangeLatencyHandler = new ExchangeLatencyHandler();
    private ArrayList<AssetHandler> userAssetsList = new ArrayList<AssetHandler>();
    private OrderBookHandler orderBookHandler = new OrderBookHandler();
    private TempStrategyExecutor strategyExecutor;

    private SingleTransaction transaction;

    private long previousInterestTimestamp = 0;

    private boolean userAssetsUpdate = false;
    private boolean positionsUpdate = false;

    private int orderCount;
    private int runningOrderCounter = 0;
    private int winCounter = 0;
    private int lossCounter = 0;
    private int breakevenCounter = 0;

    public ExchangeHandler(Double initialUSDTPortfolio){

        this.strategyExecutor = new TempStrategyExecutor(this);

        this.userAssets.setFreeUSDT(initialUSDTPortfolio);
        this.userAssetsList.add(userAssets);
    }


    //FIXME: Currently I'm using the timestamp of the current transaction to define exchange time because they are almost equal to our local time. During walls that might present a problem if I don't add dynamic transaction latencies.
    public void newTransaction(SingleTransaction transaction, boolean isWall) {
        this.transaction = transaction;

        exchangeLatencyHandler.recalculateLatencies(transaction.getTimestamp());
        conditionallyAddInterest();

        checkStopLoss();
        checkFills();
        checkMarginLevel();
        
        handleUserRequest(exchangeLatencyHandler.getDelayedLocalPositionsUpdate(transaction.getTimestamp()));

        updateUserDataStream();

        strategyExecutor.onTransaction(transaction, isWall);
    }


    private void updateUserDataStream(){
        if(userAssetsUpdate || positionsUpdate || !rejectedPositionsActions.isEmpty()){
            exchangeLatencyHandler.addUserDataStream(
                new UserDataStream(
                    userAssets, 
                    filledPositions, 
                    unfilledPositions, 
                    closedPositions,
                    cancelledPositions,
                    rejectedPositionsActions),
                transaction.getTimestamp());

            rejectedPositionsActions.clear();

            userAssetsUpdate = false;
            positionsUpdate = false;
        }
    }

    private void handleUserRequest(Map<OrderAction, ArrayList<Position>> temp){

        if(temp == null){
            return;
        }

        for(Map.Entry<OrderAction, ArrayList<Position>> entry : temp.entrySet()){
            ArrayList<Position> positions = entry.getValue();

            switch(entry.getKey()){
                case CREATE_ORDER: //Thanks ChatGPT for refactoring
                    for(Position position : positions){
                        boolean isPositionInProcessingLists = unfilledPositions.contains(position) || 
                                                            filledPositions.contains(position) || 
                                                            closedPositions.contains(position) || 
                                                            cancelledPositions.contains(position);
                                                            
                        // Check if the position is not in any list
                        if(!isPositionInProcessingLists){
                            double fundsToBorrow = position.getBorrowedAmount();
                            double positionMargin = position.getMargin();
                            
                            // Check if the user has enough margin
                            if(userAssets.getFreeUSDT() < positionMargin){
                                rejectedPositionsActions.add(newRejection(RejectionReason.INSUFFICIENT_MARGIN, position));
                                continue;
                            }

                            boolean canProcessOrder = (position.getOrderType().equals(OrderType.MARKET)) || 
                                                    (position.getOrderType().equals(OrderType.LIMIT) && 
                                                    (filledPositions.size() + unfilledPositions.size() < MAX_PROG_ORDERS));

                            // Check if the order can be processed
                            if(!canProcessOrder){
                                rejectedPositionsActions.add(newRejection(RejectionReason.EXCESS_PROG_ORDERS, position));
                                continue;
                            }
                                
                            // Position can be processed
                            userAssets.setFreeUSDT(userAssets.getFreeUSDT() - positionMargin);
                            userAssets.setLockedUSDT(userAssets.getLockedUSDT() + positionMargin);
                            userAssets.setTotalUnpaidInterest(userAssets.getTotalUnpaidInterest() + position.getTotalUnpaidInterest());

                            if(position.getDirection().equals(OrderSide.BUY)){
                                userAssets.setTotalBorrowedUSDT(userAssets.getTotalBorrowedUSDT() + fundsToBorrow);
                            } else {
                                userAssets.setTotalBorrowedBTC(userAssets.getTotalBorrowedBTC() + fundsToBorrow);
                            }

                            userAssetsUpdate = true;
                            unfilledPositions.add(position);
                            positionsUpdate = true;
                        } 
                        // Check if the position is a limit order and it would be triggered immediately
                        else if(position.getOrderType().equals(OrderType.LIMIT) &&
                                ((position.getDirection().equals(OrderSide.BUY) && position.getOpenPrice() >= transaction.getPrice()) ||
                                (position.getDirection().equals(OrderSide.SELL) && position.getOpenPrice() <= transaction.getPrice()))){
                            
                            rejectedPositionsActions.add(newRejection(RejectionReason.WOULD_TRIGGER_IMMEDIATELY, position));
                        } 
                        // The order state is invalid
                        else {
                            rejectedPositionsActions.add(newRejection(RejectionReason.INVALID_ORDER_STATE, position));
                        }
                    }
                    break;
                case ADD_STOPLOSS:
                    for(Position position : positions){ 
                        Position pos = filledPositions.contains(position) 
                                    ? filledPositions.get(filledPositions.indexOf(position))
                                    : unfilledPositions.contains(position)
                                        ? unfilledPositions.get(unfilledPositions.indexOf(position))
                                        : null;

                        if(pos != null){
                            if((filledPositions.size() + unfilledPositions.size()) < MAX_PROG_ORDERS){
                                if(pos.isClosedBeforeStoploss()){
                                    rejectedPositionsActions.add(newRejection(RejectionReason.WOULD_TRIGGER_IMMEDIATELY, position));
                                } else {
                                    pos.setStoplossActive();
                                    positionsUpdate = true;
                                }
                            } else {
                                rejectedPositionsActions.add(newRejection(RejectionReason.EXCESS_PROG_ORDERS, position));
                            }
                        }
                    }
                case SET_BREAKEVEN:
                    for(Position position : positions){
                        int index = filledPositions.indexOf(position);
                        if(index != -1){
                            Position pos = filledPositions.get(index);
                            if(!pos.isBreakEven()){

                                pos.setStopLossPrice(transaction.getPrice());
                                pos.setBreakevenFlag(true);

                                filledPositions.set(index, pos);

                                positionsUpdate = true;
                            } 
                            else { //Am I waiting for a confirmation in the local environement, is this necessary, if stoploss is already set? Also TODO: Check if sending pos is correct instead of position it should be an identical object
                                rejectedPositionsActions.add(newRejection(RejectionReason.INVALID_ORDER_STATE, pos));
                            }
                        } else {
                            rejectedPositionsActions.add(newRejection(RejectionReason.INVALID_ORDER_STATE, position));
                        }
                    }
                    break;
                case CLOSE_POSITION:
                    for(Position position : positions){
                        int index = filledPositions.indexOf(position);
                        if(index != -1){
                            Position pos = filledPositions.get(index);
                            
                            if(closePosition(pos)){
                                rejectedPositionsActions.add(newRejection(RejectionReason.INVALID_ORDER_STATE, position));
                            };

                            filledPositions.remove(pos);
                            closedPositions.add(pos);

                            positionsUpdate = true;
                        } else {
                            rejectedPositionsActions.add(newRejection(RejectionReason.INVALID_ORDER_STATE, position));
                        }
                    }
                    break;
                case CANCEL_ORDER:
                    for(Position position : positions){
                        int index = unfilledPositions.indexOf(position);
                        if(index != -1 && position.getOrderType().equals(OrderType.LIMIT)){
                            Position pos = unfilledPositions.get(index);

                            userAssets.setFreeUSDT(
                                userAssets.getFreeUSDT() + 
                                pos.cancelPosition(transaction.getTimestamp()) + 
                                pos.getMargin()); //This includes paying interest

                            userAssets.setTotalUnpaidInterest(userAssets.getTotalUnpaidInterest() - pos.getTotalUnpaidInterest());
                            userAssets.setLockedUSDT(userAssets.getLockedUSDT() - pos.getMargin());
                            
                            if(pos.getDirection().equals(OrderSide.BUY)){
                                userAssets.setTotalBorrowedUSDT(userAssets.getTotalBorrowedUSDT() - pos.getBorrowedAmount());
                            } else {
                                userAssets.setTotalBorrowedBTC(userAssets.getTotalBorrowedBTC() - pos.getBorrowedAmount());
                            }

                            userAssetsUpdate = true;

                            unfilledPositions.remove(pos);
                            cancelledPositions.add(pos);

                            positionsUpdate = true;
                        } else {
                            rejectedPositionsActions.add(newRejection(RejectionReason.INVALID_ORDER_STATE, position));
                        }
                    }
                    break;
                case BORROW: //Kind of ugly but effective - we parse positions we want to borrow assets for
                    for(Position position : positions){
                        if(position.getDirection().equals(OrderSide.BUY)){ //Borrow USDT
                            if(userAssets.getTotalBorrowedUSDT() + position.getBorrowedAmount() < 900000){

                                //TODO: Borrow USDT
                            } else {
                                rejectedPositionsActions.add(newRejection(RejectionReason.EXCESS_BORROW, position));
                            }
                        } else {
                            if(userAssets.getTotalBorrowedBTC() + position.getBorrowedAmount() < 72){
                                //TODO: Borrow BTC
                            } else {
                                rejectedPositionsActions.add(newRejection(RejectionReason.EXCESS_BORROW, position));
                            }
                        }
                    }
                    break;
                case REPAY:
                    for(Position position : positions){
                        if(position.getDirection().equals(OrderSide.BUY)){ //Borrow USDT
                            if(userAssets.getFreeUSDT() >= position.getTotalUnpaidInterest()){
                                //TODO: Repay USDT
                            } else {
                                rejectedPositionsActions.add(newRejection(RejectionReason.INSUFFICIENT_FUNDS, position));
                            }
                        } else {
                            if(userAssets.getFreeBTC() >= position.getTotalUnpaidInterest()){
                                //TODO: Repay BTC
                            } else {
                                rejectedPositionsActions.add(newRejection(RejectionReason.INSUFFICIENT_FUNDS, position));
                            }
                        }
                    }
                    break;
            }
        }
    }

    private void checkStopLoss(){
        ArrayList<Position> positions = new ArrayList<Position>(filledPositions);
        positions.addAll(unfilledPositions);

        for(Position position : positions){
            if(!position.isClosed()){
                if((position.getDirection().equals(OrderSide.BUY) && transaction.getPrice() <= position.getStopLossPrice()) ||
                   (position.getDirection().equals(OrderSide.SELL) && transaction.getPrice() >= position.getStopLossPrice())){

                    if(position.isStoplossActive()){
                        closePosition(position);

                        tempPositionList.add(position);
                    } else {
                        position.setClosedBeforeStoploss(transaction.getTimestamp());
                    }
                }
            }
        }
        if(!tempPositionList.isEmpty()){
            filledPositions.removeAll(tempPositionList);
            unfilledPositions.removeAll(tempPositionList);

            closedPositions.addAll(tempPositionList);
            tempPositionList.clear();
        }
    }
    
    private void checkFills(){
        for(Position position : unfilledPositions){
            if(!position.isClosed() && !position.isFilled()){
                if(position.getOrderType().equals(OrderType.MARKET)){ //There is no check for programmatic order limit here because there is no such check for market orders. Since we must match every market order with a programmatic stop-limit order to sustain our prefered strategy, the test for position count must be done in our backend

                    double fillPrice = orderBookHandler.getSlippagePrice(transaction.getPrice(), position.getSize(), position.getDirection());

                    position.fillPosition(fillPrice, transaction.getTimestamp());

                    tempPositionList.add(position);
                } 
                else if((position.getDirection().equals(OrderSide.BUY) && transaction.getPrice() <= position.getOpenPrice()) || 
                       (position.getDirection().equals(OrderSide.SELL) && transaction.getPrice() >= position.getOpenPrice())){ //Same here, no programmatic order for a stoploss

                    double fillPrice = orderBookHandler.getSlippagePrice(
                        position.getOpenPrice(), 
                        position.getSize(),
                        position.getDirection());

                    position.fillPosition(fillPrice, transaction.getTimestamp());

                    tempPositionList.add(position);
                }
            }
        }
        if(!tempPositionList.isEmpty()){
            positionsUpdate = true;
            filledPositions.addAll(tempPositionList);
            unfilledPositions.removeAll(tempPositionList);
            tempPositionList.clear();
        }
    }

    private boolean closePosition(Position position){
        if(position.isClosed()){
            return true;
        }

        double closePrice = orderBookHandler.getSlippagePrice(
            position.getStopLossPrice(), 
            position.getSize(), 
            position.getDirection().equals(OrderSide.BUY) ? OrderSide.SELL : OrderSide.BUY);

        userAssets.setFreeUSDT(
            userAssets.getFreeUSDT() + 
            position.closePosition(closePrice, transaction.getTimestamp()) + 
            position.getMargin()); //This includes paying interest

        userAssets.setLockedUSDT(userAssets.getLockedUSDT() - position.getMargin());
        userAssets.setTotalUnpaidInterest(userAssets.getTotalUnpaidInterest() - position.getTotalUnpaidInterest());
        
        if(position.getDirection().equals(OrderSide.BUY)){
            userAssets.setTotalBorrowedUSDT(userAssets.getTotalBorrowedUSDT() - position.getBorrowedAmount());
        } else {
            userAssets.setTotalBorrowedUSDT(userAssets.getTotalBorrowedUSDT() - position.getBorrowedAmount());
        }

        userAssetsUpdate = true;
        positionsUpdate = true;

        return false;
    }

    private void conditionallyAddInterest(){
        if(transaction.getTimestamp() - previousInterestTimestamp > 1000 * 60 * 60){
            previousInterestTimestamp = transaction.getTimestamp();

            double totalUnpaidInterest = 0;

            for(Position position : filledPositions){
                if(!position.isClosed()){
                    position.increaseUnpaidInterest(transaction.getPrice());
                    totalUnpaidInterest += position.getTotalUnpaidInterest();
                }
            }

            userAssets.setTotalUnpaidInterest(totalUnpaidInterest);

            userAssetsUpdate = true;
            positionsUpdate = true;
        }
    }

    //TODO: Add margin level notification or add it to the userAssets
    private double checkMarginLevel(){
        double totalAssetValue = 
            userAssets.getFreeUSDT() + 
            (userAssets.getFreeBTC() + userAssets.getLockedBTC()) * transaction.getPrice();

        double totalBorrowedAssetValue = 
            userAssets.getTotalBorrowedUSDT() + 
            userAssets.getTotalBorrowedBTC() * transaction.getPrice();

        if(totalBorrowedAssetValue + userAssets.getTotalUnpaidInterest() == 0){
            return 999;
        }

        double marginLevel = totalAssetValue / (totalBorrowedAssetValue + userAssets.getTotalUnpaidInterest());

        userAssets.setMarginLevel(marginLevel);

        if(marginLevel < 1.05){
            System.out.println("marginLevel = " + marginLevel);

            //TODO: We'll assume closing all filled position is enough to avoid margin call for now.
            for(Position position : filledPositions){
                closePosition(position);
            }

            closedPositions.addAll(filledPositions);
            filledPositions.clear();
        }

        return marginLevel;
    }

    private Map<RejectionReason, Position> newRejection(RejectionReason reason, Position position){

        Map<RejectionReason, Position> map = new HashMap<>();
        map.put(reason, position);

        return map;
    }


    public double getTotalAssetsValue(){
        if(transaction != null){
            return userAssets.getTotalAssetValue(transaction.getPrice());
        }
        return 0.0;
    }

    public ExchangeLatencyHandler getExchangeLatencyHandler(){
        return this.exchangeLatencyHandler;
    }

    public int getOrderCount(){
        return this.orderCount;
    }

    public int getRunningPositionCounter(){
        return this.runningOrderCounter;
    }

    public void setRunningPositionCounter(int num){
        this.runningOrderCounter = num;
    }

    public int getWinCounter(){
        return this.winCounter;
    }

    public int getLossCounter(){
        return this.lossCounter;
    }

    public int getBreakevenCounter(){
        return this.breakevenCounter;
    }

    public void addToUserAssetList(AssetHandler userAsset){
        this.userAssetsList.add(userAsset);
    }

    public ArrayList<Position> getAllPositions(){
        ArrayList<Position> positions = new ArrayList<Position>();
        positions.addAll(filledPositions);
        positions.addAll(unfilledPositions);
        positions.addAll(closedPositions);
        positions.addAll(cancelledPositions);

        return positions;
    }
}
