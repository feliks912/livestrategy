package com.localstrategy;

import java.util.HashMap;

class CustomMap<K, V> extends HashMap<K, V> {

    public CustomMap(K key, V value) {
        put(key, value);
    }

    @Override
    public String toString() {
        return "Message: " + entrySet().iterator().next().getValue() + ", order: " + entrySet().iterator().next().getKey();
    }
}