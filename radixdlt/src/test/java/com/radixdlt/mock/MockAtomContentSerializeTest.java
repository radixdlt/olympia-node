package com.radixdlt.mock;

import com.radixdlt.ledger.LedgerIndex;
import org.radix.serialization.SerializeMessageObject;

import java.util.function.Supplier;

import static org.junit.Assert.*;

public class MockAtomContentSerializeTest extends SerializeMessageObject<MockAtomContent> {
	public MockAtomContentSerializeTest() {
		super(MockAtomContent.class, () -> new MockAtomContent(
			new LedgerIndex((byte) 1, new byte[]{2, 3, 4, 5}),
			new byte[]{6, 7, 8, 9, 10}
		));
	}
}