package com.localstrategy;

import com.localstrategy.util.misc.TradingGUI;

public class App {

    public static void main( String[] args ) {

        //new TradingGUI(2000000);

        new StrategyStarter(
            "C:/--- BTCUSDT",
            null,
            "2021-03-01",
            10000
        ).execute(null);
    }
}




