package com.localstrategy;

public class App {

    public static void main( String[] args ) {

        /* StrategyExecutor zigZagStrategy = new StrategyExecutor(3, 2, 0, 0.1, 1.6, 0.3, 1000, 0.1, 0);

        TradingGUI gui = new TradingGUI(zigZagStrategy); */

        StrategyStarter strategy = new StrategyStarter(
            "C:/--- BTCUSDT",
            500000,
            100,
            3,
            2,
            0.1,
            1000,
            1,
            10000,
            0.1,
            0
        );

        strategy.execute(null);
    }
}


