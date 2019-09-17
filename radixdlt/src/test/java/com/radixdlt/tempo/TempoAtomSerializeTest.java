package com.radixdlt.tempo;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.AID;
import com.radixdlt.crypto.Hash;
import org.radix.serialization.SerializeMessageObject;

public class TempoAtomSerializeTest extends SerializeMessageObject<TempoAtom> {
	public TempoAtomSerializeTest() {
		super(TempoAtom.class, () -> new TempoAtom(
			new TempoAtomContent(1l, ImmutableMap.of("test", "test")),
			AID.from(Hash.ZERO_HASH.toByteArray()),
			ImmutableSet.of(
				1L,
				2L,
				3L
			)
		));
	}
}