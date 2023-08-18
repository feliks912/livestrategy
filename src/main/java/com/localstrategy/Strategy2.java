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

    int DISTANCE = 300;

    public Strategy2(LocalHandler localHandler, ArrayList<Candle> candles, ArrayList<Position> activePositions, LinkedList<Position> inactivePositions){
        this.handler = localHandler;
        this.candles = candles;
        this.activePositions = Collections.unmodifiableList(activePositions);
        this.inactivePositions = Collections.unmodifiableList(inactivePositions);

        if(DISPLAY_TRADING_GUI){
            this.tradingGUI = new TradingGUI(activePositions, inactivePositions, DISTANCE);
        }

        // Your code
        System.out.println("Distance: " + DISTANCE);
    }

    Candle lastCandle;

    private double ZZHigh = -1;
    private double ZZLow = -1;

    private double previousZZHigh = -1;

    private double previousZZLow = -1;

    private boolean shorted = false;
    private boolean longed = false;

    private long longHedgePositionId = -1;

    private long shortHedgePositionId = -1;

    private boolean previouslyPackedLow = false;
    private boolean previouslyPackedHigh = false;

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

    public void priceUpdate(SingleTransaction transaction){
        this.transaction = transaction;
        //TODO: Fix when forming candle would become the new high / low, executing two orders. Also, some long orders don't long?



        // --- LONG ---
        if(packingForLong){
            if(transaction.price() >= ZZHigh){
                for(Position position : activePositions){
                    if(position.getDirection().equals(OrderSide.SELL)){
                        if(position.getGroup().equals(PositionGroup.FILLED)){
                            handler.closePosition(position);
                        }
                    }
                }

                Position newMarketPosition = handler.executeMarketOrder(longRangeLowStop);

                if(newMarketPosition != null){
                    handler.activateStopLoss(newMarketPosition);
                }

                packingForLong = false;
                longRangeLowStop = Double.MAX_VALUE;
                longRangeHighEntry = 0;
                waitForNextCandle = true;
            } else if(transaction.price() <= longRangeLowStop){
                longRangeLowStop = transaction.price();
            }
        }

        // --- SHORT ---
        if (packingForShort){
            if(transaction.price() <= ZZLow){
                for(Position position : activePositions){
                    if(position.getDirection().equals(OrderSide.BUY)){
                        if(position.getGroup().equals(PositionGroup.FILLED)){
                            handler.closePosition(position);
                        }
                    }
                }

                Position newMarketPosition = handler.executeMarketOrder(shortRangeHighStop);


                if(newMarketPosition != null){
                    if(newMarketPosition.getId() == 26){
                        boolean variable = true;
                    }
                    handler.activateStopLoss(newMarketPosition);
                }

                packingForShort = false;
                shortRangeHighStop = 0;
                shortRangeLowEntry = Double.MAX_VALUE;
                waitForNextCandle = true;
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

        if(candles.size() < 2 * ZZDepth + ZZBackstep + 1){
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



        updateZigZagValue(candles);

        if(ZZHigh == -1 || ZZLow == -1){
            return;
        }

        // --- LONG ---
        if(!waitForNextCandle && candle.tick() <= -DISTANCE){ // New lows for long
            if(longBreak){
                longRangeLowStop = Double.MAX_VALUE;
                longBreak = false;
            }
            packingForLong = true;

            longRangeHighEntry = Math.min(ZZHigh, candle.high());
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

            shortRangeLowEntry = Math.max(ZZLow, candle.low());
            shortRangeHighStop = Math.max(shortRangeHighStop, candle.high());
        } else {
            shortBreak = true;
        }

        if(waitForNextCandle){
            waitForNextCandle = false;
        }

    }

    private int ZZDepth = 2;
    private int ZZBackstep = 0;

    private ZigZag zz = new ZigZag(ZZDepth, 0, ZZBackstep, 0);

    public void updateZigZagValue(ArrayList<Candle> candles){

        List<Candle> candleSublist = candles.subList(
                candles.size()-1 - 2 * ZZDepth - ZZBackstep,
                candles.size());

        double[] highsArray = candleSublist.stream()
                .mapToDouble(Candle::high)
                .toArray();

        double[] lowsArray = candleSublist.stream()
                .mapToDouble(Candle::low)
                .toArray();

        zz.calculate(highsArray.length, highsArray, lowsArray);

        double zigZagValue = zz.getZigzagBuffer()[ZZDepth];

        if(zigZagValue != 0){

            Candle zigZagCandle = candles.get(candles.size()-1 - ZZBackstep - ZZDepth); // OK

            if(zigZagValue == zigZagCandle.high()){
                lastHighIndex = zigZagCandle.index();
                ZZHigh = zigZagValue;
            } else if(zigZagValue == zigZagCandle.low()) {
                lastLowIndex = zigZagCandle.index();
                ZZLow = zigZagValue;
            }
        }
    }
}