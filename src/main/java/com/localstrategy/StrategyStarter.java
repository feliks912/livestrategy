package com.localstrategy;

import com.localstrategy.util.enums.EventDestination;
import com.localstrategy.util.enums.EventType;
import com.localstrategy.util.enums.OrderPurpose;
import com.localstrategy.util.helper.BinaryTransactionLoader;
import com.localstrategy.util.helper.EventScheduler;
import com.localstrategy.util.types.Event;
import com.localstrategy.util.types.SingleTransaction;
import com.localstrategy.util.types.UserAssets;

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

    public StrategyStarter(String inputDataFolderPath, String inputLatencyFilePath, String fromDate, String toDate, double initialUSDTPortfolio) {

        this.exchangeHandler = new BinanceHandler(initialUSDTPortfolio, scheduler);
        this.localHandler = new LocalHandler(initialUSDTPortfolio, scheduler);

        this.transactionLoader = new BinaryTransactionLoader(inputDataFolderPath, fromDate, toDate);

        LatencyProcessor.instantiateLatencies(inputLatencyFilePath);

        previousDayUSDT = initialUSDTPortfolio;
    }


    long counter = 0;

    public ArrayList<UserAssets> execute(String outputCSVPath) {

        int fileCounter = transactionLoader.getTotalFileCount();
        ;

        int initialFileCounter = fileCounter;

        int transactionCounter = 0;

//        long startTime = System.currentTimeMillis();
        ArrayList<SingleTransaction> transactionList = new ArrayList<>(transactionLoader.loadNextDay());
//        System.out.println("Load time: " + (System.currentTimeMillis() - startTime));
//        startTime = System.currentTimeMillis();

        SingleTransaction exchangeTransaction = transactionList.get(transactionCounter++);
        scheduler.addEvent(new Event(exchangeTransaction.timestamp(), EventDestination.EXCHANGE, exchangeTransaction));

        while (true) {
            Event event = scheduler.getNextEvent();

            if (event.getId() >= 212756146) {
                boolean point = true;
            }

            currentDay = initialFileCounter - fileCounter + 1;


//            if(currentDay == 52) {
//                if (!event.getType().equals(EventType.TRANSACTION)) {
//                    System.out.println(counter + " " + event);
//                    counter = 0;
//                } else if(event.getDestination().equals(EventDestination.EXCHANGE)){
//                    counter++;
//                }
//            }


            if(currentDay == 52 && event.getType().equals(EventType.USER_DATA_STREAM)) {
                boolean point = true;

                UserAssets assets = event.getUserDataStream().userAssets();

                //System.out.println("Day " + currentDay + ", Event Id " + event.getId() + ", " + assets.toString());
            }

//            if(currentDay == 53){
//                break;
//            }

            if(event.getType().equals(EventType.ACTION_REQUEST)){
//                System.out.println(event.getActionRequest().entrySet().iterator().next().getValue().getPurpose());
            }

            if (event.getType().equals(EventType.ACTION_REQUEST)
                    && event.getOrder().getId() == 8691) {
                boolean point = true;
            }

            if(event.getType().equals(EventType.ACTION_REQUEST) && event.getOrder().getPurpose().equals(OrderPurpose.REPAY)){
                boolean point = true;
            }

            if (!event.getType().equals(EventType.TRANSACTION)) {
                boolean point = true;
            }

            if (event.getDestination().equals(EventDestination.LOCAL)) {
                localHandler.onEvent(event);
            } else {
                if (event.getType().equals(EventType.TRANSACTION)) {
                    LatencyProcessor.calculateLatency(event); // Calculate next latency

                    scheduler.addEvent(new Event(event.getTransaction().timestamp(), EventDestination.LOCAL, event.getTransaction()));

                    exchangeTransaction = transactionList.get(transactionCounter++);
                    scheduler.addEvent(new Event(exchangeTransaction.timestamp(), EventDestination.EXCHANGE, exchangeTransaction));

                    if (transactionCounter >= transactionList.size()) {

                        currentDay = initialFileCounter - fileCounter + 1;

                        //dailyReport(currentDay, transactionList.get(0));

                        UserAssets assets = exchangeHandler.getUserAssets();
                        double endOfDayUSDT = assets.getFreeUSDT().add(assets.getLockedUSDT()).subtract(assets.getTotalBorrowedUSDT())
                                .add(assets.getFreeBTC().add(assets.getLockedBTC()).subtract(assets.getTotalBorrowedBTC()).multiply(BigDecimal.valueOf(transactionList.get(0).price()))).doubleValue();

                        double dayDiffPct = (endOfDayUSDT - previousDayUSDT) / previousDayUSDT * 100;

                        System.out.printf("Day %d done. Balance: $%.2f, profit: $%.2f, pct change: %.2f\n", currentDay, endOfDayUSDT, (endOfDayUSDT - previousDayUSDT), dayDiffPct);

                        previousDayUSDT = endOfDayUSDT;

                        if (--fileCounter <= 0) {
                            //TODO: No more days to load, exit strategy
                            break;
                        }
//                        System.out.println("Execution time: " + (System.currentTimeMillis() - startTime));
//                        startTime = System.currentTimeMillis();
                        transactionList = new ArrayList<>(transactionLoader.loadNextDay());
//                        System.out.println("Load time: " + (System.currentTimeMillis() - startTime));
//                        startTime = System.currentTimeMillis();
                        transactionCounter = 0;

                    }
                }
                exchangeHandler.onEvent(event);
            }
        }

        return exchangeHandler.getUserAssetsList();

        //ArrayList<Double> portfolioList = exchangeHandler.terminateAndReport(outputCSVPath);
    }



    private void dailyReport(int day, SingleTransaction transaction) {
        UserAssets assets = exchangeHandler.getUserAssets();
        double endOfDayUSDT = assets.getFreeUSDT().add(assets.getLockedUSDT()).subtract(assets.getTotalBorrowedUSDT())
                .add(assets.getFreeBTC().add(assets.getLockedBTC()).subtract(assets.getTotalBorrowedBTC()).multiply(BigDecimal.valueOf(transaction.price()))).doubleValue();

        double dayDiffPct = (endOfDayUSDT - previousDayUSDT) / previousDayUSDT * 100;

        previousDayUSDT = endOfDayUSDT;

        System.out.printf("Day %d done. Profit: $%.2f, pct change: %.2f\n", day, endOfDayUSDT, dayDiffPct);
    }
}
