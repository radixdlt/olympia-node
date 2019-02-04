package org.radix.serialization2;

import org.junit.Test;
import org.radix.serialization2.client.Serialize;

import com.radixdlt.client.core.atoms.Atom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class AtomSerializationTest {

	@Test
	public void testAtomSerialization() {
		String atomString = "{\"version\":100,\"serializer\":2019665,\"particleGroups\":"
			+ "[{\"version\":100,\"serializer\":-67058791,\"particles\":"
			+ "[{\"version\":100,\"serializer\":-993052100,\"particle\":"
			+ "{\"version\":100,\"serializer\":-1034420571,\"quarks\":"
			+ "[{\"version\":100,\"serializer\":836187407,\"addresses\":"
			+ "[\":adr:JHTovbsWcWhy8RqSbkeHPfSz7p4Fva9B76vLW53UjbacFaV3LMq\"]},"
			+ "{\"version\":100,\"serializer\":68029398,\"owner\":\":byt:A7RcVzMG38i1W6hogAZkX4fUbuHgYATKr0BbKe1fGe+5\"}],"
			+ "\"name\":\":str:Joshy Token\",\"symbol\":\":str:JOSH\","
			+ "\"description\":\":str:The Best Coin Ever\",\"granularity\":\":u20:1\",\"permissions\":"
			+ "{\"burn\":\":str:token_owner_only\",\"mint\":\":str:token_owner_only\",\"transfer\":\":str:all\"}},"
			+ "\"spin\":1},{\"version\":100,\"serializer\":-993052100,\"particle\":"
			+ "{\"version\":100,\"serializer\":-1820701723,\"quarks\":"
			+ "[{\"version\":100,\"serializer\":68029398,\"owner\":\":byt:A7RcVzMG38i1W6hogAZkX4fUbuHgYATKr0BbKe1fGe+5\"},"
			+ "{\"version\":100,\"serializer\":836187407,\"addresses\":"
			+ "[\":adr:JHTovbsWcWhy8RqSbkeHPfSz7p4Fva9B76vLW53UjbacFaV3LMq\"]},"
			+ "{\"version\":100,\"serializer\":572705468,\"planck\":25881510,\"nonce\":1549290623280,\"amount\":"
			+ "\":u20:10000000000000000000000\",\"type\":\":str:minted\"}],\"token_reference\":"
			+ "\":rri:/JHTovbsWcWhy8RqSbkeHPfSz7p4Fva9B76vLW53UjbacFaV3LMq/tokenclasses/JOSH\",\"granularity\":\":u20:1\"},"
			+ "\"spin\":1}]},{\"version\":100,\"serializer\":-67058791,\"particles\":"
			+ "[{\"version\":100,\"serializer\":-993052100,\"particle\":"
			+ "{\"version\":100,\"serializer\":-1611775620,\"quarks\":"
			+ "[{\"version\":100,\"serializer\":-495126317,\"timestamps\":{\"default\":1549290623263}}]},\"spin\":1}]},"
			+ "{\"version\":100,\"serializer\":-67058791,\"particles\":"
			+ "[{\"version\":100,\"serializer\":-993052100,\"particle\":{\"version\":100,\"serializer\":-95901716,\"quarks\":"
			+ "[{\"version\":100,\"serializer\":68029398,\"owner\":\":byt:A7RcVzMG38i1W6hogAZkX4fUbuHgYATKr0BbKe1fGe+5\"},"
			+ "{\"version\":100,\"serializer\":836187407,\"addresses\":"
			+ "[\":adr:JHTovbsWcWhy8RqSbkeHPfSz7p4Fva9B76vLW53UjbacFaV3LMq\"]},"
			+ "{\"version\":100,\"serializer\":572705468,\"planck\":25881510,\"nonce\":435193558312749,"
			+ "\"amount\":\":u20:68461\",\"type\":\":str:minted\"}],"
			+ "\"token_reference\":\":rri:/JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor/tokenclasses/POW\","
			+ "\"granularity\":\":u20:1\",\"service\":\":uid:00000000000000000000000000000001\"},\"spin\":1}]}],"
			+ "\"signatures\":{\"80b52acd9de604be9810f527a6ecbd35\":{\"version\":100,\"serializer\":-434788200,"
			+ "\"s\":\":byt:WuFx4C+LcNHBzc5W5IArrPVNGLwfodXcXCRN+JeFH48=\","
			+ "\"r\":\":byt:FhbelTNHCzBACu3jtZZcWo/4DsyjViCmFzvd3HLj4+U=\"}}}";

		Atom atom = Serialize.getInstance().fromJson(atomString, Atom.class);
		assertNotNull(atom);
		assertEquals("a097281877ae2430239a6e41bd97031294c7603829c78db7790d1c5902f651d7", atom.getHash().toHexString());
	}

}
