package com.localstrategy.util.enums;

public enum RejectionReason {
    WOULD_TRIGGER_IMMEDIATELY,
    MAX_NUM_ALGO_ORDERS,
    INSUFFICIENT_MARGIN,
    EXCESS_BORROW,
    INSUFFICIENT_FUNDS,
    INVALID_ORDER_STATE //This one is to be interpreted locally?
}
