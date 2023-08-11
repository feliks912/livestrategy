package com.localstrategy.util.misc;

import com.localstrategy.util.types.SingleTransaction;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.PriorityQueue;
import java.util.Queue;

public class TemporaryBinaryTester {

    Queue<Path> filesQueue = new PriorityQueue<>();

    Path lastFilePath;

    public TemporaryBinaryTester(String inputFolderPath) {

        Path dir = Paths.get(inputFolderPath);

        try (DirectoryStream<Path> paths = Files.newDirectoryStream(dir, "*.csv")) {
            for (Path path : paths) {
                filesQueue.add(path);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public ArrayList<SingleTransaction> convertNextFile(){

        if(filesQueue.isEmpty()){
            return null;
        }

        Path filePath = filesQueue.poll();
        String filename = filePath.getFileName().toString();
        String binaryFileName = filename.substring(0, filename.length() - 4).concat(".bin");

        try (BufferedReader br = new BufferedReader(new FileReader(filePath.toFile()));
             ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(byteArrayOutputStream)) {

            String firstLine = br.readLine();

//                StringTokenizer st = new StringTokenizer(firstLine, ",");
//                String[] firstValues = {st.nextToken(), st.nextToken(), st.nextToken()};

            String[] firstValues = firstLine.split(",");

            double firstPriceUnadjusted = Double.parseDouble(firstValues[0]);

            int firstPrice = customRoundToInt(Double.parseDouble(firstValues[0]) * 100);
            int quantity = Integer.parseInt(firstValues[1]);
            long firstTimestamp = Long.parseLong(firstValues[2]);

            dos.write(ByteBuffer.allocate(4).putInt(firstPrice).array());
            dos.write(ByteBuffer.allocate(4).putInt(quantity).array());
            dos.write(ByteBuffer.allocate(8).putLong(firstTimestamp).array());

            String line;
            while ((line = br.readLine()) != null) {

//                    st = new StringTokenizer(line, ",");
//                    String[] values = {st.nextToken(), st.nextToken(), st.nextToken()};

                String[] values = line.split(",");

                int price = customRoundToInt((Double.parseDouble(values[0]) + firstPriceUnadjusted) * 100);

                quantity = Integer.parseInt(values[1]);
                int deltaTimestamp = Integer.parseInt(values[2]);

                dos.write(ByteBuffer.allocate(4).putInt(price).array());
                dos.write(ByteBuffer.allocate(4).putInt(quantity).array());
                dos.write(ByteBuffer.allocate(4).putInt(deltaTimestamp).array());
            }

            ArrayList<SingleTransaction> transactionDataList = new ArrayList<>();

            byte[] allBytes = byteArrayOutputStream.toByteArray();
            ByteBuffer buffer = ByteBuffer.wrap(allBytes);

            boolean firstLineB = true;
            firstTimestamp = 0;
            while (buffer.hasRemaining()) {
                int price = buffer.getInt();
                quantity = buffer.getInt();
                long timestamp;
                if (firstLineB) {
                    timestamp = buffer.getLong();
                    firstTimestamp = timestamp;
                    firstLineB = false;
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

//                try{
//                    if(!Files.deleteIfExists(filePath)){
//                        System.out.println("File unsuccessfully deleted.");
//                    }
//                } catch(SecurityException e){
//                    e.printStackTrace();
//                }


        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    public static int customRoundToInt(double value){
        double remainder = value % 1;
        if(remainder >= 0.5){
            value = (int) value + 1;
        }
        return (int) value;
    }
}
