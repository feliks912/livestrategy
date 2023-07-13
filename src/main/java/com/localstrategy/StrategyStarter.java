package com.localstrategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.localstrategy.util.helper.TransactionLoader;
import com.localstrategy.util.types.SingleTransaction;

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

            boolean isWall = false;

            transactionList = transactionLoader.loadNextDay();

            for(SingleTransaction transaction : transactionList){

                //Positive lookahead for the wall.
                //  Wall is defines as a series of delayed transactions made in close time proximity one from another, which    significantly move the market price and wreck chaos on the orderbook

                //FIXME: IndexOf seems to be **very** expensive. Change IndexOf in exchangeHandler to an alternative as well
                int transactionIndex = transactionList.indexOf(transaction);

                long pTTimestamp = transaction.getTimestamp();

                if(!isWall){
                    for(int w = 1; w < 6; w++){
                        SingleTransaction pT = transactionList.get(transactionIndex + w);

                        if(pT.getTimestamp() - pTTimestamp < 5){ //If 5 continuous transactions have a delta timestamp of less than 5 seconds we assume that is a wall and set the wall variable until the dt is larger than 5
                            isWall = true;
                        } else {
                            isWall = false;
                        }

                        pTTimestamp = pT.getTimestamp();
                    }
                } else {
                    if(transactionList.get(transactionIndex + 1).getTimestamp() - pTTimestamp > 5){
                        isWall = false;
                    }
                }

                exchangeHandler.newTransaction(transaction, isWall);

                int currentPositionCount = exchangeHandler.getOrderCount();
                if(maxPositionCount > currentPositionCount){
                    maxPositionCount = currentPositionCount;
                }
            }

            int totalDailyPositionCount = exchangeHandler.getRunningPositionCounter();
            exchangeHandler.setRunningPositionCounter(0);

            double currentFreePortfolio = exchangeHandler.getTotalAssetsValue();
            //TODO: Format file name propperly
            System.out.printf("File %s done. Portfolio: $%.2f. Profit: $%.2f, change: %.2f%%, Maximum positions: %d, Total positions: %d\n", 
                transactionLoader.getLastFileName(), 
                currentFreePortfolio, 
                currentFreePortfolio - previousPortfolioValue, 
                (currentFreePortfolio - previousPortfolioValue) / previousPortfolioValue * 100, 
                maxPositionCount, 
                totalDailyPositionCount);

            previousPortfolioValue = currentFreePortfolio;
        }

        //ArrayList<Double> portfolioList = exchangeHandler.terminateAndReport(outputCSVPath);

        
    }

    
    /* public ArrayList<AssetHandler> terminateAndReport(ArrayList<Position> allPositions, String outputCSVPath, SingleTransaction transaction){
        //FIXME: Handle summing profit to portfolio correctly
        for(Position position : allPositions){
            if(!position.isClosed() && position.isFilled()){

                    userAssets.setFreeUSDT(
                        userAssets.getFreeUSDT() + 
                        position.closePosition(transaction.getPrice(), transaction.getTimestamp()) + 
                        position.getMargin()); //This includes paying interest

                    userAssets.setLockedUSDT(userAssets.getLockedUSDT() - position.getMargin());

                    closedPositions.add(position);
            } else {
                closedPositions.add(position);
            }
        }



        allPositions.sort((Position p1, Position p2) -> Long.compare(p1.getOpenTimestamp(), p2.getOpenTimestamp()));

        //TODO: Add more information to output CSV
        if(outputCSVPath != null){
            System.out.println("Trade report written to " + outputCSVPath);
            ResultConsolidator.writePositionsToCSV(allPositions, outputCSVPath);
        }

        System.out.printf("Wins: %d, Losses: %d, Breakevens: %d, Final portfolio: %.2f, Max portfolio value: %.2f, Drawdown: %.2f, R squared: %.3f\n",
            exchangeHandler.getWinCounter(),
            exchangeHandler.getLossCounter(),              
            exchangeHandler.getBreakevenCounter(),
            portfolioList.get(portfolioList.size() - 1),
            Collections.max(portfolioList, null),
            DrawdownCalculator.calculateMaxDrawdown(portfolioList) * 100,
            LinearDegree.calculateRSquared(portfolioList));

        PortfolioPlotter.plot(portfolioList);
    } */
}
