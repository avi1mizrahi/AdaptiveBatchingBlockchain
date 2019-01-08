package Blockchain;

import java.util.LinkedHashMap;
import java.util.Map;

class BoundedMap<K, V> extends LinkedHashMap<K, V> {
    private final int maxElements;

    BoundedMap(int maxElements) {
        this.maxElements = maxElements;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return this.size() > this.maxElements;
    }
}
