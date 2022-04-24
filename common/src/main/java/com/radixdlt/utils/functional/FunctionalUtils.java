/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.utils.functional;

import com.google.common.collect.ImmutableMap;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface FunctionalUtils {
  /**
   * Use this method when it's necessary to pick last element from the stream.
   *
   * <p>For example:
   *
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
   * Use this method to return part of iterable starting from element right after one which matched
   * the predicated.
   *
   * @param input Source iterable
   * @param predicate Predicate to test
   * @return List consisting of the elements from input iterable which were found after the
   *     predicate match. Empty list if match not found.
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
   * This method takes map and new entry and returns new map where existing entry with same key as
   * new entry, is get replaced with new entry. If no entry with same key exists, then new entry is
   * added to resulting map. Input map remains intact, returned map is a new map instance.
   *
   * @param newEntry the entry which will be put into new map
   * @param existingMap input map
   * @return new map with old entry replaced with new entry
   */
  static <K, V> Map<K, V> replaceEntry(Map.Entry<K, V> newEntry, Map<K, V> existingMap) {
    return Stream.concat(
            Stream.of(newEntry),
            existingMap.entrySet().stream().filter(e -> !newEntry.getKey().equals(e.getKey())))
        .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  /**
   * This method takes a map and returns new map with entry with specified key removed.
   *
   * @param keyToRemove the key to remove
   * @param existingMap input map
   * @return new map with specified key removed
   */
  static <K, V> Map<K, V> removeKey(K keyToRemove, Map<K, V> existingMap) {
    return existingMap.entrySet().stream()
        .filter(e -> !keyToRemove.equals(e.getKey()))
        .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  /**
   * Return copy of the input set with specified element removed.
   *
   * @param element element to remove
   * @param input input set
   * @return new set with specified element removed
   */
  static <T> Set<T> removeElement(T element, Set<T> input) {
    return input.stream().filter(e -> !e.equals(element)).collect(Collectors.toSet());
  }

  /**
   * Return copy of the input set with provided element added.
   *
   * @param element element to add
   * @param input input set
   * @return new set with provided element added
   */
  static <T> Set<T> addElement(T element, Set<T> input) {
    return Stream.concat(input.stream(), Stream.of(element)).collect(Collectors.toSet());
  }

  /**
   * Merge several sets into one.
   *
   * @param inputs sets to merge
   * @return merged set
   */
  @SafeVarargs
  static <T> Set<T> mergeAll(Set<T>... inputs) {
    var output = new HashSet<T>();

    for (var input : inputs) {
      output.addAll(input);
    }

    return Set.copyOf(output);
  }

  /**
   * Create new immutable map entry.
   *
   * @param key entry key
   * @param value entry value
   * @return created entry
   */
  static <K, V> Map.Entry<K, V> newEntry(K key, V value) {
    return new SimpleImmutableEntry<>(key, value);
  }
}
