package com.localstrategy;

import com.localstrategy.util.helper.PortfolioPlotter;
import com.localstrategy.util.types.UserAssets;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;

public class App {

    public static void main(String[] args) {

        StrategyStarter starter = new StrategyStarter(
                "C:\\--- BTCUSDT",
                "src/main/java/Resources/only_latencies_fixed.csv",
                null,
                null,
                10000
        );

         ArrayList<UserAssets> assets = starter.execute(null);

        ArrayList<Map.Entry<Long, Double>> assetsUSDT = assets.stream()
                .map(a -> new AbstractMap.SimpleEntry<>(a.getTimestamp(), a.getMomentaryOwnedAssets()))
                .collect(Collectors.toCollection(ArrayList::new));

         PortfolioPlotter.plot(assetsUSDT);
}
}




