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

package com.radixdlt.serialization;

import java.util.HashMap;
import java.util.Map;

/** Helpers for creating maps used by the serializer for ephemeral data. */
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
  public static Map<String, Object> mapOf(
      String k1, Object v1, String k2, Object v2, String k3, Object v3) {
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
  public static Map<String, Object> mapOf(
      String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4) {
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
   * @param v5 The value of the fifth element to add to the new map
   * @return A freshly created mutable map with the specified contents
   */
  public static Map<String, Object> mapOf(
      String k1,
      Object v1,
      String k2,
      Object v2,
      String k3,
      Object v3,
      String k4,
      Object v4,
      String k5,
      Object v5) {
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
   * @param v5 The value of the fifth element to add to the new map
   * @param k6 The key of the sixth element to add to the new map
   * @param v6 The value of the sixth element to add to the new map
   * @return A freshly created mutable map with the specified contents
   */
  public static Map<String, Object> mapOf(
      String k1,
      Object v1,
      String k2,
      Object v2,
      String k3,
      Object v3,
      String k4,
      Object v4,
      String k5,
      Object v5,
      String k6,
      Object v6) {
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
   * @param v5 The value of the fifth element to add to the new map
   * @param k6 The key of the sixth element to add to the new map
   * @param v6 The value of the sixth element to add to the new map
   * @param k7 The key of the seventh element to add to the new map
   * @param v7 The value of the seventh element to add to the new map
   * @return A freshly created mutable map with the specified contents
   */
  public static Map<String, Object> mapOf(
      String k1,
      Object v1,
      String k2,
      Object v2,
      String k3,
      Object v3,
      String k4,
      Object v4,
      String k5,
      Object v5,
      String k6,
      Object v6,
      String k7,
      Object v7) {
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
   * @param v5 The value of the fifth element to add to the new map
   * @param k6 The key of the sixth element to add to the new map
   * @param v6 The value of the sixth element to add to the new map
   * @param k7 The key of the seventh element to add to the new map
   * @param v7 The value of the seventh element to add to the new map
   * @param k8 The key of the eighth element to add to the new map
   * @param v8 The value of the eighth element to add to the new map
   * @return A freshly created mutable map with the specified contents
   */
  public static Map<String, Object> mapOf(
      String k1,
      Object v1,
      String k2,
      Object v2,
      String k3,
      Object v3,
      String k4,
      Object v4,
      String k5,
      Object v5,
      String k6,
      Object v6,
      String k7,
      Object v7,
      String k8,
      Object v8) {
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
   * @param v5 The value of the fifth element to add to the new map
   * @param k6 The key of the sixth element to add to the new map
   * @param v6 The value of the sixth element to add to the new map
   * @param k7 The key of the seventh element to add to the new map
   * @param v7 The value of the seventh element to add to the new map
   * @param k8 The key of the eighth element to add to the new map
   * @param v8 The value of the eighth element to add to the new map
   * @param k9 The key of the ninth element to add to the new map
   * @param v9 The value of the ninth element to add to the new map
   * @return A freshly created mutable map with the specified contents
   */
  public static Map<String, Object> mapOf(
      String k1,
      Object v1,
      String k2,
      Object v2,
      String k3,
      Object v3,
      String k4,
      Object v4,
      String k5,
      Object v5,
      String k6,
      Object v6,
      String k7,
      Object v7,
      String k8,
      Object v8,
      String k9,
      Object v9) {
    Map<String, Object> newMap =
        mapOf(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6, k7, v7, k8, v8);
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
   * @param v5 The value of the fifth element to add to the new map
   * @param k6 The key of the sixth element to add to the new map
   * @param v6 The value of the sixth element to add to the new map
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
  public static Map<String, Object> mapOf(
      String k1,
      Object v1,
      String k2,
      Object v2,
      String k3,
      Object v3,
      String k4,
      Object v4,
      String k5,
      Object v5,
      String k6,
      Object v6,
      String k7,
      Object v7,
      String k8,
      Object v8,
      String k9,
      Object v9,
      String k10,
      Object v10) {
    Map<String, Object> newMap =
        mapOf(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6, k7, v7, k8, v8, k9, v9);
    newMap.put(k10, v10);
    return newMap;
  }
}
