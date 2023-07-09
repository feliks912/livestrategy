package com.localstrategy;

import com.opencsv.CSVReader;
import java.util.ArrayList;
import java.util.StringJoiner;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;


public class CandleFileManager {
    public static ArrayList<Candle> readCandles(String filepath, int startIndex, int endIndex){
        try {
            CSVReader reader = new CSVReader(new FileReader(filepath));
    
            //reader.readNext(); // Read headers
    
            String[] nextLine;
    
            ArrayList<Candle> candles = new ArrayList<>();
    
            if(endIndex == 0){
                endIndex = Integer.MAX_VALUE;
            }

            int i = 0;

            for(; i < startIndex; i++){
                reader.readNext();
            }
    
            // Read OHLC from input file
            while ((nextLine = reader.readNext()) != null && i < endIndex) {
                    Candle candle = new Candle();
    
                    candle.setIndex(Integer.parseInt(nextLine[0]));
                    candle.setLastTransactionId(Long.parseLong(nextLine[1])); //Read last candle transaction ID
                    candle.setOpen(Double.parseDouble(nextLine[2]));
                    candle.setHigh(Double.parseDouble(nextLine[3]));
                    candle.setLow(Double.parseDouble(nextLine[4]));
                    candle.setClose(Double.parseDouble(nextLine[5]));
                    candle.setTick(Integer.parseInt(nextLine[6])); // Read distances here
                    candle.setVolume(Integer.parseInt(nextLine[7])); //Read time here
                    candle.setTimestamp(Long.parseLong(nextLine[8])); //Read candle timestamp
    
                    candles.add(candle);
                    i++;
                }
            
    
            return candles;
    
        } catch (Exception e) {
            System.out.println("Candle reading exception: " + e);
    
            return null;
        }
    }

    public static void appendCandle(String filepath, Candle candle){
        try {
            Path path = Paths.get(filepath);

            if(!Files.exists(path)){
                try{
                    Files.createFile(path);
                } catch (Exception e){
                    System.out.println(e);
                    return;
                }
            }

            StringJoiner joiner = new StringJoiner(",");
            joiner
                .add(String.valueOf(candle.getIndex()))
                .add(String.valueOf(candle.getLastTransactionId()))
                .add(String.valueOf(candle.getOpen()))
                .add(String.valueOf(candle.getHigh()))
                .add(String.valueOf(candle.getLow()))
                .add(String.valueOf(candle.getClose()))
                .add(String.valueOf(candle.getTick()))
                .add(String.valueOf(candle.getVolume()))
                .add(String.valueOf(candle.getTimestamp()));
            

            Files.write(path, (joiner.toString() + System.lineSeparator()).getBytes(), StandardOpenOption.APPEND);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}


