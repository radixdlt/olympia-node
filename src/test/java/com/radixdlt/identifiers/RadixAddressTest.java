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

package com.radixdlt.identifiers;

import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.utils.Bytes;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RadixAddressTest {

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(RadixAddress.class)
				.withIgnoredFields("key") // other field(s) dependent on `key` is used
				.withIgnoredFields("base58") // other field(s) dependent on `base58` is used
				.verify();
	}

	@Test
	public void when_an_address_is_created_with_same_string__they_should_be_equal_and_have_same_hashcode() {
		RadixAddress address0 = RadixAddress.from("JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor");
		RadixAddress address1 = RadixAddress.from(address0.toString());
		assertThat(address0).isEqualTo(address1);
		assertThat(address0).hasSameHashCodeAs(address1);
	}

	private static final class MagicByteProvider implements Magical {
		private int magic;
		MagicByteProvider(int magic) {
			this.magic = magic;
		}

		@Override
		public int getMagic() {
			return magic;
		}
	}

	@Test
	public void address_from_key_and_magical() throws CryptoException {
		String publicKeyHexString = "03000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F";
		ECPublicKey key = new ECPublicKey(Bytes.fromHexString(publicKeyHexString));
		int magicByte = 2;
		RadixAddress address = RadixAddress.from(new MagicByteProvider(magicByte), key);

		// https://github.com/radixdlt/radixdlt-swift/blob/develop/Tests/TestCases/UnitTests/RadixStack/1_Subatomic/SubatomicModels/Address/AddressTests.swift
		String expectedAddressHexString = "02" + // magic byte
				publicKeyHexString +
				"175341a9"; // checksum

		assertThat(expectedAddressHexString).isEqualToIgnoringCase(Bytes.toHexString(address.toByteArray()));
	}
}