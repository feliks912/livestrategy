package com.localstrategy;

import com.localstrategy.util.misc.TradingGUI;
import com.localstrategy.util.old.StrategyExecutor;

public class App {

    public static void main( String[] args ) {

        new TradingGUI(5000000, new StrategyExecutor(500, 5, 3, 0, 100, 1, 10000, 0.1, 0));

        /* new StrategyStarter(
            "C:/--- BTCUSDT",
            null,
            null,
            10000
        ).execute(null); */
    }
}




