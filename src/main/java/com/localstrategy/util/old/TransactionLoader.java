package com.localstrategy.util.old;

import com.localstrategy.StrategyStarter;
import com.localstrategy.util.types.SingleTransaction;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class TransactionLoader {

    //FIXME: Mutliple edits, check before using

    //NOTE: hardcoded value 10_000_000 because that was the chosen precision used during dataset size reduction. It results in 99.9999% precision of the original dataset

    private final Queue<Path> filesQueue;
    private final int processorCount = Runtime.getRuntime().availableProcessors();

    public TransactionLoader(String folderPath, String inputFromDate, String inputToDate) {
        this.filesQueue = new PriorityQueue<>();

        Path dir = Paths.get(folderPath);

        String fromDate = inputFromDate;
        String toDate = inputToDate;

        if(fromDate == null && toDate == null){
            try(DirectoryStream<Path> paths = Files.newDirectoryStream(dir, "*.csv")) {
                for(Path path : paths) {
                    this.filesQueue.add(path);
                }
            } catch(IOException ex){
                ex.printStackTrace();
            }
        } else {
            if(fromDate == null){
                fromDate = "2000-01-01";
            }
            if(toDate == null){
                toDate = "2050-01-01";
            }
            LocalDate startDate = LocalDate.parse(fromDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            LocalDate endDate = LocalDate.parse(toDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            try (Stream<Path> paths = Files.walk(dir)) {
                paths
                        .filter(path -> path.toString().endsWith(".csv"))
                        .filter(path -> {
                            String filename = path.getFileName().toString();
                            String datePart = filename.substring(15, 25); // Extract date part of the filename
                            LocalDate fileDate = LocalDate.parse(datePart, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                            return ((fileDate.isAfter(startDate) || fileDate.isEqual(startDate)) && (fileDate.isBefore(endDate) || fileDate.isEqual(endDate))); // Check if file date is after or equal to start date
                        })
                        .forEach(filesQueue::add);
            } catch(IOException ex){
                ex.printStackTrace();
            }
        }
    }

    public List<SingleTransaction> loadNextDaySingleThread() {
        StrategyStarter.newDay = true;

        if (filesQueue.isEmpty()) {
            return null;
        }

        List<SingleTransaction> transactions = new ArrayList<>();

        Path filePath = filesQueue.poll();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath.toFile()))) {
            List<String> lines = reader.lines().toList();

            if (lines.isEmpty()) {
                return null;
            }

            String[] firstTransactionData = lines.get(0).split(",");
            double firstPrice = round(Double.parseDouble(firstTransactionData[0]), 2);
            long firstTime = Long.parseLong(firstTransactionData[2]);

            transactions.add(new SingleTransaction(
                    firstPrice,
                    firstPrice * Integer.parseInt(firstTransactionData[1]) / 10_000_000,
                    firstTime
            ));

            for (int i = 1; i < lines.size(); i++) {
                String[] transactionData = lines.get(i).split(",");
                transactions.add(createTransaction(transactionData, firstPrice, firstTime));
            }

        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return transactions;
    }


    public List<SingleTransaction> loadNextDayConcurrent() {

        StrategyStarter.newDay = true;

        if(filesQueue.isEmpty()) {
            return null;
        }

        List<SingleTransaction> transactions =   new ArrayList<>();

        Path filePath = filesQueue.poll();

        try (Stream<String> lines = Files.lines(filePath)) {
            Iterator<String> lineIterator = lines.iterator();

            if (!lineIterator.hasNext()) {
                return null;
            }

            String[] firstTransactionData = lineIterator.next().split(",");
            double firstPrice = Double.parseDouble(firstTransactionData[0]);
            long firstTime = Long.parseLong(firstTransactionData[2]);

            transactions.add(new SingleTransaction(
                    firstPrice,
                    firstPrice * Integer.parseInt(firstTransactionData[1]) / 10_000_000,
                    firstTime
            ));

            ForkJoinPool customThreadPool = new ForkJoinPool(processorCount);

            try{
                List<SingleTransaction> parallelTransactions = customThreadPool.submit(() -> {
                    Stream<String> remainingLines = StreamSupport.stream(
                            Spliterators.spliteratorUnknownSize(lineIterator, Spliterator.ORDERED),
                            false
                    );

                    return remainingLines
                            .parallel()
                            .map(line -> line.split(","))
                            .map(line -> createTransaction(line, firstPrice, firstTime))
                            .collect(Collectors.toList());
                }).get();

                transactions.addAll(parallelTransactions);
            } catch (InterruptedException | ExecutionException ex) {
                ex.printStackTrace();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return transactions;
    }


    private SingleTransaction createTransaction(String[] transactionData, double firstPrice, long firstTime) {

        double price = round(Double.parseDouble(transactionData[0]) + firstPrice, 2); // Round to 2 decimals

        return new SingleTransaction(
                price,
                price * Integer.parseInt(transactionData[1]) / 10_000_000,
                Long.parseLong(transactionData[2]) + firstTime
        );
    }

    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }


    public int getTotalFileCount() {
        return filesQueue.size();
    }
}
