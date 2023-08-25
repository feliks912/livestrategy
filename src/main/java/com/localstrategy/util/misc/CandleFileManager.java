package com.localstrategy.util.misc;

import com.localstrategy.util.types.Candle;

import javax.swing.*;
import java.io.*;
import java.util.ArrayList;

public class CandleFileManager {
    public interface ProgressListener {
        void updateProgress(int progress);
    }

    private static JProgressBar progressBar; // Added instance variable for the progress bar

    public static ArrayList<Candle> readCandles(String filepath, int startIndex, int endIndex) {
        ArrayList<Candle> candles = new ArrayList<>();

        try (DataInputStream dataInputStream = new DataInputStream(new FileInputStream(filepath))) {
            int fileSize = dataInputStream.available();
            int i = 0;

            SwingUtilities.invokeLater(() -> {
                JFrame frame = new JFrame("Loading Progress");
                progressBar = new JProgressBar(0, fileSize); // Assigning to instance variable
                progressBar.setStringPainted(true);
                frame.add(progressBar);
                frame.setSize(300, 100);
                frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                frame.setVisible(true);
            });

            for (; i < startIndex; i++) {
                // Skip bytes for each candle (assuming a fixed size for each candle)
                dataInputStream.skipBytes(56); // 8 bytes per double * 6 + 4 bytes per int + 8 bytes per long
            }

            while (i < endIndex && dataInputStream.available() >= 56) {
                double open = dataInputStream.readDouble();
                double high = dataInputStream.readDouble();
                double low = dataInputStream.readDouble();
                double close = dataInputStream.readDouble();
                int volume = dataInputStream.readInt();
                int tick = dataInputStream.readInt();
                int index = dataInputStream.readInt();
                long timestamp = dataInputStream.readLong();
                long lastTransactionId = dataInputStream.readLong();

                Candle candle = new Candle(open, high, low, close, volume, tick, index, timestamp, lastTransactionId);

                candles.add(candle);
                i++;

                if(dataInputStream.available() % 56 * 10000 == 0){
                    updateProgressBar(fileSize - dataInputStream.available());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return candles;
    }

    private static void updateProgressBar(int bytesRead) {
        SwingUtilities.invokeLater(() -> {
            // Update the progress bar here based on the bytesRead value
            progressBar.setValue(bytesRead);
        });
    }

    public static void appendCandle(String filepath, Candle candle) {
        try (DataOutputStream dataOutputStream = new DataOutputStream(new FileOutputStream(filepath, true))) {
            dataOutputStream.writeDouble(candle.open());
            dataOutputStream.writeDouble(candle.high());
            dataOutputStream.writeDouble(candle.low());
            dataOutputStream.writeDouble(candle.close());
            dataOutputStream.writeInt(candle.volume());
            dataOutputStream.writeInt(candle.tick());
            dataOutputStream.writeInt(candle.index());
            dataOutputStream.writeLong(candle.timestamp());
            dataOutputStream.writeLong(candle.lastTransactionId());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
