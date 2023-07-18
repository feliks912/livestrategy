package com.localstrategy.util.types;

import com.localstrategy.Order;
import com.localstrategy.UserAssets;

import java.util.ArrayList;

public class UserDataResponse {
    
    private final UserAssets userAssets;

    private final ArrayList<Order> updatedOrders;

    public UserDataResponse(
        UserAssets userAssets,
        ArrayList<Order> updatedOrders) {

            this.userAssets = new UserAssets(userAssets);
            this.updatedOrders = Order.deepCopyOrderList(updatedOrders);
    }

    public UserAssets getUserAssets() {
        return this.userAssets;
    }

    public ArrayList<Order> getUpdatedOrders(){ return this.updatedOrders; }
}
