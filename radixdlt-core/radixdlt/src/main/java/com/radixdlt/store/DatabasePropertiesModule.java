/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.store;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.radixdlt.properties.RuntimeProperties;

/**
 * Manages conversion of runtime properties to guice type properties
 */
public final class DatabasePropertiesModule extends AbstractModule {
    private final long maxMemory = Runtime.getRuntime().maxMemory();
    private final long minCacheSizeLimit = Math.max(50_000_000L, (long) (maxMemory * 0.1));
    private final long maxCacheSizeLimit = (long) (maxMemory * 0.25);
    private final long defaultCacheSize = (long) (maxMemory * 0.125);

    @Provides
    @DatabaseLocation
    String databaseLocation(RuntimeProperties properties) {
        return properties.get("db.location", ".//RADIXDB");
    }

    @Provides
    @DatabaseCacheSize
    long databaseCacheSize(RuntimeProperties properties) {
        var minCacheSize = properties.get("db.cache_size.min", minCacheSizeLimit);
        var maxCacheSize = properties.get("db.cache_size.max", maxCacheSizeLimit);
        var cacheSize = properties.get("db.cache_size", defaultCacheSize);

        return Math.min(Math.max(cacheSize, minCacheSize), maxCacheSize);
    }
}
