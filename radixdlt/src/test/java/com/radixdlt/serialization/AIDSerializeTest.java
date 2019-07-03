package com.radixdlt.serialization;

import org.junit.Ignore;
import com.radixdlt.common.AID;

public class AIDSerializeTest extends SerializeMessageObject<AID> {
	public AIDSerializeTest() {
		super(AID.class, AIDSerializeTest::getAID);
	}

	private static AID getAID() {
		byte[] bytes = new byte[AID.BYTES];
		for (int i = 0; i < bytes.length; i++) {
			bytes[i] = (byte) i;
		}
		return AID.from(bytes);
	}

	@Override
	@Ignore("Not applicable to AIDs.")
	public void testNONEIsEmpty() {
	}
}
