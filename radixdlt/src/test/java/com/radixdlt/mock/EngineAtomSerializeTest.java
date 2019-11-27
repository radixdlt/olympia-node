package com.radixdlt.mock;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.common.Atom;
import org.radix.serialization.SerializeMessageObject;

public class EngineAtomSerializeTest extends SerializeMessageObject<Atom> {
	public EngineAtomSerializeTest() {
		super(Atom.class, () -> new Atom(1l,
			ImmutableMap.of("test","test")));
	}
}