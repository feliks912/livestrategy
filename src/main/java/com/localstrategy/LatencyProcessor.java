package com.localstrategy;

import com.localstrategy.util.types.Event;
import com.localstrategy.util.types.Latency;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LatencyProcessor {

    //TODO: Overlap read latencies to all transactions based on their time. For each transaction, find that transaction's relative timestamp in the labeled list and use that transaction's latency.
    // We don't want to read the whole file every time, so save the last index every time.
    // The total latency

    //TODO: Implement latency walls. That one will be fun because we probably won't end up using this individualistic, one-by-one implementation. Rather the latencies will be calculated for the entire day up-front, and all events will draw from those
    //FIXME: Add static Exchange processing time to all action requests, but include it before calculating the latency rule (or after, does it matter?)

    private static final long MILLISECONDS_IN_DAY = 24 * 60 * 60 * 1000;

    private static int currentDay = 0;
    private static long firstWeeklyTimestamp = 0;
    private static long firstDailyTimestamp = 0;
    private static Map<Integer, ArrayList<Latency>> latencyList = new HashMap<>();
    private static ArrayList<Latency> dailyLatencyList = new ArrayList<>();

    private static int currentLatencyIndex = 0;
    private static int currentLatency = 0;


    //Group latencies from input file by day in the week - each latency has a relative timestamp to the first transaction **in the week**
    public static void instantiateLatencies(String file) {
        Path filePath = Paths.get(file);

        long startingTimestamp = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath.toFile()))) {
            String firstLine = reader.readLine();
            if (firstLine != null) {
                String[] parts = firstLine.split(";");
                if (parts.length >= 1) {
                    startingTimestamp = Long.parseLong(parts[0]);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        try (Stream<String> lines = Files.lines(filePath)) {

            long finalStartingTimestamp = startingTimestamp;

            latencyList = lines
                    .map(line -> line.split(";"))
                    .filter(pair -> pair.length == 2)
                    .map(pair -> new Latency(Integer.parseInt(pair[1]), Long.parseLong(pair[0]) - finalStartingTimestamp))
                    .collect(Collectors.groupingBy(
                            (Latency latency) -> (int) (latency.timestamp() / MILLISECONDS_IN_DAY),
                            HashMap::new,
                            Collectors.collectingAndThen(
                                    Collectors.toCollection(ArrayList::new),
                                    (ArrayList<Latency> tempList) -> {
                                        long dayOffset = tempList.get(0).timestamp();
                                        return tempList.stream()
                                                .map(tempLatency -> new Latency(tempLatency.latency(), tempLatency.timestamp() - dayOffset))
                                                .collect(Collectors.toCollection(ArrayList::new));
                                    }
                            )
                    ));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void calculateLatency(Event event) {

        if (firstWeeklyTimestamp == 0) {
            firstWeeklyTimestamp = event.getTimestamp();
            firstDailyTimestamp = firstWeeklyTimestamp;
            dailyLatencyList = latencyList.get(0); // Get by key
        } else if (event.getTimestamp() - firstWeeklyTimestamp > (long) (currentDay + 1) * MILLISECONDS_IN_DAY) { //It's the next day oh boyah
            if (++currentDay > 6) {
                currentDay = 0;
                firstWeeklyTimestamp = event.getTimestamp();
            }
            firstDailyTimestamp = event.getTimestamp();
            dailyLatencyList = latencyList.get(currentDay);
            currentLatencyIndex = 0;
        }

        while (currentLatencyIndex < dailyLatencyList.size() - 1 && event.getTimestamp() - firstDailyTimestamp >= dailyLatencyList.get(currentLatencyIndex + 1).timestamp()) {
            currentLatencyIndex++;
        }

        double t1 = dailyLatencyList.get(currentLatencyIndex).timestamp();
        double l1 = dailyLatencyList.get(currentLatencyIndex).latency();

        double t2;
        double l2;

        if(currentLatencyIndex >= dailyLatencyList.size() - 1){
            if(currentDay != 6){
                t2 = latencyList.get(currentDay + 1).get(0).timestamp() + firstDailyTimestamp;
                l2 = latencyList.get(currentDay + 1).get(0).latency();
            } else {
                t2 = latencyList.get(0).get(0).timestamp() + firstDailyTimestamp;
                l2 = latencyList.get(0).get(0).latency();
            }
        } else {
            t2 = dailyLatencyList.get(currentLatencyIndex + 1).timestamp();
            l2 = dailyLatencyList.get(currentLatencyIndex + 1).latency();
        }

        //Scale latency to timestamp difference
        if(t1 != t2){
            double tn = event.getTimestamp() - firstDailyTimestamp;

            currentLatency = (int) (((tn - t1) / (t2 - t1) * (t2 + l2 - (t1 + l1))) + t1 + l1 - tn);
        } else {
            currentLatency = (int) (l1 + l2) / 2;
        }
    }

    public static int getCurrentLatency(){
        return currentLatency;
    }
}
