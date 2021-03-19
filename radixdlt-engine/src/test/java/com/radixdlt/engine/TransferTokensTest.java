package com.radixdlt.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.atommodel.tokens.FixedSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TokensConstraintScrypt;
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.atommodel.validators.ValidatorConstraintScrypt;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.InMemoryEngineStore;
import com.radixdlt.utils.UInt256;
import org.junit.Before;
import org.junit.Test;

public class TransferTokensTest {
	private RadixEngine<RadixEngineAtom> engine;
	private EngineStore<RadixEngineAtom> store;
	private ECKeyPair keyPair;
	private TransferrableTokensParticle transferrableTokensParticle;

	@Before
	public void setup() {
		CMAtomOS cmAtomOS = new CMAtomOS();
		cmAtomOS.load(new ValidatorConstraintScrypt());
		cmAtomOS.load(new TokensConstraintScrypt());
		ConstraintMachine cm = new ConstraintMachine.Builder()
			.setParticleStaticCheck(cmAtomOS.buildParticleStaticCheck())
			.setParticleTransitionProcedures(cmAtomOS.buildTransitionProcedures())
			.build();
		this.store = new InMemoryEngineStore<>();
		this.engine = new RadixEngine<>(
			cm,
			cmAtomOS.buildVirtualLayer(),
			store
		);

		this.keyPair = ECKeyPair.generateNew();
		RadixAddress address = new RadixAddress((byte) 0, keyPair.getPublicKey());
		RRI rri = RRI.of(address, "TEST");
		RRIParticle rriParticle = new RRIParticle(rri);
		FixedSupplyTokenDefinitionParticle tokenDefinitionParticle = new FixedSupplyTokenDefinitionParticle(
			rri,
			"TEST",
			"description",
			UInt256.TEN,
			UInt256.ONE,
			null,
			null
		);
		this.transferrableTokensParticle = new TransferrableTokensParticle(
			address,
			UInt256.TEN,
			UInt256.ONE,
			rri,
			ImmutableMap.of()
		);
		ImmutableList<CMMicroInstruction> instructions = ImmutableList.of(
			CMMicroInstruction.checkSpinAndPush(rriParticle, Spin.UP),
			CMMicroInstruction.checkSpinAndPush(tokenDefinitionParticle, Spin.NEUTRAL),
			CMMicroInstruction.checkSpinAndPush(transferrableTokensParticle, Spin.NEUTRAL),
			CMMicroInstruction.particleGroup()
		);
		CMInstruction instruction = new CMInstruction(
			instructions,
			ImmutableMap.of(keyPair.euid(), keyPair.sign(HashUtils.zero256()))
		);
		this.store.storeAtom(new BaseAtom(instruction, HashUtils.zero256()));
	}

	@Test
	public void transfer_token_twice_in_one_instruction() throws RadixEngineException {
		// Arrange
		TransferrableTokensParticle nextParticle1 = new TransferrableTokensParticle(
			transferrableTokensParticle.getAddress(),
			transferrableTokensParticle.getAmount(),
			transferrableTokensParticle.getGranularity(),
			transferrableTokensParticle.getTokDefRef(),
			transferrableTokensParticle.getTokenPermissions()
		);
		TransferrableTokensParticle nextParticle2 = new TransferrableTokensParticle(
			transferrableTokensParticle.getAddress(),
			transferrableTokensParticle.getAmount(),
			transferrableTokensParticle.getGranularity(),
			transferrableTokensParticle.getTokDefRef(),
			transferrableTokensParticle.getTokenPermissions()
		);
		ImmutableList<CMMicroInstruction> instructions = ImmutableList.of(
			CMMicroInstruction.checkSpinAndPush(transferrableTokensParticle, Spin.UP),
			CMMicroInstruction.checkSpinAndPush(nextParticle1, Spin.NEUTRAL),
			CMMicroInstruction.checkSpinAndPush(nextParticle1, Spin.UP),
			CMMicroInstruction.checkSpinAndPush(nextParticle2, Spin.NEUTRAL),
			CMMicroInstruction.particleGroup()
		);
		CMInstruction instruction = new CMInstruction(
			instructions,
			ImmutableMap.of(keyPair.euid(), keyPair.sign(HashUtils.zero256()))
		);

		// Act
		this.engine.execute(new BaseAtom(instruction, HashUtils.zero256()));

		// Assert
		assertThat(this.store.getSpin(transferrableTokensParticle)).isEqualTo(Spin.DOWN);
		assertThat(this.store.getSpin(nextParticle1)).isEqualTo(Spin.DOWN);
		assertThat(this.store.getSpin(nextParticle2)).isEqualTo(Spin.UP);
	}

}
