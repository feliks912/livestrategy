package com.localstrategy.util.misc;

import org.jfree.chart.*;
import org.jfree.chart.annotations.XYAnnotation;
import org.jfree.chart.annotations.XYLineAnnotation;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.axis.*;
import org.jfree.chart.plot.*;
import org.jfree.chart.renderer.xy.*;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.xy.*;

import com.localstrategy.Position;
import com.localstrategy.util.helper.CandleConstructor;
import com.localstrategy.util.helper.TransactionLoader;
import com.localstrategy.util.old.StrategyExecutor;
import com.localstrategy.util.types.Candle;
import com.localstrategy.util.types.SingleTransaction;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.Duration;

import org.jfree.data.Range;

import javax.swing.*;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Calendar;

public class CandlestickChart extends JFrame {
    private static final int MAX_CANDLES = 75; // Maximum number of candles on the chart
    private CustomOHLCDataset dataset;
    private int currentIndex;
    private Calendar calendar = Calendar.getInstance();
    List<XYAnnotation> annotations = new ArrayList<>();
    private List<ValueMarker> valueMarkers = new ArrayList<ValueMarker>();
    private XYTextAnnotation infoBox;
    private XYTextAnnotation infoBox2;
    private XYTextAnnotation infoBox3;
    private XYTextAnnotation infoBox4;
    private XYTextAnnotation infoBox5;
    private double previousProfit = 0;
    private double oldPortfolio = 0;
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private long startingTimestamp = 0;
    private StrategyExecutor zigZagStrat;
    private PositionsTable positionsTable;
    boolean isButtonPressed = false;
    CandleConstructor candleConstructor;
    private int distanceSet;

    public CandlestickChart(double candleVolume, PositionsTable positionsTable, StrategyExecutor zigZagStrat, boolean visible) {
        super("Candlestick Chart Demo");
        this.positionsTable = positionsTable;
        this.zigZagStrat = zigZagStrat;
        this.oldPortfolio = zigZagStrat.getPortfolio();

        this.distanceSet = zigZagStrat.getDistance();

        this.candleConstructor = new CandleConstructor(candleVolume);

        calendar.set(1970, Calendar.JANUARY, 1, 0, 0, 0);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Create the initial dataset with empty data
        dataset = new CustomOHLCDataset("Series", zigZagStrat.getDistance());

        // Create the candlestick chart
        JFreeChart chart = createChart(dataset);

        // Create a chart panel and add it to the frame
        ChartPanel chartPanel = new ChartPanel(chart);
        getContentPane().add(chartPanel);

        /* InputMap inputMap = chartPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = chartPanel.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "space");
        actionMap.put("space", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {

                
            }
        }); */


        // Add a key listener to handle keypress events
        /* addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_SPACE) {

                    
                }
            }
        }); */

        // Set frame properties
        setPreferredSize(new Dimension(800, 600));
        pack();
        setLocationRelativeTo(null);
        setVisible(visible);

        // Set focus to the frame so that key events are captured
        requestFocus();
    }

    private JFreeChart createChart(OHLCDataset dataset) {
    // Create a time series chart
        /* JFreeChart chart = ChartFactory.createCandlestickChart(
                "Candlestick Chart Demo",
                "Time",
                "Price",
                dataset,
                false
        ); */

        CandlestickRenderer customRenderer = new CustomCandlestickRenderer();
        //CandlestickRenderer customRenderer = new CandlestickRenderer();
        XYPlot customPlot = new XYPlot(dataset, new DateAxis("Date"), new NumberAxis("Price"), customRenderer);
        JFreeChart chart = new JFreeChart("Candlestick Demo", JFreeChart.DEFAULT_TITLE_FONT, customPlot, false);

        // Customize the chart appearance
        XYPlot plot = (XYPlot) chart.getPlot();
        CandlestickRenderer renderer = (CandlestickRenderer) plot.getRenderer();
        renderer.setUseOutlinePaint(true);
        renderer.setDrawVolume(false);

        // Add info box with current index information
        infoBox = new XYTextAnnotation("", 0, 0);
        infoBox.setFont(new Font("SansSerif", Font.PLAIN, 12));
        infoBox.setPaint(Color.BLACK);
        infoBox.setTextAnchor(TextAnchor.TOP_RIGHT);
        plot.addAnnotation(infoBox);

        // Add info box with current index information
        infoBox2 = new XYTextAnnotation("", 0, 0);
        infoBox2.setFont(new Font("SansSerif", Font.PLAIN, 12));
        infoBox2.setPaint(Color.BLACK);
        infoBox2.setTextAnchor(TextAnchor.TOP_RIGHT);
        plot.addAnnotation(infoBox2);

        // Add info box with current index information
        infoBox3 = new XYTextAnnotation("", 0, 0);
        infoBox3.setFont(new Font("SansSerif", Font.PLAIN, 12));
        infoBox3.setPaint(Color.BLACK);
        infoBox3.setTextAnchor(TextAnchor.TOP_RIGHT);
        plot.addAnnotation(infoBox3);

        // Add info box with current index information
        infoBox4 = new XYTextAnnotation("", 0, 0);
        infoBox4.setFont(new Font("SansSerif", Font.PLAIN, 12));
        infoBox4.setPaint(Color.BLACK);
        infoBox4.setTextAnchor(TextAnchor.TOP_RIGHT);
        plot.addAnnotation(infoBox4);

        // Add info box with current index information
        infoBox5 = new XYTextAnnotation("", 0, 0);
        infoBox5.setFont(new Font("SansSerif", Font.PLAIN, 12));
        infoBox5.setPaint(Color.BLACK);
        infoBox5.setTextAnchor(TextAnchor.TOP_RIGHT);
        plot.addAnnotation(infoBox5);

        // Set the domain axis to display the time values
        DateAxis domainAxis = new DateAxis("Time");
        domainAxis.setDateFormatOverride(new SimpleDateFormat("HH:mm:ss"));
        plot.setDomainAxis(domainAxis);

        // Hide the tick labels of the domain axis
        domainAxis.setTickLabelsVisible(false);

        // Set the range axis to autoscale
        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setAutoRange(true);
        rangeAxis.setAutoRangeIncludesZero(false); // Enable autoscaling including zero

        return chart;
    }

    public void executor(){
        ArrayList<SingleTransaction> transactionList;

        TransactionLoader transactionLoader = new TransactionLoader("C:/--- BTCUSDT/", 
        //null,
        "2022-07-13", 
        null);

        int fileCount = transactionLoader.getTotalCsvFiles();

        for(int i = 1; i <= fileCount; i++){

            transactionList = new ArrayList<SingleTransaction>(transactionLoader.loadNextDay());

            for(SingleTransaction transaction : transactionList){
                if(startingTimestamp == 0){
                    startingTimestamp = transaction.getTimestamp();
                }

                Candle candle = candleConstructor.processTradeEvent(transaction);

                if(candle == null && candleConstructor.getLastCandleIndex() != 0){ //New trasaction
                    //zigZagStrat.priceUpdate(i, transaction, candleConstructor.getLastCandle());
                } 
                else if(candle != null){ // New candle is formed
                    //zigZagStrat.newCandle(transactiodn, candleConstructor.getCandles());

                    //TODO: Update chart and set boolean flag to wait
                    eventUpdateAction();
                    positionsTable.refreshTableData(zigZagStrat.getClosedPositions());

                    zigZagStrat.resetTemporaryProfit();

                    if(isButtonPressed){
                        isButtonPressed = false;
                        while (!isButtonPressed) {
                            try {
                                // Wait for 100ms before checking again
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        isButtonPressed = false;
                    } else {
                        try{
                            Thread.sleep(30);
                        } catch(Exception e){
                            System.out.println(e);
                        }
                    } 
                }
            }
        }
    }

    public void eventUpdateAction(){

        addNewCandle();
        currentIndex++;

        // Update the value markers with the new candle's high and low values
        updateValueMarkers(zigZagStrat.getPositions());

        // Update the info box with the current index information
        updateInfoBox(infoBox, "Position count: " + Integer.toString(zigZagStrat.getPositions().size()), 0);

        //FIXME: Add temporaryProfit
        double profit = zigZagStrat.getTemporaryProfit();

        if(profit != 0){
            updateInfoBox(infoBox2, "last Profit: " + String.format("%.2f", profit), 0.02);
            previousProfit = profit;
        } else {
            updateInfoBox(infoBox2, "last Profit: " + String.format("%.2f", previousProfit), 0.02);
        }
        
        updateInfoBox(infoBox3, "Porftolio value: " + String.format("%.2f", zigZagStrat.getPortfolio()) , 0.04);

        //FIXME: Current candle timestamp, add candles to candles
        long currentTimestamp = candleConstructor.getLastCandle().getTimestamp();

        LocalDateTime currentDateTime = Instant.ofEpochMilli(currentTimestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime();

        LocalDateTime startingDateTime = Instant.ofEpochMilli(startingTimestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime();


        // Calculate duration between timestamps
        Duration duration = Duration.between(startingDateTime, currentDateTime);
        Period period = Period.between(startingDateTime.toLocalDate(), currentDateTime.toLocalDate());

        // Format duration to string
        String formattedDuration = formatDuration(duration, period);

        updateInfoBox(infoBox4, "Current time: " + currentDateTime.format(formatter) , 0.06);

        updateInfoBox(infoBox5, "Elapsed time: " + formattedDuration , 0.08);
    }

    private void addNewCandle() {
        if (currentIndex >= MAX_CANDLES) {
            // Remove the oldest candle from the dataset
            dataset.removeFirstItem();
        }

        // Create a new candle and add it to the dataset
        calendar.add(Calendar.MINUTE, 1);

        Candle tempCandle = candleConstructor.getLastCandle();

        OHLCDataItem candle = new OHLCDataItem(
            calendar.getTime(),
            tempCandle.getOpen(),
            tempCandle.getHigh(),
            tempCandle.getLow(),
            tempCandle.getClose(),
            Math.abs(tempCandle.getTick()) < distanceSet ? -tempCandle.getVolume() : tempCandle.getVolume()
        );
        dataset.addCandle(candle);
        //currentIndex++;

        // Update the chart
        ChartPanel chartPanel = (ChartPanel) getContentPane().getComponent(0);
        chartPanel.restoreAutoBounds();

        // Update the info box with the current index information
    }

    private void updateValueMarkers(List<Position> positions) {
        // Get the chart's plot
        JFreeChart chart = ((ChartPanel) getContentPane().getComponent(0)).getChart();
        XYPlot plot = chart.getXYPlot();

        // Clear existing annotations
        for (XYAnnotation annotation : annotations) {
            plot.removeAnnotation(annotation);
        }
        annotations.clear();

        for (ValueMarker marker : valueMarkers) {
            plot.removeRangeMarker(marker);
        }
        valueMarkers.clear();

        // If positions list is empty, exit function
        if (positions.isEmpty()) {
            return;
        }

        // Define max endpoint index for rays
        int maxEndpointIndex = Math.min(dataset.getItemCount(0) - 1 + MAX_CANDLES, dataset.getItemCount(0) - 1);

        // Add new annotations for each position
        for (Position position : positions) {
            double priceEntry = position.getOpenPrice();
            double stopLossPrice = position.getStopLossPrice();
            int entryIndex = position.getEntryPriceIndex();
            int stopLossIndex = position.getInitialStopLossIndex();

            if(currentIndex - entryIndex < MAX_CANDLES - 6){ // margin 3 on both sides
                // Create and add ray for entry price
                XYLineAnnotation entryRay = new XYLineAnnotation(
                    dataset.getXValue(0, maxEndpointIndex - (currentIndex - entryIndex) + 1), priceEntry, dataset.getXValue(0, maxEndpointIndex), priceEntry,
                    new BasicStroke(2f), position.isFilled() ? Color.GREEN : Color.ORANGE
                );
                plot.addAnnotation(entryRay);
                annotations.add(entryRay);
            } else {
                ValueMarker entryMarker = new ValueMarker(priceEntry);
                entryMarker.setPaint(position.isFilled() ? Color.GREEN : Color.ORANGE);
                entryMarker.setStroke(new BasicStroke(2f));
                plot.addRangeMarker(entryMarker);
                valueMarkers.add(entryMarker);
            }

            if(currentIndex - stopLossIndex < MAX_CANDLES - 6){
                // Create and add ray for stoploss price
                XYLineAnnotation stopLossRay = new XYLineAnnotation(
                    dataset.getXValue(0, maxEndpointIndex - (currentIndex - stopLossIndex) + 1), stopLossPrice, dataset.getXValue(0, maxEndpointIndex), stopLossPrice,
                    new BasicStroke(2f), Color.RED
                );
                plot.addAnnotation(stopLossRay);
                annotations.add(stopLossRay);
            } else {
                ValueMarker stoplossMarker = new ValueMarker(stopLossPrice);
                stoplossMarker.setPaint(Color.RED);
                stoplossMarker.setStroke(new BasicStroke(2f));
                plot.addRangeMarker(stoplossMarker);
                valueMarkers.add(stoplossMarker);
            }
        }
    }



    private void updateInfoBox(XYTextAnnotation infoBox, String text, double yOffsetPercentage) {
        // Get the plot from the chart
        XYPlot plot = ((ChartPanel) getContentPane().getComponent(0)).getChart().getXYPlot();

        // Calculate the coordinates for the top right corner
        double x = plot.getDomainAxis().getUpperBound();
        double y = plot.getRangeAxis().getUpperBound();

        // Apply the offset percentage to the y-coordinate
        Range range = plot.getRangeAxis().getRange();
        double offset = range.getLength() * yOffsetPercentage;
        y = y - offset;

        // Update the info box coordinates
        infoBox.setX(x);
        infoBox.setY(y);

        // Update the info box text with the current index information
        infoBox.setText(text);
    }

    private static String formatDuration(Duration duration, Period period) {
        long years = period.getYears();
        long months = period.getMonths();
        long days = period.getDays();
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.getSeconds() % 60;

        StringBuilder builder = new StringBuilder();

        if (years > 0) {
            builder.append(years).append(" year").append(years > 1 ? "s" : "").append(" ");
        }
        if (months > 0) {
            builder.append(months).append(" month").append(months > 1 ? "s" : "").append(" ");
        }
        if (days > 0) {
            builder.append(days).append(" day").append(days > 1 ? "s" : "").append(" ");
        }
        if (hours > 0) {
            builder.append(hours).append(" hour").append(hours > 1 ? "s" : "").append(" ");
        }
        if (minutes > 0) {
            builder.append(minutes).append(" minute").append(minutes > 1 ? "s" : "").append(" ");
        }
        if (seconds > 0) {
            builder.append(seconds).append(" second").append(seconds > 1 ? "s" : "").append(" ");
        }

        return builder.toString().trim();
    }

    public StrategyExecutor getStrategyExecutor(){
        return this.zigZagStrat;
    }

    public void setIsKeyPressed(boolean isit){
        this.isButtonPressed = isit;
    }
}




class CustomOHLCDataset extends DefaultOHLCDataset {
    private List<OHLCDataItem> candles;

    public CustomOHLCDataset(String seriesKey, int distance) {
        super(seriesKey, new OHLCDataItem[0]);
        candles = new ArrayList<>();
    }

    public void addCandle(OHLCDataItem candle) {
        candles.add(candle);
        fireDatasetChanged();
    }

    public void removeFirstItem() {
        if (!candles.isEmpty()) {
            candles.remove(0);
            fireDatasetChanged();
        }
    }

    @Override
    public int getItemCount(int series) {
        return candles.size();
    }

    @Override
    public Number getX(int series, int item) {
              return candles.get(item).getDate().getTime();
    }

    @Override
    public Number getY(int series, int item) {
        return candles.get(item).getClose();
    }

    @Override
    public Number getHigh(int series, int item) {
        return candles.get(item).getHigh();
    }

    @Override
    public Number getLow(int series, int item) {
        return candles.get(item).getLow();
    }

    @Override
    public Number getOpen(int series, int item) {
        return candles.get(item).getOpen();
    }

    @Override
    public Number getClose(int series, int item) {
        return candles.get(item).getClose();
    }

    @Override
    public Number getVolume(int series, int item){
        return candles.get(item).getVolume();
    }
}

