/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.client.core.address;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import java.util.Arrays;
import java.util.List;

import org.bouncycastle.util.encoders.Base64;
import org.bouncycastle.util.encoders.DecoderException;
import org.junit.Test;
import org.radix.common.ID.EUID;

import com.radixdlt.client.core.crypto.ECPublicKey;

import static org.junit.Assert.assertEquals;

public class RadixAddressTest {

	@Test
	public void createAddressFromPublicKey() {
		ECPublicKey publicKey = new ECPublicKey(Base64.decode("A455PdOZNwyRWaSWFXyYYkbj7Wv9jtgCCqUYhuOHiPLC"));
		RadixAddress address = new RadixAddress(RadixUniverseConfigs.getLocalnet(), publicKey);
		assertEquals("JHB89drvftPj6zVCNjnaijURk8D8AMFw4mVja19aoBGmRXWchnJ", address.toString());
		assertEquals(address, RadixAddress.from("JHB89drvftPj6zVCNjnaijURk8D8AMFw4mVja19aoBGmRXWchnJ"));
	}

	@Test(expected = DecoderException.class)
	public void createAddressFromBadPublicKey() {
		ECPublicKey publicKey = new ECPublicKey(Base64.decode("BADKEY"));
		new RadixAddress(RadixUniverseConfigs.getLocalnet(), publicKey);
	}

	@Test
	public void createAddressAndCheckUID() {
		RadixAddress address = new RadixAddress("JHB89drvftPj6zVCNjnaijURk8D8AMFw4mVja19aoBGmRXWchnJ");
		assertEquals(new EUID("8cfef50ea6a767813631490f9a94f73f"), address.getUID());
	}

	@Test
	public void generateAddress() {
		new RadixAddress(RadixUniverseConfigs.getLocalnet(), new ECPublicKey(new byte[33]));
	}

	@Test
	public void testAddresses() {
		List<String> addresses = Arrays.asList(
			"JHB89drvftPj6zVCNjnaijURk8D8AMFw4mVja19aoBGmRXWchnJ"
		);

		addresses.forEach(address -> {
			RadixAddress.from(address);
		});
	}
}