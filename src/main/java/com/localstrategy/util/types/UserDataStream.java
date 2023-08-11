package com.localstrategy.util.types;

import java.util.ArrayList;

public record UserDataStream(UserAssets userAssets, ArrayList<Order> updatedOrders) {

    public UserDataStream(
            UserAssets userAssets,
            ArrayList<Order> updatedOrders) {

        this.userAssets = new UserAssets(userAssets);
        this.updatedOrders = Order.deepCopyOrderList(updatedOrders);
    }
}
