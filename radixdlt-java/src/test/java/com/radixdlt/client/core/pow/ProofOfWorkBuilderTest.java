package com.radixdlt.client.core.pow;

import org.junit.Test;

public class ProofOfWorkBuilderTest {
	@Test
	public void test() throws ProofOfWorkException {
		int magic = 12345;
		byte[] seed = new byte[32];
		ProofOfWork pow = new ProofOfWorkBuilder().build(magic, seed, 16);

		pow.validate();
	}
}