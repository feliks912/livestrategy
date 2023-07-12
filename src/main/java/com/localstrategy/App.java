package com.localstrategy;

import java.lang.Integer;

public class App {

    public static void main( String[] args ) {

        //new TradingGUI(2000000, new StrategyExecutor(200, 5, 3, 0, 100, 1, 10000, 0.1, 0)); */

        /* new StrategyStarter(
            "C:/--- BTCUSDT",
            null,
            null,
            10000
        ).execute(null); */

        Integer i = 0;
        Test test = new Test(i);
        i++;
        test.printI();
    }

    public static class Test{

        Integer i;
        public Test(Integer i){
            this.i = i;
        }

        public void printI(){
            System.out.println(i);
        }
    }
}




