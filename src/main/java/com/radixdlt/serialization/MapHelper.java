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

package com.radixdlt.serialization;

import java.util.HashMap;
import java.util.Map;

/**
 * Helpers for creating maps used by the serializer for ephemeral data.
 */
public final class MapHelper {

	private MapHelper() {
		throw new IllegalStateException("Can't construct");
	}

	/**
	 * Create a new mutable map with contents {@code (k1, v1)}.
	 *
	 * @param k1 The key of the element to add to the new map
	 * @param v1 The value of the element to add to the new map
	 * @return A freshly created mutable map with the specified contents
	 */
	public static Map<String, Object> mapOf(String k1, Object v1) {
		Map<String, Object> newMap = new HashMap<>();
		newMap.put(k1, v1);
		return newMap;
	}

	/**
	 * Create a new mutable map with the specified contents.
	 *
	 * @param k1 The key of the first element to add to the new map
	 * @param v1 The value of the first element to add to the new map
	 * @param k2 The key of the second element to add to the new map
	 * @param v2 The value of the second element to add to the new map
	 * @return A freshly created mutable map with the specified contents
	 */
	public static Map<String, Object> mapOf(String k1, Object v1, String k2, Object v2) {
		Map<String, Object> newMap = mapOf(k1, v1);
		newMap.put(k2, v2);
		return newMap;
	}

	/**
	 * Create a new mutable map with the specified contents.
	 *
	 * @param k1 The key of the first element to add to the new map
	 * @param v1 The value of the first element to add to the new map
	 * @param k2 The key of the second element to add to the new map
	 * @param v2 The value of the second element to add to the new map
	 * @param k3 The key of the third element to add to the new map
	 * @param v3 The value of the third element to add to the new map
	 * @return A freshly created mutable map with the specified contents
	 */
	public static Map<String, Object> mapOf(String k1, Object v1, String k2, Object v2, String k3, Object v3) {
		Map<String, Object> newMap = mapOf(k1, v1, k2, v2);
		newMap.put(k3, v3);
		return newMap;
	}

	/**
	 * Create a new mutable map with the specified contents.
	 *
	 * @param k1 The key of the first element to add to the new map
	 * @param v1 The value of the first element to add to the new map
	 * @param k2 The key of the second element to add to the new map
	 * @param v2 The value of the second element to add to the new map
	 * @param k3 The key of the third element to add to the new map
	 * @param v3 The value of the third element to add to the new map
	 * @param k4 The key of the fourth element to add to the new map
	 * @param v4 The value of the fourth element to add to the new map
	 * @return A freshly created mutable map with the specified contents
	 */
	public static Map<String, Object> mapOf(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4) {
		Map<String, Object> newMap = mapOf(k1, v1, k2, v2, k3, v3);
		newMap.put(k4, v4);
		return newMap;
	}

	/**
	 * Create a new mutable map with the specified contents.
	 *
	 * @param k1 The key of the first element to add to the new map
	 * @param v1 The value of the first element to add to the new map
	 * @param k2 The key of the second element to add to the new map
	 * @param v2 The value of the second element to add to the new map
	 * @param k3 The key of the third element to add to the new map
	 * @param v3 The value of the third element to add to the new map
	 * @param k4 The key of the fourth element to add to the new map
	 * @param v4 The value of the fourth element to add to the new map
	 * @param k5 The key of the fifth element to add to the new map
	 * @param v5 The value of the fifth  element to add to the new map
	 * @return A freshly created mutable map with the specified contents
	 */
	public static Map<String, Object> mapOf(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4,
			String k5, Object v5) {
		Map<String, Object> newMap = mapOf(k1, v1, k2, v2, k3, v3, k4, v4);
		newMap.put(k5, v5);
		return newMap;
	}

	/**
	 * Create a new mutable map with the specified contents.
	 *
	 * @param k1 The key of the first element to add to the new map
	 * @param v1 The value of the first element to add to the new map
	 * @param k2 The key of the second element to add to the new map
	 * @param v2 The value of the second element to add to the new map
	 * @param k3 The key of the third element to add to the new map
	 * @param v3 The value of the third element to add to the new map
	 * @param k4 The key of the fourth element to add to the new map
	 * @param v4 The value of the fourth element to add to the new map
	 * @param k5 The key of the fifth element to add to the new map
	 * @param v5 The value of the fifth  element to add to the new map
	 * @param k6 The key of the sixth element to add to the new map
	 * @param v6 The value of the sixth  element to add to the new map
	 * @return A freshly created mutable map with the specified contents
	 */
	public static Map<String, Object> mapOf(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4,
			String k5, Object v5, String k6, Object v6) {
		Map<String, Object> newMap = mapOf(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5);
		newMap.put(k6, v6);
		return newMap;
	}

	/**
	 * Create a new mutable map with the specified contents.
	 *
	 * @param k1 The key of the first element to add to the new map
	 * @param v1 The value of the first element to add to the new map
	 * @param k2 The key of the second element to add to the new map
	 * @param v2 The value of the second element to add to the new map
	 * @param k3 The key of the third element to add to the new map
	 * @param v3 The value of the third element to add to the new map
	 * @param k4 The key of the fourth element to add to the new map
	 * @param v4 The value of the fourth element to add to the new map
	 * @param k5 The key of the fifth element to add to the new map
	 * @param v5 The value of the fifth  element to add to the new map
	 * @param k6 The key of the sixth element to add to the new map
	 * @param v6 The value of the sixth  element to add to the new map
	 * @param k7 The key of the seventh element to add to the new map
	 * @param v7 The value of the seventh  element to add to the new map
	 * @return A freshly created mutable map with the specified contents
	 */
	public static Map<String, Object> mapOf(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4,
			String k5, Object v5, String k6, Object v6, String k7, Object v7) {
		Map<String, Object> newMap = mapOf(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6);
		newMap.put(k7, v7);
		return newMap;
	}

	/**
	 * Create a new mutable map with the specified contents.
	 *
	 * @param k1 The key of the first element to add to the new map
	 * @param v1 The value of the first element to add to the new map
	 * @param k2 The key of the second element to add to the new map
	 * @param v2 The value of the second element to add to the new map
	 * @param k3 The key of the third element to add to the new map
	 * @param v3 The value of the third element to add to the new map
	 * @param k4 The key of the fourth element to add to the new map
	 * @param v4 The value of the fourth element to add to the new map
	 * @param k5 The key of the fifth element to add to the new map
	 * @param v5 The value of the fifth  element to add to the new map
	 * @param k6 The key of the sixth element to add to the new map
	 * @param v6 The value of the sixth  element to add to the new map
	 * @param k7 The key of the seventh element to add to the new map
	 * @param v7 The value of the seventh element to add to the new map
	 * @param k8 The key of the eighth element to add to the new map
	 * @param v8 The value of the eighth element to add to the new map
	 * @return A freshly created mutable map with the specified contents
	 */
	public static Map<String, Object> mapOf(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4,
			String k5, Object v5, String k6, Object v6, String k7, Object v7, String k8, Object v8) {
		Map<String, Object> newMap = mapOf(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6, k7, v7);
		newMap.put(k8, v8);
		return newMap;
	}

	/**
	 * Create a new mutable map with the specified contents.
	 *
	 * @param k1 The key of the first element to add to the new map
	 * @param v1 The value of the first element to add to the new map
	 * @param k2 The key of the second element to add to the new map
	 * @param v2 The value of the second element to add to the new map
	 * @param k3 The key of the third element to add to the new map
	 * @param v3 The value of the third element to add to the new map
	 * @param k4 The key of the fourth element to add to the new map
	 * @param v4 The value of the fourth element to add to the new map
	 * @param k5 The key of the fifth element to add to the new map
	 * @param v5 The value of the fifth  element to add to the new map
	 * @param k6 The key of the sixth element to add to the new map
	 * @param v6 The value of the sixth  element to add to the new map
	 * @param k7 The key of the seventh element to add to the new map
	 * @param v7 The value of the seventh element to add to the new map
	 * @param k8 The key of the eighth element to add to the new map
	 * @param v8 The value of the eighth element to add to the new map
	 * @param k9 The key of the ninth element to add to the new map
	 * @param v9 The value of the ninth element to add to the new map
	 * @return A freshly created mutable map with the specified contents
	 */
	public static Map<String, Object> mapOf(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4,
			String k5, Object v5, String k6, Object v6, String k7, Object v7, String k8, Object v8, String k9, Object v9) {
		Map<String, Object> newMap = mapOf(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6, k7, v7, k8, v8);
		newMap.put(k9, v9);
		return newMap;
	}

	/**
	 * Create a new mutable map with the specified contents.
	 *
	 * @param k1 The key of the first element to add to the new map
	 * @param v1 The value of the first element to add to the new map
	 * @param k2 The key of the second element to add to the new map
	 * @param v2 The value of the second element to add to the new map
	 * @param k3 The key of the third element to add to the new map
	 * @param v3 The value of the third element to add to the new map
	 * @param k4 The key of the fourth element to add to the new map
	 * @param v4 The value of the fourth element to add to the new map
	 * @param k5 The key of the fifth element to add to the new map
	 * @param v5 The value of the fifth  element to add to the new map
	 * @param k6 The key of the sixth element to add to the new map
	 * @param v6 The value of the sixth  element to add to the new map
	 * @param k7 The key of the seventh element to add to the new map
	 * @param v7 The value of the seventh element to add to the new map
	 * @param k8 The key of the eighth element to add to the new map
	 * @param v8 The value of the eighth element to add to the new map
	 * @param k9 The key of the ninth element to add to the new map
	 * @param v9 The value of the ninth element to add to the new map
	 * @param k10 The key of the tenth element to add to the new map
	 * @param v10 The value of the tenth element to add to the new map
	 * @return A freshly created mutable map with the specified contents
	 */
	public static Map<String, Object> mapOf(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4,
			String k5, Object v5, String k6, Object v6, String k7, Object v7, String k8, Object v8, String k9, Object v9,
			String k10, Object v10) {
		Map<String, Object> newMap = mapOf(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6, k7, v7, k8, v8, k9, v9);
		newMap.put(k10, v10);
		return newMap;
	}
}
