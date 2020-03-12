package org.radix.shards;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class ShardRangeTest {
	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(ShardRange.class)
			.verify();
	}
}
