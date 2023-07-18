package com.localstrategy.util.types;

import java.util.ArrayList;

import com.localstrategy.UserAssets;
import com.localstrategy.Order;

public class UserDataResponse {
    
    private UserAssets userAssets;

    private ArrayList<Order> filledOrders = new ArrayList<Order>();
    private ArrayList<Order> newOrders = new ArrayList<Order>();
    private ArrayList<Order> cancelledOrders = new ArrayList<Order>();
    private ArrayList<Order> rejectedOrders = new ArrayList<Order>();

    public UserDataResponse(
        UserAssets userAssets,
        ArrayList<Order> newOrders, 
        ArrayList<Order> filledOrders,
        ArrayList<Order> cancelledOrders,
        ArrayList<Order> rejectedOrders) {

            this.userAssets = new UserAssets(userAssets);
            this.filledOrders = Order.deepCopyOrderList(filledOrders);
            this.newOrders = Order.deepCopyOrderList(newOrders);
            this.cancelledOrders = Order.deepCopyOrderList(cancelledOrders);
            this.rejectedOrders = Order.deepCopyOrderList(rejectedOrders);
    }

    public UserAssets getUserAssets() {
        return this.userAssets;
    }

    public ArrayList<Order> getFilledOrders() {
        return this.filledOrders;
    }

    public ArrayList<Order> getNewOrders() {
        return this.newOrders;
    }

    public ArrayList<Order> getCancelledOrders() {
        return this.cancelledOrders;
    }

    public ArrayList<Order> getRejectedOrders() {
        return this.rejectedOrders;
    }
}
