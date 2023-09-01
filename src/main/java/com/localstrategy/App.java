package com.localstrategy;

import com.localstrategy.util.helper.DrawdownCalculator;
import com.localstrategy.util.helper.PortfolioPlotter;
import com.localstrategy.util.indicators.ZigZag;
import com.localstrategy.util.types.UserAssets;
import org.apache.commons.collections4.map.SingletonMap;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;

public class App {

    public static ZigZag zz = new ZigZag(5, 0, 0, 0);

    public static void main(String[] args) throws IOException {

//        DataOutputStream dataOutputStream;
//
//        dataOutputStream = new DataOutputStream(new FileOutputStream("C:\\BTCCandles\\1MAll_MLData.bin", true));
//
//        ArrayList<Candle> candles = new ArrayList<>(CandleFileManager.readCandles("C:\\BTCCandles\\" + "1M" + "All.bin", 0, Integer.MAX_VALUE));
//
//        int ZZDepth = 4;
//        int ZZBackstep = 4;
//
//        ZigZag zz = new ZigZag(ZZDepth, 0, ZZBackstep, 0);
//
//        int size = candles.size();
//
//        for(int i = 2 * ZZDepth + ZZBackstep; i < size; i++){
//
//            ArrayList<Candle> can = new ArrayList<>(candles.subList(i - 2 * ZZDepth - ZZBackstep, i + 1));
//
//            double high = zz.getLastHigh();
//            double low = zz.getLastLow();
//
//            zz.updateZigZagValue(can);
//
//            int diff = 0;
//
//            if(high != zz.getLastHigh()){
//                diff = 1;
//            }
//            if (low != zz.getLastLow()) {
//                diff = -1;
//            }
//
//            Candle candle = candles.get(i - ZZDepth - ZZBackstep);
//
//            double returns = (candle.open() - candle.close()) / candle.close();
//
//            try{
//                dataOutputStream.writeDouble(candle.high() - candle.low());
//                dataOutputStream.writeDouble(returns);
//                dataOutputStream.writeInt(diff);
//                dataOutputStream.writeInt(candle.tick());
//                dataOutputStream.writeInt(candle.volume());
//                dataOutputStream.writeLong(candle.timestamp());
//            } catch (IOException e){
//                throw e;
//            }
//        }

        String inputDataFolderPath = "C:\\--- BTCTUSD";

        StrategyStarter starter = new StrategyStarter(
                Params.dataPath,
                "src/main/java/Resources/only_latencies_fixed.csv",
                //"2023-02-17","2023-02-17",
                //"2023-05-05", "2023-06-22",
                "2023-03-25", null,
                //null,null,
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