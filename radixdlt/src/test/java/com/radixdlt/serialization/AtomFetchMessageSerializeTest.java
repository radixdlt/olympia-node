package com.radixdlt.serialization;

import org.radix.atoms.messages.AtomFetchMessage;
import com.radixdlt.common.EUID;

/**
 * Check serialization of AtomFetchMessage
 */
public class AtomFetchMessageSerializeTest extends SerializeMessageObject<AtomFetchMessage> {
	public AtomFetchMessageSerializeTest() {
		super(AtomFetchMessage.class, AtomFetchMessageSerializeTest::get);
	}

	private static AtomFetchMessage get() {
		return new AtomFetchMessage(EUID.TWO);
	}
}
