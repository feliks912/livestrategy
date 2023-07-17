package com.localstrategy.util.misc;

import java.util.LinkedList;
import java.util.Queue;

public class RunningStatsLength<T extends Number> {

    private Queue<Double> lastNNumbers;
    private double sum, sumSq;
    private int N;

    public RunningStatsLength(int N) {
        this.lastNNumbers = new LinkedList<>();
        this.sum = 0.0;
        this.sumSq = 0.0;
        this.N = N;
    }

    public void addNumber(T number) {
        double numberAsDouble = number.doubleValue();

        lastNNumbers.add(numberAsDouble);
        sum += numberAsDouble;
        sumSq += numberAsDouble * numberAsDouble;

        if (lastNNumbers.size() > N) {
            double oldestNumber = lastNNumbers.remove();
            sum -= oldestNumber;
            sumSq -= oldestNumber * oldestNumber;
        }
    }

    public double getMean() {
        return lastNNumbers.isEmpty() ? 0.0 : sum / lastNNumbers.size();
    }

    public double getVariance() {
        if (lastNNumbers.size() <= 1) return 0.0;
        double mean = getMean();
        return sumSq / lastNNumbers.size() - mean * mean;
    }

    public double getStandardDeviation() {
        return Math.sqrt(getVariance());
    }

    public int getCount() {
        return lastNNumbers.size();
    }
}
