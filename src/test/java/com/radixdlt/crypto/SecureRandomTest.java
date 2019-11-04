package com.radixdlt.crypto;

import com.radixdlt.TestSetupUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.security.SecureRandom;

public class SecureRandomTest {

	@Before
	public void setUp() {
		TestSetupUtils.installBouncyCastleProvider();
	}

	@Test
	public void verifySecureRandomUsingBouncyCastleProviderByDefault() {
		Assert.assertTrue("BouncyCastleProvider should be used by default", new SecureRandom().getProvider() instanceof BouncyCastleProvider);
	}
}