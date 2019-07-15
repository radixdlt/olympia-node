package org.radix.serialization;

import com.radixdlt.common.EUID;
import org.radix.mass.NodeMass;
import com.radixdlt.utils.UInt384;

/**
 * Check serialization of NodeMass
 */
public class NodeMassSerializeTest extends SerializeObject<NodeMass> {
	public NodeMassSerializeTest() {
		super(NodeMass.class, NodeMassSerializeTest::get);
	}

	private static NodeMass get() {
		return new NodeMass(EUID.TWO, UInt384.from(123456789L), 1234);
	}
}
