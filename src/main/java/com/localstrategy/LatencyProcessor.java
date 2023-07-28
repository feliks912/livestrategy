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
                String[] parts = firstLine.split(",");
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
                    .map(line -> line.split(","))
                    .filter(pair -> pair.length == 2)
                    .map(pair -> new Latency(Integer.parseInt(pair[0]), Long.parseLong(pair[1]) - finalStartingTimestamp))
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
                    )); //Damn cool, hard to debug. Hope it works.

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void calculateLatency(Event event) {

        // Load all latencies and group them by day
        // For each file date calculate the day in the week
        // Count all timestamps from zero from both the transaction and latency file.
        // For each transaction find the first timestamp instance which is larger than the current timestamp
        // Use that latency
        // Since all days have 24 hours we don't have scale it.
        // Keep the last latency index in memory, and if the current event timestamp is larger than that of the current latency, loop until we satisfy the upper condition.
        // We can categorize them by day (enables shuffling) or categorize them by timestamp in a week. I prefer the former.

        if (firstWeeklyTimestamp == 0) {
            firstWeeklyTimestamp = event.getTimestamp();
            dailyLatencyList = latencyList.get(0);
        } else if (event.getTimestamp() - firstWeeklyTimestamp > (long) (currentDay + 1) * MILLISECONDS_IN_DAY) { //It's the next day oh boyah
            if (++currentDay >= 7) {
                currentDay = 0;
                firstWeeklyTimestamp = event.getTimestamp();
            }
            firstDailyTimestamp = event.getTimestamp();
            dailyLatencyList = latencyList.get(currentDay);
            currentLatencyIndex = 0;
        }

        while (dailyLatencyList.get(currentLatencyIndex).timestamp() < event.getTimestamp() - firstDailyTimestamp && currentLatencyIndex < dailyLatencyList.size()) {
            currentLatency = dailyLatencyList.get(currentLatencyIndex++).latency();
        }
    }

    public static int getCurrentLatency(){
        return currentLatency;
    }
}
