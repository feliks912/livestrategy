package com.localstrategy;

import java.util.ArrayList;
import java.util.Collections;

public class StrategyStarter {
    private int distance;
    private int ZZDepth;
    private int ZZBackstep;
    private double tpRR;
    private double fixedRR;
    private double BEPercentage;
    private double initialPortfolio;
    private double riskPercentage;
    private double brokerCommissionRate;
    private double candleVolume;
    private String inputDataFolderPath;

    private MeanAndStandardDeviation dailyStdDev = new MeanAndStandardDeviation();
    private MeanAndStandardDeviation totalStdDev = new MeanAndStandardDeviation();
    private MeanAndStandardDeviation orderStandardDeviation = new MeanAndStandardDeviation();
    private MeanAndStandardDeviation priceStandardDeviation = new MeanAndStandardDeviation();

    private double previousPrice;

    public StrategyStarter(String inputDataFolderPath, double candleVolume, int distance, int ZZDepth, int ZZBackstep, double tpRR, double fixedRR, double BEPercentage, double initialPortfolio, double riskPercentage, double brokerCommissionRate) {
        this.inputDataFolderPath = inputDataFolderPath;
        this.candleVolume = candleVolume;
        this.distance = distance;
        this.ZZDepth = ZZDepth;
        this.ZZBackstep = ZZBackstep;
        this.tpRR = tpRR;
        this.fixedRR = fixedRR;
        this.BEPercentage = BEPercentage;
        this.initialPortfolio = initialPortfolio;
        this.riskPercentage = riskPercentage;
        this.brokerCommissionRate = brokerCommissionRate;

        priceStandardDeviation.addNumber(2.35);
    }

    public void execute(String outputCSVPath){

        double previousPortfolioValue = initialPortfolio;

        ArrayList<SingleTransaction> transactionList = new ArrayList<SingleTransaction>();

        CandleConstructor candleConstructor = new CandleConstructor(candleVolume);

        StrategyExecutor zigZagStrategy = new StrategyExecutor(distance, ZZDepth, ZZBackstep, tpRR, fixedRR, BEPercentage, initialPortfolio, riskPercentage, brokerCommissionRate);

        TransactionLoader transactionLoader = new TransactionLoader(inputDataFolderPath, 
        null,
            //"2022-11-22", 
            "2023-03-23");

        int fileCount = transactionLoader.getTotalCsvFiles();
        
        System.out.println("Total days: " + fileCount + ". Starting portfolio: $" + previousPortfolioValue);
        System.out.printf("Volume: %.0f, Distance: %d, Depth: %d, Backstep: %d, tpRR: %.1f, fixedRR: %.1f, Breakeven: %.1f, risk: %.2f\n", candleVolume, distance, ZZDepth, ZZBackstep, tpRR, fixedRR, BEPercentage, riskPercentage);

        SingleTransaction previousTransaction = null;

        for(int i = 1; i <= fileCount; i++){

            transactionList = transactionLoader.loadNextDay();

            if(previousTransaction == null){
                previousTransaction = transactionList.get(0);
            }

            for(SingleTransaction transaction : transactionList){

                Candle candle = candleConstructor.processTradeEvent(transaction);

                if(previousPrice != 0){
                    dailyStdDev.addNumber(transaction.getPrice() - previousPrice);
                }

                previousPrice = transaction.getPrice();

                if(candleConstructor.getLastCandleIndex() != 0){ //New trasaction
                    zigZagStrategy.priceUpdate(priceStandardDeviation.calculateMean(), transaction, candleConstructor.getLastCandle());
                } 
                
                if(candle != null){ // New candle is formed
                    zigZagStrategy.newCandle(transaction, candleConstructor.getCandles());
                }
            }


            priceStandardDeviation.addNumber(dailyStdDev.calculateFirstTwoStandardDeviations()[2]);

            //double currentFreePortfolio = zigZagStrategy.getPortfolio() - zigZagStrategy.getUsedMargin();
            double currentFreePortfolio = zigZagStrategy.getPortfolio();

            orderStandardDeviation.setNumbers(zigZagStrategy.getAverageOrderDistanceList());

            //System.out.printf("Day %d over. Portfolio: $%.2f. Profit: $%.2f, change: %.2f%%. Maximum positions: %d. Price stats - mean: %.2f, fd: %.2f, sd: %.2f, avgOrderDist - mean: %.2f, fd: %.2f, sd: %.2f\n", i, currentFreePortfolio, currentFreePortfolio - previousPortfolioValue, (currentFreePortfolio - previousPortfolioValue) / previousPortfolioValue * 100, zigZagStrategy.getMaxPositionCounter(), dailyStdDev.calculateMean(), dailyStdDev.calculateFirstTwoStandardDeviations()[2], dailyStdDev.calculateFirstTwoStandardDeviations()[3], orderStandardDeviation.calculateMean(), orderStandardDeviation.calculateFirstTwoStandardDeviations()[2], orderStandardDeviation.calculateFirstTwoStandardDeviations()[3]);

            System.out.printf("Day %d over. Portfolio: $%.2f. Profit: $%.2f, change: %.2f%%, Maximum positions: %d, Total positions: %d\n", i, currentFreePortfolio, currentFreePortfolio - previousPortfolioValue, (currentFreePortfolio - previousPortfolioValue) / previousPortfolioValue * 100, zigZagStrategy.getMaxPositionCounter(), zigZagStrategy.getDailyPositionCount());

            zigZagStrategy.resetMaximumPositionsCount();
            zigZagStrategy.resetAverageOrderDistanceList();
            zigZagStrategy.resetDailyPositionCount();

            previousPortfolioValue = currentFreePortfolio;
            totalStdDev.addNumber(dailyStdDev.calculateMean());
            dailyStdDev.clear();
        }

        System.out.printf("End lsd: %.2f, lfd: %.2f, mean: %.2f, rfd: %.2f, rsd: %.2f\n", totalStdDev.calculateFirstTwoStandardDeviations()[0], totalStdDev.calculateFirstTwoStandardDeviations()[1], totalStdDev.calculateMean(), totalStdDev.calculateFirstTwoStandardDeviations()[2], totalStdDev.calculateFirstTwoStandardDeviations()[3]);

        ArrayList<Double> portfolioList = zigZagStrategy.endStrategy(transactionList.get(transactionList.size() - 1), candleConstructor.getLastCandle(), outputCSVPath);

        System.out.printf("%d;%d;%d;%.2f;%.2f;%.2f;%.3f;%d\n",
            zigZagStrategy.getWinCounter(),
            zigZagStrategy.getLossCounter(),              
            zigZagStrategy.getBreakevenCounter(),
            portfolioList.get(portfolioList.size() - 1),
            Collections.max(portfolioList, null),
            DrawdownCalculator.calculateMaxDrawdown(portfolioList) * 100,
            LinearDegree.calculateRSquared(portfolioList),
            zigZagStrategy.getMaxPositionCounter());
        PortfolioPlotter.plot(portfolioList);
    }
}
