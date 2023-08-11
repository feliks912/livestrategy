package com.localstrategy.util.helper;

import com.localstrategy.StrategyStarter;
import com.localstrategy.util.types.SingleTransaction;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.stream.Stream;

public class BinaryTransactionLoader {

    private final Queue<Path> filesQueue;

    public BinaryTransactionLoader(String folderPath, String inputFromDate, String inputToDate) {
        this.filesQueue = new PriorityQueue<>();

        Path dir = Paths.get(folderPath);

        String fromDate = inputFromDate;
        String toDate = inputToDate;

        if(fromDate == null && toDate == null){
            try(DirectoryStream<Path> paths = Files.newDirectoryStream(dir, "*.bin")) {
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
                        .filter(path -> path.toString().endsWith(".bin"))
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

        StrategyStarter.newDay = true;

        if(filesQueue.isEmpty()) {
            return null;
        }

        try {
            return readBinaryData(filesQueue.poll());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private ArrayList<SingleTransaction> readBinaryData(Path binaryFilePath) throws IOException {
        ArrayList<SingleTransaction> transactionDataList = new ArrayList<>((int) (binaryFilePath.toFile().length() / 12));

        byte[] allBytes = Files.readAllBytes(binaryFilePath);
        ByteBuffer buffer = ByteBuffer.wrap(allBytes);

        boolean firstLine = true;
        long firstTimestamp = 0;
        while (buffer.hasRemaining()) {
            int price = buffer.getInt();
            int quantity = buffer.getInt();
            long timestamp;
            if (firstLine) {
                timestamp = buffer.getLong();
                firstTimestamp = timestamp;
                firstLine = false;
            } else {
                timestamp = buffer.getInt() + firstTimestamp;
            }

            double formattedPrice = price / 100.;

            SingleTransaction transaction = new SingleTransaction(
                    formattedPrice,
                    (formattedPrice * quantity / 10_000_000),
                    timestamp
            );

            transactionDataList.add(transaction);
        }

        return transactionDataList;
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
