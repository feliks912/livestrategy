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


//TODO: I didn't consider how EXACTLY these price walls work.

//TODO: Check if overall borrow / profit logic is alright

//TODO: Separate Order and Position classes - Position class is a local class comprising of position value and a stoploss order, Order class is a single Binance-supported order request



//FIXME: Refactor

//Note - we don't use borrowed funds when calculating or positons sizes. That's important during live trading. handle it properly that's why I made the request to the Binance API team

//TODO: Parse strategy as parameter from App instead of hardcoding it into TempStrategyExecutor, unless TempStrategyExecutor is the strategy (later)
//TODO: Add symbol rules (later)
//TODO: Binance doesn't actually keep track of filled positions but it does offer a list (later)
//TODO: Add manual borrow action (later)
//TODO: Position liquidation (later)

public class Binance {

    public final static int MAX_PROG_ORDERS = 5;

    private int MAX_BORROW_USDT = 900_000;
    private int MAX_BORROW_BTC = 72;

    private ArrayList<Position> newPositions = new ArrayList<Position>();
    private ArrayList<Position> filledPositions = new ArrayList<Position>();
    private ArrayList<Position> cancelledPositions = new ArrayList<Position>();
    private ArrayList<Position> rejectedOrders = new ArrayList<Position>();

    private ArrayList<Position> previousPositions = new ArrayList<Position>();

    private ArrayList<Position> acceptedPositionList = new ArrayList<Position>();

    private UserAssets userAssets = new UserAssets();
    private LatencyHandler latencyHandler = new LatencyHandler();
    private ArrayList<UserAssets> userAssetsList = new ArrayList<UserAssets>();
    private SlippageHandler slippageHandler = new SlippageHandler();
    private TierManager tierManager = new TierManager();
    private LocalStrategy localStrategy;

    private SingleTransaction transaction;

    private long previousInterestTimestamp = 0;

    private boolean userAssetsUpdated = false;
    private boolean positionsUpdated = false;

    public Binance(Double initialUSDTPortfolio){

        this.localStrategy = new LocalStrategy(this);

        this.userAssets.setFreeUSDT(initialUSDTPortfolio);
        this.userAssetsList.add(userAssets);
    }

    public void newTransaction(SingleTransaction transaction, boolean isWall) {
        this.transaction = transaction;

        latencyHandler.recalculateLatencies(transaction.getTimestamp());
        conditionallyAddInterest();

        checkFills();
        checkMarginLevel();
        
        handleUserRequest(latencyHandler.getDelayedUserActionRequests(transaction.getTimestamp()));

        updateUserDataStream();

        localStrategy.onTransaction(transaction, isWall);
    }

    private void updateUserDataStream(){
        if(userAssetsUpdated || positionsUpdated){
            latencyHandler.addUserDataStream(
                new UserDataResponse(
                    userAssets, // userAssets copy constructor
                    newPositions,
                    filledPositions,
                    cancelledPositions,
                    rejectedOrders
                ),
                transaction.getTimestamp());

            if(userAssetsUpdated){
                userAssetsList.add(new UserAssets(userAssets)); // userAssets copy constructor
            }

            userAssetsUpdated = false;
            positionsUpdated = false;
        }
    }

    
    private void handleUserRequest(ArrayList<Map<OrderAction, Position>> entryBlock){

        if(entryBlock.isEmpty()){
            return;
        }

        for(Map<OrderAction, Position> entry : entryBlock){

            Map.Entry<OrderAction, Position> tempMap = entry.entrySet().iterator().next();

            OrderAction orderAction = tempMap.getKey();
            Position position = tempMap.getValue();

            switch(orderAction){
                case CREATE_ORDER: //Thanks ChatGPT for refactoring
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

                    if(position.isAutomaticBorrow() && position.getOrderType().equals(OrderType.LIMIT)){ // "If you use MARGIN_BUY, the amount necessary to borrow in order to execute your order later will be borrowed at the creation time, regardless of the order type (whether it's limit or stop-limit)."
                        RejectionReason rejectionReason = borrowFunds(position);

                        if(rejectionReason != null){
                            rejectOrder(rejectionReason, position);
                            continue;
                        }
                    }

                    newPositions.add(position);

                    positionsUpdated = true;

                    break;
                case CANCEL_ORDER: //FIXME: Rejecting a cancel_order position will remove the position from new positions to rejected positions which isn't what we want.
                    int index = newPositions.indexOf(position);

                    if(index == -1 || !position.getOrderType().equals(OrderType.LIMIT)){
                        rejectOrder(RejectionReason.INVALID_ORDER_STATE, position);
                        continue;
                    }

                    if(position.isAutoRepayAtCancel()){

                        RejectionReason rejectionReason = repayFunds(position);

                        if(rejectionReason != null){
                            rejectOrder(rejectionReason, position);
                            continue;
                        }
                    }

                    position.setStatus(OrderStatus.CANCELED);
                    cancelledPositions.add(position);
                    newPositions.remove(position);

                    positionsUpdated = true;

                    break;
                case REPAY: //FIXME: Add some conditions here
                    RejectionReason rejectionReason = repayFunds(position);

                    if(rejectionReason != null){
                        rejectOrder(rejectionReason, position);
                        continue;
                    }

                    break;
                default:
                    break;
            }
        }
    }

    private void checkFills(){
        for(Position position : newPositions){
            if(position.getStatus().equals(OrderStatus.NEW)){

                boolean isMarketOrder = position.getOrderType().equals(OrderType.MARKET) ? true : false;
                boolean isLong = position.getDirection().equals(OrderSide.BUY);

                double fillPrice = slippageHandler.getSlippagePrice(
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

                        //Borrow funds for market orders at the time of filling the order (principally incorrect, functionally equivalent)
                        if(isMarketOrder){
                            RejectionReason rejectionReason = borrowFunds(position);

                            if(rejectionReason != null){
                                rejectOrder(rejectionReason, position);
                                continue;
                            }
                        }

                        if(isLong){

                            if(userAssets.getFreeUSDT() < position.getBorrowedAmount()){
                                rejectOrder(RejectionReason.INSUFFICIENT_FUNDS, position);
                                continue;
                            }

                            //Buy BTC
                            userAssets.setFreeUSDT(
                                userAssets.getFreeUSDT()
                                    - position.getBorrowedAmount()  
                            );

                            userAssets.setFreeBTC(
                                userAssets.getFreeBTC()
                                    + position.getSize()
                            );

                            position.setFillPrice(fillPrice);
                            acceptedPositionList.add(position);
                        
                        } else { //Short

                            if(userAssets.getFreeBTC() < position.getSize()){
                                rejectOrder(RejectionReason.INSUFFICIENT_FUNDS, position);
                                continue;
                            }

                            //Sell BTC
                            userAssets.setFreeBTC(
                                userAssets.getFreeBTC()
                                    - position.getSize()
                            );

                            userAssets.setFreeUSDT(
                                userAssets.getFreeUSDT()
                                    + position.getBorrowedAmount()
                            );

                            position.setFillPrice(fillPrice);
                            acceptedPositionList.add(position);
                        }
                    } else {

                        if(isLong){

                            if(userAssets.getFreeUSDT() < position.getBorrowedAmount()){
                                rejectOrder(RejectionReason.INSUFFICIENT_FUNDS, position);
                                continue;
                            }

                            //Buy
                            userAssets.setFreeUSDT(
                                userAssets.getFreeUSDT()
                                    - position.getBorrowedAmount()  
                            );

                            userAssets.setFreeBTC(
                                userAssets.getFreeBTC()
                                    + position.getSize()
                            );

                            position.setFillPrice(fillPrice);
                            acceptedPositionList.add(position);

                        } else {

                            if(userAssets.getFreeBTC() < position.getSize()){
                                rejectOrder(RejectionReason.INSUFFICIENT_FUNDS, position);
                                continue;
                            }

                            //Sell
                            userAssets.setFreeBTC(
                                userAssets.getFreeBTC()
                                    - position.getSize()
                            );

                            userAssets.setFreeUSDT(
                                userAssets.getFreeUSDT()
                                    + position.getBorrowedAmount()
                            );

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

        newPositions.removeAll(rejectedOrders);
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

    private RejectionReason borrowFunds(Position position){

        boolean isMarketOrder = position.getOrderType().equals(OrderType.MARKET) ? true : false;
        boolean isLong = position.getDirection().equals(OrderSide.BUY);

        double fillPrice = slippageHandler.getSlippagePrice(
            isMarketOrder ? transaction.getPrice() : position.getOpenPrice(),
            position.getSize(), 
            position.getDirection()
        );

        if(isLong){

            double positionValue = position.getSize() * fillPrice;

            //Check maximum borrow amount
            if(positionValue + userAssets.getTotalBorrowedUSDT() > MAX_BORROW_USDT){
                return RejectionReason.EXCESS_BORROW;
            }

            //Calculate leverage
            tierManager.checkAndUpdateTier(positionValue + userAssets.getTotalBorrowedUSDT(), userAssets.getTotalBorrowedBTC());

            //Calculate required margin
            double requiredMargin = positionValue / tierManager.getCurrentLeverage();
            
            if(userAssets.getFreeUSDT() < requiredMargin){
                return RejectionReason.INSUFFICIENT_MARGIN;
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

            position.setMargin(requiredMargin);
            position.setBorrowedAmount(positionValue);
        
        } else {

            double positionSize = position.getSize();

            //Check maximum borrow amount
            if(position.getSize() + userAssets.getTotalBorrowedBTC() > MAX_BORROW_BTC){
                return RejectionReason.EXCESS_BORROW;
            }

            //Calculate leverage
            tierManager.checkAndUpdateTier(userAssets.getTotalBorrowedUSDT(), positionSize + userAssets.getTotalBorrowedBTC());

            double requiredMargin = positionSize * transaction.getPrice() / tierManager.getCurrentLeverage();

            //Check margin requirement
            if(userAssets.getFreeUSDT() < requiredMargin){
                return RejectionReason.INSUFFICIENT_MARGIN;
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

            position.setMargin(requiredMargin);
            position.setBorrowedAmount(positionSize);
        }
        return null;
    }

    //TODO: Check and refactor

    private RejectionReason repayFunds(Position position){
        if(position.getBorrowedAmount() == 0){
            return RejectionReason.INVALID_ORDER_STATE;
        }

        if(position.getDirection().equals(OrderSide.BUY)){
            //Return USDT

            if(userAssets.getFreeUSDT() < position.getBorrowedAmount() + position.getTotalUnpaidInterest()){
                return RejectionReason.INSUFFICIENT_FUNDS;
            }

            if(userAssets.getLockedUSDT() < position.getMargin()){
                System.out.println("Error at repayFunds side BUY - locked USDT has insufficient funds to unlock margin?");
                System.exit(1);
            }

            if(userAssets.getTotalBorrowedUSDT() < position.getBorrowedAmount()){
                System.out.println("Error at repayFunds side BUY - borrowed USDT would go negative if we were to exclude the borrowed amount?");
                System.exit(1);
            }

            userAssets.setFreeUSDT(
                userAssets.getFreeUSDT()
                    - position.getBorrowedAmount()
                    + position.getMargin()
                    - position.getTotalUnpaidInterest()
            );

            userAssets.setLockedUSDT(
                userAssets.getLockedUSDT()
                    - position.getMargin()
            );

            userAssets.setTotalBorrowedUSDT(
                userAssets.getTotalBorrowedUSDT()
                    - position.getBorrowedAmount()  
            );

        } else {
            //Return BTC

            if(userAssets.getFreeBTC() < position.getSize() || userAssets.getFreeUSDT() < position.getMargin()
                    - position.getTotalUnpaidInterest()){
                        return RejectionReason.INSUFFICIENT_FUNDS;
            }

            if(userAssets.getLockedUSDT() < position.getMargin()){
                System.out.println("Error at repayFunds side SELL - locked USDT has insufficient funds to unlock margin?");
                System.exit(1);
            }

            if(userAssets.getTotalBorrowedBTC() < position.getBorrowedAmount()){
                System.out.println("Error at repayFunds side SELL - borrowed BTC would go negative if we were to exclude the borrowed amount?");
                System.exit(1);
            }

            userAssets.setFreeBTC(
                userAssets.getFreeBTC()
                    - position.getSize()  
            );

            userAssets.setFreeUSDT(
                userAssets.getFreeUSDT()
                    + position.getMargin()
                    - position.getTotalUnpaidInterest()
            );

            userAssets.setLockedUSDT(
                userAssets.getLockedUSDT()
                    - position.getMargin()  
            );

            userAssets.setTotalBorrowedBTC(
                userAssets.getTotalBorrowedBTC()
                    - position.getSize()  
            );
        }


        if(userAssets.getTotalBorrowedBTC() < position.getBorrowedAmount()){
            System.out.println("Error at repayFunds - unpaid interest would go negative?");
            System.exit(1);
        }

        userAssets.setTotalUnpaidInterest(
            userAssets.getTotalUnpaidInterest()
                - position.getTotalUnpaidInterest()
        );

        userAssetsUpdated = true;

        return null;
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

    //TODO: liquidate position
    private void liquidatePosition(Position position){
        System.out.println("Liquidation");
        System.exit(1);
    }

    private void rejectOrder(RejectionReason reason, Position position){

        position.setStatus(OrderStatus.REJECTED);
        position.setRejectionReason(reason);
        rejectedOrders.add(position);

        positionsUpdated = true;
    }

    public double getTotalAssetsValue(){
        if(transaction != null){
            return userAssets.getTotalAssetValue(transaction.getPrice());
        }
        return 0.0;
    }

    public LatencyHandler getLatencyHandler(){
        return this.latencyHandler;
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
