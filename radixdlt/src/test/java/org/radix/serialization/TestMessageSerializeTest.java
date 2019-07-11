package org.radix.serialization;

import org.radix.network.messages.TestMessage;

/**
 * Check serialization of TestMessage
 */
public class TestMessageSerializeTest extends SerializeMessageObject<TestMessage> {
	public TestMessageSerializeTest() {
		super(TestMessage.class, TestMessageSerializeTest::get);
	}

	private static TestMessage get() {
		return new TestMessage(123);
	}
}
