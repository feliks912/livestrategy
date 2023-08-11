package com.localstrategy.util.misc;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.PriorityQueue;
import java.util.Queue;

public class BinaryCreator {

    Queue<Path> filesQueue = new PriorityQueue<>();

    String outputFolderPath;

    Path lastFilePath;

    public BinaryCreator(String inputFolderPath, String outputFolderPath) {

        this.outputFolderPath = outputFolderPath;

        Path dir = Paths.get(inputFolderPath);

        try (DirectoryStream<Path> paths = Files.newDirectoryStream(dir, "*.csv")) {
            for (Path path : paths) {
                filesQueue.add(path);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public void convertAll(){
        while(!filesQueue.isEmpty()){
            convertNextFile();
        }
    }

    public void convertNextFile(){
        if(filesQueue.isEmpty()){
            return;
        }

        Path filePath = filesQueue.poll();
        String filename = filePath.getFileName().toString();
        String binaryFileName = filename.substring(0, filename.length() - 4).concat(".bin");
        Path outputPath = Paths.get(outputFolderPath, binaryFileName);
        this.lastFilePath = outputPath;

        try (BufferedReader br = new BufferedReader(new FileReader(filePath.toFile()));
             ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(byteArrayOutputStream)) {

            String firstLine = br.readLine();

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

                String[] values = line.split(",");

                int price = customRoundToInt((Double.parseDouble(values[0]) + firstPriceUnadjusted) * 100);

                quantity = Integer.parseInt(values[1]);
                int deltaTimestamp = Integer.parseInt(values[2]);

                dos.write(ByteBuffer.allocate(4).putInt(price).array());
                dos.write(ByteBuffer.allocate(4).putInt(quantity).array());
                dos.write(ByteBuffer.allocate(4).putInt(deltaTimestamp).array());
            }

            // Write the binary data to the output file
            byte[] data = byteArrayOutputStream.toByteArray();
            try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
                fos.write(data);
            }

            System.out.println("File " + binaryFileName + " created successfully.");

            br.close();

            try{
                if(!Files.deleteIfExists(filePath)){
                    System.out.println("File unsuccessfully deleted.");
                } else {
                    System.out.println("File " + filename + " deleted successfully.");
                }
            } catch(SecurityException e){
                e.printStackTrace();
            }


        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static int customRoundToInt(double value){
        double remainder = value % 1;
        if(remainder >= 0.5){
            value = (int) value + 1;
        }
        return (int) value;
    }
}
