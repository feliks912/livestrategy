package com.localstrategy.util.helper;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

import com.localstrategy.util.types.SingleTransaction;

public class TransactionLoader {
    private Queue<Path> filesQueue;
    private final int parallelism = Runtime.getRuntime().availableProcessors();

    private String filename;

    public TransactionLoader(String folderPath, String fromDate, String toDate){
        this.filesQueue = new PriorityQueue<>();
        if(fromDate == null && toDate == null){
            try(DirectoryStream<Path> paths = Files.newDirectoryStream(Paths.get(folderPath), "*.csv")){
                for(Path path : paths){
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
            try {
                Files.walk(Paths.get(folderPath))
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

    public List<SingleTransaction> loadNextDay(){
        if(!filesQueue.isEmpty()){
            Path filePath = filesQueue.poll();
            filename = filePath.getFileName().toString();
            try {
                ForkJoinPool customThreadPool = new ForkJoinPool(parallelism);
                return customThreadPool.submit(() -> 
                    Files.lines(filePath)
                    .parallel()
                    .map(line -> line.split(","))
                    .map(this::createTransaction)
                    .collect(Collectors.toList())
                ).get();
            } catch (InterruptedException | ExecutionException ex){
                ex.printStackTrace();
            }
        }
        return Collections.emptyList();
    }

    private SingleTransaction createTransaction(String[] transactionData) {
        SingleTransaction transaction = new SingleTransaction();
        transaction.setPrice(Double.parseDouble(transactionData[0]));
        transaction.setAmount(Double.parseDouble(transactionData[1]));
        transaction.setTimestamp(Long.parseLong(transactionData[2]));
        return transaction;
    }

    public int getTotalCsvFiles() {
        return filesQueue.size();
    }

    public String getLastFileName(){
        return this.filename;
    }
}
