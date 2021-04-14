package com.radixdlt.engine;

import com.radixdlt.atom.TxLowLevelBuilder;
import com.radixdlt.atommodel.tokens.TokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TokensConstraintScrypt;
import com.radixdlt.atommodel.tokens.TokensParticle;
import com.radixdlt.atommodel.validators.ValidatorConstraintScrypt;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.atomos.RriId;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.InMemoryEngineStore;
import com.radixdlt.utils.UInt256;
import org.junit.Before;
import org.junit.Test;

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
			cm,
			store
		);
	}

	@Test
	public void create_new_token_with_no_errors() throws RadixEngineException {
		// Arrange
		ECKeyPair keyPair = ECKeyPair.generateNew();
		RadixAddress address = new RadixAddress((byte) 0, keyPair.getPublicKey());
		RRI rri = RRI.of(address, "TEST");
		RRIParticle rriParticle = new RRIParticle(rri);
		TokenDefinitionParticle tokenDefinitionParticle = new TokenDefinitionParticle(
			rri,
			"TEST",
			"description",
			"",
			"",
			UInt256.TEN
		);
		TokensParticle tokensParticle = new TokensParticle(
			address,
			UInt256.TEN,
			RriId.fromRri(rri),
			false
		);
		var builder = TxLowLevelBuilder.newBuilder()
			.virtualDown(rriParticle)
			.up(tokenDefinitionParticle)
			.up(tokensParticle)
			.particleGroup();
		var atom = builder.signAndBuild(keyPair::sign);

		// Act
		// Assert
		this.engine.execute(List.of(atom));
	}
}
