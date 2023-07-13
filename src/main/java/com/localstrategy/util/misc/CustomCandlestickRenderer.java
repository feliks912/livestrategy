package com.localstrategy.util.misc;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.geom.Rectangle2D;

import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.CrosshairState;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.CandlestickRenderer;
import org.jfree.chart.renderer.xy.XYItemRendererState;
import org.jfree.data.xy.XYDataset;

import com.localstrategy.util.old.RunningStats;

public class CustomCandlestickRenderer extends CandlestickRenderer {

    private RunningStats volumeStats = new RunningStats();

    @Override
    public void drawItem(Graphics2D g2, XYItemRendererState state, 
                         Rectangle2D dataArea, PlotRenderingInfo info, 
                         XYPlot plot, ValueAxis domainAxis, ValueAxis rangeAxis, 
                         XYDataset dataset, int series, int item, 
                         CrosshairState crosshairState, int pass) {

        CustomOHLCDataset highLowDataset = (CustomOHLCDataset) dataset;
        double open = highLowDataset.getOpenValue(series, item);
        double close = highLowDataset.getCloseValue(series, item);
        double volume = highLowDataset.getVolumeValue(series, item);

        volumeStats.addNumber(Math.abs(volume));

        // Backup original paints
        Paint originalUpPaint = getUpPaint();
        Paint originalDownPaint = getDownPaint();

        int alphaChannel = mapInputToStandardDeviation(
            false ? Math.abs(volume) : (volume < 0 ? 1e6 : volume),
            volumeStats.getMean(), 
            volumeStats.getStandardDeviation());

        // Create semi-transparent colors with alpha = 128
        if (open > close) {
            setDownPaint(new Color(255, 0, 0, alphaChannel));
            setUpPaint(new Color(255, 0, 0, alphaChannel));
        } else {
            setUpPaint(new Color(0, 255, 0, alphaChannel)); // semi-transparent red
            setDownPaint(new Color(0, 255, 0, alphaChannel));
        }

        // Call the superclass's drawItem method
        super.drawItem(g2, state, dataArea, info, plot, domainAxis, rangeAxis, 
                       dataset, series, item, crosshairState, pass);

        // Restore original paints
        setUpPaint(originalUpPaint);
        setDownPaint(originalDownPaint);
    }



    private static int mapInputToStandardDeviation(double input, double mean, double stddev) {
        // Find the standard deviation multiplier
        double multiplier = (input - mean) / stddev;

        // Multiply the multiplier by 64 (128's standard deviation) and add it to 128 (mean)
        int mappedValue = (int)Math.round(multiplier * 64 + 128);

        // Reverse the output
        mappedValue = 255 - mappedValue;

        // Ensure the mapped value is between 0 and 255
        if (mappedValue < 0) {
            mappedValue = 0;
        } else if (mappedValue > 255) {
            mappedValue = 255;
        }

        return mappedValue;
    }
}

