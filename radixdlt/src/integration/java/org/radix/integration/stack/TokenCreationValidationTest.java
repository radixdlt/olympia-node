package org.radix.integration.stack;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.universe.Universe;
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
import com.radixdlt.atoms.Atom;
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

		CMAtom cmAtom = Modules.get(ValidationHandler.class).validate(atom);
		Modules.get(ValidationHandler.class).stateCheck(cmAtom);
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
		CMAtom cmAtom = Modules.get(ValidationHandler.class).validate(atom);
		Modules.get(ValidationHandler.class).stateCheck(cmAtom);
		PreparedAtom preparedAtom = new PreparedAtom(cmAtom);
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
		CMAtom cmAtom1 = Modules.get(ValidationHandler.class).validate(secondAtom);
		Assertions.assertThatThrownBy(() -> Modules.get(ValidationHandler.class).stateCheck(cmAtom1))
			.isInstanceOf(ParticleConflictException.class);
	}
}