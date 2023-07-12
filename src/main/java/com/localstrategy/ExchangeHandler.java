package com.localstrategy;

import java.util.ArrayList;
import java.util.Map;

import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.market.OrderBook;

import java.util.HashMap;

public class ExchangeHandler {

    private int PROGRAMMATIC_ORDER_LIMIT = 5;

    private int TRANSACTION_LATENCY = 0;
    private int TRADE_EXECUTION_LATENCY = 0; //request -> execution
    private int TRADE_REPORT_LATENCY = 0; //request -> response
    private int USER_DATA_LATENCY = 0;

    //Used to make a linear orderbook model
    private double ORDERBOOK_PCT = 0.4; //0.4%
    private double ORDERBOOK_QTY = 150; //150 BTC

    private ArrayList<Position> unfilledPositions = new ArrayList<Position>();
    private ArrayList<Position> filledPositions = new ArrayList<Position>();
    private ArrayList<Position> closedPositions = new ArrayList<Position>();
    private ArrayList<Position> canceledPositions = new ArrayList<Position>();

    private ArrayList<Position> tempPositionList = new ArrayList<Position>();

    private double borrowedAmount;
    private double unpaidInterest;
    private int orderCount;

    private long previousInterestTimestamp = 0;

    private boolean userAssetsChanged = false;
    private boolean positionsChanged = false;
    

    private int runningOrderCounter = 0;

    private AssetHandler userAssets = new AssetHandler();

    private ExchangeLatencyHandler exchangeLatencyHandler = new ExchangeLatencyHandler();

    //TODO: Add creating a stoploss when the position is created with a double latency, then add the case where the stoploss is hit before it was created
    //TODO: Spin a riskManager instance here to double check if the requested leverage is what it's supposed to be.
    //TODO: Add locking BTC on short and general transfer of borrowed funds into free categories
    //TODO: Add programmatical order limit check

    //TODO: Parse strategy as parameter from App instead of hardcoding it into TempStrategyExecutor, unless TempStrategyExecutor is the strategy (later)
    //TODO: Implement order rejections (later)
    //TODO: Implement borrow requests (later)
    private TempStrategyExecutor strategyExecutor = new TempStrategyExecutor(this);

    private ArrayList<AssetHandler> userAssetsList = new ArrayList<AssetHandler>();

    

    private OrderBookHandler orderBookHandler = new OrderBookHandler(ORDERBOOK_PCT, ORDERBOOK_QTY);
    
    private int winCounter = 0;
    private int lossCounter = 0;
    private int breakevenCounter = 0;

    public ExchangeHandler(Double initialUSDTPortfolio){
        
        this.userAssets.setFreeUSDT(initialUSDTPortfolio);
        this.userAssetsList.add(userAssets);
    }

    //FIXME: Currently I'm using the timestamp of the current transaction to define exchange time because they are almost equal to our local time. During walls that might present a problem if I don't add dynamic transaction latencies.
    public void newTransaction(SingleTransaction transaction, boolean isWall) {
        //Handle binance-based operations such as executing orders and calculating interest
        exchangeLatencyHandler.recalculateLatencies(transaction.getTimestamp());
        conditionallyAddInterest(transaction);

        checkStopLossHits(transaction);
        checkFills(transaction);

        //Add margin calculation
        checkMarginLevel(transaction);
        
        handleUserActionRequests(
            exchangeLatencyHandler.getDelayedLocalPositionsUpdate(transaction.getTimestamp()), 
            transaction,
            isWall);

        //Handle local operations such as recieving responses and user data

        /*  Each new event on the binance's end generates a new UserDataStream object 
         *  with a current state snapshot, which gets sent to the client through the ExchangeLatencyHandler.
         *  Each new pendingPosition event gets parsed to binance through the ExchangeLatencyHandler
         */

        updateUserDataStream(transaction);

        //Handle isWall locally. Every transaction is still available, but the wall transactions lag. We can't design the algo as if they are received at the same time as all others.
        //Realistically we can detect wall transactions during a stream and not act on them as if they are a step by step movement of the price, but that happens locally. The exchange processes all orders sequentially regardless.
        strategyExecutor.onTransaction(transaction, isWall);
    }

    private void updateUserDataStream(SingleTransaction transaction){
        if(userAssetsChanged || positionsChanged){
            exchangeLatencyHandler.addUserDataStream(
                new UserDataStream(userAssets, filledPositions, unfilledPositions), 
                transaction.getTimestamp());

            userAssetsChanged = false;
            positionsChanged = false;
        }
    }

    private void handleUserActionRequests(Map<OrderAction, ArrayList<Position>> temp, SingleTransaction transaction, boolean isWall){
        
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

                            if(userAssets.getFreeUSDT() >= positionMargin){

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
                            
                            closePosition(pos, transaction);

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

    private void checkStopLossHits(SingleTransaction transaction){
        ArrayList<Position> positions = new ArrayList<Position>(filledPositions);
        positions.addAll(unfilledPositions);

        for(Position position : positions){
            if(!position.isClosed()){
                if((position.getDirection().equals(OrderSide.BUY) && transaction.getPrice() <= position.getStopLossPrice()) ||
                   (position.getDirection().equals(OrderSide.SELL) && transaction.getPrice() >= position.getStopLossPrice())){

                    closePosition(position, transaction);

                    tempPositionList.add(position);
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

    
    private void checkFills(SingleTransaction transaction){
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

    private void closePosition(Position position, SingleTransaction transaction){
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
        
        if(position.getDirection().equals(OrderSide.BUY)){
            userAssets.setTotalBorrowedUSDT(userAssets.getTotalBorrowedUSDT() - position.getBorrowedAmount());
        } else {
            userAssets.setTotalBorrowedUSDT(userAssets.getTotalBorrowedUSDT() - position.getBorrowedAmount());
        }

        userAssetsChanged = true;
        positionsChanged = true;
    }

    private void conditionallyAddInterest(SingleTransaction transaction){
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

    public ArrayList<AssetHandler> terminateAndReport(String outputCSVPath, SingleTransaction transaction){
        //FIXME: Handle summing profit to portfolio correctly
        for(Position position : filledPositions){
            if(!position.isClosed()){
                if(position.isFilled()){

                    userAssets.setFreeUSDT(
                        userAssets.getFreeUSDT() + 
                        position.closePosition(transaction.getPrice(), transaction.getTimestamp()) + 
                        position.getMargin()); //This includes paying interest

                    userAssets.setLockedUSDT(userAssets.getLockedUSDT() - position.getMargin());

                    closedPositions.add(position);
                } else {
                    
                    userAssets.setFreeUSDT(
                        userAssets.getFreeUSDT() + 
                        position.cancelPosition(transaction.getTimestamp()) + 
                        position.getMargin()); //This includes paying interest

                    userAssets.setLockedUSDT(userAssets.getLockedUSDT() - position.getMargin());

                    canceledPositions.add(position);
                }
            } else {
                closedPositions.add(position);
            }
        }

        ArrayList<Position> allPositions = new ArrayList<Position>(closedPositions);
        allPositions.addAll(canceledPositions);

        allPositions.sort((Position p1, Position p2) -> Long.compare(p1.getOpenTimestamp(), p2.getOpenTimestamp()));

        //TODO: Add more information to output CSV
        if(outputCSVPath != null){
            System.out.println("Trade report written to " + outputCSVPath);
            ResultConsolidator.writePositionsToCSV(allPositions, outputCSVPath);
        }

        return this.userAssetsList;
    }

    private double checkMarginLevel(SingleTransaction transaction){
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
                closePosition(position, transaction);
            }

            closedPositions.addAll(filledPositions);
            filledPositions.clear();

        }

        return marginLevel;
    }

    public double getAssetsValue(SingleTransaction transaction){
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
}
