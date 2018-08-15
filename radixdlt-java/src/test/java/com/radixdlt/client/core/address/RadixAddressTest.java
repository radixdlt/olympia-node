package com.radixdlt.client.core.address;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import org.bouncycastle.util.encoders.Base64;
import org.junit.Test;

import com.radixdlt.client.core.crypto.ECPublicKey;

import static org.junit.Assert.assertEquals;

public class RadixAddressTest {

	@Test
	public void createAddressFromPublicKey() {
		ECPublicKey publicKey = new ECPublicKey(Base64.decode("A455PdOZNwyRWaSWFXyYYkbj7Wv9jtgCCqUYhuOHiPLC"));
		RadixAddress address = new RadixAddress(RadixUniverseConfigs.getWinterfell(), publicKey);
		assertEquals("JHB89drvftPj6zVCNjnaijURk8D8AMFw4mVja19aoBGmRXWchnJ", address.toString());
		assertEquals(address, RadixAddress.fromString("JHB89drvftPj6zVCNjnaijURk8D8AMFw4mVja19aoBGmRXWchnJ"));
	}

	@Test(expected = IllegalArgumentException.class)
	public void createAddressFromBadPublicKey() {
		ECPublicKey publicKey = new ECPublicKey(Base64.decode("BADKEY"));
		new RadixAddress(RadixUniverseConfigs.getWinterfell(), publicKey);
	}

	@Test
	public void createAddressAndCheckUID() {
		RadixAddress address = new RadixAddress("JHB89drvftPj6zVCNjnaijURk8D8AMFw4mVja19aoBGmRXWchnJ");
		assertEquals(new EUID(new BigInteger("-152866633757858334150738651292965210305")), address.getUID());
	}

	@Test
	public void generateAddress() {
		new RadixAddress(RadixUniverseConfigs.getWinterfell(), new ECPublicKey(new byte[33]));
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