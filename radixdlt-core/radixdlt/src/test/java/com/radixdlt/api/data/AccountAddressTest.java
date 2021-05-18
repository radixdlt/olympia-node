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
 *
 */

package com.radixdlt.api.data;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.identifiers.AccountAddress;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.utils.Bytes;
import org.junit.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AccountAddressTest {
	private final BiMap<String, String> privateKeyToAccountAddress = HashBiMap.create(
		Map.of(
			"00", "brx1qsps28kdn4epn0c9ej2rcmwfz5a4jdhq2ez03x7h6jefvr4fnwnrtqqjqllv9",
			"deadbeef", "brx1qspsel805pa0nhtdhemshp7hm0wjcvd60a8ulre6zxtd2qh3x4smq3sraak9a",
			"deadbeefdeadbeef", "brx1qsp7gnv7g60plkk9lgskjghdlevyve6rtrzggk7x3fwmp4yfyjza7gcumgm9f",
			"bead", "brx1qsppw0z477r695m9f9qjs3nj2vmdkd3rg4mfx7tf5v0gasrhz32jefqwxg7ul",
			"aaaaaaaaaaaaaaaa", "brx1qspqsfad7e5k2k9agq74g40al0j9cllv7w0ylatvhy7m64wyrwymy5g7md96s"
		)
	);

	private final BiMap<String, String> reAddrToAccountAddress = HashBiMap.create(
		Map.of(
			"03" + "03".repeat(26), "brx1qvpsxqcrqvpsxqcrqvpsxqcrqvpsxqcrqvpsxqcrqvpsytf8zx",
			"04" + "02".repeat(33), "brx1qspqyqszqgpqyqszqgpqyqszqgpqyqszqgpqyqszqgpqyqszqgpqyqs7cr9az"
		)
	);

	private final Map<String, String> invalidAddresses = Map.of(
		"vb1qvz3anvawgvm7pwvjs7xmjg48dvndczkgnufh475k2tqa2vm5c6cq9u3702", "invalid hrp",
		"brx1xhv8x3", "invalid address length 0",
		"brx1qsqsyqcyq5rqzjh9c6", "invalid length for address type 4"
	);

	@Test
	public void test_validator_privkey_to_address_serialization() {
		privateKeyToAccountAddress.forEach((privHex, expectedAddress) -> {
			var keyPair = ECKeyPair.fromSeed(Bytes.fromHexString(privHex));
			var publicKey = keyPair.getPublicKey();
			var addr = REAddr.ofPubKeyAccount(publicKey);
			var accountAddress = AccountAddress.of(addr);
			assertThat(accountAddress).isEqualTo(expectedAddress);
		});
	}

	@Test
	public void test_re_addr_to_address_serialization() {
		reAddrToAccountAddress.forEach((hex, expectedAddress) -> {
			var addr = REAddr.of(Bytes.fromHexString(hex));
			var accountAddr = AccountAddress.of(addr);
			assertThat(accountAddr).isEqualTo(expectedAddress);
		});
	}

	@Test
	public void test_priv_key_address_deserialization() throws DeserializeException {
		for (var e : privateKeyToAccountAddress.entrySet()) {
			var address = e.getValue();
			var privHex = e.getKey();
			var reAddr = AccountAddress.parse(address);
			var keyPair = ECKeyPair.fromSeed(Bytes.fromHexString(privHex));
			var pubKey = keyPair.getPublicKey();
			assertThat(reAddr).isEqualTo(REAddr.ofPubKeyAccount(pubKey));
		}
	}

	@Test
	public void test_re_addr_from_address_deserialization() throws DeserializeException {
		for (var e : reAddrToAccountAddress.entrySet()) {
			var address = e.getValue();
			var hex = e.getKey();
			var reAddr = REAddr.of(Bytes.fromHexString(hex));
			assertThat(reAddr).isEqualTo(AccountAddress.parse(address));
		}
	}

	@Test
	public void test_invalid_addresses() {
		for (var e : invalidAddresses.entrySet()) {
			var address = e.getKey();
			var expectedError = e.getValue();
			assertThatThrownBy(() -> AccountAddress.parse(address), expectedError).isInstanceOf(DeserializeException.class);
		}
	}
}