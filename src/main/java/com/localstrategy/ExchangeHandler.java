package com.localstrategy;

import java.util.ArrayList;
import java.util.Map;

import com.localstrategy.util.enums.OrderAction;
import com.localstrategy.util.enums.OrderSide;
import com.localstrategy.util.enums.OrderStatus;
import com.localstrategy.util.enums.OrderType;
import com.localstrategy.util.enums.RejectionReason;
import com.localstrategy.util.types.SingleTransaction;
import com.localstrategy.util.types.UserDataResponse;

import java.util.HashMap;


//TODO: I didn't consider how EXACTLY these walls work.
//TODO: we can query current leverage tier from Binance therefore change riskManager
//TODO: Separate Order and Position classes - Position class is a local class comprising of position value and a stoploss order, Order class is a single Binance-supported order request

//FIXME: Test userDataStream latency. It shouldn't be (lol shouldn't...) much different than order execution reponse latency
//FIXME: Refactor

//Note - we don't use borrowed funds when calculating or positons sizes. That's important during live trading. handle it properly that's why I made the request to the Binance API team

//TODO: Check if overall borrow / profit logic is alright

//TODO: implement riskManager to check tier
//TODO: Edit riskManager logic

//TODO: Parse strategy as parameter from App instead of hardcoding it into TempStrategyExecutor, unless TempStrategyExecutor is the strategy (later)
//TODO: Add symbol rules (later)
//TODO: Currently we only support auto borrow and repay. Add manual borrows and repays

public class ExchangeHandler {

    private int MAX_PROG_ORDERS = 5;
    private int MAX_BORROW_USDT = 900_000;
    private int MAX_BORROW_BTC = 72;
    private ArrayList<Position> newPositions = new ArrayList<Position>();
    private ArrayList<Position> filledPositions = new ArrayList<Position>();
    private ArrayList<Position> cancelledPositions = new ArrayList<Position>();
    private ArrayList<Position> rejectedOrders = new ArrayList<Position>();

    private ArrayList<Position> previousPositions = new ArrayList<Position>();

    private ArrayList<Map<RejectionReason, Position>> rejectedActions = new ArrayList<Map<RejectionReason, Position>>();

    private ArrayList<Position> acceptedPositionList = new ArrayList<Position>();

    private AssetHandler userAssets = new AssetHandler();
    private LatencyHandler exchangeLatencyHandler = new LatencyHandler();
    private ArrayList<AssetHandler> userAssetsList = new ArrayList<AssetHandler>();
    private SlippageHandler orderBookHandler = new SlippageHandler();
    private LocalStrategyExecutor strategyExecutor;

    private TierManager tierManager = new TierManager();

    private SingleTransaction transaction;

    private long previousInterestTimestamp = 0;

    private boolean userAssetsUpdated = false;
    private boolean positionsUpdated = false;

    private int orderCount;
    private int runningOrderCounter = 0;
    private int winCounter = 0;
    private int lossCounter = 0;
    private int breakevenCounter = 0;

    public ExchangeHandler(Double initialUSDTPortfolio){

        this.strategyExecutor = new LocalStrategyExecutor(this);

        this.userAssets.setFreeUSDT(initialUSDTPortfolio);
        this.userAssetsList.add(userAssets);
    }


    //FIXME: Currently I'm using the timestamp of the current transaction to define exchange time because they are almost equal to our local time. During walls that might present a problem if I don't add dynamic transaction latencies.
    public void newTransaction(SingleTransaction transaction, boolean isWall) {
        this.transaction = transaction;

        exchangeLatencyHandler.recalculateLatencies(transaction.getTimestamp());
        conditionallyAddInterest();

        checkFills();
        checkMarginLevel();
        
        handleUserRequest(exchangeLatencyHandler.getDelayedUserActionRequests(transaction.getTimestamp()));

        updateUserDataStream();

        strategyExecutor.onTransaction(transaction, isWall);
    }


    private void updateUserDataStream(){
        if(userAssetsUpdated || positionsUpdated || !rejectedActions.isEmpty()){
            exchangeLatencyHandler.addUserDataStream(
                new UserDataResponse(
                    userAssets, 
                    newPositions, 
                    filledPositions, 
                    cancelledPositions, 
                    rejectedOrders, 
                    rejectedActions
                ),
                transaction.getTimestamp());

            rejectedActions.clear();

            if(userAssetsUpdated){
                userAssetsList.add(userAssets);
            }

            userAssetsUpdated = false;
            positionsUpdated = false;
        }
    }

    private void handleUserRequest(Map<OrderAction, ArrayList<Position>> temp){

        if(temp == null){
            return;
        }

        for(Map.Entry<OrderAction, ArrayList<Position>> entry : temp.entrySet()){
            ArrayList<Position> positions = entry.getValue();

            switch(entry.getKey()){

                /*Supported Bincne API functions we want to emulate
                 * New order
                 * Cancel order(s)
                 * Adjust max leverage (later)
                 * Query tier data
                 * User stream
                 * Borrow asset (later)
                 * Repay asset (later)
                 */

                case CREATE_ORDER: //Thanks ChatGPT for refactoring
                    for(Position position : positions){
                        boolean canProcessOrder = (position.getOrderType().equals(OrderType.MARKET)) || 
                                                (position.getOrderType().equals(OrderType.LIMIT) && 
                                                (newPositions.size() + filledPositions.size() < MAX_PROG_ORDERS));

                        // Max programmatic orders reached?
                        if(!canProcessOrder){
                            rejectOrder(RejectionReason.EXCESS_PROG_ORDERS, position);
                            continue;
                        }

                        // Immediate trigger?
                        if(position.getOrderType().equals(OrderType.LIMIT) &&
                            ((position.getDirection().equals(OrderSide.BUY) && position.getOpenPrice() >= transaction.getPrice()) ||
                             (position.getDirection().equals(OrderSide.SELL) && position.getOpenPrice() <= transaction.getPrice()))){
                        
                            rejectOrder(RejectionReason.WOULD_TRIGGER_IMMEDIATELY, position);
                            continue;
                        }

                        newPositions.add(position);

                        positionsUpdated = true;
                    }
                    break;
                case CANCEL_ORDER:
                    for(Position position : positions){

                        int index = newPositions.indexOf(position);

                        if(index == -1){
                            rejectOrder(RejectionReason.INVALID_ORDER_STATE, position);
                        } 

                        if(position.isAutoRepayAtCancel()){

                            double borrowedAmount = position.getSize();

                            if(position.getDirection().equals(OrderSide.BUY)){ //Borrow USDT

                                if(userAssets.getLockedUSDT() < borrowedAmount || userAssets.getFreeUSDT() < position.getTotalUnpaidInterest()){
                                    //Not enough funds to return
                                    rejectOrder(RejectionReason.INSUFFICIENT_FUNDS, position);
                                }

                                //Return funds
                                userAssets.setFreeUSDT(
                                        userAssets.getFreeUSDT()
                                            - position.getTotalUnpaidInterest()
                                            + position.getMargin()
                                );

                                userAssets.setLockedUSDT(
                                    userAssets.getLockedUSDT()
                                        - borrowedAmount
                                        - position.getMargin()
                                );

                                userAssets.setTotalBorrowedUSDT(
                                    userAssets.getTotalBorrowedUSDT()
                                    - borrowedAmount
                                );


                            } else {

                                if(userAssets.getLockedBTC() < borrowedAmount || userAssets.getFreeUSDT() < position.getTotalUnpaidInterest()){
                                    rejectOrder(RejectionReason.INSUFFICIENT_FUNDS, position);
                                }

                                userAssets.setFreeBTC(
                                    userAssets.getFreeBTC()
                                        + position.getMargin()
                                        - position.getTotalUnpaidInterest()
                                );

                                userAssets.setLockedBTC(
                                    userAssets.getLockedBTC()
                                        - borrowedAmount
                                );

                                userAssets.setTotalBorrowedBTC(
                                    userAssets.getTotalBorrowedBTC()
                                    - borrowedAmount
                                );

                                
                            }

                        } else {

                            if(position.getDirection().equals(OrderSide.BUY)){

                                userAssets.setFreeUSDT(
                                    userAssets.getFreeUSDT()
                                        + position.getBorrowedAmount()  
                                );

                                userAssets.setLockedUSDT(
                                    userAssets.getLockedUSDT()
                                        - position.getBorrowedAmount()  
                                );

                            } else {

                                userAssets.setFreeBTC(
                                    userAssets.getFreeBTC()
                                        + position.getBorrowedAmount()
                                );  

                                userAssets.setLockedBTC(
                                    userAssets.getLockedBTC()
                                        - position.getBorrowedAmount()
                                );  
                            }
                        }

                        userAssetsUpdated = true;

                        cancelledPositions.add(position);
                        newPositions.remove(position);

                        positionsUpdated = true;
                    }
                    break;
                default:
                    break;
            }
        }
    }
    
    private void checkFills(){

        /*If automaticBorrow:
        *  Calculate required Borrow
        *  Check borrow limit
        *  Check margin requirement
        *  Borrow funds
        *  Lock funds
        *  Fill position
        * Else:
        *  Check margin requirement
        *  Fill position
        */

        for(Position position : newPositions){
            if(!position.isClosed() && !position.isFilled()){

                boolean isMarketOrder = position.getOrderType().equals(OrderType.MARKET) ? true : false;
                boolean isLong = position.getDirection().equals(OrderSide.BUY);

                double fillPrice = orderBookHandler.getSlippagePrice(
                        isMarketOrder ? transaction.getPrice() : position.getOpenPrice(),
                        position.getSize(), 
                        position.getDirection()
                    );

                if(isMarketOrder || 
                        (position.getDirection().equals(OrderSide.BUY) && transaction.getPrice() <= position.getOpenPrice()) || 
                        (position.getDirection().equals(OrderSide.SELL) && transaction.getPrice() >= position.getOpenPrice())){ 

                    if(position.isAutomaticBorrow()){
                        if(isLong){

                            double borrowAmount = position.getSize() * fillPrice;

                            if(borrowAmount + userAssets.getTotalBorrowedUSDT() > MAX_BORROW_USDT){
                                rejectOrder(RejectionReason.EXCESS_BORROW, position);
                                continue;
                            }
                            //Calculate leverage
                            tierManager.checkAndUpdateTier(borrowAmount + userAssets.getTotalBorrowedUSDT(), userAssets.getTotalBorrowedBTC());

                            double requiredMargin = borrowAmount / tierManager.getCurrentLeverage();

                            //Check margin requirement
                            if(userAssets.getFreeUSDT() < requiredMargin){
                                rejectOrder(RejectionReason.INSUFFICIENT_MARGIN, position);
                                continue;
                            }

                            //Borrow funds
                            userAssets.setFreeUSDT(
                                userAssets.getFreeUSDT()
                                    - requiredMargin
                            );

                            //Lock funds
                            userAssets.setLockedUSDT(
                                userAssets.getLockedUSDT()
                                    + requiredMargin
                                    + borrowAmount
                            );

                            userAssets.setTotalBorrowedUSDT(
                                userAssets.getTotalBorrowedUSDT()
                                    + borrowAmount  
                            );

                            position.setBorrowedAmount(borrowAmount);
                            position.setMargin(requiredMargin);
                            acceptedPositionList.add(position);
                        
                        } else {

                            double borrowAmount = position.getSize();

                            if(position.getSize() + userAssets.getTotalBorrowedBTC() > MAX_BORROW_BTC){
                                rejectOrder(RejectionReason.EXCESS_BORROW, position);
                                continue;
                            }
                            //Calculate leverage

                            tierManager.checkAndUpdateTier(userAssets.getTotalBorrowedUSDT(), borrowAmount + userAssets.getTotalBorrowedBTC());

                            double requiredMargin = borrowAmount * transaction.getPrice() / tierManager.getCurrentLeverage();

                            //Check margin requirement
                            if(userAssets.getFreeUSDT() < requiredMargin){
                                rejectOrder(RejectionReason.INSUFFICIENT_MARGIN, position);
                                continue;
                            }

                            userAssets.setFreeUSDT(
                                userAssets.getFreeUSDT()
                                    - requiredMargin
                            );

                            userAssets.setLockedUSDT(
                                userAssets.getLockedUSDT()
                                    + requiredMargin  
                            );

                            userAssets.setLockedBTC(
                                userAssets.getLockedBTC()
                                    + borrowAmount  
                            );

                            userAssets.setTotalBorrowedBTC(
                                userAssets.getTotalBorrowedBTC()
                                    + borrowAmount  
                            );

                            //Fill position
                            position.setBorrowedAmount(borrowAmount);
                            position.setMargin(requiredMargin);
                            acceptedPositionList.add(position);
                        }
                    } else {

                        tierManager.checkAndUpdateTier(userAssets.getTotalBorrowedUSDT(), userAssets.getTotalBorrowedBTC());

                        if(isLong){

                            double positionValue = position.getSize() * transaction.getPrice();

                            if(userAssets.getFreeUSDT() < positionValue){
                                rejectOrder(RejectionReason.INSUFFICIENT_MARGIN, position);
                                continue;
                            }

                            userAssets.setFreeUSDT(
                                userAssets.getFreeUSDT()
                                    - positionValue  
                            );

                            userAssets.setLockedUSDT(
                                userAssets.getLockedUSDT()
                                    + positionValue  
                            );

                            acceptedPositionList.add(position);

                        } else {
                            
                            double positionSize = position.getSize();

                            if(userAssets.getFreeBTC() < positionSize){
                                rejectOrder(RejectionReason.INSUFFICIENT_MARGIN, position);
                                continue;
                            }

                            userAssets.setFreeBTC(
                                userAssets.getFreeBTC()
                                    - positionSize  
                            );

                            userAssets.setLockedBTC(
                                userAssets.getLockedBTC()
                                    + positionSize  
                            );

                            acceptedPositionList.add(position);
                        }
                    }

                }
            }
        }
        if(!acceptedPositionList.isEmpty()){

            for(Position position : acceptedPositionList){
                position.setStatus(OrderStatus.FILLED);
            }

            positionsUpdated = true;
            userAssetsUpdated = true;

            filledPositions.addAll(acceptedPositionList);
            newPositions.removeAll(acceptedPositionList);
            acceptedPositionList.clear();
        }
    }

    private void conditionallyAddInterest(){
        if(transaction.getTimestamp() - previousInterestTimestamp > 1000 * 60 * 60){
            previousInterestTimestamp = transaction.getTimestamp();

            double totalUnpaidInterest = 0;

            for(Position position : filledPositions){
                position.increaseUnpaidInterest(transaction.getPrice());
                totalUnpaidInterest += position.getTotalUnpaidInterest();
            }

            userAssets.setTotalUnpaidInterest(totalUnpaidInterest);

            userAssetsUpdated = true;
            positionsUpdated = true;
        }
    }

    //TODO: Add margin level notification or add it to the userAssets
    private double checkMarginLevel(){
        double totalAssetValue = 
            userAssets.getFreeUSDT() + userAssets.getLockedUSDT() +
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
                liquidatePosition(position);
            }

            previousPositions.addAll(filledPositions);
            filledPositions.clear();
        }

        return marginLevel;
    }

    private void liquidatePosition(Position position){
        //TODO: liquidate a position
    }

    private void rejectOrder(RejectionReason reason, Position position){

        Map<RejectionReason, Position> map = new HashMap<>();
        map.put(reason, position);

        rejectedActions.add(map);
        rejectedOrders.add(position);
    }

    public double getTotalAssetsValue(){
        if(transaction != null){
            return userAssets.getTotalAssetValue(transaction.getPrice());
        }
        return 0.0;
    }

    public LatencyHandler getExchangeLatencyHandler(){
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
        positions.addAll(newPositions);
        positions.addAll(filledPositions);
        positions.addAll(rejectedOrders);
        positions.addAll(cancelledPositions);
        positions.addAll(previousPositions);

        return positions;
    }
}
