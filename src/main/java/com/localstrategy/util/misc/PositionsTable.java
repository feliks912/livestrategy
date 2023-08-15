package com.localstrategy.util.misc;

import com.localstrategy.util.enums.OrderSide;
import com.localstrategy.util.types.Position;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class PositionsTable extends JFrame {

    private JTable positionTable;
    private DefaultTableModel tableModel;

    ArrayList<Position> inactivePositions;

    public PositionsTable(ArrayList<Position> inactivePositions, boolean visible) {

        this.inactivePositions = inactivePositions;

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(new Dimension(800, 600));

        String[] columnNames = {"ID", "Order Type", "Entry Price", "Initial Stop Loss Price", "Closing Stop Loss Price",
                "Size", "Closing Price", "Direction", "RR", "Profit", "Filled"};
        tableModel = new DefaultTableModel(columnNames, 0);
        positionTable = new JTable(tableModel);

        JScrollPane scrollPane = new JScrollPane(positionTable);
        add(scrollPane, BorderLayout.CENTER);

        setVisible(visible);
    }

    public void refreshTableData() {
        tableModel.setRowCount(0);  // clear current data

        if(inactivePositions.isEmpty()){
            return;
        }

        Collections.sort(inactivePositions, Comparator.comparingLong(Position::getId).reversed());

        for (Position position : inactivePositions) {
            if(position.getFillPrice() != null){
                Object[] rowData = {
                        position.getId(),
                        position.getOrderType(),
                        position.getOpenPrice(),
                        position.getInitialStopLossPrice(),
                        position.getStopLossPrice(),
                        String.format("%.5f", position.getSize()),
                        position.getClosingPrice(),
                        position.getDirection().equals(OrderSide.BUY) ? "long" : "short",
                        position.calculateRR(),
                        String.format("%.2f", position.getProfit()),
                        position.isFilled()
                };
                tableModel.addRow(rowData);
            }
        }
        tableModel.fireTableDataChanged();  // signal the JTable to update
    }
}
