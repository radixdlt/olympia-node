package org.radix.serialization2;

import com.radixdlt.client.core.atoms.RadixHash;
import org.junit.Test;
import org.radix.serialization2.client.Serialize;

import com.radixdlt.client.core.atoms.Atom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class AtomSerializationTest {

	@Test
	public void testAtomSerialization() {
		String atomString = "{\n" +
			"   \"version\":100,\n" +
			"   \"serializer\":2019665,\n" +
			"   \"particleGroups\":[\n" +
			"      {\n" +
			"         \"version\":100,\n" +
			"         \"serializer\":-67058791,\n" +
			"         \"particles\":[\n" +
			"            {\n" +
			"               \"version\":100,\n" +
			"               \"serializer\":-993052100,\n" +
			"               \"particle\":{\n" +
			"                  \"version\":100,\n" +
			"                  \"serializer\":-1034420571,\n" +
			"                  \"quarks\":[\n" +
			"                     {\n" +
			"                        \"version\":100,\n" +
			"                        \"serializer\":1697220864,\n" +
			"                        \"id\":\":rri:/JHd1zCEKkXMhwz7GgSuENRrcFpPKveWugkFCn4u1NCqfc629zH6/tokenclasses/JOSH\"\n" +
			"                     },\n" +
			"                     {\n" +
			"                        \"version\":100,\n" +
			"                        \"serializer\":836187407,\n" +
			"                        \"addresses\":[\n" +
			"                           \":adr:JHd1zCEKkXMhwz7GgSuENRrcFpPKveWugkFCn4u1NCqfc629zH6\"\n" +
			"                        ]\n" +
			"                     },\n" +
			"                     {\n" +
			"                        \"version\":100,\n" +
			"                        \"serializer\":68029398,\n" +
			"                        \"owner\":\":byt:A8lEvWqjdjic9W0Lxe0qFXpYHK62MtDNLSa2+xaP9w0G\"\n" +
			"                     }\n" +
			"                  ],\n" +
			"                  \"name\":\":str:Joshy Token\",\n" +
			"                  \"description\":\":str:The Best Coin Ever\",\n" +
			"                  \"granularity\":\":u20:1\",\n" +
			"                  \"permissions\":{\n" +
			"                     \"burn\":\":str:token_owner_only\",\n" +
			"                     \"mint\":\":str:token_owner_only\",\n" +
			"                     \"transfer\":\":str:all\"\n" +
			"                  }\n" +
			"               },\n" +
			"               \"spin\":1\n" +
			"            },\n" +
			"            {\n" +
			"               \"version\":100,\n" +
			"               \"serializer\":-993052100,\n" +
			"               \"particle\":{\n" +
			"                  \"version\":100,\n" +
			"                  \"serializer\":-1820701723,\n" +
			"                  \"quarks\":[\n" +
			"                     {\n" +
			"                        \"version\":100,\n" +
			"                        \"serializer\":68029398,\n" +
			"                        \"owner\":\":byt:A8lEvWqjdjic9W0Lxe0qFXpYHK62MtDNLSa2+xaP9w0G\"\n" +
			"                     },\n" +
			"                     {\n" +
			"                        \"version\":100,\n" +
			"                        \"serializer\":836187407,\n" +
			"                        \"addresses\":[\n" +
			"                           \":adr:JHd1zCEKkXMhwz7GgSuENRrcFpPKveWugkFCn4u1NCqfc629zH6\"\n" +
			"                        ]\n" +
			"                     },\n" +
			"                     {\n" +
			"                        \"version\":100,\n" +
			"                        \"serializer\":572705468,\n" +
			"                        \"planck\":25865418,\n" +
			"                        \"nonce\":1548325112815,\n" +
			"                        \"amount\":\":u20:10000000000000000000000\",\n" +
			"                        \"type\":\":str:minted\"\n" +
			"                     }\n" +
			"                  ],\n" +
			"                  \"token_reference\":\":rri:/JHd1zCEKkXMhwz7GgSuENRrcFpPKveWugkFCn4u1NCqfc629zH6/tokenclasses/JOSH\",\n" +
			"                  \"granularity\":\":u20:1\"\n" +
			"               },\n" +
			"               \"spin\":1\n" +
			"            }\n" +
			"         ]\n" +
			"      },\n" +
			"      {\n" +
			"         \"version\":100,\n" +
			"         \"serializer\":-67058791,\n" +
			"         \"particles\":[\n" +
			"            {\n" +
			"               \"version\":100,\n" +
			"               \"serializer\":-993052100,\n" +
			"               \"particle\":{\n" +
			"                  \"version\":100,\n" +
			"                  \"serializer\":-1611775620,\n" +
			"                  \"quarks\":[\n" +
			"                     {\n" +
			"                        \"version\":100,\n" +
			"                        \"serializer\":-495126317,\n" +
			"                        \"timestamps\":{\n" +
			"                           \"default\":1548325112801\n" +
			"                        }\n" +
			"                     }\n" +
			"                  ]\n" +
			"               },\n" +
			"               \"spin\":1\n" +
			"            }\n" +
			"         ]\n" +
			"      },\n" +
			"      {\n" +
			"         \"version\":100,\n" +
			"         \"serializer\":-67058791,\n" +
			"         \"particles\":[\n" +
			"            {\n" +
			"               \"version\":100,\n" +
			"               \"serializer\":-993052100,\n" +
			"               \"particle\":{\n" +
			"                  \"version\":100,\n" +
			"                  \"serializer\":-95901716,\n" +
			"                  \"quarks\":[\n" +
			"                     {\n" +
			"                        \"version\":100,\n" +
			"                        \"serializer\":68029398,\n" +
			"                        \"owner\":\":byt:A8lEvWqjdjic9W0Lxe0qFXpYHK62MtDNLSa2+xaP9w0G\"\n" +
			"                     },\n" +
			"                     {\n" +
			"                        \"version\":100,\n" +
			"                        \"serializer\":836187407,\n" +
			"                        \"addresses\":[\n" +
			"                           \":adr:JHd1zCEKkXMhwz7GgSuENRrcFpPKveWugkFCn4u1NCqfc629zH6\"\n" +
			"                        ]\n" +
			"                     },\n" +
			"                     {\n" +
			"                        \"version\":100,\n" +
			"                        \"serializer\":572705468,\n" +
			"                        \"planck\":25865418,\n" +
			"                        \"nonce\":1887866948436064,\n" +
			"                        \"amount\":\":u20:34991\",\n" +
			"                        \"type\":\":str:minted\"\n" +
			"                     }\n" +
			"                  ],\n" +
			"                  \"token_reference\":\":rri:/JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor/tokenclasses/POW\",\n" +
			"                  \"granularity\":\":u20:1\",\n" +
			"                  \"service\":\":uid:00000000000000000000000000000001\"\n" +
			"               },\n" +
			"               \"spin\":1\n" +
			"            }\n" +
			"         ]\n" +
			"      }\n" +
			"   ],\n" +
			"   \"signatures\":{\n" +
			"      \"71c3c2fc9fee73b13cad082800a6d0de\":{\n" +
			"         \"version\":100,\n" +
			"         \"serializer\":-434788200,\n" +
			"         \"r\":\":byt:AJRULGkmWzxVx0AtO8NYmZ0Aqbi6hG/Vj6GeoB3TvHAX\",\n" +
			"         \"s\":\":byt:AKbKCyHw9GYP6EyjbyQackXtF4Hj7CgX2fmTltg5VX9H\"\n" +
			"      }\n" +
			"   }\n" +
			"}";
		Atom atom = Serialize.getInstance().fromJson(atomString, Atom.class);
		assertNotNull(atom);
		assertEquals("2d8227aa5097ac892ba57fa7dc466f2433ac036df668fba212cb10361d275278", atom.getHash().toHexString());
	}

}
