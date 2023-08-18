package com.localstrategy.util.helper;

import org.apache.commons.collections4.map.SingletonMap;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import java.util.ArrayList;
import java.util.Date;
import java.util.Map;

public class PortfolioPlotter {
    public static void plot(
            ArrayList<Map.Entry<Long, Double>> portfolioData,
            ArrayList<SingletonMap<Long, Double>> closePrices,
            String chartName) {
        // Create the portfolio data series
        TimeSeries portfolioSeries = new TimeSeries("Portfolio Data");

        // Add portfolio data to the series
        for (Map.Entry<Long, Double> entry : portfolioData) {
            long timestamp = entry.getKey();
            Double value = entry.getValue();

            // Convert timestamp to Date
            Date date = new Date(timestamp);

            // Create Millisecond object from the Date
            Millisecond timeUnit = new Millisecond(date);

            // Use addOrUpdate to handle duplicate timestamps
            portfolioSeries.addOrUpdate(timeUnit, value);
        }

        // Calculate the minimum and maximum values of the portfolio data
        double portfolioMin = portfolioSeries.getMinY();
        double portfolioMax = portfolioSeries.getMaxY();

        // Create the close price series
        TimeSeries closePriceSeries = new TimeSeries("Close Prices");

        // Add close prices to the series
        for (SingletonMap<Long, Double> closePriceEntry : closePrices) {
            long timestamp = closePriceEntry.getKey();
            Double value = closePriceEntry.getValue();

            // Convert timestamp to Date
            Date date = new Date(timestamp);

            // Create Millisecond object from the Date
            Millisecond timeUnit = new Millisecond(date);

            // Use addOrUpdate to handle duplicate timestamps
            closePriceSeries.addOrUpdate(timeUnit, value);
        }

        // Calculate the minimum and maximum values of the close prices
        double closePriceMin = closePriceSeries.getMinY();
        double closePriceMax = closePriceSeries.getMaxY();

        // Calculate the scale factor and shift for the close prices
        double scaleFactor = (portfolioMax - portfolioMin) / (closePriceMax - closePriceMin);
        double shiftValue = portfolioMin - (scaleFactor * closePriceMin);

        // Apply the scale factor and shift to the close prices
        for (int i = 0; i < closePriceSeries.getItemCount(); i++) {
            Millisecond timeUnit = (Millisecond) closePriceSeries.getDataItem(i).getPeriod();
            double value = closePriceSeries.getValue(i).doubleValue();
            double scaledValue = scaleFactor * value + shiftValue;
            closePriceSeries.update(timeUnit, scaledValue);
        }

        // Create the dataset
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(portfolioSeries);
        dataset.addSeries(closePriceSeries);

        // Create the chart
        JFreeChart chart = ChartFactory.createTimeSeriesChart(
                chartName,
                "Timestamp",
                "Value",
                dataset
        );

        // Customize rendering (remove shapes)
        XYPlot plot = chart.getXYPlot();
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();

        // Set shapes visible to false to remove rectangles
        renderer.setSeriesShapesVisible(0, false);
        renderer.setSeriesShapesVisible(1, false);

        // Set lines visible to true
        renderer.setSeriesLinesVisible(0, true);
        renderer.setSeriesLinesVisible(1, true);

        // Set the colors of the plot lines
        renderer.setSeriesPaint(0, java.awt.Color.RED);
        renderer.setSeriesPaint(1, java.awt.Color.BLUE);

        plot.setRenderer(renderer);

        // Set the scale for the close prices to match the portfolio data scale
        NumberAxis closePriceAxis = new NumberAxis("Close Prices");
        closePriceAxis.setAutoRangeIncludesZero(false);
        plot.setRangeAxis(1, closePriceAxis);
        plot.mapDatasetToRangeAxis(1, 1);
        plot.getRangeAxis(1).setRange(portfolioMin, portfolioMax);

        // Display the chart in a frame
        ChartFrame frame = new ChartFrame("Time Series Plot", chart);
        frame.pack();
        frame.setVisible(true);
    }
}