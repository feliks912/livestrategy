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

//        ArrayList<Candle> candles = new ArrayList<>(CandleFileManager.readCandles("C:\\BTCCandles\\500kAll.bin", 0, Integer.MAX_VALUE));
//
//        ArrayList<Candle> newCandles = new ArrayList<>();
//
//        int highCount = 0;
//        int lowCount = 0;
//
//        for(Candle candle : candles) {
//            newCandles.add(candle);
//
//            double low = zz.getLastLow();
//            double high = zz.getLastHigh();
//
//            if(newCandles.size() > 2 * zz.getDepth() + zz.getBackstep()){
//                zz.updateZigZagValue(newCandles);
//            }
//
//            if(zz.getLastLow() != low){
//                lowCount++;
//            }
//
//            if(zz.getLastHigh() != high){
//                highCount++;
//            }
//        }
//
//        System.out.println("Lows: " + lowCount + ", Highs: " + highCount);


//        boolean packingHigh = false;
//        boolean packingLow = false;
//
//        int firstLargerIndex = 0;
//        int lastLargerIndex = 0;
//
//        int firstLowerIndex = 0;
//        int lastLowerIndex = 0;
//
//        enum LastGroup{
//            NONE,
//            HIGH,
//            LOW
//        }
//
//        final int DISTANCE = 300;
//
//        LastGroup lastGroup = LastGroup.NONE;
//
//        int totalGroupCounter = 0;
//
//        ArrayList<SingletonMap<SingletonMap<Integer, Integer>, String>> groups = new ArrayList<>();
//        ArrayList<SingletonMap<SingletonMap<Integer, Integer>, String>> all_groups = new ArrayList<>();
//
//        for(Candle candle : candles){
//            if(candle.tick() > DISTANCE){
//                if(lastGroup.equals(LastGroup.LOW)){
//                    lastGroup = LastGroup.NONE;
//                    groups.add(new SingletonMap<>(new SingletonMap<>(firstLowerIndex, lastLowerIndex), "LOW: "));
//                    //Add to group
//                }
//                if(!packingHigh){
//                    packingHigh = true;
//                    firstLargerIndex = candle.index();
//                }
//            } else if(packingHigh){
//                lastLargerIndex = candle.index() - 1;
//                packingHigh = false;
//                totalGroupCounter++;
//                all_groups.add(new SingletonMap<>(new SingletonMap<>(firstLowerIndex, lastLowerIndex), "LOW: "));
//
//                lastGroup = LastGroup.HIGH;
//            }
//
//            if(candle.tick() < -DISTANCE){
//                if(lastGroup.equals(LastGroup.HIGH)){
//                    lastGroup = LastGroup.NONE;
//                    groups.add(new SingletonMap<>(new SingletonMap<>(firstLargerIndex, lastLargerIndex), "HIGH: "));
//                }
//                if(!packingLow){
//                    packingLow = true;
//                    firstLowerIndex = candle.index();
//                }
//            } else if(packingLow){
//                lastLowerIndex = candle.index() - 1;
//                packingLow = false;
//                totalGroupCounter++;
//                all_groups.add(new SingletonMap<>(new SingletonMap<>(firstLargerIndex, lastLargerIndex), "HIGH: "));
//
//                lastGroup = LastGroup.LOW;
//            }
//        }
//        System.out.println("Distance: " + DISTANCE + ", total groups: " + totalGroupCounter + ", lastGroups: " + groups.size());
//
//        for(SingletonMap<SingletonMap<Integer, Integer>, String> map : all_groups){
//            int startIndex = map.getKey().getKey();
//            int endIndex = map.getKey().getValue();
//            String group = map.getValue();
//            System.out.println(endIndex-startIndex+1);
//        }




        String inputDataFolderPath = "C:\\--- BTCUSDT";

        StrategyStarter starter = new StrategyStarter(
                inputDataFolderPath,
                "src/main/java/Resources/only_latencies_fixed.csv",
                "2023-01-31","2023-03-09",
                //"2023-03-21", null,
                //null,null,
                10000
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