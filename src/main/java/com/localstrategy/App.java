package com.localstrategy;

public class App {

    public static void main( String[] args ) {

        //new TradingGUI(2000000, new StrategyExecutor(200, 5, 3, 0, 100, 1, 10000, 0.1, 0)); */

        new StrategyStarter(
            "C:/--- BTCUSDT",
            null,
            null,
            10000
        ).execute(null);
    }
}




