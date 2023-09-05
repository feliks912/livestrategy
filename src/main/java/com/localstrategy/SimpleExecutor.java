package com.localstrategy;

import com.localstrategy.util.enums.EventDestination;
import com.localstrategy.util.enums.OrderSide;
import com.localstrategy.util.helper.*;
import com.localstrategy.util.indicators.ZigZag;
import com.localstrategy.util.misc.TradingGUI;
import com.localstrategy.util.types.Candle;
import com.localstrategy.util.types.Event;
import com.localstrategy.util.types.Position;
import com.localstrategy.util.types.SingleTransaction;
import org.apache.commons.collections4.map.SingletonMap;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

public class SimpleExecutor {

    private final int ZZDepth = 2;
    private final int ZZBackstep = 0;

    private final ZigZag zz = new ZigZag(ZZDepth, 0, ZZBackstep, 0);

    private final BinaryTransactionLoader loader = new BinaryTransactionLoader("C:\\--- BTCTUSD", null, null);

    private final CandleConstructor constructor = new CandleConstructor(100_000);

    private final ArrayList<Candle> candles = new ArrayList<>();

    enum Side{
        LONG,
        SHORT
    }

    public enum State{
        NEW,
        FILLED,
        CLOSED
    }

    record Pair(double high, double low){}

    private final ArrayList<Pair> pairs = new ArrayList<>();

    private final ArrayList<SimplePosition> activePositions = new ArrayList<>();
    private final ArrayList<SimplePosition> closedPositions = new ArrayList<>();

    record Delay(long requestTimestamp, long requestDelay){}
    private final Map<SimplePosition, Delay> posToClose = new HashMap<>();

    private final ArrayList<Map.Entry<Long, Double>> moneys = new ArrayList<>();

    private final ArrayList<SingletonMap<Long, Double>> closePrices = new ArrayList<>();

    private final Map<SimplePosition, Delay> posToBE = new HashMap<>();

    double freeMoney = 1000000;
    double lockedMoney = 0;
    static double risk = 0.3 / 100;
    int delay = 35;
    private int day = 0;

    private final boolean DISPLAY_GUI = false;

    long iteration = 0;

    ArrayList<Position> actPos = new ArrayList<>();
    LinkedList<Position> inactPos = new LinkedList<>();

    private final int PRICE_DIFF = 5;

    long initialTimestamp = 0;

    private int positionCounter = 0;

    BinaryOrderbookReader reader;

    private TradingGUI gui;
    public SimpleExecutor() throws IOException {

        LatencyProcessor.instantiateLatencies("C:\\Users\\Admin\\Desktop\\livestrategy\\src\\main\\java\\Resources\\only_latencies_fixed.csv");

        reader = new BinaryOrderbookReader("C:\\Users\\Admin\\Desktop\\livestrategy\\src\\main\\java\\Resources\\orderbook.bin");

        if(DISPLAY_GUI){
            gui = new TradingGUI(new ArrayList<>(), inactPos, 100000, 30);
        }
        while(loader.getRemainingFileCount() != 0){
            ArrayList<SingleTransaction> transactions = new ArrayList<>(loader.loadNextDay());
            for(SingleTransaction transaction : transactions){

                if(initialTimestamp == 0){
                    initialTimestamp = transaction.timestamp();
                }

                try{
                    reader.loadUntil(transaction.timestamp() - initialTimestamp);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                LatencyProcessor.calculateLatency(new Event(
                        transaction.timestamp(),
                        EventDestination.EXCHANGE,
                        transaction
                ));
                delay = LatencyProcessor.getCurrentLatency();

                Candle candle = constructor.processTradeEvent(transaction);
                if(candle != null){

                    candles.add(candle);
                    if(DISPLAY_GUI){
                        gui.getCandlestickChart().newCandle(freeMoney + lockedMoney, candle, transaction);
                    }

//                    for(SimplePosition pos : activePositions){
//                        if(pos.state.equals(State.FILLED)){
//                            if(pos.side.equals(Side.LONG)){
//                                if(candle.close() > pos.entry){
//                                    posToBE.put(pos, new Delay(transaction.timestamp(), delay));
//                                }
//                            } else {
//                                if(candle.close() < pos.entry){
//                                    posToBE.put(pos, new Delay(transaction.timestamp(), delay));
//                                }
//                            }
//                        }
//                    }

                    if(candle.index() >= 2 * ZZDepth + ZZBackstep){
                        closePrices.add(new SingletonMap<>(candle.timestamp(), candle.close()));

                        double high = zz.getLastHigh();
                        double low = zz.getLastLow();

                        zz.updateZigZagValue(candles);

                        if(zz.getLastLow() != -1 && zz.getLastHigh() != -1) {
                            if(candles.size() > 1000){
                                candles.remove(0);
                            }

                            if((high != zz.getLastHigh() || low != zz.getLastLow()) && Math.abs(zz.getLastHigh() - zz.getLastLow()) > PRICE_DIFF){
                                pairs.add(new Pair(zz.getLastHigh(), zz.getLastLow()));
                            }
                        } else {
                            continue;
                        }
                    }
                }

                ArrayList<Pair> tempPairs = new ArrayList<>();

                for(Pair pair : pairs){

                    double high = pair.high;
                    double low = pair.low;

                    if(transaction.price() >= high && Math.abs(transaction.price() - low) > PRICE_DIFF){

                        SimplePosition pos = null;
                        if(activePositions.size() < 5){
                            pos = new SimplePosition(transaction.price(), low, freeMoney * risk, transaction.timestamp(), delay);
                            freeMoney -= pos.lockedMoney;
                            lockedMoney += pos.lockedMoney;
                        }

                        for(SimplePosition p : activePositions){
                            if(
                                    p.side.equals(Side.SHORT) &&
                                    p.state.equals(State.FILLED)){
                                posToClose.put(p, new Delay(transaction.timestamp(), delay));
                            }
                        }

                        if(pos != null){
                            activePositions.add(pos);
                        }

                        tempPairs.add(pair);
                    } else if(transaction.price() <= low && Math.abs(transaction.price() - high) > PRICE_DIFF){

                        SimplePosition pos = null;
                        if(activePositions.size() < 5){
                            pos = new SimplePosition(transaction.price(), high, freeMoney * risk, transaction.timestamp(), delay);
                            freeMoney -= pos.lockedMoney;
                            lockedMoney += pos.lockedMoney;
                        }

                        for(SimplePosition p : activePositions){
                            if(
                                    p.side.equals(Side.LONG) &&
                                    p.state.equals(State.FILLED)){
                                posToClose.put(p, new Delay(transaction.timestamp(), delay));
                            }
                        }

                        if(pos != null){
                            activePositions.add(pos);
                        }

                        tempPairs.add(pair);
                    }
                }
                pairs.removeAll(tempPairs);

                ArrayList<SimplePosition> tempPoss = new ArrayList<>();
                for(SimplePosition pos : activePositions){
                    if(pos.state.equals(State.NEW) && transaction.timestamp() - pos.openTime >= pos.openDelay){
                        double entry = pos.entry;
                        pos.entry = SlippageHandler.getSlippageFillFromBook(BigDecimal.valueOf(transaction.price()), BigDecimal.valueOf(pos.size), pos.side.equals(Side.LONG) ? OrderSide.BUY : OrderSide.SELL).doubleValue();
                        pos.state = State.FILLED;
                        positionCounter++;
                    } else if(pos.state.equals(State.FILLED)){
                        if(pos.side.equals(Side.LONG)){
                            if(transaction.price() <= pos.stop){
                                double stop = pos.stop;
                                double closePrice =  SlippageHandler.getSlippageFillFromBook(BigDecimal.valueOf(pos.stop), BigDecimal.valueOf(pos.size), pos.side.equals(Side.LONG) ? OrderSide.SELL : OrderSide.BUY).doubleValue();
                                pos.closePosition(closePrice);
                                lockedMoney -= pos.lockedMoney;
                                freeMoney += pos.lockedMoney + pos.profit;

                                moneys.add(new AbstractMap.SimpleEntry<>(transaction.timestamp(), freeMoney));

                                tempPoss.add(pos);
                            }
                        } else {
                            if(transaction.price() >= pos.stop){
                                double stop = pos.stop;
                                double closePrice =  SlippageHandler.getSlippageFillFromBook(BigDecimal.valueOf(pos.stop), BigDecimal.valueOf(pos.size), pos.side.equals(Side.LONG) ? OrderSide.SELL : OrderSide.BUY).doubleValue();
                                pos.closePosition(closePrice);
                                lockedMoney -= pos.lockedMoney;
                                freeMoney += pos.lockedMoney + pos.profit;

                                moneys.add(new AbstractMap.SimpleEntry<>(transaction.timestamp(), freeMoney));

                                tempPoss.add(pos);
                            }
                        }
                    }
                }
                activePositions.removeAll(tempPoss);
                //closedPositions.addAll(tempPoss);
                for(SimplePosition pos : tempPoss){
                    posToClose.remove(pos);
                    posToBE.remove(pos);
                }

                tempPoss.clear();

                for(Map.Entry<SimplePosition, Delay> e : posToClose.entrySet()){
                    SimplePosition pos = e.getKey();
                    Delay d = e.getValue();
                    if(transaction.timestamp() - d.requestTimestamp > d.requestDelay){
                        double close = transaction.price();
                        double closePrice =  SlippageHandler.getSlippageFillFromBook(BigDecimal.valueOf(transaction.price()), BigDecimal.valueOf(pos.size), pos.side.equals(Side.LONG) ? OrderSide.SELL : OrderSide.BUY).doubleValue();
                        pos.closePosition(closePrice);
                        lockedMoney -= pos.lockedMoney;
                        freeMoney += pos.lockedMoney + pos.profit;

                        moneys.add(new AbstractMap.SimpleEntry<>(transaction.timestamp(), freeMoney));

                        tempPoss.add(pos);
                    }
                }
                for(SimplePosition pos : tempPoss){
                    posToClose.remove(pos);
                }
                activePositions.removeAll(tempPoss);
                //closedPositions.addAll(tempPoss);
                tempPoss.clear();

                for(Map.Entry<SimplePosition, Delay> e : posToBE.entrySet()){
                    SimplePosition pos = e.getKey();
                    Delay d = e.getValue();
                    if(transaction.timestamp() - d.requestDelay > d.requestDelay){
                        pos.stop = pos.entry;
                        tempPoss.add(pos);
                    }
                }
                for(SimplePosition pos : tempPoss){
                    posToBE.remove(pos);
                }
            }
            System.out.println("Day " + (++day) + ", moneys: " + (freeMoney + lockedMoney) +", total positions: " + positionCounter);
            positionCounter = 0;
        }
        PortfolioPlotter.plot(moneys, closePrices, "QuickChart");
    }
}
