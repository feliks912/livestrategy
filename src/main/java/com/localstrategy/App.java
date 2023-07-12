package com.localstrategy;

public class App {

    public static void main( String[] args ) {

        StrategyExecutor zigZagStrategy = new StrategyExecutor(100, 5, 3, 0, 100, 0, 10000, 0.1, 0);

        TradingGUI gui = new TradingGUI(2000000, zigZagStrategy);

        /* StrategyStarter strategy = new StrategyStarter(
            "C:/--- BTCUSDT",
            2000000,
            300,
            2,
            1,
            0,
            1000,
            1,
            10000,
            0.1,
            0
        );

        strategy.execute(null); */
    }
}


