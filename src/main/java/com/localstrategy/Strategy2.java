package com.localstrategy;

import com.localstrategy.util.enums.PositionGroup;
import com.localstrategy.util.helper.BinaryTransactionLoader;
import com.localstrategy.util.helper.CandleConstructor;
import com.localstrategy.util.indicators.ZigZag;
import com.localstrategy.util.misc.TradingGUI;
import com.localstrategy.util.types.Candle;
import com.localstrategy.util.types.Position;
import com.localstrategy.util.types.SingleTransaction;
import org.apache.commons.collections4.map.SingletonMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class Strategy2 {
    private final static boolean DISPLAY_TRADING_GUI = Params.showGraph;

    private final static int CANDLE_STEP_TIME_MS = 30;



    private LocalHandler handler;
    private List<Position> activePositions;
    private List<Position> inactivePositions;
    private SingleTransaction transaction;
    private ArrayList<Candle> candles;

    private final ArrayList<SingletonMap<Double, Double>> unusedStructure = new ArrayList<>();

    private final CandleConstructor localCandleConstructor = new CandleConstructor(Params.volume);

    private final BinaryTransactionLoader localLoader = new BinaryTransactionLoader("C:\\--- BTCUSDT", "2023-03-25", null);

    private final ArrayList<SingleTransaction> localTransactionList = new ArrayList<>(localLoader.loadNextDay());

    private SingleTransaction localTransaction = localTransactionList.get(0);

    private TradingGUI tradingGUI;

    int DISTANCE = Params.distance;

    public static int positionCount = 0;

    private int ZZDepth = Params.depth;
    private int ZZBackstep = Params.backstep;

    public Strategy2(LocalHandler localHandler, ArrayList<Candle> candles, ArrayList<Position> activePositions, LinkedList<Position> inactivePositions){
        this.handler = localHandler;
        this.candles = candles;
        this.activePositions = Collections.unmodifiableList(activePositions);
        this.inactivePositions = Collections.unmodifiableList(inactivePositions);

        if(DISPLAY_TRADING_GUI){
            this.tradingGUI = new TradingGUI(activePositions, inactivePositions, DISTANCE, CANDLE_STEP_TIME_MS);
        }

        // Your code
        System.out.println("Strategy distance: " + DISTANCE + ", ZZDepth " + ZZDepth + ", ZZBackstep " + ZZBackstep);
    }

    Candle lastCandle;

    private double previousZZHigh = -1;

    private double previousZZLow = -1;

    private boolean shorted = false;
    private boolean longed = false;

    private long longHedgePositionId = -1;

    private long shortHedgePositionId = -1;

    private boolean previouslyPackedLow = false;
    private boolean previouslyPackedHigh = false;

    private boolean shortOnEmptyActivePositions = false;
    private boolean longOnEmptyActivePositions = false;

    ArrayList<Position> summedPositions = new ArrayList<>();

    boolean packingForLong = false;
    boolean packingForShort = false;

    double longRangeHighEntry = 0;
    double longRangeLowStop = Double.MAX_VALUE;

    double shortRangeLowEntry = Double.MAX_VALUE;

    double shortRangeHighStop = 0;

    private boolean waitForNextCandle = false;

    private long lastHighIndex;
    private long lastLowIndex;

    private boolean newLow = false;
    private boolean newHigh = false;

    private boolean shortAttempt = false;

    private boolean longAttempt = false;

    private final ZigZag zz = new ZigZag(ZZDepth, 0, ZZBackstep, 0);
    private final ZigZag zz_SL = new ZigZag(15, 0, 10, 0);

    private int candleCounter = 0;

    private Candle localCandle;

    private ArrayList<Position> closeConfirmationList = new ArrayList<>();

    private boolean executeLong = true;
    private boolean executeShort = true;

    private boolean execute = false;

    public void priceUpdate(SingleTransaction transaction){

        this.transaction = transaction;

        if(packingForShort && transaction.price() > shortRangeHighStop){
            shortRangeHighStop = transaction.price();
        }
        if(packingForLong && transaction.price() < longRangeLowStop){
            longRangeLowStop = transaction.price();
        }

        //local transaction is always a bit ahead of the binance transaction
        if(transaction.timestamp() > localTransactionList.get(localTransactionList.size() - 1).timestamp()){
            if(localLoader.getRemainingFileCount() == 0){
                //Halt the program
                System.out.println("We're done.");
                StrategyStarter.exitBit = true;
                return;
            }
            localTransactionList.clear();
            localTransactionList.addAll(localLoader.loadNextDay());
            candleCounter = 0;
        }

        while(transaction.timestamp() > localTransaction.timestamp()){
            localTransaction = localTransactionList.get(candleCounter++);
            Candle candle = localCandleConstructor.processTradeEvent(localTransaction);
            if(candle != null){
                localCandle = candle;
                candleUpdate(candle);
            }
        }

        //TODO: Fix when forming candle would become the new high / low, executing two orders. Also, some long orders don't long?

//        ArrayList<SingletonMap<Double, Double>> tempList = new ArrayList<>();
//        for(SingletonMap<Double, Double> map : unusedStructure){
//            double high = map.getKey();
//            double low = map.getValue();
//
//            if(transaction.price() > high){
//                boolean activeLong = false;
//                for(Position position : activePositions){
//                    if(position.getDirection().equals(OrderSide.BUY)){
//                        activeLong = true;
//                    }
//                }
//                if(!activeLong){
//                    for(Position position : activePositions){
//                        if(position.getDirection().equals(OrderSide.SELL) && position.getGroup().equals(PositionGroup.FILLED)){
//                            handler.closePosition(position);
//                        }
//                    }
//                    handler.activateStopLoss(handler.executeMarketOrder(low, true));
//                }
//                tempList.add(map);
//            } else if(transaction.price() < low){
//                boolean activeShort = false;
//                for(Position position : activePositions){
//                    if(position.getDirection().equals(OrderSide.SELL)){
//                        activeShort = true;
//                    }
//                }
//                if(!activeShort){
//                    for(Position position : activePositions){
//                        if(position.getDirection().equals(OrderSide.BUY) && position.getGroup().equals(PositionGroup.FILLED)){
//                            handler.closePosition(position);
//                        }
//                    }
//                    handler.activateStopLoss(handler.executeMarketOrder(high, true));
//                }
//                tempList.add(map);
//            }
//        }
//        if(!tempList.isEmpty()){
//            unusedStructure.removeAll(tempList);
//        }

//        if(!activePositions.isEmpty()){
//            Position position = activePositions.get(0);
//            if(position.getGroup().equals(PositionGroup.FILLED)) {
//                if(position.calculateRR(transaction.price()) > 60){
//                    handler.closePosition(position);
//                } else if(position.getCloseOrder() == null) {
//                    if(zz_SL.getLastLow() != -1 && zz_SL.getLastHigh() != -1) {
//                        if (position.getDirection().equals(OrderSide.BUY) && transaction.price() >= zz_SL.getLastHigh()) {
//                            if (position.getStopOrder().getOpenPrice().doubleValue() < zz_SL.getLastLow()) {
//                                handler.updateStopLoss(zz_SL.getLastLow(), position);
//                            }
//                        } else if (position.getDirection().equals(OrderSide.SELL) && transaction.price() <= zz_SL.getLastLow()) {
//                            if (position.getStopOrder().getOpenPrice().doubleValue() > zz_SL.getLastHigh()) {
//                                handler.updateStopLoss(zz_SL.getLastHigh(), position);
//                            }
//                        }
//                    }
//                }
//            }
//        }


//        if(longAttempt){
//            ArrayList<SingletonMap<Double, Double>> tempMap = new ArrayList<>();
//            for(SingletonMap<Double, Double> map : unusedStructure){
//                double high = map.getKey();
//                double low = map.getValue();
//                if(transaction.price() >= high){
//                    handler.activateStopLoss(handler.executeMarketOrder(low, true));
//                    newHigh = false;
//                    longAttempt = false;
//                    tempMap.add(map);
//                }
//            }
//            if(!tempMap.isEmpty()){
//                unusedStructure.clear();
//            }
//        }
//        if(shortAttempt){
//            ArrayList<SingletonMap<Double, Double>> tempMap = new ArrayList<>();
//            for(SingletonMap<Double, Double> map : unusedStructure){
//                double high = map.getKey();
//                double low = map.getValue();
//                if(transaction.price() <= low){
//                    handler.activateStopLoss(handler.executeMarketOrder(high, true));
//                    newHigh = false;
//                    shortAttempt = false;
//                    tempMap.add(map);
//                }
//            }
//            if(!tempMap.isEmpty()){
//                unusedStructure.clear();
//            }
//        }



        if(activePositions.isEmpty() && longOnEmptyActivePositions){

            Position newMarketPosition = handler.executeMarketOrder(longRangeLowStop
                    - (transaction.price() - longRangeLowStop) * 0.2, true);

            if(newMarketPosition != null){
                handler.activateStopLoss(newMarketPosition);
                positionCount++;
            }

            packingForLong = false;
            longRangeLowStop = Double.MAX_VALUE;
            longRangeHighEntry = 0;
            waitForNextCandle = true;

            longOnEmptyActivePositions = false;
        } else if (activePositions.isEmpty() && shortOnEmptyActivePositions){

            Position newMarketPosition = handler.executeMarketOrder(shortRangeHighStop
                    + (shortRangeHighStop - transaction.price()) * 0.2, true);

            if(newMarketPosition != null){
                handler.activateStopLoss(newMarketPosition);
                positionCount++;
            }

            packingForShort = false;
            shortRangeHighStop = 0;
            shortRangeLowEntry = Double.MAX_VALUE;
            waitForNextCandle = true;

            shortOnEmptyActivePositions = false;
        }



        // --- LONG ---
        if(packingForLong){
            if(localTransaction.price() >= zz.getLastHigh()){

                longOnEmptyActivePositions = true;

            } else if(localTransaction.price() <= longRangeLowStop){
                longRangeLowStop = localTransaction.price();
            }
        }

        // --- SHORT ---
        if (packingForShort){
            if(localTransaction.price() <= zz.getLastLow()){

                shortOnEmptyActivePositions = true;

            } else if(localTransaction.price() >= shortRangeHighStop) {
                shortRangeHighStop = localTransaction.price();
            }
        }
    }

    boolean longBreak = false;
    boolean shortBreak = false;

    private Candle globalCandle;

    boolean realPackingForLong = false;
    boolean realPackingForShort = false;

    public void candleUpdate(Candle candle){

        if(candle != localCandle){
            //TODO: Manually update value markers
            if(DISPLAY_TRADING_GUI){
                tradingGUI.getCandlestickChart().newCandle(handler.getUserAssets().getMomentaryOwnedAssets(), candle, this.transaction);
            }

            if(candle.tick() < -DISTANCE){
                realPackingForLong = true;
            } else if(realPackingForLong){
                realPackingForLong = false;

                for(Position position : activePositions){
                    if(position.getGroup().equals(PositionGroup.FILLED)){
                        handler.closePosition(position);
                        closeConfirmationList.add(position);
                    }
                }
            }

            if(candle.tick() > DISTANCE){
                realPackingForShort = true;
            } else if(realPackingForShort){
                realPackingForShort = false;

                for(Position position : activePositions){
                    if(position.getGroup().equals(PositionGroup.FILLED)){
                        handler.closePosition(position);
                        closeConfirmationList.add(position);
                    }
                }
            }

            return;
        }

//        if(candle == localCandle){
//            if(globalCandle == null){
//                globalCandle = candle;
//            }
//        } else {
//            globalCandle = candle;
//            return;
//        }

//        if(lastCandle != null && lastCandle.tick() <= -DISTANCE && candle.tick() > -DISTANCE){
//            shortAttempt = true;
//        } else if(lastCandle != null && lastCandle.tick() >= DISTANCE && candle.tick() < DISTANCE){
//            longAttempt = true;
//        }

        if(candles.size() < 2 * zz.getDepth() + zz.getBackstep() + 1){
            return;
        }

        // -- BREAKEVENS ---
//        for(Position position : activePositions){
//            if(!position.isBreakEvenActive() && position.getGroup().equals(PositionGroup.FILLED)){
//                if(position.getDirection().equals(OrderSide.BUY) && transaction.price() > position.getEntryOrder().getFillPrice().doubleValue()){
//                    handler.updateStopLoss(position.getEntryOrder().getFillPrice().doubleValue(), position);
//                    position.setBreakEvenStatus(true);
//                } else if(position.getDirection().equals(OrderSide.SELL) && transaction.price() < position.getEntryOrder().getFillPrice().doubleValue()){
//                    handler.updateStopLoss(position.getEntryOrder().getFillPrice().doubleValue(), position);
//                    position.setBreakEvenStatus(true);
//                }
//            }
//        }


//        double previousHigh = zz.getLastHigh();
//        double previousLow = zz.getLastLow();

        zz.updateZigZagValue(candles);

//        if(previousHigh != zz.getLastHigh()){
//            newHigh = true;
//            if(longAttempt){
//                unusedStructure.add(new SingletonMap<>(zz.getLastHigh(), zz.getLastLow()));
//            }
//        }
//        if(previousLow != zz.getLastLow()){
//            newLow = true;
//            if(shortAttempt){
//                unusedStructure.add(new SingletonMap<>(zz.getLastHigh(), zz.getLastLow()));
//            }
//        }

        if(zz.getLastHigh() == -1 || zz.getLastLow() == -1){
            return;
        }

        // --- LONG ---
        if(!waitForNextCandle && candle.tick() <= -DISTANCE){ // New lows for long
            if(longBreak){
                longRangeLowStop = Double.MAX_VALUE;
                longBreak = false;
            }
            packingForLong = true;

            longRangeHighEntry = Math.min(zz.getLastHigh(), candle.high());
            longRangeLowStop = Math.min(longRangeLowStop, candle.low());
        } else {
            longBreak = true;

            if(packingForLong){
//                for(Position position : activePositions){
//                    if(position.getDirection().equals(OrderSide.SELL)){
//                        if(position.getGroup().equals(PositionGroup.FILLED)){
//                            handler.closePosition(position);
//                        }
//                    }
//                }
            }
        }

        // --- SHORT  ---
        if(!waitForNextCandle && candle.tick() >= DISTANCE){ // New highs for short
            if(shortBreak){
                shortRangeHighStop = 0;
                shortBreak = false;
            }
            packingForShort = true;

            shortRangeLowEntry = Math.max(zz.getLastLow(), candle.low());
            shortRangeHighStop = Math.max(shortRangeHighStop, candle.high());
        } else {
            shortBreak = true;

            if(packingForShort){
//                for(Position position : activePositions){
//                    if(position.getDirection().equals(OrderSide.BUY)){
//                        if(position.getGroup().equals(PositionGroup.FILLED)){
//                            handler.closePosition(position);
//                        }
//                    }
//                }
            }
        }

        if(waitForNextCandle){
            waitForNextCandle = false;
        }

    }
}