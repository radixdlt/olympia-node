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

import org.bouncycastle.util.encoders.Base64;
import org.bouncycastle.util.encoders.DecoderException;
import org.junit.Test;

import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.utils.Bytes;

import java.util.List;

import nl.jqno.equalsverifier.EqualsVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;

public class RadixAddressTest {

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(RadixAddress.class)
				.withIgnoredFields("publicKey") // other field(s) dependent on `key` is used
				.withIgnoredFields("base58") // other field(s) dependent on `base58` is used
				.verify();
	}

	@Test
	public void when_an_address_is_created_with_same_string__they_should_be_equal_and_have_same_hashcode() {
		var address0 = RadixAddress.from("JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor");
		var address1 = RadixAddress.from(address0.toString());
		assertThat(address0).isEqualTo(address1);
		assertThat(address0).hasSameHashCodeAs(address1);
	}

	@Test
	public void address_from_key_and_magical() throws PublicKeyException {
		var publicKeyHexString = "033fedae769522b862ad738874b6690ad78b166c6358cf20e316008465cb5f1562";
		var key = ECPublicKey.fromBytes(Bytes.fromHexString(publicKeyHexString));
		var address = new RadixAddress((byte) 2, key);

		// https://github.com/radixdlt/radixdlt-swift/
		// blob/develop/Tests/TestCases/UnitTests/
		// RadixStack/1_Subatomic/SubatomicModels/Address/AddressTests.swift
		String expectedAddressHexString = "02" // magic byte
				+ publicKeyHexString
				+ "d1478c49"; // checksum

		assertThat(expectedAddressHexString).isEqualToIgnoringCase(Bytes.toHexString(address.toByteArray()));
	}

	@Test
	public void createAddressFromPublicKey() throws PublicKeyException {
		var publicKey = ECPublicKey.fromBytes(Base64.decode("A455PdOZNwyRWaSWFXyYYkbj7Wv9jtgCCqUYhuOHiPLC"));
		var address = new RadixAddress(magicByte(), publicKey);
		assertEquals("JHB89drvftPj6zVCNjnaijURk8D8AMFw4mVja19aoBGmRXWchnJ", address.toString());
		assertEquals(address, RadixAddress.from("JHB89drvftPj6zVCNjnaijURk8D8AMFw4mVja19aoBGmRXWchnJ"));
	}

	@Test
	public void createBadPublicKey() {
		assertThatThrownBy(() -> ECPublicKey.fromBytes(Base64.decode("BADKEY")))
			.isInstanceOf(DecoderException.class);
	}

	@Test
	public void createAddressAndCheckUID() {
		var address = RadixAddress.from("JHB89drvftPj6zVCNjnaijURk8D8AMFw4mVja19aoBGmRXWchnJ");
		assertEquals(new EUID("8cfef50ea6a767813631490f9a94f73f"), address.euid());
	}

	@Test
	public void generateAddress() {
		assertThatThrownBy(() -> new RadixAddress(magicByte(), ECPublicKey.fromBytes(new byte[33])))
			.isInstanceOf(PublicKeyException.class);
	}

	@Test
	public void testAddresses() {
		var addresses = List.of("JHB89drvftPj6zVCNjnaijURk8D8AMFw4mVja19aoBGmRXWchnJ");

		addresses.forEach(RadixAddress::from);
	}

	private byte magicByte() {
		return (byte) (magic() & 0xff);
	}

	private int magic() {
		return -1332248574;
	}
}