/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package org.radix.api;

import com.radixdlt.DefaultSerialization;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle.TokenTransition;
import com.radixdlt.atommodel.tokens.TokenPermission;
import java.util.Map;

import com.radixdlt.atommodel.Atom;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.utils.UInt256;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TokenDefinitionSchemaTest {

	static Serialization serialization;

	@BeforeClass
	public static void setupSerializer() {
		serialization = DefaultSerialization.getInstance();
	}

	@Test
	public void when_validating_complete_tokendefinition_particle_against_schema__validation_is_successful() throws CryptoException {
		ECKeyPair kp = ECKeyPair.generateNew();
		RadixAddress addr = new RadixAddress((byte) 12, kp.getPublicKey());
		Map<TokenTransition, TokenPermission> tp = ImmutableMap.of(
			TokenTransition.MINT, TokenPermission.TOKEN_OWNER_ONLY,
			TokenTransition.BURN, TokenPermission.TOKEN_OWNER_ONLY
		);
		MutableSupplyTokenDefinitionParticle tokenDefinition = new MutableSupplyTokenDefinitionParticle(addr, "TEST", "Test token", "Test token", UInt256.ONE, "http://example.com", tp);
		Atom atom = new Atom();
		atom.addParticleGroupWith(tokenDefinition, Spin.UP);

		JSONObject jsonAtom = serialization.toJsonObject(atom, Output.WIRE);

		Schema schema = AtomSchemas.get();
		schema.validate(jsonAtom);

		// All good if we get here - exception on validation fail
		assertTrue(true);
	}

	@Test(expected = ValidationException.class)
	public void when_validating_old_tokendefinition_particle_against_schema__an_exception_is_thrown() {
		String strAtom = "" +
			"{\n" +
			"    \"serializer\": \"radix.atom\",\n" +
			"    \"version\": 100,\n" +
			"    \"particleGroups\": [{\n" +
			"        \"serializer\": \"radix.particle_group\",\n" +
			"        \"particles\": [{\n" +
			"            \"spin\": 1,\n" +
			"            \"serializer\": \"radix.spun_particle\",\n" +
			"            \"particle\": {\n" +
			"                \"symbol\": \":str:TEST\",\n" +
			"                \"address\": \":adr:2n3VYbjQyB2sySwMqiacjGjzAwEHYrgnSsZXDBo1F2x49a3RMBsF\",\n" +
			"                \"granularity\": \":u20:1\",\n" +
			"                \"permissions\": {\n" +
			"                    \"burn\": \":str:token_creation_only\",\n" +
			"                    \"mint\": \":str:token_creation_only\"\n" +
			"                },\n" +
			"                \"destinations\": [\":uid:1c4c0a2915f4406ddfbd6c64549cfe1a\"],\n" +
			"                \"name\": \":str:Test token\",\n" +
			"                \"serializer\": \"radix.particles.token_definition\",\n" +
			"                \"description\": \":str:Test token\",\n" +
			"                \"icon\": \":byt:some bytes that don't matter for this test\",\n" +
			"                \"version\": 100\n" +
			"            },\n" +
			"            \"version\": 100\n" +
			"        }],\n" +
			"        \"version\": 100\n" +
			"    }]\n" +
			"}";
		JSONObject jsonAtom = new JSONObject(strAtom);
		Schema schema = AtomSchemas.get();
		schema.validate(jsonAtom);

		// fail if we get here - exception should be thrown
		fail();
	}
}
