package com.radixdlt.client.core.pow;

import static org.junit.Assert.*;

import com.radixdlt.client.core.util.Hash;
import java.nio.ByteBuffer;
import java.util.BitSet;
import javax.xml.bind.DatatypeConverter;
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