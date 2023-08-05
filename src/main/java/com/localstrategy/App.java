package com.localstrategy;

public class App {

    public static void main( String[] args ) {

//        new TradingGUI(2000000, null, null);

         new StrategyStarter(
            "C:\\--- BTCUSDT",
            "src/main/java/Resources/only_latencies_fixed.csv",
            null,
            null,
            10000
        ).execute(null);
    }
}




