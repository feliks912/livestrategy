package com.localstrategy.util.misc;

import com.localstrategy.util.enums.OrderSide;
import com.localstrategy.util.types.Position;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.Comparator;
import java.util.LinkedList;

public class PositionsTable extends JFrame {

    private JTable positionTable;
    private DefaultTableModel tableModel;

    LinkedList<Position> inactivePositions;

    public PositionsTable(LinkedList<Position> inactivePositions, boolean visible) {

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

        inactivePositions.sort(Comparator.comparingLong(Position::getId).reversed());

        for (Position position : inactivePositions) {
            if(position.getFillPrice() != null){

                boolean isFucked = false;

                if(position.getDirection().equals(OrderSide.BUY)){
                    if(position.isFilled()){
                        if(position.getStopOrder().getOpenPrice().compareTo(position.getEntryOrder().getFillPrice()) >= 0){
                            if(position.getProfit().doubleValue() > 0){
                                isFucked = true;
                            }
                        }
                    }
                } else {
                    if(position.isFilled()){
                        if(position.getStopOrder().getOpenPrice().compareTo(position.getEntryOrder().getFillPrice()) <= 0){
                            if(position.getProfit().doubleValue() > 0){
                                isFucked = true;
                            }
                        }
                    }
                }

                Object[] rowData = {
                        position.getId(),
                        position.getOrderType(),
                        position.getOpenPrice(),
                        position.getInitialStopLossPrice(),
                        position.getStopLossPrice(),
                        String.format("%.5f", position.getSize()),
                        position.getClosingPrice(),
                        position.getDirection().equals(OrderSide.BUY) ? "long" : "short",
                        position.getRR(),
                        String.format("%.2f", position.getProfit()),
                        isFucked
                };
                tableModel.addRow(rowData);
            }
        }
        tableModel.fireTableDataChanged();  // signal the JTable to update
    }
}
