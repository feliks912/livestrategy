package com.localstrategy.util.old;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.opencsv.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import com.opencsv.exceptions.CsvException;

public class CsvModifier {

    private static int currentFile;

    public static void modifyCsvFiles(String directoryPath, String fromDate) {
        currentFile = 0;
        if(fromDate == null){
            try {
                Files.walk(Paths.get(directoryPath))
                    .filter(path -> path.toString().endsWith(".csv"))
                    .forEach(CsvModifier::processFile);
            } catch(IOException ex) {
                ex.printStackTrace();
            }
        } else {
            LocalDate startDate = LocalDate.parse(fromDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            try {
                Files.walk(Paths.get(directoryPath))
                    .filter(path -> path.toString().endsWith(".csv"))
                    .filter(path -> {
                        String filename = path.getFileName().toString();
                        String datePart = filename.substring(15, 25); // Extract date part of the filename
                        LocalDate fileDate = LocalDate.parse(datePart, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                        return (fileDate.isAfter(startDate) || fileDate.isEqual(startDate)); // Check if file date is after or equal to start date
                    })
                    .forEach(CsvModifier::processFile);
            } catch(IOException ex){
                ex.printStackTrace();
            }
        }
        
    }

    private static void processFile(Path filePath) {
        String fileName = filePath.toString();
        List<String[]> newRows = new ArrayList<>();

        try (CSVReader reader = new CSVReader(new FileReader(fileName))) {
            String[] nextLine;
            while ((nextLine = reader.readNext()) != null) {
                // Remove the 0th, 2nd, 5th, 6th column
                List<String> lineList = new ArrayList<>(Arrays.asList(nextLine));
                lineList.remove(6);
                lineList.remove(5);
                lineList.remove(2);
                lineList.remove(0);
                
                // Round the 1st column (0th index after removing) to 2 decimal places
                double roundedValue = new BigDecimal(lineList.get(0))
                        .setScale(2, RoundingMode.HALF_UP).doubleValue();
                lineList.set(0, String.valueOf(roundedValue));
                
                newRows.add(lineList.toArray(new String[0]));
            }
        } catch (IOException | CsvException ex) {
            ex.printStackTrace();
        }

        // Write modified rows back to the file
        try {
            ICSVWriter writer = new CSVWriterBuilder(new FileWriter(fileName))
                    .withSeparator(CSVWriter.DEFAULT_SEPARATOR)
                    .withQuoteChar(ICSVWriter.NO_QUOTE_CHARACTER)
                    .build();
            writer.writeAll(newRows);
            writer.close();
            System.out.println("wrote to file " + currentFile);
            currentFile++;
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
