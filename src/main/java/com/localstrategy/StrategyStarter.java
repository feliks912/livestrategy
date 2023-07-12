package com.localstrategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StrategyStarter {

    
    TransactionLoader transactionLoader;
    private List<SingleTransaction> transactionList = new ArrayList<SingleTransaction>();
    
    ExchangeHandler exchangeHandler;

    private double initialUSDTPortfolio;


    public StrategyStarter(String inputDataFolderPath, String fromDate, String toDate, double initialUSDTPortfolio) {

        this.initialUSDTPortfolio = initialUSDTPortfolio;

        this.exchangeHandler = new ExchangeHandler(initialUSDTPortfolio);
        this.transactionLoader = new TransactionLoader(inputDataFolderPath, fromDate, toDate);
    }


    public void execute(String outputCSVPath){

        int fileCount = transactionLoader.getTotalCsvFiles();

        double previousPortfolioValue = initialUSDTPortfolio;
        
        System.out.println("Total days: " + fileCount + ". Starting portfolio: $" + initialUSDTPortfolio);

        for(int i = 1; i <= fileCount; i++){

            int maxPositionCount = 0;

            transactionList = transactionLoader.loadNextDay();

            for(SingleTransaction transaction : transactionList){

                exchangeHandler.newTransaction(transaction);

                int currentPositionCount = exchangeHandler.getOrderCount();
                if(maxPositionCount > currentPositionCount){
                    maxPositionCount = currentPositionCount;
                }
            }

            int totalDailyPositionCount = exchangeHandler.getRunningPositionCounter();
            exchangeHandler.setRunningPositionCounter(0);

            double currentFreePortfolio = exchangeHandler.getAssetsValue();

            System.out.printf("File %s done. Portfolio: $%.2f. Profit: $%.2f, change: %.2f%%, Maximum positions: %d, Total positions: %d\n", 
                transactionLoader.getLastFileName(), 
                currentFreePortfolio, 
                currentFreePortfolio - previousPortfolioValue, 
                (currentFreePortfolio - previousPortfolioValue) / previousPortfolioValue * 100, 
                maxPositionCount, 
                totalDailyPositionCount);

            previousPortfolioValue = currentFreePortfolio;
        }

        ArrayList<Double> portfolioList = exchangeHandler.terminateAndReport(outputCSVPath);

        System.out.printf("Wins: %d, Losses: %d, Breakevens: %d, Final portfolio: %.2f, Max portfolio value: %.2f, Drawdown: %.2f, R squared: %.3f\n",
            exchangeHandler.getWinCounter(),
            exchangeHandler.getLossCounter(),              
            exchangeHandler.getBreakevenCounter(),
            portfolioList.get(portfolioList.size() - 1),
            Collections.max(portfolioList, null),
            DrawdownCalculator.calculateMaxDrawdown(portfolioList) * 100,
            LinearDegree.calculateRSquared(portfolioList));

        PortfolioPlotter.plot(portfolioList);
    }
}
