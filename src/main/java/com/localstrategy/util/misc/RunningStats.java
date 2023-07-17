package com.localstrategy.util.misc;

public class RunningStats<T extends Number> {

    private double oldMean, newMean, oldS, newS;
    private int count;

    public RunningStats() {
        oldMean = newMean = oldS = newS = 0.0;
        count = 0;
    }

    public void addNumber(T number) {
        count++;

        double numberAsDouble = number.doubleValue();

        // If this is the first value, set the new mean and variance to this value
        if (count == 1) {
            oldMean = newMean = numberAsDouble;
            oldS = 0.0;
        } else {
            // Calculate new mean and variance
            newMean = oldMean + (numberAsDouble - oldMean) / count;
            newS = oldS + (numberAsDouble - oldMean) * (numberAsDouble - newMean);

            // Set up for next iteration
            oldMean = newMean;
            oldS = newS;
        }
    }

    public double getMean() {
        return count > 0 ? newMean : 0.0;
    }

    public double getVariance() {
        return count > 1 ? newS / (count - 1) : 0.0;
    }

    public double getStandardDeviation() {
        return Math.sqrt(getVariance());
    }

    public int getCount() {
        return count;
    }
}
