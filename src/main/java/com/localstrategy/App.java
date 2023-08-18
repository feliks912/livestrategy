package com.localstrategy;

import com.localstrategy.util.helper.DrawdownCalculator;
import com.localstrategy.util.helper.PortfolioPlotter;
import com.localstrategy.util.types.UserAssets;
import org.apache.commons.collections4.map.SingletonMap;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;

public class App {

    public static void main(String[] args) {


        String inputDataFolderPath = "C:\\--- ETHUSDT";

        StrategyStarter starter = new StrategyStarter(
                inputDataFolderPath,
                "src/main/java/Resources/only_latencies_fixed.csv",
                null,
                null,
                10000
        );

        starter.execute(null);

        ArrayList<UserAssets> assets = starter.getBinanceHandler().getUserAssetsList();

        ArrayList<SingletonMap<Long, Double>> closePrices = starter.getLocalHandler().getCandleCloseList();

        ArrayList<Map.Entry<Long, Double>> assetsUSDT = assets.stream()
                .map(a -> new AbstractMap.SimpleEntry<>(a.getTimestamp(), a.getMomentaryOwnedAssets()))
                .collect(Collectors.toCollection(ArrayList::new));

        PortfolioPlotter.plot(assetsUSDT, closePrices, inputDataFolderPath.split(" ")[1]);

        System.out.printf("Max drawdown: %.2f%%" , (1 - DrawdownCalculator.calculateMaxDrawdown(assets.stream().map(UserAssets::getMomentaryOwnedAssets).collect(Collectors.toCollection(ArrayList::new)))) * 100);
    }
}




