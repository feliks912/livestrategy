package com.localstrategy;

import com.localstrategy.util.enums.OrderSide;
import com.localstrategy.util.helper.BinaryTransactionLoader;
import com.localstrategy.util.helper.CandleConstructor;
import com.localstrategy.util.indicators.ZigZag;
import com.localstrategy.util.misc.TradingGUI;
import com.localstrategy.util.types.Candle;
import com.localstrategy.util.types.Position;
import com.localstrategy.util.types.SingleTransaction;
import org.apache.commons.collections4.map.SingletonMap;

import java.util.*;

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

    private boolean newLow = false;
    private boolean newHigh = false;


    private final ZigZag zz = new ZigZag(ZZDepth, 0, ZZBackstep, 0);

    private final Map<Double, Double> longOpportunities = new HashMap<>();
    private final Map<Double, Double> shortOpportunities = new HashMap<>();

    boolean pleaseLong = false;
    boolean pleaseShort = true;

    double longStop;
    double shortStop;

    public void priceUpdate(SingleTransaction transaction){

        boolean no = false;

        if(pleaseLong){
            for(Position pos : activePositions){
                if(pos.getDirection().equals(OrderSide.SELL)){
                    no = true;
                    break;
                }
            }
            if(!no){
                Position newPos = handler.executeMarketOrder(longStop, true);
                if(newPos != null){
                    handler.activateStopLoss(newPos);
                }
                positionCount++;

                pleaseLong = false;
            }
        }
        no = false;

        if(pleaseShort){
            for(Position pos : activePositions){
                if (pos.getDirection().equals(OrderSide.BUY) && !pos.isRepayRequestSent()) {
                    no = true;
                    break;
                }
            }
            if(!no){
                Position newPos = handler.executeMarketOrder(shortStop, true);
                if(newPos != null){
                    handler.activateStopLoss(newPos);
                }
                positionCount++;

                pleaseShort = false;
            }
        }

        this.transaction = transaction;

        ArrayList<Double> removeKey = new ArrayList<>();

        for(Map.Entry<Double, Double> entry : longOpportunities.entrySet()){
            if(transaction.price() > entry.getKey()){
                for(Position pos : activePositions){
                    if(pos.getDirection().equals(OrderSide.SELL) && !pos.isRepayRequestSent()){
                        handler.closePosition(pos);
                    }
                }

                pleaseLong = true;
                longStop = entry.getValue();

                removeKey.add(entry.getKey());
            }
        }
        for(Double key : removeKey){
            longOpportunities.remove(key);
        }
        removeKey.clear();

        for(Map.Entry<Double, Double> entry : shortOpportunities.entrySet()){
            if(transaction.price() < entry.getKey()){
                for(Position pos : activePositions){
                    if(pos.getDirection().equals(OrderSide.BUY) && !pos.isRepayRequestSent()){
                        handler.closePosition(pos);
                    }
                }

                pleaseShort = true;
                shortStop = entry.getValue();

                removeKey.add(entry.getKey());
            }
        }
        for(Double key : removeKey){
            shortOpportunities.remove(key);
        }
    }

    public void candleUpdate(Candle candle){

        if(DISPLAY_TRADING_GUI){
            tradingGUI.getCandlestickChart().newCandle(handler.getUserAssets().getMomentaryOwnedAssets(), candle, this.transaction);
        }

        if(candle.index() < 2 * zz.getDepth() + zz.getBackstep()){
            return;
        }

        double high = zz.getLastHigh();
        double low = zz.getLastLow();

        zz.updateZigZagValue(candles);

        if(high != zz.getLastHigh()){
            longOpportunities.put(zz.getLastHigh(), zz.getLastLow());
            shortOpportunities.clear();
        }
        if(low != zz.getLastLow()){
            shortOpportunities.put(zz.getLastLow(), zz.getLastHigh());
            longOpportunities.clear();
        }
    }
}