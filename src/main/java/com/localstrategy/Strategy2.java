package com.localstrategy;

import com.localstrategy.util.enums.OrderSide;
import com.localstrategy.util.enums.PositionGroup;
import com.localstrategy.util.indicators.ZigZag;
import com.localstrategy.util.misc.TradingGUI;
import com.localstrategy.util.types.Candle;
import com.localstrategy.util.types.Position;
import com.localstrategy.util.types.SingleTransaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class Strategy2 {
    private final static boolean DISPLAY_TRADING_GUI = false;



    private LocalHandler handler;
    private List<Position> activePositions;
    private List<Position> inactivePositions;
    private SingleTransaction transaction;
    private ArrayList<Candle> candles;

    private TradingGUI tradingGUI;

    int DISTANCE = 150;

    private int ZZDepth = 3;
    private int ZZBackstep = 0;

    public Strategy2(LocalHandler localHandler, ArrayList<Candle> candles, ArrayList<Position> activePositions, LinkedList<Position> inactivePositions){
        this.handler = localHandler;
        this.candles = candles;
        this.activePositions = Collections.unmodifiableList(activePositions);
        this.inactivePositions = Collections.unmodifiableList(inactivePositions);

        if(DISPLAY_TRADING_GUI){
            this.tradingGUI = new TradingGUI(activePositions, inactivePositions, DISTANCE);
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

    private final ZigZag zz = new ZigZag(ZZDepth, 0, ZZBackstep, 0);
    private final ZigZag zz_SL = new ZigZag(15, 0, 10, 0);

    public void priceUpdate(SingleTransaction transaction){
        this.transaction = transaction;
        //TODO: Fix when forming candle would become the new high / low, executing two orders. Also, some long orders don't long?

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

        if(longOnEmptyActivePositions && activePositions.isEmpty()){

            Position newMarketPosition = handler.executeMarketOrder(longRangeLowStop, true);

            if(newMarketPosition != null){
                handler.activateStopLoss(newMarketPosition);
            }

            packingForLong = false;
            longRangeLowStop = Double.MAX_VALUE;
            longRangeHighEntry = 0;
            waitForNextCandle = true;

            longOnEmptyActivePositions = false;
        } else if (shortOnEmptyActivePositions && activePositions.isEmpty()){

            Position newMarketPosition = handler.executeMarketOrder(shortRangeHighStop, true);

            if(newMarketPosition != null){
                handler.activateStopLoss(newMarketPosition);
            }

            packingForShort = false;
            shortRangeHighStop = 0;
            shortRangeLowEntry = Double.MAX_VALUE;
            waitForNextCandle = true;

            shortOnEmptyActivePositions = false;
        }

        // --- LONG ---
        if(packingForLong){
            if(transaction.price() >= zz.getLastHigh()){
                for(Position position : activePositions){
                    if(position.getDirection().equals(OrderSide.SELL)){
                        if(position.getGroup().equals(PositionGroup.FILLED)){
                            handler.closePosition(position);
                        }
                    }
                }

                longOnEmptyActivePositions = true;

            } else if(transaction.price() <= longRangeLowStop){
                longRangeLowStop = transaction.price();
            }
        }

        // --- SHORT ---
        if (packingForShort){
            if(transaction.price() <= zz.getLastLow()){
                for(Position position : activePositions){
                    if(position.getDirection().equals(OrderSide.BUY)){
                        if(position.getGroup().equals(PositionGroup.FILLED)){
                            handler.closePosition(position);
                        }
                    }
                }

                shortOnEmptyActivePositions = true;

            } else if(transaction.price() >= shortRangeHighStop) {
                shortRangeHighStop = transaction.price();
            }
        }
    }

    boolean longBreak = false;
    boolean shortBreak = false;

    public void candleUpdate(Candle candle){

        //TODO: Manually update value markers
        if(DISPLAY_TRADING_GUI){
            tradingGUI.getCandlestickChart().newCandle(handler.getUserAssets().getMomentaryOwnedAssets(), candle, transaction);
        }

        this.lastCandle = candle;

        if(candles.size() < 2 * Math.max(zz.getDepth(), zz_SL.getDepth()) + Math.max(zz.getBackstep(), zz_SL.getBackstep()) + 1){
            return;
        }

        // -- BREAKEVENS ---
//        for(Position position : activePositions){
//            if(!position.isBreakEvenActive() && position.getGroup().equals(PositionGroup.FILLED)){
//                if(position.isBreakEvenActive()){
//                    if(position.getDirection().equals(OrderSide.BUY) && transaction.price() > position.getEntryOrder().getFillPrice().doubleValue()){
//                        handler.updateStopLoss(position.getEntryOrder().getFillPrice().doubleValue(), position);
//                        position.setBreakEvenStatus(true);
//                    } else if(position.getDirection().equals(OrderSide.SELL) && transaction.price() < position.getEntryOrder().getFillPrice().doubleValue()){
//                        handler.updateStopLoss(position.getEntryOrder().getFillPrice().doubleValue(), position);
//                        position.setBreakEvenStatus(true);
//                    }
//                }
//            }
//        }



        updateZigZagValue(zz, candles);
        updateZigZagValue(zz_SL, candles);

        if(zz.getLastHigh() == -1 || zz.getLastLow() == -1 || zz_SL.getLastHigh() == -1 || zz_SL.getLastLow() == -1){
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
        }

        if(waitForNextCandle){
            waitForNextCandle = false;
        }

    }

    public void updateZigZagValue(ZigZag indicator, ArrayList<Candle> candles){

        int depth = indicator.getDepth();
        int backstep = indicator.getBackstep();

        List<Candle> candleSublist = candles.subList(
                candles.size()-1 - 2 * depth - backstep,
                candles.size());

        double[] highsArray = candleSublist.stream()
                .mapToDouble(Candle::high)
                .toArray();

        double[] lowsArray = candleSublist.stream()
                .mapToDouble(Candle::low)
                .toArray();

        indicator.calculate(highsArray.length, highsArray, lowsArray);

        double zigZagValue = indicator.getZigzagBuffer()[depth];

        if(zigZagValue != 0){

            Candle zigZagCandle = candles.get(candles.size()-1 - backstep - depth); // OK

            if(zigZagValue == zigZagCandle.high()){
                lastHighIndex = zigZagCandle.index();
                indicator.setLastHigh(zigZagValue);
            } else if(zigZagValue == zigZagCandle.low()) {
                lastLowIndex = zigZagCandle.index();
                indicator.setLastLow(zigZagValue);
            }
        }
    }
}