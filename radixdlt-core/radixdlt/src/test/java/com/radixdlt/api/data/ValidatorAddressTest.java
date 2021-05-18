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
import com.radixdlt.identifiers.ValidatorAddress;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.utils.Bytes;
import org.junit.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ValidatorAddressTest {
	private final BiMap<String, String> privateKeyToValidatorId = HashBiMap.create(
		Map.of(
			"00", "vb1qvz3anvawgvm7pwvjs7xmjg48dvndczkgnufh475k2tqa2vm5c6cq9u3702",
			"deadbeef", "vb1qvx0emaq0tua6md7wu9c047mm5krrwnlfl8c7ws3jm2s9uf4vxcyvrwrazy",
			"deadbeefdeadbeef", "vb1q0jym8jxnc0a4306y95j9m07tprxws6ccjz9h352tkcdfzfysh0jxll64dl",
			"bead", "vb1qgtnc40hs73dxe2fgy5yvujnxmdnvg69w6fhj6drr68vqac525k2gkfdady",
			"aaaaaaaaaaaaaaaa", "vb1qgyz0t0kd9j4302q8429tl0mu3w8lm8nne8l2m9e8k74t3qm3xe9z8l2049"
		)
	);

	private final Map<String, String> invalidAddresses = Map.of(
		"vb1qvx0emaq0tua6md7wu9c047mm5krrwnlfl8c7ws3jm2s9uf4vxcyvrwrazz", "Bad checksum",
		"xrd_rr1gd5j68", "Bad hrp",
		"vb1qqweu28r", "Not enough bytes for public key"
	);

	@Test
	public void test_validator_address_serialization() {
		privateKeyToValidatorId.forEach((privHex, expectedAddress) -> {
			var keyPair = ECKeyPair.fromSeed(Bytes.fromHexString(privHex));
			var publicKey = keyPair.getPublicKey();
			var validatorAddress = ValidatorAddress.of(publicKey);
			assertThat(validatorAddress).isEqualTo(expectedAddress);
		});
	}

	@Test
	public void test_correct_validator_address_deserialization() throws DeserializeException {
		for (var e : privateKeyToValidatorId.entrySet()) {
			var address = e.getValue();
			var privHex = e.getKey();
			var pubKey = ValidatorAddress.parse(address);
			var keyPair = ECKeyPair.fromSeed(Bytes.fromHexString(privHex));
			var expectedPubKey = keyPair.getPublicKey();
			assertThat(pubKey).isEqualTo(expectedPubKey);
		}
	}

	@Test
	public void test_invalid_addresses() {
		for (var e : invalidAddresses.entrySet()) {
			var address = e.getKey();
			var expectedError = e.getValue();
			assertThatThrownBy(() -> ValidatorAddress.parse(address), expectedError).isInstanceOf(DeserializeException.class);
		}
	}
}