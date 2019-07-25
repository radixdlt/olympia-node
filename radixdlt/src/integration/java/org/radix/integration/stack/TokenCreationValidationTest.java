package org.radix.integration.stack;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.common.Pair;
import com.radixdlt.engine.AtomEventListener;
import com.radixdlt.engine.RadixEngineUtils;
import com.radixdlt.engine.StateCheckResult.StateCheckResultAcceptor;
import com.radixdlt.universe.Universe;
import com.radixdlt.utils.UInt384;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.radix.atoms.PreparedAtom;
import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.atommodel.tokens.TokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TokenDefinitionParticle.TokenTransition;
import com.radixdlt.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.atommodel.tokens.TokenPermission;
import org.radix.atoms.Atom;
import org.radix.atoms.AtomStore;
import com.radixdlt.constraintmachine.CMAtom;
import com.radixdlt.atoms.Spin;
import org.radix.atoms.particles.conflict.ParticleConflictException;
import com.radixdlt.crypto.ECKeyPair;
import org.radix.integration.RadixTestWithStores;
import org.radix.modules.Modules;
import org.radix.properties.RuntimeProperties;
import org.radix.time.Time;
import com.radixdlt.utils.UInt256;
import org.radix.validation.ValidationHandler;

import java.io.File;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class TokenCreationValidationTest extends RadixTestWithStores {
	private ECKeyPair identity;
	private RadixAddress universeAddress;

	@Before
	public void createIdentity() throws Exception {
		String universeKeyPath = Modules.get(RuntimeProperties.class).get("universe.key.path", "universe.key");
		identity = ECKeyPair.fromFile(new File(universeKeyPath), true);
		Universe universe = Modules.get(Universe.class);
		universeAddress = RadixAddress.from(universe, identity.getPublicKey());
	}

	@Test
	public void tokenDefinitionRegistration() throws Exception {
		Atom atom = new Atom(Time.currentTimestamp());

		TokenDefinitionParticle tokenDefinition = new TokenDefinitionParticle(universeAddress,
			"RADIX",
			"Radix Token",
			"Just a test token",
			UInt256.ONE,
			null,
			ImmutableMap.of(
				TokenTransition.MINT, TokenPermission.TOKEN_CREATION_ONLY,
				TokenTransition.BURN, TokenPermission.NONE
			)
		);
		UnallocatedTokensParticle unallocatedTokensParticle = new UnallocatedTokensParticle(
			UInt256.MAX_VALUE,
			UInt256.ONE,
			tokenDefinition.getRRI(),
			tokenDefinition.getTokenPermissions()
		);
		RRIParticle rriParticle = new RRIParticle(tokenDefinition.getRRI());

		atom.addParticleGroupWith(
			tokenDefinition, Spin.UP,
			unallocatedTokensParticle, Spin.UP,
			rriParticle, Spin.DOWN
		);
		atom.sign(identity);

		CMAtom cmAtom = RadixEngineUtils.toCMAtom(atom);
		AtomEventListener listener = mock(AtomEventListener.class);
		Modules.get(ValidationHandler.class).getRadixEngine().addAtomEventListener(listener);
		Modules.get(ValidationHandler.class).getRadixEngine().stateCheck(cmAtom, ImmutableMap.of());
		verify(listener, times(1))
			.onStateSuccess(eq(cmAtom), any());
	}

	@Test
	public void tokenDefinitionRegisterDuplicate() throws Exception {
		Atom atom = new Atom(Time.currentTimestamp());

		TokenDefinitionParticle tokenDefinition = new TokenDefinitionParticle(universeAddress,
			"RADRAD",
			"Radix Token",
			"Just a test token",
			UInt256.ONE,
			null,
			ImmutableMap.of(
				TokenTransition.MINT, TokenPermission.TOKEN_CREATION_ONLY,
				TokenTransition.BURN, TokenPermission.NONE
			)
		);
		UnallocatedTokensParticle unallocatedTokensParticle = new UnallocatedTokensParticle(
			UInt256.MAX_VALUE,
			UInt256.ONE,
			tokenDefinition.getRRI(),
			tokenDefinition.getTokenPermissions()
		);
		RRIParticle rriParticle = new RRIParticle(tokenDefinition.getRRI());
		atom.addParticleGroupWith(
			rriParticle, Spin.DOWN,
			tokenDefinition, Spin.UP,
			unallocatedTokensParticle, Spin.UP
		);
		addTemporalVertex(atom); // Can't store atom without vertex from this node
		atom.sign(identity);
		CMAtom cmAtom = RadixEngineUtils.toCMAtom(atom);
		PreparedAtom preparedAtom = new PreparedAtom(cmAtom, UInt384.ONE);
		Modules.get(AtomStore.class).storeAtom(preparedAtom);

		Atom secondAtom = new Atom(Time.currentTimestamp());
		TokenDefinitionParticle secondTokenDefinition = new TokenDefinitionParticle(universeAddress,
			"RADRAD",
			"Radix Token",
			"Just a test token duplicate",
			UInt256.ONE,
			null,
			ImmutableMap.of(
				TokenTransition.MINT, TokenPermission.TOKEN_CREATION_ONLY,
				TokenTransition.BURN, TokenPermission.NONE
			)
		);
		UnallocatedTokensParticle secondUnallocateTokensParticle = new UnallocatedTokensParticle(
			UInt256.MAX_VALUE,
			UInt256.ONE,
			tokenDefinition.getRRI(),
			tokenDefinition.getTokenPermissions()
		);
		secondAtom.addParticleGroupWith(
			rriParticle, Spin.DOWN,
			secondTokenDefinition, Spin.UP,
			secondUnallocateTokensParticle, Spin.UP
		);
		secondAtom.sign(identity);
		CMAtom secondCMAtom = RadixEngineUtils.toCMAtom(secondAtom);

		AtomEventListener listener = mock(AtomEventListener.class);
		Modules.get(ValidationHandler.class).getRadixEngine().addAtomEventListener(listener);
		Modules.get(ValidationHandler.class).getRadixEngine().stateCheck(secondCMAtom, ImmutableMap.of());
		verify(listener, times(1))
			.onStateConflict(eq(secondCMAtom), any(), any());
	}
}