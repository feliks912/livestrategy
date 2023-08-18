package com.localstrategy.util.helper;

import java.util.ArrayList;

public class DrawdownCalculator {
    public static double calculateMaxDrawdown(ArrayList<Double> portfolio) {
        if (portfolio == null || portfolio.size() == 0) {
            throw new IllegalArgumentException("Portfolio cannot be null or empty");
        }
        
        double maxDrawdown = 0.0;
        double currentPeak = portfolio.get(0);

        for (int i = 1; i < portfolio.size(); i++) {
            double currentValue = portfolio.get(i);
            if (currentValue > currentPeak) {
                currentPeak = currentValue;  // Current value is higher than current peak, so update it
            } else {
                double drawdown = (currentPeak - currentValue) / currentPeak;
                if (drawdown > maxDrawdown) {
                    maxDrawdown = drawdown;  // Current drawdown is higher than maximum observed so far, so update it
                }
            }
        }

        return maxDrawdown;
    }
}
