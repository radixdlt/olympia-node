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

package com.radixdlt.identifiers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.Pair;
import java.util.Map;
import org.junit.Test;

public class ResourceAddressingTest {
  private final ResourceAddressing resourceAddressing = ResourceAddressing.bech32("_rb");
  private final BiMap<Pair<String, String>, String> reAddressToRri =
      HashBiMap.create(
          Map.of(
              Pair.of("xrd", "01"), "xrd_rb1qya85pwq",
              Pair.of("usdc", "03" + "00".repeat(26)),
                  "usdc_rb1qvqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq6gwwwd"));

  private final Map<String, String> invalidRris =
      Map.of(
          "xrd1pzdsczc", "no _rb suffix",
          "xrd_rb1avu205I", "invalid address type (0)",
          "usdc_rb1qg8vs72e", "invalid address type (2)",
          "usdc_rb1qqqsqs6ztc", "invalid length for address type 1",
          "usdc_rb1qvgxjc9r", "invalid length for address type 3");

  private final BiMap<Pair<Integer, String>, String> privateKeyAndNameToRri =
      HashBiMap.create(
          Map.of(
              Pair.of(1, "foo"), "foo_rb1qv9ee5j4qun9frqj2mcg79maqq55n46u5ypn2j0g9c3q32j6y3",
              Pair.of(1, "bar"), "bar_rb1qwaa87cznx0nmeq08dya2ae43u92g4g0nkfktd9u9lpq6hgjca",
              Pair.of(2, "foo"), "foo_rb1qvmf6ak360gxjfhxeh0x5tn99gjzzh5d7u3kvktj26rsu5qa3u",
              Pair.of(2, "bar"), "bar_rb1qd3t7gnvwxddj2wxg5dl4adr7er9uw62g7x0ku6hyw4qfk0pfz"));

  private final Map<String, String> systemRris =
      Map.of(
          "xrd", "xrd_rb1qya85pwq",
          "eth", "eth_rb1qynl40gy",
          "btc", "btc_rb1qytls7qn");

  @Test
  public void test_rri_serialization() {
    reAddressToRri.forEach(
        (pair, expected) -> {
          var reAddr = REAddr.of(Bytes.fromHexString(pair.getSecond()));
          var rri = resourceAddressing.of(pair.getFirst(), reAddr);
          assertThat(expected).isEqualTo(rri);
        });
  }

  @Test
  public void test_rri_deserialization() throws Exception {
    for (var e : reAddressToRri.entrySet()) {
      var expected = e.getKey();
      var rri = e.getValue();
      var pair = resourceAddressing.parseOrThrow(rri, IllegalStateException::new);
      var expectedAddr = REAddr.of(Bytes.fromHexString(expected.getSecond()));

      pair.map(
          (symbol, address) -> {
            assertThat(expected.getFirst()).isEqualTo(symbol);
            assertThat(expectedAddr).isEqualTo(address);
            return null;
          });
    }
  }

  @Test
  public void test_invalid_rris() {
    for (var e : invalidRris.entrySet()) {
      assertThatThrownBy(
              () -> resourceAddressing.parseOrThrow(e.getKey(), IllegalStateException::new),
              e.getValue())
          .isInstanceOf(IllegalStateException.class);
    }
  }

  private ECPublicKey publicKeyOfPrivateKey(int privateKeyScalar) {
    assertThat(privateKeyScalar).isLessThanOrEqualTo(9);
    try {
      return ECKeyPair.fromPrivateKey(Bytes.fromHexString("0".repeat(63) + privateKeyScalar))
          .getPublicKey();
    } catch (Exception e) {
      throw new IllegalStateException("bad key");
    }
  }

  private String rriFromPKAndName(int privateKey, String name) {
    var reAddr = REAddr.ofHashedKey(publicKeyOfPrivateKey(privateKey), name);
    return resourceAddressing.of(name, reAddr);
  }

  @Test
  public void test_system_rris() {
    var systemTokenREAddr = REAddr.ofNativeToken();
    for (var e : systemRris.entrySet()) {
      var rri = resourceAddressing.of(e.getKey(), systemTokenREAddr);
      assertThat(rri).isEqualTo(e.getValue());
    }
  }

  @Test
  public void test_rri_from_pubkey_serialization() {
    privateKeyAndNameToRri.forEach(
        (pair, expected) -> {
          var rri = rriFromPKAndName(pair.getFirst(), pair.getSecond());
          assertThat(rri).isEqualTo(expected);
        });
  }
}
