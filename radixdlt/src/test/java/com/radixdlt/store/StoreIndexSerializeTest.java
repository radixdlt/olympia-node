package com.radixdlt.store;

import com.radixdlt.utils.Bytes;
import org.radix.serialization.SerializeMessageObject;

public class StoreIndexSerializeTest extends SerializeMessageObject<StoreIndex> {
	public StoreIndexSerializeTest() {
		super(StoreIndex.class, () -> new StoreIndex(Bytes.fromHexString("deadbeef")));
	}
}
