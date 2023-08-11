package com.localstrategy.util.helper;

import com.localstrategy.util.types.Position;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.StringJoiner;

public class ResultConsolidator {
    
    public static void writePositionsToCSV(List<Position> positions, String filePath) {

        positions.sort(Comparator.comparingLong(Position::getCloseTimestamp)); // Sort list by index closed

        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(filePath))) {
            // write header
            writer.write("index;trade_id;index_open;price_open;price_stop_init;index_filled;index_closed;price_closed;profit;rr;price_stop;position_size;breakeven;filled\n");
    
            int i = 0;

            for (Position position : positions) {
                // only write positions with non-zero profit and that are closed
                if (position.isClosed() && position.getProfit().doubleValue() != 0) {
                    StringJoiner joiner = new StringJoiner(";");
    
                    i++;

                    joiner.add(String.valueOf(i))
                            .add(String.valueOf(position.getId()))
                            .add(String.valueOf(position.getOpenTimestamp()))
                            .add(String.valueOf(position.getOpenPrice()))
                            .add(String.valueOf(position.getInitialStopLossPrice()))
                            .add(String.valueOf(position.getFillTimestamp()))
                            .add(String.valueOf(position.getCloseTimestamp()))
                            .add(String.valueOf(position.getClosingPrice()))
                            .add(String.valueOf(position.getProfit()))
                            .add(String.valueOf(position.calculateRR(position.getClosingPrice())))
                            .add(String.valueOf(position.getStopLossPrice()))
                            .add(String.valueOf(position.getSize()))
                            .add(String.valueOf(position.isBreakEven()))
                            .add(String.valueOf(position.isFilled()));
    
                    writer.write(joiner.toString() + "\n");
                }
            }
        } catch (IOException e) {
            System.err.println("Error writing to CSV file: " + e.getMessage());
        }
    }
}
