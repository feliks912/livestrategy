package com.localstrategy;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class TransactionLoader {
    private Queue<Path> filesQueue;

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

    public ArrayList<SingleTransaction> loadNextDay(){
        ArrayList<SingleTransaction> transactions = new ArrayList<>();
        if(!filesQueue.isEmpty()){
            Path filePath = filesQueue.poll();
            try(CSVReader reader = new CSVReader(new FileReader(filePath.toString()))){
                String[] nextLine;
                /* while ((nextLine = reader.readNext()) != null) {
                    SingleTransaction transaction = new SingleTransaction();
                    transaction.setTransactionId(Long.parseLong(nextLine[0]));
                    transaction.setPrice(Double.parseDouble(nextLine[1]));
                    transaction.setQuantity(Double.parseDouble(nextLine[2]));
                    transaction.setAmount(Double.parseDouble(nextLine[3]));
                    transaction.setTimestamp(Long.parseLong(nextLine[4]));
                    transactions.add(transaction);
                } */
                while ((nextLine = reader.readNext()) != null) {
                    SingleTransaction transaction = new SingleTransaction();
                    transaction.setPrice(Double.parseDouble(nextLine[0]));
                    transaction.setAmount(Double.parseDouble(nextLine[1]));
                    transaction.setTimestamp(Long.parseLong(nextLine[2]));
                    transactions.add(transaction);
                }
            } catch (IOException | CsvValidationException ex){
                ex.printStackTrace();
            }
        }
        return transactions;
    }

    public int getTotalCsvFiles() {
        return filesQueue.size();
    }
}
