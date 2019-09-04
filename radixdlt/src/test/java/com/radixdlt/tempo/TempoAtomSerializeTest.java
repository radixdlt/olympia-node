package com.radixdlt.tempo;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.AID;
import com.radixdlt.crypto.Hash;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.mock.MockAtomContent;
import org.radix.serialization.SerializeMessageObject;

public class TempoAtomSerializeTest extends SerializeMessageObject<TempoAtom> {
	public TempoAtomSerializeTest() {
		super(TempoAtom.class, () -> new TempoAtom(
			new MockAtomContent(
				new LedgerIndex((byte) 1, new byte[]{2, 3, 4, 5}),
				new byte[]{6, 7, 8, 9, 10}
			),
			AID.from(Hash.ZERO_HASH.toByteArray()),
			ImmutableSet.of(
				1L,
				2L,
				3L
			)
		));
	}
}