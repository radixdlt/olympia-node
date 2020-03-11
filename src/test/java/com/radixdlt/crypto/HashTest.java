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

import static org.hamcrest.number.OrderingComparison.lessThan;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import com.google.common.base.Strings;
import com.radixdlt.TestSetupUtils;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.Longs;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.BeforeClass;
import org.junit.Test;

public class HashTest {

	@BeforeClass
	public static void setupBouncyCastle() {
		TestSetupUtils.installBouncyCastleProvider();
	}

	@Test
	public void from_engine___equalsContract() {
		EqualsVerifier.forClass(Hash.class)
				.withIgnoredFields("data") // other field(s) dependent on `data` is used
				.withIgnoredFields("idCached") // `idCached` is derived from other field(s) in use.
				.verify();
	}

	@Test
	public void from_engine___verify_that_random_is_not_null() {
		assertNotNull(Hash.random());
	}

	@Test(expected = IllegalArgumentException.class)
    public void from_engine___from_engine___verify_that_an_error_is_thrown_for_too_short_hex_string_constructor() {
		new Hash("deadbeef");
		fail();
	}

	@Test
	public void from_engine___verify_that_id_equals_for_same_hash() {
		assertEquals(deadbeef().getID(), deadbeef().getID());
	}

	@Test
	public void from_engine___verify_that_hexstring_remains_the_same_as_passed_in_constructor() {
		String hex = deadbeefString();
		Hash hash = new Hash(hex);
		assertEquals(hex, hash.toString());
	}

	@Test
	public void from_engine___verify_that_tobytearray_returns_same_bytes_as_passed_in_constructor() {
		String hex = deadbeefString();
		Hash hash = new Hash(hex);
		byte[] expectedBytes = Bytes.fromHexString(hex);
		assertArrayEquals(expectedBytes, hash.toByteArray());
	}

	@Test
	public void from_engine___testFirstByte() {
		assertEquals(Bytes.fromHexString("de")[0], deadbeef().getFirstByte());
	}

	@Test
	public void from_engine___testCompare() {
		Hash low = new Hash(Strings.repeat("1", 64));
		Hash high = new Hash(Strings.repeat("9", 64));
		assertThat(low, lessThan(high));
	}

	@Test
	public void from_engine___testHashValues256() {
		assertArrayEquals(
			Bytes.fromHexString("7ef0ca626bbb058dd443bb78e33b888bdec8295c96e51f5545f96370870c10b9"),
			hash256(Longs.toByteArray(0L))
		);
		assertArrayEquals(
			Bytes.fromHexString("3ae5c198d17634e79059c2cd735491553d22c4e09d1d9fea3ecf214565df2284"),
			hash256(Longs.toByteArray(1L))
		);
		assertArrayEquals(
			Bytes.fromHexString("d03524a98786b780bd640979dc49316702799f46bb8029e29aaf3e7d3b8b7c3e"),
			hash256(Longs.toByteArray(10L))
		);
		assertArrayEquals(
			Bytes.fromHexString("43c1dd54b91ef646f74dc83f2238292bc3340e34cfdb2057aba0193a662409c5"),
			hash256(Longs.toByteArray(100L))
		);
		assertArrayEquals(
			Bytes.fromHexString("4bba9cc5d36a21b9d09cd16bbd83d0472f9b95afd2d7aa1621bb6fa580c99bfc"),
			hash256(Longs.toByteArray(1_000L))
		);
	}

	@Test
	public void from_engine___testHashValues512() {
		assertArrayEquals(
			Bytes.fromHexString(
				"d6f117761cef5715fcb3fe49a3cf2ebb268acec9e9d87a1e8812a8aa811a1d02ed636b9d04694c160fd071e687772d0cc2e1c3990da4435409c7b1f7b87fa632"
			),
			hash512(Longs.toByteArray(0L))
		);
		assertArrayEquals(
			Bytes.fromHexString(
				"ec9d8eba8da254c20b3681454bdb3425ba144b7d36421ceffa796bad78d7e66c3439c73a6bbb2d985883b0ff081cfa3ebbac90c580065bad0eb1e9bee74eb0c9"
			),
			hash512(Longs.toByteArray(1L))
		);
		assertArrayEquals(
			Bytes.fromHexString(
				"78a037d80204606de0f166a625d3fdc81de417da21bf0f5d7c9b756d73a4decd770c349f21fd5141329d0f2a639c143b30942cc044ff7d0d95209107c38e045c"
			),
			hash512(Longs.toByteArray(10L))
		);
		assertArrayEquals(
			Bytes.fromHexString(
				"1cb75a3020fda027b89157eebde70134c281e719f700e84b9f12607b3ab3ae286c34c144e8b444eb0fd163948d00bcae900b2c08d263c7127fc1bf85f43c28a0"
			),
			hash512(Longs.toByteArray(100L))
		);
		assertArrayEquals(
			Bytes.fromHexString(
				"66c0a8301d6a3cc9ad2151af74ad748d10ecfe83a5b1c765a5e31c72916f238f1de2006f2fcb12f634948e13200f354c6f47fc183c7208c1a022575e761c4222"
			),
			hash512(Longs.toByteArray(1_000L))
		);
	}

	private byte[] hash256(byte[] data) {
		return Hash.hash256(data);
	}

	private byte[] hash512(byte[] data) {
		return Hash.hash512(data);
	}

	private Hash deadbeef() {
		return new Hash(deadbeefString());
	}

	private String deadbeefString() {
		return Strings.repeat("deadbeef", 8);
	}
}

