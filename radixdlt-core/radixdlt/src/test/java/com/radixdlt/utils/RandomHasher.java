/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.utils;

import com.google.common.hash.HashCode;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.crypto.Hasher;
import org.bouncycastle.util.encoders.Hex;

import java.util.HashMap;
import java.util.Map;

/**
 * A hasher that returns a random hash for any object.
 * The same object will always get the same hash.
 */
public class RandomHasher implements Hasher {

    private final Map<Object, HashCode> cache = new HashMap<>();

    @Override
    public int bytes() {
        return 32;
    }

    @Override
    public HashCode hash(Object o) {
        cache.putIfAbsent(o, HashUtils.random256());
        return cache.get(o);
    }

    @Override
    public HashCode hashBytes(byte[] bytes) {
        var key = Hex.toHexString(bytes);
        cache.putIfAbsent(key, HashUtils.random256());
        return cache.get(key);
    }
}
