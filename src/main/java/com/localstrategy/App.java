package com.localstrategy;

import com.localstrategy.util.misc.TradingGUI;

public class App {

    public static void main( String[] args ) {

        //new TradingGUI(100000, new StrategyExecutor(500, 5, 3, 0, 100, 1, 10000, 0.1, 0));

        new StrategyStarter(
            "C:/--- BTCUSDT",
            null,
            "2021-03-01",
            10000
        ).execute(null);
    }
}




