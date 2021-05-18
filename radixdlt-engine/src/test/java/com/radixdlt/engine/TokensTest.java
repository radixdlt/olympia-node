package com.radixdlt.engine;

import com.radixdlt.atom.ActionConstructors;
import com.radixdlt.atom.TxLowLevelBuilder;
import com.radixdlt.atommodel.tokens.TokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TokensConstraintScrypt;
import com.radixdlt.atommodel.tokens.TokensParticle;
import com.radixdlt.atommodel.validators.ValidatorConstraintScrypt;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.REAddrParticle;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.InMemoryEngineStore;
import com.radixdlt.utils.UInt256;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class TokensTest {
	private RadixEngine<Void> engine;
	private EngineStore<Void> store;

	@Before
	public void setup() {
		CMAtomOS cmAtomOS = new CMAtomOS();
		cmAtomOS.load(new ValidatorConstraintScrypt());
		cmAtomOS.load(new TokensConstraintScrypt());
		ConstraintMachine cm = new ConstraintMachine.Builder()
			.setVirtualStoreLayer(cmAtomOS.virtualizedUpParticles())
			.setParticleStaticCheck(cmAtomOS.buildParticleStaticCheck())
			.setParticleTransitionProcedures(cmAtomOS.buildTransitionProcedures())
			.build();
		this.store = new InMemoryEngineStore<>();
		this.engine = new RadixEngine<>(
			ActionConstructors.newBuilder().build(),
			cm,
			store
		);
	}

	@Test
	public void create_new_token_with_no_errors() throws RadixEngineException {
		// Arrange
		ECKeyPair keyPair = ECKeyPair.generateNew();
		REAddr rri = REAddr.ofHashedKey(keyPair.getPublicKey(), "test");
		REAddrParticle rriParticle = new REAddrParticle(rri);
		TokenDefinitionParticle tokenDefinitionParticle = new TokenDefinitionParticle(
			rri,
			"TEST",
			"description",
			"",
			"",
			UInt256.TEN
		);

		var holdingAddress = REAddr.ofPubKeyAccount(keyPair.getPublicKey());
		var tokensParticle = new TokensParticle(
			holdingAddress,
			UInt256.TEN,
			rri
		);
		var builder = TxLowLevelBuilder.newBuilder()
			.virtualDown(rriParticle, "test".getBytes(StandardCharsets.UTF_8))
			.up(tokenDefinitionParticle)
			.up(tokensParticle)
			.particleGroup();
		var sig = keyPair.sign(builder.hashToSign().asBytes());
		var txn = builder.sig(sig).build();

		// Act
		// Assert
		this.engine.execute(List.of(txn));
	}
}
