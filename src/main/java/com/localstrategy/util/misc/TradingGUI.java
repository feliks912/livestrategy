package com.localstrategy.util.misc;

import com.localstrategy.util.types.Position;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;

public class TradingGUI extends JFrame {

    private CandlestickChart candlestickChart;
    private PositionsTable positionsTable;
    private double oldPortfolio = 0;
    private double currentPortfolio = 0;


    public TradingGUI(ArrayList<Position> activePositions, ArrayList<Position> inactivePositions, int distance) {
        // Set the layout manager for the frame
        setLayout(new BorderLayout());

        // Create a candlestick chart
        positionsTable = new PositionsTable(inactivePositions, false);
        candlestickChart = new CandlestickChart(distance, positionsTable, activePositions, false);
        

        // Get initial portfolio values
        oldPortfolio = currentPortfolio;

        // Create a JSplitPane to display the chart and table side by side
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, candlestickChart.getContentPane(), positionsTable.getContentPane());
        splitPane.setOneTouchExpandable(true);
        splitPane.setDividerLocation(500); // you can change this to set initial divider location

        // Create the key binding
        /* addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                    candlestickChart.eventUpdateAction();
                    //positionsTable.refreshTableData(candlestickChart.getStrategyZigZagRange().getClosedPositions());
                }
            }
        }); */

        KeyStroke spaceKey = KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(spaceKey, "SPACE_KEY_PRESSED");
        getRootPane().getActionMap().put("SPACE_KEY_PRESSED", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                /* while(currentPortfolio - oldPortfolio < 100){
                    candlestickChart.eventUpdateAction();
                    currentPortfolio = candlestickChart.getStrategyZigZagRange().getPortfolio();
                }
                oldPortfolio = currentPortfolio; */

                candlestickChart.setIsKeyPressed(true);
                System.out.println("pressed");
            }
        });

        // Add the JSplitPane to the frame
        add(splitPane, BorderLayout.CENTER);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 600); // adjust size to fit both components
        setLocationRelativeTo(null);
        setVisible(true);

        requestFocus();
    }

    public CandlestickChart getCandlestickChart(){
        return this.candlestickChart;
    }
}
