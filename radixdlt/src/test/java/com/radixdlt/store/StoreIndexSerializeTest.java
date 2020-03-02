package com.radixdlt.store;

import com.radixdlt.common.AID;
import com.radixdlt.crypto.Hash;
import com.radixdlt.utils.Bytes;
import org.radix.serialization.SerializeMessageObject;

public class StoreIndexSerializeTest extends SerializeMessageObject<StoreIndex> {
	public StoreIndexSerializeTest() {
		super(StoreIndex.class, () -> new StoreIndex(Bytes.fromHexString("deadbeef")));
	}
}