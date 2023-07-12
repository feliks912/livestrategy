package com.localstrategy;

import java.util.ArrayList;
import java.util.Map;

//TODO: Spin a riskManager instance here to double check if the requested leverage is what it's supposed to be.
//TODO: Add locking BTC on short and general transfer of borrowed funds into free categories


//TODO: Parse strategy as parameter from App instead of hardcoding it into TempStrategyExecutor, unless TempStrategyExecutor is the strategy (later)
//TODO: Implement order rejections (later)
//TODO: Implement borrow requests (later)

public class ExchangeHandler {

    private int PROGRAMMATIC_ORDER_LIMIT = 5;

    private ArrayList<Position> unfilledPositions = new ArrayList<Position>();
    private ArrayList<Position> filledPositions = new ArrayList<Position>();
    private ArrayList<Position> closedPositions = new ArrayList<Position>();
    private ArrayList<Position> canceledPositions = new ArrayList<Position>();
    private ArrayList<Position> rapidMoveRejectedStopPositions = new ArrayList<Position>(); //Stoploss rejected due to rapid price movement
    private ArrayList<Position> excessOrderRejectedStopPositions = new ArrayList<Position>(); //Stoploss rejected due to too many programmatical orders


    private ArrayList<Position> tempPositionList = new ArrayList<Position>();

    private AssetHandler userAssets = new AssetHandler();
    private ExchangeLatencyHandler exchangeLatencyHandler = new ExchangeLatencyHandler();
    private ArrayList<AssetHandler> userAssetsList = new ArrayList<AssetHandler>();
    private OrderBookHandler orderBookHandler = new OrderBookHandler();
    private TempStrategyExecutor strategyExecutor;

    private SingleTransaction transaction;

    private long previousInterestTimestamp = 0;

    private boolean userAssetsChanged = false;
    private boolean positionsChanged = false;

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
        if(userAssetsChanged || positionsChanged){
            exchangeLatencyHandler.addUserDataStream(
                new UserDataStream(
                    userAssets, 
                    filledPositions, 
                    unfilledPositions, 
                    rapidMoveRejectedStopPositions,
                    excessOrderRejectedStopPositions),
                transaction.getTimestamp());

            rapidMoveRejectedStopPositions.clear(); //TODO: Or remove it from the list when the position closes (later)
            excessOrderRejectedStopPositions.clear(); //Same?

            userAssetsChanged = false;
            positionsChanged = false;
        }
    }

    private void handleUserRequest(Map<OrderAction, ArrayList<Position>> temp){

        for(Map.Entry<OrderAction, ArrayList<Position>> entry : temp.entrySet()){
            ArrayList<Position> positions = entry.getValue();

            switch(entry.getKey()){
                case CREATE_ORDER:
                    for(Position position : positions){
                        if(!(unfilledPositions.contains(position) || 
                            filledPositions.contains(position) || 
                            closedPositions.contains(position) || 
                            canceledPositions.contains(position))){ //TODO: Check if the condition works

                            double fundsToBorrow = position.getBorrowedAmount();
                            double positionMargin = position.getMargin();

                            if(userAssets.getFreeUSDT() >= positionMargin &&
                                    ((position.getOrderType().equals(OrderType.MARKET)) || 
                                        (position.getOrderType().equals(OrderType.LIMIT) && 
                                        filledPositions.size() + unfilledPositions.size() < PROGRAMMATIC_ORDER_LIMIT))){

                                userAssets.setFreeUSDT(userAssets.getFreeUSDT() - positionMargin);
                                userAssets.setLockedUSDT(userAssets.getLockedUSDT() + positionMargin);
                                
                                userAssets.setTotalUnpaidInterest(userAssets.getTotalUnpaidInterest() + position.getTotalUnpaidInterest());

                                if(position.getDirection().equals(OrderSide.BUY)){
                                    userAssets.setTotalBorrowedUSDT(userAssets.getTotalBorrowedUSDT() + fundsToBorrow);
                                } else {
                                    userAssets.setTotalBorrowedBTC(userAssets.getTotalBorrowedBTC() + fundsToBorrow);
                                }

                                userAssetsChanged = true;

                                unfilledPositions.add(position);

                                positionsChanged = true;
                            }
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
                            if((filledPositions.size() + unfilledPositions.size()) < PROGRAMMATIC_ORDER_LIMIT){
                                if(pos.isClosedBeforeStoploss()){
                                    rejectedStopPositions.add(pos);
                                } else {
                                    pos.setStoplossActive();
                                }

                                positionsChanged = true;
                            } else {
                                //TODO: Notify user the stoploss cannot be created due to too many programmatical positions
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

                                positionsChanged = true;
                            }
                        }
                    }
                    break;
                case CLOSE_POSITION:
                    for(Position position : positions){
                        int index = filledPositions.indexOf(position);
                        if(index != -1){
                            Position pos = filledPositions.get(index);
                            
                            closePosition(pos);

                            filledPositions.remove(pos);
                            closedPositions.add(pos);

                            positionsChanged = true;
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

                            userAssetsChanged = true;

                            unfilledPositions.remove(pos);
                            canceledPositions.add(pos);

                            positionsChanged = true;
                        }
                    }
                    break;
                default:
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
            positionsChanged = true;
            filledPositions.addAll(tempPositionList);
            unfilledPositions.removeAll(tempPositionList);
            tempPositionList.clear();
        }
    }

    private void closePosition(Position position){
        if(position.isClosed()){
            return;
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

        userAssetsChanged = true;
        positionsChanged = true;
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

            userAssetsChanged = true;
            positionsChanged = true;
        }
    }

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
        ArrayList<Position> positions = new ArrayList<Position>(filledPositions);
        positions.addAll(unfilledPositions);
        positions.addAll(closedPositions);
        positions.addAll(canceledPositions);

        return positions;
    }
}
