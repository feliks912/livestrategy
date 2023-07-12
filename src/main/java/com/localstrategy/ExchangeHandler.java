package com.localstrategy;

import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

public class ExchangeHandler {

    private double BROKER_COMMISSION_RATE = 0;
    private int TOTAL_ORDER_LIMIT = 5;

    private int TRANSACTION_LATENCY = 0;
    private int TRADE_EXECUTION_LATENCY = 0; //request -> execution
    private int TRADE_REPORT_LATENCY = 0; //request -> response
    private int USER_DATA_LATENCY = 0;

    //Used to make a linear orderbook model
    private double ORDERBOOK_PCT = 0;
    private double ORDERBOOK_QTY = 0;

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
    private boolean activePositionsChanged = false;
    

    private int runningOrderCounter = 0;

    private SingleTransaction lastTransaction = null;

    private AssetHandler userAssets = new AssetHandler();

    private ExchangeLatencyHandler exchangeLatencyHandler = new ExchangeLatencyHandler();

    //TODO: Edit RiskManager
    private RiskManager riskManager = new RiskManager(filledPositions);

    //TODO: Use orderMaker to request an order action
    private OrderRequestHandler orderRequestHandler = 
        new OrderRequestHandler(
            unfilledPositions, 
            filledPositions, 
            riskManager, 
            userAssets, 
            TempStrategyExecutor.RISK_PCT, 
            TOTAL_ORDER_LIMIT);

    //TODO: Parse strategy as parameter from App instead of hardcoding it into TempStrategyExecutor, unless TempStrategyExecutor is the strategy.
    private TempStrategyExecutor strategyExecutor = new TempStrategyExecutor(this, exchangeLatencyHandler);

    private ArrayList<Double> portfolioList = new ArrayList<Double>();
    
    private int winCounter = 0;
    private int lossCounter = 0;
    private int breakevenCounter = 0;

    public ExchangeHandler(Double initialUSDTPortfolio){
        
        this.userAssets.setFreeUSDT(initialUSDTPortfolio);
        this.portfolioList.add(initialUSDTPortfolio);
    }

    //FIXME: Currently I'm using the timestamp of the current transaction to define exchange time because they are almost equal to our local time. During walls that might present a problem if I don't add dynamic transaction latencies.
    public void newTransaction(SingleTransaction transaction, boolean isWall){
        //Handle binance-based operations such as executing orders and calculating interest
        exchangeLatencyHandler.calculateLatencies(transaction.getTimestamp());
        checkInterestTime(transaction.getTimestamp());

        checkStopLosses(transaction);

        checkFills(transaction);

        checkCloses();
        checkCancels();
        
        handlePositionActionRequests(exchangeLatencyHandler.getDelayedLocalPositionsUpdate(transaction.getTimestamp()));

        //Handle local operations such as recieving responses and user data

        /*  Each new event on the binance's end generates a new UserDataStream object 
         *  with a current state snapshot, which gets sent to the client through the ExchangeLatencyHandler.
         *  Each new pendingPosition event gets parsed to binance through the ExchangeLatencyHandler
         */

        //TODO: Check if there's a change before sending
        //TODO: Add sending position close and cancel requests
        if(userAssetsChanged || activePositionsChanged){
            exchangeLatencyHandler.addUserDataStream(
                new UserDataStream(userAssets, filledPositions, unfilledPositions), 
                transaction.getTimestamp());

            userAssetsChanged = false;
            activePositionsChanged = false;
        }

        strategyExecutor.onTransaction(transaction);
    }

    private void handlePositionActionRequests(Map<String, ArrayList<Position>> temp){
        for(Map.Entry<String, ArrayList<Position>> entry : temp.entrySet()){
            ArrayList<Position> positions = entry.getValue();
            if(entry.getKey() == "create"){
                for(Position position : positions){
                    //TODO: Reject order if we borrowed too many funds or if we lack necessary margin
                    double borrowedFunds = position.getBorrowedAmount();
                    double positionMargin = position.getMargin();

                    userAssets.setLockedUSDT(userAssets.getLockedUSDT() + positionMargin);
                    userAssets.setTotalUnpaidInterest(userAssets.getTotalUnpaidInterest() + position.getTotalUnpaidInterest());

                    if(position.getDirection() == 1){
                        userAssets.setTotalBorrowedUSDT(userAssets.getTotalBorrowedUSDT() + borrowedFunds);
                    } else {
                        userAssets.setTotalBorrowedBTC(userAssets.getTotalBorrowedBTC() + borrowedFunds);
                    }

                    unfilledPositions.add(position);
                }
            } else if(entry.getKey() == "close" && filledPositions.contains(positions)) { //TODO: Check if second condition works
                for(Position position : positions){
                    //Handle position closing (opening a market order in the opposite direction)

                    filledPositions.remove(position);
                    closedPositions.add(position);
                }
            } else if(entry.getKey() == "cancel" && unfilledPositions.contains(positions)) { //TODO: Same here
                for(Position position : positions){
                    //Handle canceling positions (repay + dept)

                    unfilledPositions.remove(position);
                    canceledPositions.add(position);
                }
            }
        }
    }

    private void checkStopLosses(SingleTransaction transaction){
        ArrayList<Position> positions = new ArrayList<Position>(filledPositions);
        positions.addAll(unfilledPositions);

        for(Position position : positions){
            if(!position.isClosed()){
                if(position.getDirection() == 1 && transaction.getPrice() <= position.getStopLossPrice()){
                    //Handle long position stoploss

                    tempPositionList.add(position);
                }
                else if(position.getDirection() == -1 && transaction.getPrice() >= position.getStopLossPrice()){
                    //Handle short position stoploss

                    tempPositionList.add(position);
                }
            }
        }
        if(!tempPositionList.isEmpty()){
            activePositionsChanged = true;
            userAssetsChanged = true;

            filledPositions.removeAll(tempPositionList);
            unfilledPositions.removeAll(tempPositionList);

            closedPositions.addAll(tempPositionList);
            tempPositionList.clear();
        }
    }

    private void checkFills(SingleTransaction transaction){
        for(Position position : unfilledPositions){
            if(!position.isClosed() && !position.isFilled()){
                if(position.getOrderType().equals("market")){
                    //Handle market order filling

                    tempPositionList.add(position);
                } else {
                    if(position.getDirection() == 1 && transaction.getPrice() <= position.getOpenPrice()){
                        //Handle limit order filling

                        tempPositionList.add(position);
                    }
                    else if(position.getDirection() == -1 && transaction.getPrice() >= position.getOpenPrice()){
                        //Handle limit order filling
                        
                        tempPositionList.add(position);
                    }
                }
            }
        }
        if(!tempPositionList.isEmpty()){
            activePositionsChanged = true;
            filledPositions.addAll(tempPositionList);
            unfilledPositions.removeAll(tempPositionList);
            tempPositionList.clear();
        }
    }

    private void checkInterestTime(long currentExchangeTimestamp){
        if(currentExchangeTimestamp - previousInterestTimestamp > 1000 * 60 * 60){
            previousInterestTimestamp = currentExchangeTimestamp;

            for(Position position : filledPositions){
                if(!position.isClosed()){
                    position.increaseUnpaidInterest(lastTransaction.getPrice());
                }
            }
            activePositionsChanged = true;
        }
    }

    public ArrayList<Double> terminateAndReport(String outputCSVPath){
        for(Position position : filledPositions){
            if(!position.isClosed()){
                if(position.isFilled()){
                    position.closePosition(lastTransaction.getPrice(), lastTransaction.getTimestamp());
                    closedPositions.add(position);
                } else {
                    position.cancelPosition(lastTransaction.getTimestamp());
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

        return this.portfolioList;
    }

    public double getAssetsValue(){
        if(lastTransaction != null){
            return userAssets.getTotalAssetValue(lastTransaction.getPrice());
        }
        return 0.0;
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
}
