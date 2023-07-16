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

//FIXME: Mayor bug is we parse references of positions around in time instead of copying them. This changes them when a change happens in a position which is not how latency manager ought to work like - FIXED added deep copy


//TODO: I didn't consider how EXACTLY these price walls work.
//TODO: we use automatic borrowings and repays therefore edit borrowed amount calculation from orderRequestManager
//TODO: Separate Order and Position classes - Position class is a local class comprising of position value and a stoploss order, Order class is a single Binance-supported order request
//TODO: Check if overall borrow / profit logic is alright
//TODO: Auto repay on cancel is not auto repay on reverse order. Integrate manual repay


//FIXME: Test userDataStream latency. It shouldn't be (lol shouldn't...) much different than order execution reponse latency
//FIXME: Refactor

//Note - we don't use borrowed funds when calculating or positons sizes. That's important during live trading. handle it properly that's why I made the request to the Binance API team

//TODO: Parse strategy as parameter from App instead of hardcoding it into TempStrategyExecutor, unless TempStrategyExecutor is the strategy (later)
//TODO: Add symbol rules (later)
//TODO: Currently we only support auto borrow and repay. Add manual borrows and repays (later)
//TODO: Binance doesn't actually keep track of filled positions but it does offer a list (later)

public class Binance {

    public final static int MAX_PROG_ORDERS = 5;
    private int MAX_BORROW_USDT = 900_000;
    private int MAX_BORROW_BTC = 72;

    private ArrayList<Position> newPositions = new ArrayList<Position>();
    private ArrayList<Position> filledPositions = new ArrayList<Position>();
    private ArrayList<Position> cancelledPositions = new ArrayList<Position>();
    private ArrayList<Position> rejectedOrders = new ArrayList<Position>();

    private ArrayList<Position> previousPositions = new ArrayList<Position>();

    private ArrayList<Map<RejectionReason, Position>> rejectedActions = new ArrayList<Map<RejectionReason, Position>>();

    private ArrayList<Position> acceptedPositionList = new ArrayList<Position>();

    private UserAssets userAssets = new UserAssets();
    private LatencyHandler exchangeLatencyHandler = new LatencyHandler();
    private ArrayList<UserAssets> userAssetsList = new ArrayList<UserAssets>();
    private SlippageHandler orderBookHandler = new SlippageHandler();
    private LocalStrategy strategyExecutor;

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

    public Binance(Double initialUSDTPortfolio){

        this.strategyExecutor = new LocalStrategy(this);

        this.userAssets.setFreeUSDT(initialUSDTPortfolio);
        this.userAssetsList.add(userAssets);
    }

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
                    userAssets, // userAssets copy constructor
                    newPositions,
                    filledPositions,
                    cancelledPositions,
                    rejectedOrders, 
                    rejectedActions
                ),
                transaction.getTimestamp());

            rejectedActions.clear();

            if(userAssetsUpdated){
                userAssetsList.add(new UserAssets(userAssets)); // userAssets copy constructor
            }

            userAssetsUpdated = false;
            positionsUpdated = false;
        }
    }

    
    private void handleUserRequest(ArrayList<Map<OrderAction, ArrayList<Position>>> entryBlocks){

        if(entryBlocks.isEmpty()){
            return;
        }

        for(Map<OrderAction, ArrayList<Position>> entryBlock : entryBlocks){

            for(Map.Entry<OrderAction, ArrayList<Position>> entry : entryBlock.entrySet()){

                ArrayList<Position> positions = entry.getValue();

                switch(entry.getKey()){

                    case CREATE_ORDER: //Thanks ChatGPT for refactoring
                        for(Position position : positions){
                            boolean canProcessOrder = (position.getOrderType().equals(OrderType.MARKET)) || 
                                                    (position.getOrderType().equals(OrderType.LIMIT) && 
                                                    (newPositions
                                                        .stream()
                                                        .filter(p -> p.getOrderType()
                                                        .equals(OrderType.LIMIT))
                                                        .count()
                                                            < Binance.MAX_PROG_ORDERS));

                            // Max programmatic orders reached?
                            if(!canProcessOrder){
                                rejectOrder(RejectionReason.EXCESS_PROG_ORDERS, position);
                                continue;
                            }

                            // Immediate trigger?
                            if(position.getOrderType().equals(OrderType.LIMIT) &&
                                ((position.getDirection().equals(OrderSide.BUY) && 
                                    (position.isStopLoss() && transaction.getPrice() >= position.getOpenPrice() || 
                                    !position.isStopLoss() && transaction.getPrice() <= position.getOpenPrice())) ||
                                (position.getDirection().equals(OrderSide.SELL) && 
                                    (position.isStopLoss() && transaction.getPrice() <= position.getOpenPrice() ||
                                    !position.isStopLoss() && transaction.getPrice() >= position.getOpenPrice())))){
                            
                                rejectOrder(RejectionReason.WOULD_TRIGGER_IMMEDIATELY, position);
                                continue;
                            }

                            newPositions.add(position);
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
                                                + position.getMargin()
                                                - position.getTotalUnpaidInterest()
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
    }
    
    //FIXME: It seems I'm handling shorting BTC as selling BTC which isn't the case
    // We can use BTC to borrow BTC
    // When we short we lock USDT to be used as margin and receive FreeBTC.
    // When we long we lock USDT to be used as margin and receive FreeUSDT.
    // TODO: To us then, closing a position is repaying dept.
    // TODO: Issue when calculating filling. On limit orders we buy when the price crosses from above.
    //  On limit we sell when the price crosses from below
    //  On stop we sell when the price crosses from above
    //  On stop we buy when the price crosses from below

    // So on order filling we are likely to get a better price
    // But on stoplosses we are likely to get a worse price

    //FIXME: 4 configurations of a limit order - Order direction X stop-limit difference

    //TODO: Refactor
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
            if(position.getStatus().equals(OrderStatus.NEW)){

                boolean isMarketOrder = position.getOrderType().equals(OrderType.MARKET) ? true : false;
                boolean isLong = position.getDirection().equals(OrderSide.BUY);

                double fillPrice = orderBookHandler.getSlippagePrice(
                    isMarketOrder ? transaction.getPrice() : position.getOpenPrice(),
                    position.getSize(), 
                    position.getDirection()
                );

                if(isMarketOrder || (!isMarketOrder && 
                        ((position.getDirection().equals(OrderSide.BUY) && 
                            (position.isStopLoss() && transaction.getPrice() >= position.getOpenPrice() || 
                            !position.isStopLoss() && transaction.getPrice() <= position.getOpenPrice())) ||
                        (position.getDirection().equals(OrderSide.SELL) && 
                            (position.isStopLoss() && transaction.getPrice() >= position.getOpenPrice() ||
                            !position.isStopLoss() && transaction.getPrice() <= position.getOpenPrice()))))){ 

                    if(position.isAutomaticBorrow()){
                        if(isLong){

                            double positionValue = position.getSize() * fillPrice;

                            //Check maximum borrow amount
                            if(positionValue + userAssets.getTotalBorrowedUSDT() > MAX_BORROW_USDT){
                                rejectOrder(RejectionReason.EXCESS_BORROW, position);
                                continue;
                            }

                            //Calculate leverage
                            tierManager.checkAndUpdateTier(positionValue + userAssets.getTotalBorrowedUSDT(), userAssets.getTotalBorrowedBTC());

                            //Calculate required margin
                            double requiredMargin = positionValue / tierManager.getCurrentLeverage();
                            
                            if(userAssets.getFreeUSDT() < requiredMargin){
                                rejectOrder(RejectionReason.INSUFFICIENT_MARGIN, position);
                                continue;
                            }

                            //Lock funds
                            userAssets.setFreeUSDT(
                                userAssets.getFreeUSDT()
                                    - requiredMargin
                            );

                            userAssets.setLockedUSDT(
                                userAssets.getLockedUSDT()
                                    + requiredMargin
                            );

                            //Receive asset
                            userAssets.setFreeUSDT(
                                userAssets.getFreeUSDT() 
                                    + positionValue
                            );

                            userAssets.setTotalBorrowedUSDT(
                                userAssets.getTotalBorrowedUSDT()
                                    + positionValue  
                            );

                            userAssets.setTotalUnpaidInterest(
                                userAssets.getTotalUnpaidInterest() 
                                    + position.getTotalUnpaidInterest());

                            //Buy BTC
                            userAssets.setFreeUSDT(
                                userAssets.getFreeUSDT()
                                    - positionValue  
                            );

                            userAssets.setFreeBTC(
                                userAssets.getFreeBTC()
                                    + positionValue / fillPrice
                            );
                            
                            position.setFillPrice(fillPrice);
                            position.setMargin(requiredMargin);
                            position.setBorrowedAmount(positionValue);

                            acceptedPositionList.add(position);
                        
                        } else {

                            double positionSize = position.getSize();

                            //Check maximum borrow amount
                            if(position.getSize() + userAssets.getTotalBorrowedBTC() > MAX_BORROW_BTC){
                                rejectOrder(RejectionReason.EXCESS_BORROW, position);
                                continue;
                            }

                            //Calculate leverage
                            tierManager.checkAndUpdateTier(userAssets.getTotalBorrowedUSDT(), positionSize + userAssets.getTotalBorrowedBTC());

                            double requiredMargin = positionSize * transaction.getPrice() / tierManager.getCurrentLeverage();

                            //Check margin requirement
                            if(userAssets.getFreeUSDT() < requiredMargin){
                                rejectOrder(RejectionReason.INSUFFICIENT_MARGIN, position);
                                continue;
                            }

                            //Lock funds
                            userAssets.setFreeUSDT(
                                userAssets.getFreeUSDT()
                                    - requiredMargin
                            );

                            userAssets.setLockedUSDT(
                                userAssets.getLockedUSDT()
                                    + requiredMargin  
                            );

                            //Receive funds
                            userAssets.setFreeBTC(
                                userAssets.getFreeBTC()
                                    + positionSize
                            );

                            userAssets.setTotalBorrowedBTC(
                                userAssets.getTotalBorrowedBTC()
                                    + positionSize  
                            );

                            userAssets.setTotalUnpaidInterest(
                                userAssets.getTotalUnpaidInterest() 
                                    + position.getTotalUnpaidInterest());

                            //Sell BTC
                            userAssets.setFreeBTC(
                                userAssets.getFreeBTC()
                                    - positionSize
                            );

                            userAssets.setFreeUSDT(
                                userAssets.getFreeUSDT()
                                    + positionSize * fillPrice  
                            );

                            //Fill position
                            position.setFillPrice(fillPrice);
                            position.setMargin(requiredMargin);
                            position.setBorrowedAmount(positionSize);

                            acceptedPositionList.add(position);
                        }
                    } else {

                        if(isLong){

                            double positionValue = position.getSize() * fillPrice;

                            if(userAssets.getFreeUSDT() < positionValue){
                                rejectOrder(RejectionReason.INSUFFICIENT_MARGIN, position);
                                continue;
                            }

                            //Buy
                            userAssets.setFreeUSDT(
                                userAssets.getFreeUSDT()
                                    - positionValue  
                            );

                            userAssets.setFreeBTC(
                                userAssets.getFreeBTC()
                                    + position.getSize()
                            );

                            //Fill position
                            position.setFillPrice(fillPrice);

                            acceptedPositionList.add(position);

                        } else {
                            
                            double positionSize = position.getSize();

                            if(userAssets.getFreeBTC() < positionSize){
                                rejectOrder(RejectionReason.INSUFFICIENT_MARGIN, position);
                                continue;
                            }

                            //Sell
                            userAssets.setFreeBTC(
                                userAssets.getFreeBTC()
                                    - positionSize
                            );

                            userAssets.setFreeUSDT(
                                userAssets.getFreeUSDT()
                                    + positionSize * fillPrice  
                            );

                            //Fill position
                            position.setFillPrice(fillPrice);

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

            filledPositions.addAll(acceptedPositionList);
            newPositions.removeAll(acceptedPositionList);
            acceptedPositionList.clear();

            positionsUpdated = true;
            userAssetsUpdated = true;
        }

        if(!rejectedOrders.isEmpty()){
            newPositions.removeAll(rejectedOrders);
        }
    }

    private void conditionallyAddInterest(){
        if((!filledPositions.isEmpty() || !newPositions.isEmpty()) && 
            transaction.getTimestamp() - previousInterestTimestamp > 1000 * 60 * 60){

            previousInterestTimestamp = transaction.getTimestamp();

            double totalUnpaidInterest = 0;

            for(Position position : filledPositions){
                position.increaseUnpaidInterest(transaction.getPrice());
                totalUnpaidInterest += position.getTotalUnpaidInterest();
            }

            for(Position position : newPositions){
                position.increaseUnpaidInterest(transaction.getPrice());
                totalUnpaidInterest += position.getTotalUnpaidInterest();
            }

            userAssets.setTotalUnpaidInterest(totalUnpaidInterest);

            userAssetsUpdated = true;
            positionsUpdated = true;
        }
    }

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

            for(Position position : filledPositions){
                liquidatePosition(position);
            }
        }

        return marginLevel;
    }

    //TODO: liquidate a position
    private void liquidatePosition(Position position){
        
    }

    private void rejectOrder(RejectionReason reason, Position position){
        Map<RejectionReason, Position> map = new HashMap<>();

        map.put(reason, position);
        rejectedActions.add(map);

        position.setStatus(OrderStatus.REJECTED);
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

    public void addToUserAssetList(UserAssets userAsset){
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

    public UserAssets getUserassets(){
        return this.userAssets;
    }

}
