package com.radixdlt.engine;

import com.radixdlt.atom.ActionConstructors;
import com.radixdlt.atom.TxLowLevelBuilder;
import com.radixdlt.atom.actions.CreateMutableToken;
import com.radixdlt.atom.actions.MintToken;
import com.radixdlt.atommodel.tokens.construction.CreateMutableTokenConstructor;
import com.radixdlt.atommodel.tokens.construction.MintTokenConstructor;
import com.radixdlt.atommodel.tokens.state.TokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.scrypt.TokensConstraintScrypt;
import com.radixdlt.atommodel.tokens.state.TokensParticle;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
			.setParticleTransitionProcedures(cmAtomOS.getProcedures())
			.build();
		this.store = new InMemoryEngineStore<>();
		this.engine = new RadixEngine<>(
			ActionConstructors.newBuilder()
				.put(CreateMutableToken.class, new CreateMutableTokenConstructor())
				.put(MintToken.class, new MintTokenConstructor())
				.build(),
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

	@Test
	public void authorization_failure_on_mint() throws Exception {
		var key = ECKeyPair.generateNew();
		var txn = this.engine.construct(
			key.getPublicKey(),
			new CreateMutableToken("test", "Name", "", "", "")
		).signAndBuild(key::sign);
		this.engine.execute(List.of(txn));

		var addr = REAddr.ofHashedKey(key.getPublicKey(), "test");
		var nextKey = ECKeyPair.generateNew();
		var mintTxn = this.engine.construct(
			new MintToken(addr, REAddr.ofPubKeyAccount(key.getPublicKey()), UInt256.ONE)
		).signAndBuild(nextKey::sign);
		assertThatThrownBy(() -> this.engine.execute(List.of(mintTxn))).isInstanceOf(RadixEngineException.class);
	}
}
