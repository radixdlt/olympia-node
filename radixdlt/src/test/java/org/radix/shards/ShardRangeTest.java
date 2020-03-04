package org.radix.shards;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Test;

public class ShardRangeTest {
	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(ShardRange.class)
				.suppress(Warning.NONFINAL_FIELDS) // due to serialization we cannot make fields `low` and `high` final
				.withRedefinedSuperclass()
				.verify();
	}
}
