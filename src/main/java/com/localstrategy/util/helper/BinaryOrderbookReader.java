package com.localstrategy.util.helper;

import com.localstrategy.util.enums.OrderbookSide;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public class BinaryOrderbookReader {

    private final Path path;
    private RandomAccessFile inputStream;
    private long startingTimestamp = 0;
    private long endingTimestamp = 0;

    private static final Map<Double, Double> asks = new TreeMap<>();
    private static final Map<Double, Double> bids = new TreeMap<>(Comparator.reverseOrder());
    private long relativeTimestamp = 0;

    private long bytesRead = 0;

    private static double midprice = 0;

    public BinaryOrderbookReader(String filePath) throws IOException {
        this.path = Path.of(filePath);
        this.inputStream = new RandomAccessFile(filePath, "r");
    }

    public void loadUntil(long timestampDifference) throws IOException {
        while (relativeTimestamp < timestampDifference) {
            if(loadNextLine()){
                inputStream.seek(0);
                endingTimestamp = relativeTimestamp;
            }
        }
    }

    public static LinkedHashMap<Double, Double> shiftOrderbook(double price, OrderbookSide side){

        LinkedHashMap<Double, Double> adjustedBook = new LinkedHashMap<>();

        double diff = price - midprice;

        if(side.equals(OrderbookSide.ASKS)){
            for(Map.Entry<Double, Double> entry : asks.entrySet()){
                adjustedBook.put(entry.getKey() + diff, entry.getValue());
            }
        } else {
            for(Map.Entry<Double, Double> entry : bids.entrySet()){
                adjustedBook.put(entry.getKey() + diff, entry.getValue());
            }
        }

        return adjustedBook;
    }

    private boolean loadNextLine() {
        try {
            if (inputStream.getFilePointer() >= inputStream.length()) {
                return true; // No more data in the file
            }

            byte[] t = new byte[4];
            inputStream.readFully(t); // Read the data

            ByteBuffer b = ByteBuffer.wrap(t);
            b.order(ByteOrder.LITTLE_ENDIAN); // Assuming little-endian byte order

            int length = b.getInt();

            byte[] lineBytes = new byte[length];
            inputStream.readFully(lineBytes); // Read the data

            ByteBuffer buffer = ByteBuffer.wrap(lineBytes);
            buffer.order(ByteOrder.LITTLE_ENDIAN); // Assuming little-endian byte order

            relativeTimestamp = buffer.getLong();

            if (relativeTimestamp == 0) {
                asks.clear();
                bids.clear();
            }

            if (startingTimestamp == 0 && relativeTimestamp != 0) {
                startingTimestamp = relativeTimestamp;
            }

            relativeTimestamp = (relativeTimestamp - startingTimestamp) + endingTimestamp;

            while (buffer.hasRemaining()) {
                double price = buffer.getDouble();
                double quantity = buffer.getDouble();

                if (quantity == 0) {
                    asks.remove(price);
                    bids.remove(price);
                } else if (quantity < 0) {
                    asks.put(price, quantity);
                } else {
                    bids.put(price, quantity);
                }
            }

            midprice = (bids.keySet().iterator().next() + asks.keySet().iterator().next()) / 2;

            return false;
        } catch (IOException e) {
            System.out.println(e);
        }

        return true;
    }

    public long getEndingTimestamp() {
        return endingTimestamp;
    }
}
