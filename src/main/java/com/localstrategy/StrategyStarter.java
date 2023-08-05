package com.localstrategy;

import com.localstrategy.util.enums.EventDestination;
import com.localstrategy.util.enums.EventType;
import com.localstrategy.util.helper.EventScheduler;
import com.localstrategy.util.helper.TransactionLoader;
import com.localstrategy.util.helper.UserAssets;
import com.localstrategy.util.types.Event;
import com.localstrategy.util.types.SingleTransaction;

import java.util.ArrayList;

public class StrategyStarter {
    TransactionLoader transactionLoader;
    BinanceHandler exchangeHandler;
    LocalHandler localHandler;
    private final EventScheduler scheduler = new EventScheduler();

    private double previousDayUSDT;

    public StrategyStarter(String inputDataFolderPath, String inputLatencyFilePath, String fromDate, String toDate, double initialUSDTPortfolio) {

        this.exchangeHandler = new BinanceHandler(initialUSDTPortfolio, scheduler);
        this.localHandler = new LocalHandler(initialUSDTPortfolio, scheduler);

        this.transactionLoader = new TransactionLoader(inputDataFolderPath, fromDate, toDate);

        LatencyProcessor.instantiateLatencies(inputLatencyFilePath);

        previousDayUSDT = initialUSDTPortfolio;
    }


    public void execute(String outputCSVPath){

        int fileCounter = transactionLoader.getTotalCsvFiles();;

        int initialFileCounter = fileCounter;

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

                        dailyReport(initialFileCounter - fileCounter + 1, transactionList.get(0));

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

    private void dailyReport(int day, SingleTransaction transaction){
        UserAssets assets = exchangeHandler.getUserAssets();
        double endOfDayUSDT = (assets.getFreeUSDT() + assets.getLockedUSDT() - assets.getTotalBorrowedUSDT())
                + (assets.getFreeBTC() + assets.getLockedBTC() - assets.getTotalBorrowedBTC()) * transaction.price();

        double dayDiffPct = (endOfDayUSDT - previousDayUSDT) / previousDayUSDT * 100;

        previousDayUSDT = endOfDayUSDT;

        System.out.printf("Day %d done. Profit: $%.2f, pct change: %.2f\n", day, endOfDayUSDT, dayDiffPct);
    }
}
