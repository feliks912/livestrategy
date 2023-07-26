package com.localstrategy;

import com.localstrategy.util.enums.EventDestination;
import com.localstrategy.util.enums.EventType;
import com.localstrategy.util.helper.TransactionLoader;
import com.localstrategy.util.types.Event;
import com.localstrategy.util.types.SingleTransaction;

import java.util.ArrayList;

public class StrategyStarter {

    
    TransactionLoader transactionLoader;
    private ArrayList<SingleTransaction> transactionList = new ArrayList<>();

    Binance exchangeHandler;

    LocalStrategy localHandler;

    private final double initialUSDTPortfolio;

    private final EventScheduler scheduler = new EventScheduler();


    public StrategyStarter(String inputDataFolderPath, String fromDate, String toDate, double initialUSDTPortfolio) {

        this.initialUSDTPortfolio = initialUSDTPortfolio;

        this.exchangeHandler = new Binance(initialUSDTPortfolio, scheduler);
        this.localHandler = new LocalStrategy(initialUSDTPortfolio, scheduler);


        this.transactionLoader = new TransactionLoader(inputDataFolderPath, fromDate, toDate);
    }


    public void execute(String outputCSVPath){

        int fileCount = transactionLoader.getTotalCsvFiles();
        int fileCounter = fileCount;

        int transactionCounter = 0;

        transactionList = new ArrayList<>(transactionLoader.loadNextDay());

        SingleTransaction exchangeTransaction = transactionList.get(transactionCounter++);
        scheduler.addEvent(new Event(exchangeTransaction.timestamp(), EventDestination.EXCHANGE, exchangeTransaction));

        //FIXME: When to load the next day assuming we don't want to break the queue or finish a daily one and only then load the next day? We need continuity.
        //Start loading next day when the final transaction of the day is reached
        while(!scheduler.isEmpty()){
            Event event = scheduler.getNextEvent();

            if(event.getDestination().equals(EventDestination.EXCHANGE)){

                if(event.getType().equals(EventType.ACTION_RESPONSE) || event.getType().equals(EventType.USER_DATA_STREAM)){
                    System.out.println("How did these get here? StrategyStarter.java. Exiting.");
                    System.exit(1);
                }

                //  We can load the next transaction on the exchange side once we reach the previous transaction because we don't add transaction events as last event EventScheduler, therefore that event isn't considered during the chain rule check.
                if(event.getType().equals(EventType.TRANSACTION)){

                    //Add this transaction to a local event queue
                    scheduler.addEvent(new Event(event.getTransaction().timestamp(), EventDestination.LOCAL, event.getTransaction()));

                    //Add the next transaction to an exchange queue
                    exchangeTransaction = transactionList.get(transactionCounter++);
                    scheduler.addEvent(new Event(exchangeTransaction.timestamp(), EventDestination.EXCHANGE, exchangeTransaction));

                    if(transactionCounter == transactionList.size()){
                        //Last transaction of the day is reached and loaded
                        //Load the next day
                        if(--fileCounter <= 0){
                           //No more days to load, exit strategy
                            //TODO: exit strategy
                        }
                        transactionList = new ArrayList<>(transactionLoader.loadNextDay());
                    }
                }
                exchangeHandler.onEvent(event);
            } else {
                //Run local handler
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
