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