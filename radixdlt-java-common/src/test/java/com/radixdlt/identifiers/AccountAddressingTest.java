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
import com.radixdlt.utils.Bytes;
import java.util.Map;
import org.junit.Test;

public class AccountAddressingTest {
  private final AccountAddressing accountAddresses = AccountAddressing.bech32("brx");
  private final BiMap<String, String> privateKeyToAccountAddress =
      HashBiMap.create(
          Map.of(
              "00", "brx1qsps28kdn4epn0c9ej2rcmwfz5a4jdhq2ez03x7h6jefvr4fnwnrtqqjqllv9",
              "deadbeef", "brx1qspsel805pa0nhtdhemshp7hm0wjcvd60a8ulre6zxtd2qh3x4smq3sraak9a",
              "deadbeefdeadbeef",
                  "brx1qsp7gnv7g60plkk9lgskjghdlevyve6rtrzggk7x3fwmp4yfyjza7gcumgm9f",
              "bead", "brx1qsppw0z477r695m9f9qjs3nj2vmdkd3rg4mfx7tf5v0gasrhz32jefqwxg7ul",
              "aaaaaaaaaaaaaaaa",
                  "brx1qspqsfad7e5k2k9agq74g40al0j9cllv7w0ylatvhy7m64wyrwymy5g7md96s"));

  private final BiMap<String, String> reAddrToAccountAddress =
      HashBiMap.create(
          Map.of(
              "04" + "02".repeat(33),
              "brx1qspqyqszqgpqyqszqgpqyqszqgpqyqszqgpqyqszqgpqyqszqgpqyqs7cr9az"));

  private final Map<String, String> invalidAddresses =
      Map.of(
          "vb1qvz3anvawgvm7pwvjs7xmjg48dvndczkgnufh475k2tqa2vm5c6cq9u3702", "invalid hrp",
          "brx1xhv8x3", "invalid address length 0",
          "brx1qsqsyqcyq5rqzjh9c6", "invalid length for address type 4");

  @Test
  public void test_validator_privkey_to_address_serialization() {
    privateKeyToAccountAddress.forEach(
        (privHex, expectedAddress) -> {
          var keyPair = ECKeyPair.fromSeed(Bytes.fromHexString(privHex));
          var publicKey = keyPair.getPublicKey();
          var addr = REAddr.ofPubKeyAccount(publicKey);
          var accountAddress = accountAddresses.of(addr);
          assertThat(accountAddress).isEqualTo(expectedAddress);
        });
  }

  @Test
  public void test_re_addr_to_address_serialization() {
    reAddrToAccountAddress.forEach(
        (hex, expectedAddress) -> {
          var addr = REAddr.of(Bytes.fromHexString(hex));
          var accountAddr = accountAddresses.of(addr);
          assertThat(accountAddr).isEqualTo(expectedAddress);
        });
  }

  @Test
  public void test_priv_key_address_deserialization() {
    for (var e : privateKeyToAccountAddress.entrySet()) {
      var address = e.getValue();
      var privHex = e.getKey();
      var reAddr = accountAddresses.parseOrThrow(address, IllegalStateException::new);
      var keyPair = ECKeyPair.fromSeed(Bytes.fromHexString(privHex));
      var pubKey = keyPair.getPublicKey();
      assertThat(reAddr).isEqualTo(REAddr.ofPubKeyAccount(pubKey));
    }
  }

  @Test
  public void test_re_addr_from_address_deserialization() {
    for (var e : reAddrToAccountAddress.entrySet()) {
      var address = e.getValue();
      var hex = e.getKey();
      var reAddr = REAddr.of(Bytes.fromHexString(hex));
      assertThat(reAddr)
          .isEqualTo(accountAddresses.parseOrThrow(address, IllegalStateException::new));
    }
  }

  @Test
  public void test_invalid_addresses() {
    for (var e : invalidAddresses.entrySet()) {
      var address = e.getKey();
      var expectedError = e.getValue();
      assertThatThrownBy(
              () -> accountAddresses.parseOrThrow(address, IllegalStateException::new),
              expectedError)
          .isInstanceOf(IllegalStateException.class);
    }
  }
}
