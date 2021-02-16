package com.radixdlt.store;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.radixdlt.properties.RuntimeProperties;

public class DatabasePropertiesModule extends AbstractModule {
    @Provides
    @DatabaseLocation
    String databaseLocation(RuntimeProperties properties) {
        return properties.get("db.location", ".//RADIXDB");
    }

    @Provides
    @DatabaseCacheSize
    long databaseCacheSize(RuntimeProperties properties) {
        long minCacheSize = properties.get("db.cache_size.min", Math.max(50000000, (long) (Runtime.getRuntime().maxMemory() * 0.1)));
        long maxCacheSize = properties.get("db.cache_size.max", (long) (Runtime.getRuntime().maxMemory() * 0.25));
        long cacheSize = properties.get("db.cache_size", (long) (Runtime.getRuntime().maxMemory() * 0.125));
        cacheSize = Math.max(cacheSize, minCacheSize);
        cacheSize = Math.min(cacheSize, maxCacheSize);
        return cacheSize;
    }
}
