package com.radixdlt.crypto;

import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.Longs;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

import test.radix.TestSetupUtils;

public class HashTest {

	@BeforeClass
	public static void setupBouncyCastle() {
		TestSetupUtils.installBouncyCastleProvider();
	}

	@Test
	public void testHashValues256() {
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
	public void testHashValues512() {
		assertArrayEquals(
			Bytes.fromHexString("d6f117761cef5715fcb3fe49a3cf2ebb268acec9e9d87a1e8812a8aa811a1d02ed636b9d04694c160fd071e687772d0cc2e1c3990da4435409c7b1f7b87fa632"),
			hash512(Longs.toByteArray(0L))
		);
		assertArrayEquals(
			Bytes.fromHexString("ec9d8eba8da254c20b3681454bdb3425ba144b7d36421ceffa796bad78d7e66c3439c73a6bbb2d985883b0ff081cfa3ebbac90c580065bad0eb1e9bee74eb0c9"),
			hash512(Longs.toByteArray(1L))
		);
		assertArrayEquals(
			Bytes.fromHexString("78a037d80204606de0f166a625d3fdc81de417da21bf0f5d7c9b756d73a4decd770c349f21fd5141329d0f2a639c143b30942cc044ff7d0d95209107c38e045c"),
			hash512(Longs.toByteArray(10L))
		);
		assertArrayEquals(
			Bytes.fromHexString("1cb75a3020fda027b89157eebde70134c281e719f700e84b9f12607b3ab3ae286c34c144e8b444eb0fd163948d00bcae900b2c08d263c7127fc1bf85f43c28a0"),
			hash512(Longs.toByteArray(100L))
		);
		assertArrayEquals(
			Bytes.fromHexString("66c0a8301d6a3cc9ad2151af74ad748d10ecfe83a5b1c765a5e31c72916f238f1de2006f2fcb12f634948e13200f354c6f47fc183c7208c1a022575e761c4222"),
			hash512(Longs.toByteArray(1_000L))
		);
	}

	private byte[] hash256(byte[] data) {
		return Hash.hash256(data);
	}

	private byte[] hash512(byte[] data) {
		return Hash.hash512(data);
	}
}
