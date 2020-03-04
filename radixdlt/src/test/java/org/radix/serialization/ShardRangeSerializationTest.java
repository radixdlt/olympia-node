package org.radix.serialization;

import com.radixdlt.store.LedgerEntry;
import org.radix.shards.ShardRange;

public class ShardRangeSerializationTest extends SerializeMessageObject<ShardRange> {
	public ShardRangeSerializationTest() {
		super(ShardRange.class, () -> new ShardRange(1, 3));
	}
}
