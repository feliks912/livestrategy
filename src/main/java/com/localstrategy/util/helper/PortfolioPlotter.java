package com.localstrategy.helper;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.JFreeChart;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import java.util.ArrayList;

public class PortfolioPlotter {
    public static void plot(ArrayList<Double> data){
        XYSeries series = new XYSeries("Data");
        for (int i = 0; i < data.size(); i++) {
            series.add(i, data.get(i));
        }
        XYSeriesCollection dataset = new XYSeriesCollection(series);

        // Create the chart
        JFreeChart chart = ChartFactory.createXYLineChart(
                "Array Plot",
                "Index",
                "Value",
                dataset
        );

        // Display the chart in a frame
        ChartFrame frame = new ChartFrame("Array Plot", chart);
        frame.pack();
        frame.setVisible(true);
    }
}
