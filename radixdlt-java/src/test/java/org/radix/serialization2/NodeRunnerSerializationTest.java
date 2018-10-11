package org.radix.serialization2;

import org.junit.Test;
import org.radix.serialization2.client.Serialize;

import com.radixdlt.client.core.network.RadixSystem;

import static org.junit.Assert.assertNotNull;

public class NodeRunnerSerializationTest {

	@Test
	public void testDeserializeNodeRunnerData() {
		String data = "{\"shards\":{\"high\":9223372036854775807,\"low\":-9223372036854775808},\"period\":0,\"agent\":{\"protocol\":100,\"name\":\":str:/Radix:/2400000\",\"version\":2400000},\"port\":30000,\"serializer\":-1833998801,\"commitment\":\":hsh:0000000000000000000000000000000000000000000000000000000000000000\",\"clock\":0,\"version\":100,\"key\":\":byt:AzSRhlkCmLZDS2XmmZr0bCEKfn0ujYbVySbHN78eZeQz\"}";
//		String data = "{\"period\":0,\"port\":30000,\"serializer\":-1833998801,\"commitment\":\":hsh:0000000000000000000000000000000000000000000000000000000000000000\",\"clock\":0,\"version\":100,\"key\":\":byt:AzSRhlkCmLZDS2XmmZr0bCEKfn0ujYbVySbHN78eZeQz\"}";
		RadixSystem rs = Serialize.getInstance().fromJson(data, RadixSystem.class);
		assertNotNull(rs);
	}
}
