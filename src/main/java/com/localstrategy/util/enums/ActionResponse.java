package com.localstrategy.util.enums;

/*
 * This is for immediate action requests from the side of the user, not user data streams where positions are checked for fills or rejections
 */

public enum ActionResponse {
    ORDER_CREATED,
    ORDER_CANCELLED,
    ORDER_REJECTED, //Rejection reason is written to the Order
    FUNDS_REPAID,

    ORDER_FILLED,

    ACTION_REJECTED //Rejection reason is not written to the Order
}
