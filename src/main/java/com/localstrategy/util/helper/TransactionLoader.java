package com.localstrategy.util.helper;

import com.localstrategy.util.types.SingleTransaction;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TransactionLoader {

    private final Queue<Path> filesQueue;
    private final int parallelism = Runtime.getRuntime().availableProcessors();

    private String filename;

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


    public List<SingleTransaction> loadNextDay() {
        if (!filesQueue.isEmpty()) {
            Path filePath = filesQueue.poll();
            filename = filePath.getFileName().toString();
            try (ForkJoinPool customThreadPool = new ForkJoinPool(parallelism)) {
                return customThreadPool.submit(() -> {
                    try (Stream<String> lines = Files.lines(filePath)) {
                        return lines
                                .parallel()
                                .map(line -> line.split(","))
                                .map(this::createTransaction)
                                .collect(Collectors.toList());
                    } catch (IOException ex) {
                        ex.printStackTrace();
                        return new ArrayList<SingleTransaction>(); // Return an explicitly typed empty list
                    }
                }).get();
            } catch (InterruptedException | ExecutionException ex) {
                ex.printStackTrace();
            }
        }
        return new ArrayList<SingleTransaction>(); // Return an explicitly typed empty list
    }


    private SingleTransaction createTransaction(String[] transactionData) {
        return new SingleTransaction(
                Double.parseDouble(transactionData[0]),
                Double.parseDouble(transactionData[1]),
                Long.parseLong(transactionData[2])
        );
    }

    public int getTotalCsvFiles() {
        return filesQueue.size();
    }

    public String getLastFileName(){
        return this.filename;
    }
}
