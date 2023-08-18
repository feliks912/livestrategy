package com.localstrategy;

import com.localstrategy.util.types.Order;
import com.localstrategy.util.types.UserAssets;

class CustomMapWrapper {
    private final Order order;

    private final UserAssets assets;
    private final String message;

    public CustomMapWrapper(Order order, UserAssets assets, String message) {
        this.order = order.clone();
        this.assets = new UserAssets(assets);
        this.message = message;
    }

    @Override
    public String toString() {
        return "Message: " + message + ", order: " + order.toString();
    }
}