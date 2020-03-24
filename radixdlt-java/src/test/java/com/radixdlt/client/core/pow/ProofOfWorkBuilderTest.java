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

package com.radixdlt.client.core.pow;

import com.google.common.collect.ImmutableList;
import com.radixdlt.utils.Bytes;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ProofOfWorkBuilderTest {
	@Test
	public void test() throws ProofOfWorkException {
		int magic = 12345;
		byte[] seed = new byte[32];
		ProofOfWork pow = new ProofOfWorkBuilder().build(magic, seed, 16);

		pow.validate();
	}

	@Test
	public void test_vectors() {
		testVectors.forEach(v -> test_vector(v));
	}

	private void test_vector(TestVector vector) {
		ProofOfWork pow = new ProofOfWorkBuilder().build(vector.getMagic(), vector.getSeed(), vector.getNumberOfLeadingZeros());
		int nonce = (int) pow.getNonce();
		assertEquals(vector.getNonce(), nonce);
		try {
			pow.validate();
		} catch (ProofOfWorkException e) {
			fail("Got unexpected error: " + e);
		}
	}

	// Some test vectors with high nonce values.
	private static final class TestVector {
		private int nonce;
		private byte[] seed;
		private int magic;
		private int numberOfLeadingZeros;

		TestVector(
				int nonce,
				byte[] seed,
				int magic,
				int numberOfLeadingZeros
		) {
			this.nonce = nonce;
			this.seed = seed;
			this.magic = magic;
			this.numberOfLeadingZeros = numberOfLeadingZeros;
		}

		static TestVector hex(
				int nonce,
				String seedHex
		) {
			return new TestVector(nonce, Bytes.fromHexString(seedHex), -1332248574, 16);
		}

		public int getNonce() {
			return nonce;
		}

		public byte[] getSeed() {
			return seed;
		}

		public int getMagic() {
			return magic;
		}

		public int getNumberOfLeadingZeros() {
			return numberOfLeadingZeros;
		}
	}

	private static List<TestVector> testVectors = ImmutableList.of(
			TestVector.hex(
					510190,
					"887a9e87ecbcc8f13ea60dd732a3c115ea9478519ee3faac3be3ed89b4bbc535"
			),

			TestVector.hex(
					322571,
					"46ad4f54098f18f856a2ff05df25f5af587bd4f6dfc1e3b4cb406ceb25c61552"
			),


			TestVector.hex(
					312514,
					"f0f178d42ffe8fade8b8197782fd1ee72a4068d046d868806da7bfb1d0ffa7c1"
			)
	);
}