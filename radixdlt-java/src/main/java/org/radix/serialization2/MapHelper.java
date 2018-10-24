package org.radix.serialization2;

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
	 * Create a new mutable empty map.
	 *
	 * @return Empty mutable map.
	 */
	public static Map<String, Object> mapOf() {
		return new HashMap<>();
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
}
