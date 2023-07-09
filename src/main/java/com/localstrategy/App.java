package com.localstrategy;

public class App {

    public static void main( String[] args ) {

        /* StrategyExecutor zigZagStrategy = new StrategyExecutor(10, 2, 0, 100, 100, 0, 10000, 0.1, 0);

        TradingGUI gui = new TradingGUI(1000000, zigZagStrategy); */

        StrategyStarter strategy = new StrategyStarter(
            "C:/--- BTCUSDT",
            1000000,
            10,
            2,
            0,
            100,
            1000,
            0,
            10000,
            0.1,
            0
        );

        strategy.execute(null);
    }
}


