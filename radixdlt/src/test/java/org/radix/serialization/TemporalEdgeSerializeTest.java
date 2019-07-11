package org.radix.serialization;

import com.radixdlt.common.EUID;
import org.radix.time.TemporalEdge;

/**
 * Check serialization of TemporalEdge
 */
public class TemporalEdgeSerializeTest extends SerializeObject<TemporalEdge> {
	public TemporalEdgeSerializeTest() {
		super(TemporalEdge.class, TemporalEdgeSerializeTest::get);
	}

	private static TemporalEdge get() {
		return new TemporalEdge(EUID.ONE, 1234L, EUID.TWO, 5678L);
	}
}
