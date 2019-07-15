package org.radix.serialization;

import java.util.Arrays;

import com.radixdlt.common.EUID;
import org.radix.routing.RoutingTable;

/**
 * Check serialization of RoutingTable
 */
public class RoutingTableSerializeTest extends SerializeObject<RoutingTable> {
	public RoutingTableSerializeTest() {
		super(RoutingTable.class, RoutingTableSerializeTest::get);
	}

	private static RoutingTable get() {
		return new RoutingTable(EUID.ZERO, Arrays.asList(EUID.ONE, EUID.TWO), 12);
	}
}
