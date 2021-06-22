/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.utils.functional;

import com.google.common.collect.ImmutableMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

public interface FunctionalUtils {
	/**
	 * Use this method when it's necessary to pick last element from the stream.
	 * <p>
	 * For example:
	 * <pre>
	 *     var lastElement = ...
	 *     .stream()
	 *     .reduce(FunctionalUtils::findLast);
	 * </pre>
	 */
	static <T> T findLast(T first, T second) {
		return second;
	}

	/**
	 * Use this method to return part of iterable starting from element
	 * right after one which matched the predicated.
	 *
	 * @param input Source iterable
	 * @param predicate Predicate to test
	 *
	 * @return List consisting of the elements from input iterable which were found
	 * 	after the predicate match. Empty list if match not found.
	 */
	static <T> List<T> skipUntil(Iterable<T> input, Predicate<T> predicate) {
		var output = new ArrayList<T>();
		var found = false;

		for (var info : input) {
			if (predicate.test(info)) {
				found = true;
				continue;
			}
			if (found) {
				output.add(info);
			}
		}

		return output;
	}

	/**
	 * This method takes map and new entry and returns new map where existing entry with same key as new entry,
	 * is get replaced with new entry. If no entry with same key exists, then new entry is added to resulting map.
	 * Input map remains intact, returned map is a new map instance.
	 *
	 * @param newEntry the entry which will be put into new map
	 * @param existingMap input map
	 *
	 * @return new map with old entry replaced with new entry
	 */
	static <K, V> Map<K, V> replaceEntry(Map.Entry<K, V> newEntry, Map<K, V> existingMap) {
		return Stream.concat(
			Stream.of(newEntry),
			existingMap.entrySet().stream().filter(e -> !newEntry.getKey().equals(e.getKey()))
		).collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	/**
	 * This method takes a map and returns new map with entry with specified key removed.
	 *
	 * @param keyToRemove the key to remove
	 * @param existingMap input map
	 *
	 * @return new map with specified key removed
	 */
	static <K, V> Map<K, V> removeKey(K keyToRemove, Map<K, V> existingMap) {
		return existingMap.entrySet().stream()
			.filter(e -> !keyToRemove.equals(e.getKey()))
			.collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
	}
}
