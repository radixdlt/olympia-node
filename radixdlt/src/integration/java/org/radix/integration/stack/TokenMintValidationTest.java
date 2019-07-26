package org.radix.integration.stack;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.engine.AtomEventListener;
import com.radixdlt.engine.RadixEngineUtils;
import com.radixdlt.engine.StateCheckResult.StateCheckResultAcceptor;
import com.radixdlt.utils.UInt384;
import org.junit.Before;
import org.junit.Test;
import org.radix.atoms.PreparedAtom;
import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.atommodel.tokens.TokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TokenDefinitionParticle.TokenTransition;
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.atommodel.tokens.TokenPermission;
import org.radix.atoms.Atom;
import org.radix.atoms.AtomStore;
import com.radixdlt.atomos.RRI;
import com.radixdlt.constraintmachine.CMAtom;
import com.radixdlt.atoms.Spin;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.utils.Offset;
import org.radix.integration.RadixTestWithStores;
import org.radix.modules.Modules;
import org.radix.properties.RuntimeProperties;
import org.radix.time.NtpService;
import org.radix.time.Time;
import com.radixdlt.utils.UInt256;
import com.radixdlt.universe.Universe;
import org.radix.validation.ValidationHandler;

import java.io.File;

public class TokenMintValidationTest extends RadixTestWithStores {
	private ECKeyPair identity;
	private RadixAddress universeAddress;

	@Before
	public void createIdentity() throws Exception {
		String universeKeyPath = Modules.get(RuntimeProperties.class).get("universe.key.path", "universe.key");
		identity = ECKeyPair.fromFile(new File(universeKeyPath), true);
		Universe universe = Modules.get(Universe.class);
		universeAddress = RadixAddress.from(universe, identity.getPublicKey());
	}

	private static long currentPlanckTime() {
		return Modules.get(Universe.class).toPlanck(Modules.get(NtpService.class).getUTCTimeMS(), Offset.NONE);
	}

	@Test
	public void simpleMint() throws Exception {
		// Create RADIX TokenDefinition
		TokenDefinitionParticle tokenDefinition = new TokenDefinitionParticle(universeAddress,
			"RADIX",
			"Radix Token",
			"Just a test token",
			UInt256.ONE,
			null,
			ImmutableMap.of(
				TokenTransition.MINT, TokenPermission.ALL,
				TokenTransition.BURN, TokenPermission.NONE
			)
		);

		UnallocatedTokensParticle unallocatedTokensParticle = new UnallocatedTokensParticle(
			UInt256.MAX_VALUE,
			UInt256.ONE,
			tokenDefinition.getRRI(),
			ImmutableMap.of(
				TokenTransition.MINT, TokenPermission.ALL,
				TokenTransition.BURN, TokenPermission.NONE
			)
		);

		RRIParticle rriParticle = new RRIParticle(
			tokenDefinition.getRRI()
		);

		Atom atom = new Atom(Time.currentTimestamp());
		atom.addParticleGroupWith(
			tokenDefinition, Spin.UP,
			unallocatedTokensParticle, Spin.UP,
			rriParticle, Spin.DOWN
		);
		addTemporalVertex(atom); // Can't store atom without vertex from this node
		atom.sign(identity);

		CMAtom cmAtom = RadixEngineUtils.toCMAtom(atom);
		PreparedAtom preparedAtom = new PreparedAtom(cmAtom, UInt384.ONE);
		Modules.get(AtomStore.class).storeAtom(preparedAtom);

		// Mint some RADIX tokens
		TransferrableTokensParticle mintParticle = new TransferrableTokensParticle(universeAddress,
			UInt256.TEN.pow(5),
			UInt256.ONE,
			RRI.of(tokenDefinition.getOwner(), tokenDefinition.getSymbol()),
			currentPlanckTime(),
			ImmutableMap.of(
				TokenTransition.MINT, TokenPermission.ALL,
				TokenTransition.BURN, TokenPermission.NONE
			)
		);

		UnallocatedTokensParticle leftOver = new UnallocatedTokensParticle(
			UInt256.MAX_VALUE.subtract(mintParticle.getAmount()),
			UInt256.ONE,
			tokenDefinition.getRRI(),
			ImmutableMap.of(
				TokenTransition.MINT, TokenPermission.ALL,
				TokenTransition.BURN, TokenPermission.NONE
			)
		);

		atom = new Atom(Time.currentTimestamp());
		atom.addParticleGroupWith(unallocatedTokensParticle, Spin.DOWN, mintParticle, Spin.UP, leftOver, Spin.UP);
		atom.sign(identity);

		CMAtom cmAtom2 = RadixEngineUtils.toCMAtom(atom);
		AtomEventListener atomEventListener = mock(AtomEventListener.class);
		Modules.get(ValidationHandler.class).getRadixEngine().addAtomEventListener(atomEventListener);
		Modules.get(ValidationHandler.class).getRadixEngine().submit(cmAtom2);
		verify(atomEventListener, timeout(5000).times(1))
			.onStateSuccess(eq(cmAtom2), any());
	}
}
