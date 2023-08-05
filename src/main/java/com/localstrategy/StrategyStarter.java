package com.localstrategy;

import com.localstrategy.util.enums.EventDestination;
import com.localstrategy.util.enums.EventType;
import com.localstrategy.util.helper.EventScheduler;
import com.localstrategy.util.helper.TransactionLoader;
import com.localstrategy.util.types.Event;
import com.localstrategy.util.types.SingleTransaction;

import java.util.ArrayList;

public class StrategyStarter {
    TransactionLoader transactionLoader;
    BinanceHandler exchangeHandler;
    LocalHandler localHandler;
    private final EventScheduler scheduler = new EventScheduler();

    public StrategyStarter(String inputDataFolderPath, String inputLatencyFilePath, String fromDate, String toDate, double initialUSDTPortfolio) {

        this.exchangeHandler = new BinanceHandler(initialUSDTPortfolio, scheduler);
        this.localHandler = new LocalHandler(initialUSDTPortfolio, scheduler);

        this.transactionLoader = new TransactionLoader(inputDataFolderPath, fromDate, toDate);

        LatencyProcessor.instantiateLatencies(inputLatencyFilePath);
    }


    public void execute(String outputCSVPath){

        int fileCounter = transactionLoader.getTotalCsvFiles();;

        int transactionCounter = 0;

        ArrayList<SingleTransaction> transactionList = new ArrayList<>(transactionLoader.loadNextDay());

        SingleTransaction exchangeTransaction = transactionList.get(transactionCounter++);
        scheduler.addEvent(new Event(exchangeTransaction.timestamp(), EventDestination.EXCHANGE, exchangeTransaction));

        while(true){
            Event event = scheduler.getNextEvent();

            if(event.getDestination().equals(EventDestination.EXCHANGE)){
                if(event.getType().equals(EventType.TRANSACTION)){
                    LatencyProcessor.calculateLatency(event); // Calculate next latency

                    scheduler.addEvent(new Event(event.getTransaction().timestamp(), EventDestination.LOCAL, event.getTransaction()));

                    exchangeTransaction = transactionList.get(transactionCounter++);
                    scheduler.addEvent(new Event(exchangeTransaction.timestamp(), EventDestination.EXCHANGE, exchangeTransaction));

                    if(transactionCounter >= transactionList.size()){
                        if(--fileCounter <= 0){
                            //TODO: No more days to load, exit strategy
                            break;
                        }
                        transactionList = new ArrayList<>(transactionLoader.loadNextDay());
                        transactionCounter = 0;
                    }
                }
                exchangeHandler.onEvent(event);
            } else {
                localHandler.onEvent(event);
            }
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

        System.out.printf("Wins: %d, Losses: %d, Break-evens: %d, Final portfolio: %.2f, Max portfolio value: %.2f, Draw-down: %.2f, R squared: %.3f\n",
            exchangeHandler.getWinCounter(),
            exchangeHandler.getLossCounter(),              
            exchangeHandler.getBreak-evenCounter(),
            portfolioList.get(portfolioList.size() - 1),
            Collections.max(portfolioList, null),
            Draw-downCalculator.calculateMaxDraw-down(portfolioList) * 100,
            LinearDegree.calculateRSquared(portfolioList));

        PortfolioPlotter.plot(portfolioList);
    } */
}
