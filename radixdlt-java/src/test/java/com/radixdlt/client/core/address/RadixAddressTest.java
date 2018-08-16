package com.radixdlt.client.core.address;

import com.radixdlt.client.core.util.Int128;
import java.util.Arrays;
import java.util.List;

import org.bouncycastle.util.encoders.Base64;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Test;

import com.radixdlt.client.core.crypto.ECPublicKey;

import static org.junit.Assert.assertEquals;

public class RadixAddressTest {

	@Test
	public void createAddressFromPublicKey() {
		ECPublicKey publicKey = new ECPublicKey(Base64.decode("A455PdOZNwyRWaSWFXyYYkbj7Wv9jtgCCqUYhuOHiPLC"));
		RadixAddress address = new RadixAddress(RadixUniverseConfigs.getBetanet(), publicKey);
		assertEquals("JHB89drvftPj6zVCNjnaijURk8D8AMFw4mVja19aoBGmRXWchnJ", address.toString());
		assertEquals(address, RadixAddress.fromString("JHB89drvftPj6zVCNjnaijURk8D8AMFw4mVja19aoBGmRXWchnJ"));
	}

	@Test(expected = IllegalArgumentException.class)
	public void createAddressFromBadPublicKey() {
		ECPublicKey publicKey = new ECPublicKey(Base64.decode("BADKEY"));
		new RadixAddress(RadixUniverseConfigs.getBetanet(), publicKey);
	}

	@Test
	public void createAddressAndCheckUID() {
		RadixAddress address = new RadixAddress("JHB89drvftPj6zVCNjnaijURk8D8AMFw4mVja19aoBGmRXWchnJ");
		assertEquals(new EUID(Int128.from(Hex.decode("8cfef50ea6a767813631490f9a94f73f"))), address.getUID());
	}

	@Test
	public void generateAddress() {
		new RadixAddress(RadixUniverseConfigs.getBetanet(), new ECPublicKey(new byte[33]));
	}

	@Test
	public void testAddresses() {
		List<String> addresses = Arrays.asList(
			"JHB89drvftPj6zVCNjnaijURk8D8AMFw4mVja19aoBGmRXWchnJ"
		);

		addresses.forEach(address -> {
			RadixAddress.fromString(address);
		});
	}
}