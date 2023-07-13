package com.localstrategy;

import java.util.*;

public class LinearDegree {
    public static double calculateRSquared(List<Double> data) {
        int size = data.size();

        double sumX = 0.0, sumY = 0.0, sumXY = 0.0, sumXSquare = 0.0, sumYSquare = 0.0;
        for(int i = 0; i < size; i++) {
            double x = i + 1;
            double y = data.get(i);

            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumXSquare += x * x;
            sumYSquare += y * y;
        }

        double meanX = sumX / size;
        double meanY = sumY / size;

        double numerator = sumXY - size * meanX * meanY;
        double denominator = Math.sqrt((sumXSquare - size * meanX * meanX) * (sumYSquare - size * meanY * meanY));

        double correlationCoefficient = numerator / denominator;

        return Math.pow(correlationCoefficient, 2);
    }
}
