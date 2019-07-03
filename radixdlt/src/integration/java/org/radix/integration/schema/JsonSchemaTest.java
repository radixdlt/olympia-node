package org.radix.integration.schema;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.universe.Universe;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Test;
import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.atommodel.message.MessageParticle;
import com.radixdlt.atommodel.tokens.TokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TokenDefinitionParticle.TokenTransition;
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.atommodel.tokens.TokenPermission;
import com.radixdlt.atommodel.tokens.Tokens;
import com.radixdlt.atoms.Atom;
import com.radixdlt.atomos.RRI;
import com.radixdlt.atoms.Spin;
import com.radixdlt.crypto.ECKeyPair;
import org.radix.integration.RadixTest;
import org.radix.modules.Modules;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.utils.RadixConstants;
import com.radixdlt.utils.UInt256;

import java.io.InputStream;

public class JsonSchemaTest extends RadixTest {

	private static final int BILLION_POW_10 = 9;

	private Serialization serialization = Modules.get(Serialization.class);

	public void testAtomSchema(Atom atom) throws Exception {
		JSONObject atomJsonObject = serialization.toJsonObject(atom, Output.WIRE);

		try (InputStream inputStream = getClass().getResourceAsStream("/schemas/atom.schema.json")) {
			JSONObject rawSchema = new JSONObject(new JSONTokener(inputStream));
			Schema schema = SchemaLoader.load(rawSchema);
			schema.validate(atomJsonObject); // throws a ValidationException if this object is invalid
		} catch (ValidationException e) {
			System.out.println(atomJsonObject.toString(4));
			System.out.println(e.toJSON().toString(4));
			System.out.println(e.getMessage());
			e.getCausingExceptions().stream()
				.map(ValidationException::getMessage)
				.forEach(System.out::println);
			throw e;
		}
	}

	@Test
	public void testTokenDefinition() throws Exception {
		ECKeyPair ecKeyPair = new ECKeyPair();

		TokenDefinitionParticle testToken = new TokenDefinitionParticle(
			RadixAddress.from(Modules.get(Universe.class), ecKeyPair.getPublicKey()),
			"TEST",
			"Test RADS",
			"Radix Test Tokens",
			UInt256.ONE,
			null,
			ImmutableMap.of(
				TokenTransition.MINT, TokenPermission.TOKEN_CREATION_ONLY,
				TokenTransition.BURN, TokenPermission.NONE
			)
		);

		Atom tokenAtom = new Atom(1L);
		tokenAtom.addParticleGroupWith(testToken, Spin.UP);

		tokenAtom.sign(ecKeyPair);

		testAtomSchema(tokenAtom);
	}

	@Test
	public void testTransferAtom() throws Exception {
		ECKeyPair ecKeyPair = new ECKeyPair();
		RadixAddress address = RadixAddress.from(Modules.get(Universe.class), ecKeyPair.getPublicKey());

		Atom transactionAtom = new Atom(1L);
		transactionAtom.addParticleGroupWith(new MessageParticle(address, address, "Radix....Just Imagine".getBytes(RadixConstants.STANDARD_CHARSET)), Spin.UP);

		TransferrableTokensParticle mintParticle = new TransferrableTokensParticle(address,
			UInt256.TEN.pow(TokenDefinitionParticle.SUB_UNITS_POW_10 + BILLION_POW_10),
			UInt256.ONE,
			RRI.of(address, Tokens.getNativeTokenShortCode()),
			1L,
			ImmutableMap.of(
				TokenTransition.MINT, TokenPermission.TOKEN_CREATION_ONLY,
				TokenTransition.BURN, TokenPermission.NONE
			)
		);
		transactionAtom.addParticleGroupWith(mintParticle, Spin.UP);
		transactionAtom.sign(ecKeyPair);

		testAtomSchema(transactionAtom);
	}
}
