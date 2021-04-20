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

package com.radixdlt.client;

import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.utils.Bytes;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ValidatorAddressTest {
	@Test
	public void test_validator_address_serialization() {
		var keyPair = ECKeyPair.fromSeed(Bytes.fromHexString("deadbeef"));
		var publicKey = keyPair.getPublicKey();
		var validatorAddress = ValidatorAddress.of(publicKey);
		var expectedValidatorAddressString = "vb1qvx0emaq0tua6md7wu9c047mm5krrwnlfl8c7ws3jm2s9uf4vxcyvrwrazy";
		assertThat(validatorAddress).isEqualTo(expectedValidatorAddressString);
	}

	@Test
	public void test_correct_validator_address_deserialization() throws DeserializeException {
		var keyPair = ECKeyPair.fromSeed(Bytes.fromHexString("deadbeef"));
		var publicKey = keyPair.getPublicKey();
		var addr = "vb1qvx0emaq0tua6md7wu9c047mm5krrwnlfl8c7ws3jm2s9uf4vxcyvrwrazy";

		var publicKeyFromValidatorAddress = ValidatorAddress.parse(addr);
		assertThat(publicKey).isEqualTo(publicKeyFromValidatorAddress);
	}

	@Test
	public void test_invalid_checksum_of_validator_address_deserialization() {
		var addr = "vb1qvx0emaq0tua6md7wu9c047mm5krrwnlfl8c7ws3jm2s9uf4vxcyvrwrazz";
		assertThatThrownBy(() -> ValidatorAddress.parse(addr)).isInstanceOf(DeserializeException.class);
	}

	@Test
	public void test_invalid_hrp_of_validator_address_deserialization() {
		var addr = "xrd_rr1gd5j68";
		assertThatThrownBy(() -> ValidatorAddress.parse(addr)).isInstanceOf(DeserializeException.class);
	}
}