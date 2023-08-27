package com.localstrategy;

import com.localstrategy.util.enums.EventDestination;
import com.localstrategy.util.enums.EventType;
import com.localstrategy.util.helper.BinaryOrderbookReader;
import com.localstrategy.util.helper.BinaryTransactionLoader;
import com.localstrategy.util.helper.EventScheduler;
import com.localstrategy.util.types.Event;
import com.localstrategy.util.types.Position;
import com.localstrategy.util.types.SingleTransaction;
import com.localstrategy.util.types.UserAssets;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;

public class StrategyStarter {

    public static int currentDay = 0;
    BinaryTransactionLoader transactionLoader;
    BinanceHandler exchangeHandler;
    LocalHandler localHandler;
    private final EventScheduler scheduler = new EventScheduler();

    public static boolean newDay = true;

    private double previousDayUSDT;

    private long startingTimestamp = 0;

    private int initialFileCounter;

    private BinaryOrderbookReader bookReader;

    public StrategyStarter(String inputDataFolderPath, String inputLatencyFilePath, String fromDate, String toDate, double initialUSDTPortfolio) {

        System.out.println("Starting " + inputDataFolderPath.split(" ")[1] + " from " + fromDate + " to " + toDate);

        this.exchangeHandler = new BinanceHandler(initialUSDTPortfolio, scheduler);
        this.localHandler = new LocalHandler(initialUSDTPortfolio, scheduler);

        try {
            bookReader = new BinaryOrderbookReader("C:\\Users\\Admin\\Desktop\\livestrategy\\src\\main\\java\\Resources\\orderbook.bin");
            // Use the bookReader object
        } catch (IOException e) {
            // Handle the exception
        }

        this.transactionLoader = new BinaryTransactionLoader(inputDataFolderPath, fromDate, toDate);

        LatencyProcessor.instantiateLatencies(inputLatencyFilePath);

        previousDayUSDT = initialUSDTPortfolio;
    }

    public void execute() {

        int fileCounter = transactionLoader.getRemainingFileCount();

        this.initialFileCounter = fileCounter;

        int transactionCounter = 0;

        ArrayList<SingleTransaction> transactionList = new ArrayList<>(transactionLoader.loadNextDay());

        SingleTransaction exchangeTransaction = transactionList.get(transactionCounter++);
        scheduler.addEvent(new Event(exchangeTransaction.timestamp(), EventDestination.EXCHANGE, exchangeTransaction));

        startingTimestamp = exchangeTransaction.timestamp();

        try{
            bookReader.loadUntil(exchangeTransaction.timestamp() - startingTimestamp);
        } catch(IOException e){
            System.out.println(e);
        }

        while (true) {
            Event event = scheduler.getNextEvent();

            if(event.getType().equals(EventType.ACTION_REQUEST) && event.getOrder().getId() == 103){
                boolean stop = true;
            }

            if(!event.getType().equals(EventType.TRANSACTION)){
                boolean stop = true;
            }

            if (event.getDestination().equals(EventDestination.LOCAL)) {
                localHandler.onEvent(event);
            } else {
                if (event.getType().equals(EventType.TRANSACTION)) {

                    try{
                        bookReader.loadUntil(event.getTimestamp() - startingTimestamp);
                    } catch(IOException e){
                        System.out.println(e);
                    }

                    LatencyProcessor.calculateLatency(event);

                    scheduler.addEvent(new Event(event.getTransaction().timestamp(), EventDestination.LOCAL, event.getTransaction()));

                    exchangeTransaction = transactionList.get(transactionCounter++);
                    scheduler.addEvent(new Event(exchangeTransaction.timestamp(), EventDestination.EXCHANGE, exchangeTransaction));


                    if (transactionCounter >= transactionList.size()) {

                        dailyReport(transactionLoader.getRemainingFileCount(), transactionList.get(transactionList.size() - 1));

                        if (--fileCounter <= 0) {
                            //TODO: No more days to load, exit strategy
                            break;
                        }

                        transactionList = new ArrayList<>(transactionLoader.loadNextDay());

                        transactionCounter = 0;

                    }
                }
                exchangeHandler.onEvent(event);
            }
        }

        for(Position position : localHandler.getActivePositions()){
            System.out.println(position);
        }

        System.out.println(localHandler.getUserAssets().toString());
    }

    public LocalHandler getLocalHandler(){
        return this.localHandler;
    }

    public BinanceHandler getBinanceHandler(){
        return this.exchangeHandler;
    }

    private void dailyReport(int fileCounter, SingleTransaction transaction) {
        currentDay = initialFileCounter - fileCounter;

        UserAssets assets = exchangeHandler.getUserAssets();
        double endOfDayUSDT = assets.getFreeUSDT().add(assets.getLockedUSDT()).subtract(assets.getTotalBorrowedUSDT())
                .add(assets.getFreeBTC().add(assets.getLockedBTC()).subtract(assets.getTotalBorrowedBTC()).multiply(BigDecimal.valueOf(transaction.price()))).doubleValue();

        double dayDiffPct = (endOfDayUSDT - previousDayUSDT) / previousDayUSDT * 100;

        System.out.printf("Day %d/%d (%s) done. Balance: $%.2f, profit: $%.2f, pct change: %.2f%%. %d active positions.\n",
                currentDay,
                initialFileCounter,
                transactionLoader.getCurrentFileName().substring(
                        transactionLoader.getCurrentFileName().length() - 14,
                        transactionLoader.getCurrentFileName().length() - 4),
                endOfDayUSDT,
                (endOfDayUSDT - previousDayUSDT),
                dayDiffPct,
                localHandler.getActivePositions().size());

        previousDayUSDT = endOfDayUSDT;
    }
}
