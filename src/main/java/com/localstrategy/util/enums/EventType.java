package com.localstrategy.util.enums;

public enum EventType {
    TRANSACTION, //Transaction latencies
    ACTION_REQUEST, //All requests relate to executions and get their latencies
    ACTION_RESPONSE, //Response latencies
    USER_DATA_STREAM, //Response latencies
}
