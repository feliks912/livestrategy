package com.localstrategy.util.helper;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import java.util.ArrayList;
import java.util.Date;
import java.util.Map;

public class PortfolioPlotter {
    public static void plot(ArrayList<Map.Entry<Long, Double>> data) {
        TimeSeries series = new TimeSeries("Data");

        for (Map.Entry<Long, Double> entry : data) {
            long timestamp = entry.getKey();
            Double value = entry.getValue();

            // Convert timestamp to Date
            Date date = new Date(timestamp);

            // Create Millisecond object from the Date
            Millisecond timeUnit = new Millisecond(date);

            // Use addOrUpdate to handle duplicate timestamps
            series.addOrUpdate(timeUnit, value);
        }

        TimeSeriesCollection dataset = new TimeSeriesCollection(series);

        // Create the chart
        JFreeChart chart = ChartFactory.createTimeSeriesChart(
                "Time Series Plot",
                "Timestamp",
                "Value",
                dataset
        );

        // Customize rendering (remove shapes)
        XYPlot plot = chart.getXYPlot();
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();

        // Set shapes visible to false to remove rectangles
        renderer.setSeriesShapesVisible(0, false);

        // Set lines visible to true
        renderer.setSeriesLinesVisible(0, true);

        plot.setRenderer(renderer);

        // Display the chart in a frame
        ChartFrame frame = new ChartFrame("Time Series Plot", chart);
        frame.pack();
        frame.setVisible(true);
    }
}
