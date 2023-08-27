package com.localstrategy;

import com.localstrategy.util.helper.DrawdownCalculator;
import com.localstrategy.util.helper.PortfolioPlotter;
import com.localstrategy.util.indicators.ZigZag;
import com.localstrategy.util.types.UserAssets;
import org.apache.commons.collections4.map.SingletonMap;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;

public class App {

    public static ZigZag zz = new ZigZag(5, 0, 0, 0);

    public static void main(String[] args) {

//        String[] fileNames = {"250k", "500k", "1M", "2M", "4M"};
//
//        for(String name : fileNames){
//
//            new Thread(() ->{
//                ArrayList<Candle> candles = new ArrayList<>(CandleFileManager.readCandles("C:\\BTCCandles\\" + name + "All.bin", 0, Integer.MAX_VALUE));
//
//                System.out.println("Filename: " + name + "All.bin");
//
//                boolean packingHigh = false;
//                boolean packingLow = false;
//
//                int firstLargerIndex = 0;
//                int lastLargerIndex = 0;
//
//                int firstLowerIndex = 0;
//                int lastLowerIndex = 0;
//
//                enum LastGroup{
//                    NONE,
//                    HIGH,
//                    LOW
//                }
//
//                LastGroup lastGroup = LastGroup.NONE;
//
//                int ZZDepth;
//                int ZZBackstep;
//
//                double highStop = 0;
//                double lowStop = Double.MAX_VALUE;
//
//                boolean shortBreak = false;
//                boolean longBreak = false;
//
//                for(int DISTANCE = 10; DISTANCE <= 1000; DISTANCE += 10){
//                    int failureCounter = 0;
//                    int successCounter = 0;
//
//                    for(Candle candle : candles){
//                        if(candle.tick() > DISTANCE){
//                            if(!packingHigh){
//                                highStop = 0;
//
//                                if(lastGroup == LastGroup.HIGH){
//
//                                    failureCounter++;
//
//                                    lastGroup = LastGroup.NONE;
//                                } else if(lastGroup == LastGroup.LOW){
//                                    successCounter++;
//                                    lastGroup = LastGroup.NONE;
//                                }
//                            }
//
//                            packingHigh = true;
//
//                            highStop = Math.max(highStop, candle.high());
//
//                        } else if(packingHigh){
//                            packingHigh = false;
//                            lastGroup = LastGroup.HIGH;
//
//                        }
//                        if(candle.tick() < -DISTANCE){
//                            if(!packingLow){
//                                lowStop = Double.MAX_VALUE;
//
//                                if(lastGroup == LastGroup.LOW){
//
//                                    failureCounter++;
//
//
//                                    lastGroup = LastGroup.NONE;
//                                } else if(lastGroup == LastGroup.HIGH){
//
//                                    successCounter++;
//
//                                    lastGroup = LastGroup.NONE;
//                                }
//                            }
//
//
//
//                            packingLow = true;
//
//                            lowStop = Math.min(lowStop, candle.low());
//
//                        } else if(packingLow){
//                            packingLow = false;
//                            lastGroup = LastGroup.LOW;
//                        }
//                    }
//
//                    System.out.println("For DISTANCE " + DISTANCE + ", total: " + (failureCounter + successCounter) + ", losses: " + failureCounter + ", wins: " + successCounter + ", ratio: " + ((successCounter * 1.) / (failureCounter * 1.)));
//                }
//                System.out.println();
//                System.out.println();
//            }).start();
//        }




        String inputDataFolderPath = "C:\\--- BTCUSDT";

        StrategyStarter starter = new StrategyStarter(
                inputDataFolderPath,
                "src/main/java/Resources/only_latencies_fixed.csv",
                //"2023-02-17","2023-02-17",
                //"2023-03-25", null,
                null,null,
                100000
        );

        starter.execute();

        ArrayList<UserAssets> assets = starter.getBinanceHandler().getUserAssetsList();

        ArrayList<SingletonMap<Long, Double>> closePrices = starter.getLocalHandler().getCandleCloseList();

        ArrayList<Map.Entry<Long, Double>> assetsUSDT = assets.stream()
                .map(a -> new AbstractMap.SimpleEntry<>(a.getTimestamp(), a.getMomentaryOwnedAssets()))
                .collect(Collectors.toCollection(ArrayList::new));

        int longRandomNumber = (int) (Math.random() * 1000);

        System.out.println("Chart number " + longRandomNumber);

        PortfolioPlotter.plot(assetsUSDT, closePrices, inputDataFolderPath.split(" ")[1] + String.format(" %d", longRandomNumber));

        System.out.printf("Max drawdown: %.2f%%" , (1 - DrawdownCalculator.calculateMaxDrawdown(assets.stream().map(UserAssets::getMomentaryOwnedAssets).collect(Collectors.toCollection(ArrayList::new)))) * 100);
    }


}