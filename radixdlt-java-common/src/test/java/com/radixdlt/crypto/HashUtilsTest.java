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

package com.radixdlt.crypto;

import com.google.common.base.Strings;
import com.google.common.hash.HashCode;
import com.radixdlt.TestSetupUtils;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.Longs;
import org.assertj.core.api.Assertions;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class HashUtilsTest {

	@BeforeClass
	public static void setupBouncyCastle() {
		TestSetupUtils.installBouncyCastleProvider();
	}

	@Test
	public void verify_that_hexstring_remains_the_same_as_passed_in_constructor() {
		String hex = deadbeefString();
		HashCode hash = HashCode.fromString(hex);
		assertEquals(hex, hash.toString());
	}

	@Test
	public void verify_that_tobytearray_returns_same_bytes_as_passed_in_constructor() {
		String hex = deadbeefString();
		HashCode hash = HashCode.fromString(hex);
		byte[] expectedBytes = Bytes.fromHexString(hex);
		assertArrayEquals(expectedBytes, hash.asBytes());
	}

	@Test
	public void testHashValues256() {
		assertArrayEquals(
			Bytes.fromHexString("7ef0ca626bbb058dd443bb78e33b888bdec8295c96e51f5545f96370870c10b9"),
				HashUtils.sha256(Longs.toByteArray(0L)).asBytes()
		);
		assertArrayEquals(
			Bytes.fromHexString("3ae5c198d17634e79059c2cd735491553d22c4e09d1d9fea3ecf214565df2284"),
				HashUtils.sha256(Longs.toByteArray(1L)).asBytes()
		);
		assertArrayEquals(
			Bytes.fromHexString("d03524a98786b780bd640979dc49316702799f46bb8029e29aaf3e7d3b8b7c3e"),
				HashUtils.sha256(Longs.toByteArray(10L)).asBytes()
		);
		assertArrayEquals(
			Bytes.fromHexString("43c1dd54b91ef646f74dc83f2238292bc3340e34cfdb2057aba0193a662409c5"),
				HashUtils.sha256(Longs.toByteArray(100L)).asBytes()
		);
		assertArrayEquals(
			Bytes.fromHexString("4bba9cc5d36a21b9d09cd16bbd83d0472f9b95afd2d7aa1621bb6fa580c99bfc"),
				HashUtils.sha256(Longs.toByteArray(1_000L)).asBytes()
		);
	}

	@Test
	public void testHashValues512() {
		assertArrayEquals(
			Bytes.fromHexString(
				"d6f117761cef5715fcb3fe49a3cf2ebb268acec9e9d87a1e8812a8aa811a1d02ed636b9d04694c160fd071e687772d0cc2e1c3990da4435409c7b1f7b87fa632"
			),
				HashUtils.sha512(Longs.toByteArray(0L)).asBytes()
		);
		assertArrayEquals(
			Bytes.fromHexString(
				"ec9d8eba8da254c20b3681454bdb3425ba144b7d36421ceffa796bad78d7e66c3439c73a6bbb2d985883b0ff081cfa3ebbac90c580065bad0eb1e9bee74eb0c9"
			),
			HashUtils.sha512(Longs.toByteArray(1L)).asBytes()
		);
		assertArrayEquals(
			Bytes.fromHexString(
				"78a037d80204606de0f166a625d3fdc81de417da21bf0f5d7c9b756d73a4decd770c349f21fd5141329d0f2a639c143b30942cc044ff7d0d95209107c38e045c"
			),
				HashUtils.sha512(Longs.toByteArray(10L)).asBytes()
		);
		assertArrayEquals(
			Bytes.fromHexString(
				"1cb75a3020fda027b89157eebde70134c281e719f700e84b9f12607b3ab3ae286c34c144e8b444eb0fd163948d00bcae900b2c08d263c7127fc1bf85f43c28a0"
			),
				HashUtils.sha512(Longs.toByteArray(100L)).asBytes()
		);
		assertArrayEquals(
			Bytes.fromHexString(
				"66c0a8301d6a3cc9ad2151af74ad748d10ecfe83a5b1c765a5e31c72916f238f1de2006f2fcb12f634948e13200f354c6f47fc183c7208c1a022575e761c4222"
			),
				HashUtils.sha512(Longs.toByteArray(1_000L)).asBytes()
		);
	}

	@Test
	public void test_sha256_hash_as_reference_for_other_libraries()  {
		byte[] data = "Hello Radix".getBytes(StandardCharsets.UTF_8);

		byte[] singleHash = sha2bits256Once(data).asBytes();
		byte[] doubleHash = HashUtils.sha256(data).asBytes();

		// These hashes as just the result of running the sha256 once and output the values
		// These are then used as reference for other libraries, especially Swift which
		// lacks native Sha256 methods.
		assertEquals("374d9dc94c1252acf828cdfb94946cf808cb112aa9760a2e6216c14b4891f934", Bytes.toHexString(singleHash));
		assertEquals("fd6be8b4b12276857ac1b63594bf38c01327bd6e8ae0eb4b0c6e253563cc8cc7", Bytes.toHexString(doubleHash));
	}

	@Test
	public void verify_hash256_once() {
		testEncodeToHashedBytesFromString(
				"abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq",
				this::sha2bits256Once,
				"248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1"
		);
	}

	@Test
	public void verify_hash256_twice() {
		testEncodeToHashedBytesFromString(
				"abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq",
				HashUtils::sha256,
				"0cffe17f68954dac3a84fb1458bd5ec99209449749b2b308b7cb55812f9563af"
		);
	}

	@Test
	public void test_hash_of_publickey() throws Exception {
		String publicKeyHex = "03" + deadbeefString();
		byte[] publicKeyBytes = Bytes.fromHexString(publicKeyHex);
		ECPublicKey publicKey = ECPublicKey.fromBytes(publicKeyBytes);
		String expectedEUIDHex = "cbed388efef3a09bee696ad1b30d49a0";
		assertEquals(expectedEUIDHex, publicKey.euid().toString());
		assertEquals(expectedEUIDHex, Bytes.toHexString(HashUtils.sha256(publicKeyBytes).asBytes()).substring(0, 32));
	}

	@Test
	public void birthday_attack_test() {
		// 32-bit hashes + 300000 hashes will have a collision 99.997% of the time.
		// Practically high enough to catch any issues we have with hash collisions
		final int numHashes = 300000;
		Set<HashCode> hashes = Stream.generate(() -> HashUtils.random256())
			.limit(numHashes)
			.collect(Collectors.toSet());

		Assertions.assertThat(hashes)
				.hasSize(numHashes)
				.doesNotContainNull();
	}

	@Test
	public void zero_should_indeed_be_zero() {
		byte[] output = HashUtils.zero256().asBytes();
		assertEquals(32, output.length);
		for (int i = 0; i < output.length; i++) {
			assertEquals((byte) 0, output[i]);
		}
	}

	@Test
	public void compare_hash_codes() {
		assertEquals(-1, HashUtils.compare(HashCode.fromLong(1), HashCode.fromLong(2)));
		assertEquals(0, HashUtils.compare(HashCode.fromLong(3), HashCode.fromLong(3)));
		assertEquals(1, HashUtils.compare(HashCode.fromLong(5), HashCode.fromLong(4)));
	}

	private void testEncodeToHashedBytesFromString(String message, Function<byte[], HashCode> hashFunction, String expectedHashHex) {
		byte[] unhashedData = message.getBytes(StandardCharsets.UTF_8);
		HashCode hashedData = hashFunction.apply(unhashedData);
		assertEquals(expectedHashHex, Bytes.toHexString(hashedData.asBytes()));
	}

	private HashCode deadbeef() {
		return HashCode.fromString(deadbeefString());
	}

	private String deadbeefString() {
		return Strings.repeat("deadbeef", 8);
	}

	private HashCode sha2bits256Once(byte[] data) {
		MessageDigest hasher = null;
		try {
			hasher = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("Should always be able to sha256 hash", e);
		}
		hasher.update(data);
		return HashCode.fromBytes(hasher.digest());
	}
}

