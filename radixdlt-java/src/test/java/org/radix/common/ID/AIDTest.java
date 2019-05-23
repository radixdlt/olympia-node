package org.radix.common.ID;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.client.core.atoms.RadixHash;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.mockito.stubbing.Answer;
import org.radix.crypto.Hash;

import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AIDTest {
	@Test
	public void testIllegalConstruction() {
		Assertions.assertThatThrownBy(() -> AID.from((byte[]) null)).isInstanceOf(NullPointerException.class);
		Assertions.assertThatThrownBy(() -> AID.from((String) null)).isInstanceOf(NullPointerException.class);

		Assertions.assertThatThrownBy(() -> AID.from(new byte[7])).isInstanceOf(IllegalArgumentException.class);
		Assertions.assertThatThrownBy(() -> AID.from("deadbeef")).isInstanceOf(IllegalArgumentException.class);
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
		assertArrayEquals(bytes1, aid1.toByteArray());
		byte[] bytes1Copy = new byte[AID.BYTES];
		aid1.copyTo(bytes1Copy, 0);
		assertArrayEquals(bytes1Copy, bytes1);

		AID aid2 = AID.from(bytes2);
		assertArrayEquals(bytes2, aid2.toByteArray());

		assertNotEquals(aid1, aid2);
	}

	@Test
	public void testFromAtom() {
		byte[] hashBytes = new byte[Hash.BYTES];
		for (int i = 0; i < Hash.BYTES; i++) {
			hashBytes[i] = (byte) (i + 3);
		}
		RadixHash hash = mock(RadixHash.class);
		when(hash.getFirstByte()).thenReturn(hashBytes[0]);
		doAnswer((Answer<Object>) invocation -> {
			byte[] destination = (byte[]) invocation.getArguments()[0];
			System.arraycopy(hashBytes, 0, destination, 0, AID.HASH_BYTES);
			return null;
		}).when(hash).copyTo(new byte[0], 0, 0);

		Set<Long> shards = ImmutableSet.of(1L, 2L);

		AID aid = AID.from(hash, shards);
		// first byte of hash is 3 so 3 % 2 shards = 1 -> second shard should be selected
		assertEquals(2L, aid.getShard());
	}
}