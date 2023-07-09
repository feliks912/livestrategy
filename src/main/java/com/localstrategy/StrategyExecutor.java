package com.localstrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.commons.math3.distribution.LogNormalDistribution;

import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.account.Order;

public class StrategyExecutor {

    private ArrayList<Double> portfolioList = new ArrayList<Double>();
    private ArrayList<Position> positions = new ArrayList<Position>();
    private ArrayList<Position> removePositions = new ArrayList<Position>();
    private ArrayList<Position> closedPositions = new ArrayList<Position>();
    private ArrayList<Position> positionsToTpFixedRR = new ArrayList<Position>();
    private ArrayList<Position> positionsToBreakeven = new ArrayList<Position>();
    private ArrayList<Position> positionsToTrailingProfit = new ArrayList<Position>();
    private ArrayList<Position> longPositionsToDiscard = new ArrayList<Position>();
    private ArrayList<Position> shortPositionsToDiscard = new ArrayList<Position>();
    private ArrayList<Position> longPositionsToTpRR = new ArrayList<Position>();
    private ArrayList<Position> shortPositionsToTpRR = new ArrayList<Position>();

    private ArrayList<Double> averageOrderDistanceList = new ArrayList<Double>();

    private ZigZag zigZagIndicator;

    private int distance;
    private int ZZDepth;
    private int ZZBackstep;
    private double tpRR;
    private double fixedRR;
    private double BEPercentage;

    private double portfolio;
    private double riskPercentage;
    public static double brokerCommissionRate;

    private double usedMargin;

    private double lastHigh = 0;
    private double lastLow = 0;
    private int lastHighIndex;
    private int lastLowIndex;

    private int topHighIndex = 0;
    private int bottomLowIndex = 0;

    private double rangeHigh = 0;
    private double rangeLow = 0;

    private double orderEntryPriceLong = 0;
    private double orderEntryPriceShort = 0;

    private double stopLossPriceLong = 0;
    private double stopLossPriceShort = 0;

    private boolean firstHighLowFound = false;
    private boolean usedHigh = true;
    private boolean usedLow = true;

    private int winCounter = 0;
    private int lossCounter = 0;
    private int breakevenCounter = 0;
    private int maxPositionCounter = 0;

    private int marginTestCounter = 0;

    private boolean marketLong = false;
    private boolean marketShort = false;
    private boolean limitShort = false;
    private boolean limitLong = false;

    private RiskManager riskManager = new RiskManager(positions);

    private double temporaryProfit = 0;

    public static int maxOrderId = 0;

    private static Random random = new Random();

    private long marketLongRequestTimestamp;
    private long marketShortRequestTimestamp;
    private long positionsToCloseRequestTimestamp;
    private long positionsToBreakevenRequestTimestamp;
    private long limitLongRequestTimestamp;
    private long limitShortRequestTimestamp;

    private double newCandleNextRequestLatency;
    private double newLongMarketTradeNextRequestLatency;
    private double newShortMarketTradeNextRequestLatency;
    private double newLongLimitTradeNextRequestLatency;
    private double newShortLimitTradeNextRequestLatency;

    private double newLongMarketPrice;
    private double newShortMarketPrice;

    private boolean shortTpRRInProgress = false;
    private boolean longTpRRInProgress = false;
    private boolean fixedRRInProgress = false;

    private boolean sayLimit = true;

    private boolean newCandle = false;

    private double startTime;

    private int dailyPositionCount = 0;
    
    public StrategyExecutor(int distance, int ZZDepth, int ZZBackstep, double tpRR, double fixedRR, double BEPercentage, double initialPortfolio, double riskPercentage, double brokerCommissionRate) {
        this.distance = distance;
        this.ZZDepth = ZZDepth;
        this.ZZBackstep = ZZBackstep;
        this.tpRR = tpRR;
        this.fixedRR = fixedRR;
        this.BEPercentage = BEPercentage;
        this.portfolio = initialPortfolio;
        this.riskPercentage = riskPercentage;
        StrategyExecutor.brokerCommissionRate = brokerCommissionRate;

        portfolioList.add(initialPortfolio);

        this.zigZagIndicator = new ZigZag(ZZDepth, 0, ZZBackstep, 0);


    }





















    public void priceUpdate(double currentDeviationMean, SingleTransaction currentTransaction, Candle previousCandle){

        if(positions.size() > maxPositionCounter){
            maxPositionCounter = positions.size();
        }

        if(startTime == 0){
            startTime = currentTransaction.getTimestamp();
        }

        if(portfolio <= 0){
            throw new RuntimeException("We lost all money.");
        }

        //FIXME: Add order timeoff equal to that required to open a position on binance.
        //FIXME: Smart move.

        //It seems actual price is +-0.01 all the time.
        double currentPrice = currentTransaction.getPrice() + 0.01 * (random.nextGaussian() < 0 ? -1 : 1);

        //TODO: Fixed percentage railing take profit, activated after breakeven, at 2% distance from the current price
        /* for(Position position : positions){
            if(!position.isClosed() && position.isFilled() && position.getBreakEven()){
                if(position.getDirection() == 1 && currentPrice > position.getStopLossPrice()){

                    if(position.getTrailingPct() == 0){
                        position.setTrailingPct(currentPrice);
                    }

                    double trailingPrice = currentPrice * (1 - position.getTrailingPct());

                    if(trailingPrice > position.getStopLossPrice()){
                        position.setStopLossPrice(trailingPrice);
                    }

                    
                }
                else if(position.getDirection() == -1 && currentPrice < position.getStopLossPrice()){

                    if(position.getTrailingPct() == 0){
                        position.setTrailingPct(currentPrice);
                    }

                    double trailingPrice = currentPrice * (1 + position.getTrailingPct());

                    if(trailingPrice < position.getStopLossPrice()){
                        position.setStopLossPrice(trailingPrice);
                    }

                }
            }
        } */

        for(Position position : positions){
            if( !position.isClosed() && 
                !position.isStoplossActive()){
                    if( !position.isClosedBeforeStoploss() &&
                        currentTransaction.getTimestamp() - position.getOpenTimestamp() > position.getExchangeLatency()){

                        position.setStoplossActive();
                    }
                    else if(position.isClosedBeforeStoploss() &&
                            currentTransaction.getTimestamp() - position.getClosedBeforeStoplossTimestamp() > position.getExchangeLatency()){

                        double closePrice = riskManager.getSlippagePrice(currentPrice, position.getSize(), position.getDirection() == 1 ? OrderSide.SELL : OrderSide.BUY);

                        double profit = position.closePosition(previousCandle.getIndex() + 1, currentTransaction.getTimestamp(), closePrice);

                        portfolio += profit;
                        usedMargin -= position.getMargin();

                        removePositions.add(position);
                    }
            }
        }

        if(longTpRRInProgress && !longPositionsToTpRR.isEmpty() && currentTransaction.getTimestamp() - marketShortRequestTimestamp > newShortMarketTradeNextRequestLatency){
            for(Position position : longPositionsToTpRR){
                if(!position.isClosed()){
                    
                    double closePrice = riskManager.getSlippagePrice(currentPrice, position.getSize(), OrderSide.SELL);

                    double profit = position.closePosition(previousCandle.getIndex() + 1, currentTransaction.getTimestamp(), closePrice);
                    portfolio += profit;
                    usedMargin -= position.getMargin();

                    removePositions.add(position);
                }
            }
            longPositionsToTpRR.clear();
            longTpRRInProgress = false;
        }

        if(shortTpRRInProgress && !shortPositionsToTpRR.isEmpty() && currentTransaction.getTimestamp() - marketLongRequestTimestamp > newLongMarketTradeNextRequestLatency){
            for(Position position : shortPositionsToTpRR){
                if(!position.isClosed()){
                    
                    double closePrice = riskManager.getSlippagePrice(currentPrice, position.getSize(), OrderSide.BUY);

                    double profit = position.closePosition(previousCandle.getIndex() + 1, currentTransaction.getTimestamp(), closePrice);
                    portfolio += profit;
                    usedMargin -= position.getMargin();

                    removePositions.add(position);
                }
            }
            shortPositionsToTpRR.clear();
            shortTpRRInProgress = false;
        }

        //From new candle
        if(newCandle && currentTransaction.getTimestamp() - previousCandle.getTimestamp() > newCandleNextRequestLatency){
            if(!positionsToTpFixedRR.isEmpty()){
                for(Position position : positionsToTpFixedRR){
                    if(!position.isClosed()){

                        double closePrice = riskManager.getSlippagePrice(currentPrice, position.getSize(), position.getDirection() == 1 ? OrderSide.SELL : OrderSide.BUY);

                        double profit = position.closePosition(previousCandle.getIndex() + 1, currentTransaction.getTimestamp(), closePrice);
                        portfolio += profit;
                        usedMargin -= position.getMargin();

                        removePositions.add(position);
                    }
                }
                positionsToTpFixedRR.clear();
            }
            
            if(!positionsToBreakeven.isEmpty()){
                for(Position position : positionsToBreakeven){
                    if(!position.isClosed()){ //Could be closed in the meantime //FIXME: test if this works.
                        position.setStopLossPrice(
                            position.getInitialStopLossPrice() + 
                            BEPercentage * (position.getEntryPrice() - position.getInitialStopLossPrice()));
                        position.setBreakevenFlag(true);
                    }
                }
                positionsToBreakeven.clear();
            }

            if(!positionsToTrailingProfit.isEmpty()){
                for(Position position : positionsToTrailingProfit){
                    if(!position.isClosed()){
                        /* if(position.getDirection() == 1 && previousCandle.getLow() > position.getStopLossPrice()){
                            position.setStopLossPrice(previousCandle.getLow());
                        } 
                        else if(position.getDirection() == -1 && previousCandle.getHigh() < position.getStopLossPrice()){
                            position.setStopLossPrice(previousCandle.getHigh());
                        } */

                        double trailingPrice = position.getStopLossPrice() + ((previousCandle.getClose() - position.getStopLossPrice()) * 0.6);

                        if(position.getDirection() == 1 && trailingPrice > position.getStopLossPrice()){
                            position.setStopLossPrice(trailingPrice);
                        } 
                        else if(position.getDirection() == -1 && trailingPrice < position.getStopLossPrice()) {
                            position.setStopLossPrice(trailingPrice);
                        }

                        /* if(position.getDirection() == 1 && lastLow > position.getStopLossPrice()){
                            position.setStopLossPrice(lastLow);
                        }
                        else if(position.getDirection() == -1 && lastHigh < position.getStopLossPrice()){
                            position.setStopLossPrice(lastHigh);
                        } */
                    }
                }
                positionsToTrailingProfit.clear();
            }

            newCandle = false;
        }

        // Check stoplosses and fills
        for(Position position : positions){
            //Check position stoploss
            if(!position.isClosed()){

                if((position.getDirection() == 1 && currentPrice <= position.getStopLossPrice()) ||
                    position.getDirection() == -1 && currentPrice >= position.getStopLossPrice()){

                    if(position.isStoplossActive()){
                        double stopPrice = riskManager.getSlippagePrice(currentPrice, position.getSize(), position.getDirection() == 1 ? OrderSide.SELL : OrderSide.BUY);

                        double profit = position.closePosition(previousCandle.getIndex() + 1, currentTransaction.getTimestamp(), stopPrice);
                        temporaryProfit += profit;
                        portfolio += profit;
                        usedMargin -= position.getMargin();

                        removePositions.add(position);
                    } else {
                        position.setClosedBeforeStoploss(currentTransaction.getTimestamp());
                    }
                }
            }

            //Fill limit orders
            /* if(!position.isClosed() && !position.isFilled()) {
                if((position.getDirection() == 1 && currentPrice <= position.getEntryPrice()) ||
                    position.getDirection() == -1 && currentPrice >= position.getEntryPrice()) {

                    riskManager.calculateBorrows(currentPrice);
                    position.setMargin(position.getEntryPrice() * position.getSize() / riskManager.getCurrentLeverage());
                    position.setBorrowRatio(riskManager.borrowRatio());

                    if(portfolio - usedMargin > position.getMargin()){
                        usedMargin += position.fillPosition(previousCandle.getIndex() + 1);

                        if(riskManager.calculateMarginLevel(currentPrice, currentTransaction.getTimestamp(), portfolio - usedMargin) < 1.1){
                            position.closePosition(previousCandle.getIndex() + 1, currentTransaction.getTimestamp(), position.getEntryPrice());
                            usedMargin -= position.getMargin();
                            removePositions.add(position);
                        } else {
                            portfolio -= position.payCommission();
                        }
                    } else {
                        position.closePosition(previousCandle.getIndex() + 1, currentTransaction.getTimestamp(), position.getEntryPrice());
                        removePositions.add(position);
                    }

                    //FIXME: Transfer to next transaction
                    for(Position pos : positions) {
                        if(!pos.isClosed() && pos.getDirection() != position.getDirection()) {
                            if(pos.isFilled() && pos.calculateRR(position.getEntryPrice()) > tpRR) {
                                
                                double profit = position.closePosition(previousCandle.getIndex() + 1, currentTransaction.getTimestamp(), currentPrice); //Close the position at entry price
                                temporaryProfit += profit;
                                portfolio += profit;
                                usedMargin -= position.getMargin(); //   Reduce used margin by the margin used by the position
                                removePositions.add(position);
                            }
                            else if(!pos.isFilled()){ //Discard unfilled positions
                                pos.closePosition(previousCandle.getIndex() + 1, currentTransaction.getTimestamp(), pos.getEntryPrice()); //   Discard

                                removePositions.add(pos);
                            }
                        }
                    }    
                }
            } */
        }

        //FIXME: Edit this so positions are saved during investigation otherwise this lowers the memory requirements drastically
        if(!removePositions.isEmpty()){
            for(Position position : removePositions){ //All relevant positions are now in closedPositions
                if(position.isFilled()){
                    double profit = position.getProfit();
                    if(profit == 0){
                        breakevenCounter++;
                    } else if (profit > 0){
                        winCounter++;
                    } else if (profit < 0){
                        lossCounter++;
                    }
                }
            }
            positions.removeAll(removePositions);
            removePositions.clear();
        }

        //FIXME: Cheating but slow otherwise
        if(++marginTestCounter > 10){
            double marginLevel = riskManager.calculateMarginLevel(currentPrice, portfolio - usedMargin);
            if(marginLevel < 1.05){
                System.out.println("Liquidation risk call, liquidating all open positions with loss");
                for(Position position : positions){
                    portfolio += position.closePosition(previousCandle.getIndex() + 1, currentTransaction.getTimestamp(), position.getInitialStopLossPrice());
                    usedMargin -= position.getMargin();
                }
            } else if(marginLevel > 1.05 && marginLevel < 1.1){
                System.out.println("Margin call.");
            }
        }
        /* if(++marginTestCounter > 10){
            double marginLevel = riskManager.calculateMarginLevel(currentPrice, portfolio - usedMargin);
            if(marginLevel < 1.1 && marginLevel > 1.05){
                System.out.println("marginLevel correction attempt");
                while(marginLevel < 1.1){
                    for(Position position : positions){
                        if(!position.isClosed() && position.isFilled()){

                            usedMargin -= position.getMargin();
                            portfolio += position.partialClose(previousCandle.getIndex(), currentTransaction.getTimestamp(), currentPrice, 
                                0.95);
                            usedMargin += position.getMargin();

                        }
                    }
                    marginLevel = riskManager.calculateMarginLevel(currentPrice, portfolio - usedMargin);
                }   
                
            } else if (marginLevel < 1.05){
                System.out.println("Liquidation risk call, liquidating all open positions with loss");
                for(Position position : positions){
                    portfolio += position.closePosition(previousCandle.getIndex() + 1, currentTransaction.getTimestamp(), position.getInitialStopLossPrice());
                    usedMargin -= position.getMargin();
                }
            }
            marginTestCounter = 0;
        } */

        //TODO: Check if stoplosses are broken
        if(currentPrice < stopLossPriceLong && usedHigh == false){
            usedHigh = true;
        }
        if(currentPrice > stopLossPriceShort && usedLow == false){
            usedLow = true;
        }

        //TODO: In theory market orders are made at the price of the next transaction, we cannot execute them at the currentPrice because currentPrice is the price of the current transaction which in the meantime moved the price further.
        // A 300 millisecond time off between requesting an order and executing it on the then current market price
        /* if(marketShort && currentTransaction.getTimestamp() - marketShortRequestTimestamp > newShortMarketTradeNextRequestLatency){
            marketShort = false;

            double slippagePrice = calculateSlippagePrice(currentPrice);

            if(slippagePrice < stopLossPriceShort){
                makeOrder(slippagePrice, stopLossPriceShort, "market", previousCandle.getIndex() + 1, currentTransaction.getTimestamp());
            }
        } */

        /* if(marketLong && currentTransaction.getTimestamp() - marketLongRequestTimestamp > newLongMarketTradeNextRequestLatency){
            marketLong = false;
            
            double slippagePrice = calculateSlippagePrice(currentPrice);

            if(slippagePrice > stopLossPriceLong){
                makeOrder(slippagePrice, stopLossPriceLong, "market", previousCandle.getIndex() + 1, currentTransaction.getTimestamp());
            }
        }

        /* if(limitShort && currentTransaction.getTimestamp() - limitShortRequestTimestamp > newShortLimitTradeNextRequestLatency){
            limitShort = false;

            double slippagePrice = calculateSlippagePrice(currentPrice);

            if(slippagePrice < stopLossPriceShort){
                makeOrder(slippagePrice, stopLossPriceShort, "limit", previousCandle.getIndex() + 1, currentTransaction.getTimestamp());
            }
        }

        if(limitLong && currentTransaction.getTimestamp() - limitLongRequestTimestamp > newLongLimitTradeNextRequestLatency){
            limitLong = false;
            
            double slippagePrice = calculateSlippagePrice(currentPrice);

            if(slippagePrice > stopLossPriceLong){
                makeOrder(slippagePrice, stopLossPriceLong, "limit", previousCandle.getIndex() + 1, currentTransaction.getTimestamp());
            }
        } */

        // Check if price triggers entry levels for longs
        if(orderEntryPriceShort != 0 && stopLossPriceShort != 0 && firstHighLowFound){
            if( !usedLow && 
                //topHighIndex == lastHighIndex && 
                lastHigh > lastLow){
                if(previousCandle.getClose() < stopLossPriceShort && currentPrice < stopLossPriceShort){
                    if(previousCandle.getClose() >= orderEntryPriceShort){
                        if(currentPrice <= orderEntryPriceShort){
                            usedLow = true;
                            marketShort = true;

                            newShortMarketPrice = currentPrice;

                            marketShortRequestTimestamp = currentTransaction.getTimestamp();

                            checktpRR(currentPrice, OrderSide.SELL);

                            newShortMarketTradeNextRequestLatency = calculateTradeRequestLatency() + calculateTradeEventLatency();

                            makeOrder(orderEntryPriceShort, stopLossPriceShort, "market", previousCandle.getIndex() + 1, currentTransaction.getTimestamp());
                        }
                    } else if(previousCandle.getClose() < orderEntryPriceShort && currentPrice < orderEntryPriceShort){
                        usedLow = true;

                        if(sayLimit){
                            System.out.println("Using limit trigger");
                            sayLimit = false;
                        }

                        //makeOrder(orderEntryPriceShort, stopLossPriceShort, "limit", previousCandle.getIndex() + 1, currentTransaction.getTimestamp());
                    }
                }
            }
        }
        
        // Check if price triggers entry levels for shorts
        if(orderEntryPriceLong != 0 && stopLossPriceLong != 0 && firstHighLowFound){
            if( !usedHigh && 
                //bottomLowIndex == lastLowIndex && 
                lastHigh > lastLow){
                if(previousCandle.getClose() > stopLossPriceLong && currentPrice > stopLossPriceLong){
                    if(previousCandle.getClose() <= orderEntryPriceLong){
                        if(currentPrice >= orderEntryPriceLong){
                            usedHigh = true;
                            marketLong = true;

                            newLongMarketPrice = currentPrice;

                            marketLongRequestTimestamp = currentTransaction.getTimestamp();

                            checktpRR(currentPrice, OrderSide.BUY);

                            newLongMarketTradeNextRequestLatency = calculateTradeRequestLatency() + calculateTradeEventLatency();

                            makeOrder(orderEntryPriceLong, stopLossPriceLong, "market", previousCandle.getIndex() + 1, currentTransaction.getTimestamp());
                        }
                    } else if(previousCandle.getClose() > orderEntryPriceLong && currentPrice > orderEntryPriceLong){
                        usedHigh = true;

                        if(sayLimit){
                            System.out.println("Using limit trigger");
                            sayLimit = false;
                        }

                        //makeOrder(orderEntryPriceLong, stopLossPriceLong, "limit", previousCandle.getIndex() + 1, currentTransaction.getTimestamp());
                    }
                }
            }
        }
    }





























    public void newCandle(SingleTransaction lastTransaction, ArrayList<Candle> candles){

        newCandle = true;

        //TODO: Calculate interest rate on every hour.
        //FIXME: it's done on every round hour not every hour but it is what it is

        newCandleNextRequestLatency = calculateTradeRequestLatency() + calculateTradeEventLatency();

        if(lastTransaction.getTimestamp() - startTime > 1000 * 60 * 60){
            startTime = lastTransaction.getTimestamp();

            for(Position position : positions){
                if(!position.isClosed() && position.isFilled()){
                    position.increaseUnpaidInterest(lastTransaction.getPrice());
                }
            }
        }

        if(candles.size() - 1 < 2*ZZDepth + ZZBackstep){
            return;
        }

        Candle candle = candles.get(candles.size() - 1); //OK

        
        //Take TPs
        //FIXME: Transfer to next transaction
        for(Position position : positions){
            if(!position.isClosed() && position.isFilled()){
                if(position.calculateRR(candle.getClose()) > fixedRR){

                    //TODO: Add deviation to the close price
                    positionsToTpFixedRR.add(position);
                }
            }

            if(!position.isClosed() && position.isFilled()){
                if(!position.isBreakevenSet()){
                    if((position.getDirection() == 1 && candle.getClose() > position.getEntryPrice()) ||
                        position.getDirection() == -1 && candle.getClose() < position.getEntryPrice()) {

                        positionsToBreakeven.add(position);
                    }
                } 
                else {
                    positionsToTrailingProfit.add(position);
                }   
            }

            /* else if(!position.isClosed() && position.isFilled() && position.getBreakEven()){
                if(position.getDirection() == 1){
                    position.setStopLossPrice(stopLossPriceLong);
                } else {

                }
            } */
        }

        

        portfolioList.add(portfolio);

        if(!firstHighLowFound && lastLow != 0 && lastHigh != 0){
            firstHighLowFound = true;
        }

        if(firstHighLowFound){
            if(candle.getTick() < -distance){
                bottomLowIndex = candle.getIndex(); //Save the candle index

                stopLossPriceLong = candle.getLow();

                rangeHigh = candle.getHigh();

                for (int i = 1; i < candles.size(); i++) {  // Iterate backwards from bottomLowIndex to 0
                    Candle c = candles.get(candles.size() - i); //FIX
                    if (c.getTick() < -distance) {
                        if(c.getHigh() > rangeHigh){
                            rangeHigh = c.getHigh();
                        }
                    } else {
                        break;  // Exit the loop as soon as getTick() > -distance
                    }
                }

                usedHigh = false; //Entry switch on
            }
            
            if (candle.getTick() > distance){ //'new' high
                topHighIndex = candle.getIndex(); //Save the candle index

                stopLossPriceShort = candle.getHigh();

                rangeLow = candle.getLow();
                
                for (int i = 1; i < candles.size(); i++) {  // Iterate backwards from bottomLowIndex to 0
                    Candle c = candles.get(candles.size() - i); //FIX
                    if (c.getTick() > distance) {
                        if(c.getLow() < rangeLow){
                            rangeLow = c.getLow();
                        }
                    } else {
                        break;  // Exit the loop as soon as getTick() < distance
                    }
                }

                usedLow = false; //Entry switch on
            }
        }
        updateZigZagValue(candles);

        if(lastLow != 0){
            orderEntryPriceShort = lastLow;
        }
        if(lastHigh != 0){
            orderEntryPriceLong = lastHigh;
        }


        /* if(topHighIndex != 0 && rangeLow != 0 && lastLow != 0){
            orderEntryPriceShort = rangeLow > lastLow ? rangeLow : lastLow;
        }
        if(bottomLowIndex != 0 && rangeHigh != 0 && lastHigh != 0){
            orderEntryPriceLong = rangeHigh < lastHigh ? rangeHigh : lastHigh; // Set the appropriate order limit
        } */

        
    }

    public ArrayList<Double> endStrategy(SingleTransaction lastTransaction, Candle lastCandle, String outputCSVPath){
        for(Position position : positions){
            if(!position.isClosed() && position.isFilled()){
                double profit = position.closePosition(lastCandle.getIndex(), lastTransaction.getTimestamp(), lastCandle.getClose());
                temporaryProfit += profit;
                portfolio += profit;

                if(profit == 0){
                    breakevenCounter++;
                } else if (profit > 0){
                    winCounter++;
                } else if (profit < 0){
                    lossCounter++;
                }
            }
        }

        portfolioList.add(portfolio);

        /* if(outputCSVPath != null){
            ResultConsolidator.writePositionsToCSV(closedPositions, outputCSVPath);
        } */

        return portfolioList;
    }

    private void checktpRR(double currentPrice, OrderSide orderSide){
        for(Position position : positions){
            if(!position.isClosed() &&
                ((position.getDirection() == -1 && orderSide.equals(OrderSide.BUY)) ||  //  Against short position
                (position.getDirection() == 1 && orderSide.equals(OrderSide.SELL)))){ //   Against long position

                if(position.isFilled() && position.calculateRR(currentPrice) > tpRR) {
                    if(orderSide.equals(OrderSide.BUY)){
                        shortPositionsToTpRR.add(position);
                        shortTpRRInProgress = true;
                    } else {
                        longPositionsToTpRR.add(position);
                        longTpRRInProgress = true;
                    }
                } 
                else if(!position.isFilled()) { //Discard unfilled limit orders
                    if(orderSide.equals(OrderSide.BUY)){
                        shortPositionsToDiscard.add(position);
                    } else {
                        longPositionsToDiscard.add(position);
                    }
                }
            }
        }
    }


    //makeOrder already executed with slippage
    private void makeOrder(double entryPrice, double stopLossPrice, String orderType, int entryIndex, long entryTimestamp){

        int positionsSize = positions.size();

        averageOrderDistanceList.add(Math.abs(entryPrice - stopLossPrice));

        OrderMaker.newOrder(
            positions,
            entryIndex,
            orderType,
            entryPrice,
            stopLossPrice,
            portfolio,
            riskPercentage,
            usedMargin,
            entryTimestamp,
            calculateTradeEventLatency() + calculateTradeRequestLatency(),
            riskManager
        );

        if(positions.size() > positionsSize && orderType.equals("market")) { // Successfully created a position
            usedMargin += positions.get(positions.size() - 1).getMargin();

            if(riskManager.calculateMarginLevel(entryPrice, portfolio - usedMargin) < 1.1){
                positions.get(positions.size() - 1).closePosition(entryIndex, entryTimestamp, entryPrice); //Cancel last position
                usedMargin -= positions.get(positions.size() - 1).getMargin();
                positions.remove(positions.get(positions.size() - 1));
            } else {
                portfolio -= positions.get(positions.size() - 1).payCommission();
                dailyPositionCount++;
            }
        }
    }





















    public void updateZigZagValue(ArrayList<Candle> candles){

        List<Candle> candleSublist = candles.subList(
                            candles.size()-1 - 2 * ZZDepth - ZZBackstep, 
                            candles.size());

        double[] highsArray = candleSublist.stream()
                .mapToDouble(Candle::getHigh)
                .toArray();

        double[] lowsArray = candleSublist.stream()
                .mapToDouble(Candle::getLow)
                .toArray();

        zigZagIndicator.calculate(highsArray.length, highsArray, lowsArray);

        double zigZagValue = zigZagIndicator.getZigzagBuffer()[ZZDepth];

        if(zigZagValue != 0){

            Candle zigZagCandle = candles.get(candles.size()-1 - ZZBackstep - ZZDepth); // OK

            if(zigZagValue == zigZagCandle.getHigh()){
                lastHighIndex = zigZagCandle.getIndex();
                lastHigh = zigZagValue;
            } else if(zigZagValue == zigZagCandle.getLow()) {
                lastLowIndex = zigZagCandle.getIndex();
                lastLow = zigZagValue;
            }
        }
    }

    public double calculateTradeRequestLatency(){
        double requestLatencyTradePlace;
        
        do{
            requestLatencyTradePlace = 25 + random.nextGaussian() * 10;
        } while(requestLatencyTradePlace < 15 || requestLatencyTradePlace > 35);

        return requestLatencyTradePlace;
    }

    public double calculateTradeEventLatency(){
        double requestLatencyTradeEvent;

        do{
            requestLatencyTradeEvent = 20 + random.nextGaussian() * 10;
        } while(requestLatencyTradeEvent < 10 || requestLatencyTradeEvent > 30);

        return requestLatencyTradeEvent;
    }

    public double getPortfolio(){
        return this.portfolio;
    }

    public int getWinCounter() {
        return this.winCounter;
    }

    public int getLossCounter() {
        return this.lossCounter;
    }

    public int getBreakevenCounter() {
        return this.breakevenCounter;
    }

    public int getMaxPositionCounter(){
        return this.maxPositionCounter;
    }

    public double getUsedMargin(){
        return this.usedMargin;
    }

    public ArrayList<Position> getPositions(){
        return this.positions;
    }

    public ArrayList<Position> getClosedPositions(){
        return this.closedPositions;
    }

    public int getDepth(){
        return this.ZZDepth;
    }

    public int getBackstep(){
        return this.ZZBackstep;
    }

    public double getTemporaryProfit(){
        return this.temporaryProfit;
    }

    public void resetTemporaryProfit(){
        this.temporaryProfit = 0;
    }

    public int getMaximumPositionsCount(){
        return this.maxPositionCounter;
    }

    public void resetMaximumPositionsCount(){
        this.maxPositionCounter = 0;
    }

    public ArrayList<Double> getAverageOrderDistanceList(){
        return this.averageOrderDistanceList;
    }

    public void resetAverageOrderDistanceList(){
        this.averageOrderDistanceList.clear();
    }

    public int getDailyPositionCount(){
        return this.dailyPositionCount;
    }

    public void resetDailyPositionCount(){
        this.dailyPositionCount = 0;
    }

}
