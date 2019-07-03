package com.radixdlt.common;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.crypto.Hash;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


public class AIDTest {
	@Test
	public void testIllegalConstruction() {
		assertThatThrownBy(() -> AID.from((byte[]) null)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> AID.from((String) null)).isInstanceOf(NullPointerException.class);

		assertThatThrownBy(() -> AID.from(new byte[7])).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> AID.from("deadbeef")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	public void testCreateEquivalence() {
		byte[] bytes1 = new byte[AID.BYTES];
		for (int i = 0; i < AID.BYTES; i++) {
			bytes1[i] = (byte) i;
		}
		byte[] bytes2 = new byte[AID.BYTES];
		for (int i = 0; i < AID.BYTES; i++) {
			bytes2[i] = (byte) (AID.BYTES - i);
		}

		AID aid1 = AID.from(bytes1);
		assertArrayEquals(bytes1, aid1.getBytes());
		byte[] bytes1Copy = new byte[AID.BYTES];
		aid1.copyTo(bytes1Copy, 0);
		assertArrayEquals(bytes1Copy, bytes1);

		AID aid2 = AID.from(bytes2);
		assertArrayEquals(bytes2, aid2.getBytes());

		assertNotEquals(aid1, aid2);
	}

	@Test
	public void testFromAtom() {
		byte[] hashBytes = new byte[Hash.BYTES];
		for (int i = 0; i < Hash.BYTES; i++) {
			hashBytes[i] = (byte) (i + 3);
		}
		Hash hash = new Hash(hashBytes);
		Set<Long> shards = ImmutableSet.of(1L, 2L);

		AID aid = AID.from(hash, shards);
		// first byte of hash is 3 so 3 % 2 shards = 1 -> second shard should be selected
		assertEquals(2L, aid.getShard());
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(AID.class).verify();
	}

	@Test
	public void testArrayOffsetFactory() {
		byte[] bytes = new byte[AID.BYTES * 2];
		AID aid0 = AID.from(bytes, 0);
		assertEquals(AID.ZERO, aid0);
		AID aid1 = AID.from(bytes, AID.BYTES);
		assertEquals(AID.ZERO, aid1);

		assertThatThrownBy(() -> AID.from(bytes, -1)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> AID.from(bytes, AID.BYTES + 1)).isInstanceOf(IllegalArgumentException.class);
	}
}