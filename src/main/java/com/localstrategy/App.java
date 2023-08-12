package com.localstrategy;

import com.localstrategy.util.helper.PortfolioPlotter;
import com.localstrategy.util.types.UserAssets;

import java.util.ArrayList;
import java.util.stream.Collectors;

public class App {

    public static void main(String[] args) {

//         new TradingGUI(2000000, "2022-11-03", null);

        StrategyStarter starter = new StrategyStarter(
                "C:\\--- BTCUSDT",
                "src/main/java/Resources/only_latencies_fixed.csv",
                null,
                null,
                10000
        );

         ArrayList<UserAssets> assets = starter.execute(null);

         ArrayList<Double> assetsUSDT = assets.stream().map(UserAssets::getMomentaryOwnedAssets).collect(Collectors.toCollection(ArrayList::new));

         PortfolioPlotter.plot(assetsUSDT);
}
}




