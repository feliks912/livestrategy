package com.localstrategy;

import java.util.ArrayList;

public class MeanAndStandardDeviation {
    private ArrayList<Double> numbers = new ArrayList<Double>();

    public MeanAndStandardDeviation() {
    }

    public void addNumber(double number){
        numbers.add(number);
    }

    public void clear(){
        numbers.clear();
    }

    public double calculateMean() {
        double sum = 0;
        for (double num : numbers) {
            sum += num;
        }
        return sum / numbers.size();
    }

    public double calculateStandardDeviation() {
        double mean = calculateMean();
        double sumOfSquaredDifferences = 0;

        for (double num : numbers) {
            double difference = num - mean;
            sumOfSquaredDifferences += difference * difference;
        }

        double variance = sumOfSquaredDifferences / numbers.size();
        return Math.sqrt(variance);
    }

    public double[] calculateFirstTwoStandardDeviations() {
        double mean = calculateMean();
        double standardDeviation = calculateStandardDeviation();
        double firstLowerBound = mean - standardDeviation;
        double firstUpperBound = mean + standardDeviation;
        double secondLowerBound = mean - 2 * standardDeviation;
        double secondUpperBound = mean + 2 * standardDeviation;

        return new double[]{secondLowerBound, firstLowerBound, firstUpperBound, secondUpperBound};
    }

    public void setNumbers(ArrayList<Double> numbers){
        this.numbers.clear();
        this.numbers.addAll(numbers);
    }
}