package com.radix.regression;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction.TokenSupplyType;
import com.radixdlt.client.application.translate.tokens.TokenDefinitionReference;
import com.radixdlt.client.atommodel.tokens.TokenDefinitionParticle;
import com.radixdlt.client.atommodel.tokens.TokenDefinitionParticle.TokenTransition;
import com.radixdlt.client.atommodel.tokens.TokenPermission;
import com.radixdlt.client.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.UnsignedAtom;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.core.network.actions.SubmitAtomAction;
import com.radixdlt.client.core.network.actions.SubmitAtomResultAction;
import com.radixdlt.client.core.network.actions.SubmitAtomResultAction.SubmitAtomResultActionType;
import io.reactivex.Observable;
import io.reactivex.observers.TestObserver;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.radix.utils.UInt256;

public class UnallocatedTokensParticleTest {
	@Test
	public void given_an_account__when_the_account_executes_a_token_creation_without_unallocated_particles__then_the_atom_will_be_rejected() {
		RadixApplicationAPI api = RadixApplicationAPI.create(Bootstrap.LOCALHOST_SINGLENODE, RadixIdentities.createNew());

		List<ParticleGroup> groups = new ArrayList<>();

		TokenDefinitionParticle particle = new TokenDefinitionParticle(
			api.getMyAddress(),
			"Joshy Token",
			"JOSH",
			"Best Token",
			UInt256.ONE,
			ImmutableMap.of(
				TokenTransition.MINT, TokenPermission.TOKEN_OWNER_ONLY,
				TokenTransition.BURN, TokenPermission.NONE
			),
			null
		);

		groups.add(ParticleGroup.of(SpunParticle.up(particle)));

		UnsignedAtom unsignedAtom = api.buildAtomWithFee(groups);

		Observable<SubmitAtomAction> updates = api.getMyIdentity()
			.sign(unsignedAtom)
			.flatMapObservable(a -> api.getNetworkController().submitAtom(a));

		TestObserver<SubmitAtomResultAction> testObserver = TestObserver.create();
		updates
			.doOnNext(System.out::println)
			.ofType(SubmitAtomResultAction.class)
			.subscribe(testObserver);

		testObserver.awaitTerminalEvent();
		testObserver.assertValue(i -> i.getType() == SubmitAtomResultActionType.VALIDATION_ERROR);
	}

	@Test
	public void given_an_account_with_a_token__when_the_account_executes_an_atom_with_unallocated_particles_to_that_token__then_the_atom_will_be_rejected() {
		RadixApplicationAPI api = RadixApplicationAPI.create(Bootstrap.LOCALHOST_SINGLENODE, RadixIdentities.createNew());

		api.createToken(
			"Joshy Token",
			"JOSH",
			"Coolest Token",
			BigDecimal.ONE,
			BigDecimal.ONE,
			TokenSupplyType.FIXED
		).blockUntilComplete();

		List<ParticleGroup> groups = new ArrayList<>();

		UnallocatedTokensParticle unallocatedParticle = new UnallocatedTokensParticle(
			UInt256.MAX_VALUE,
			UInt256.ONE,
			System.currentTimeMillis(),
			TokenDefinitionReference.of(api.getMyAddress(), "JOSH"),
			ImmutableMap.of(TokenTransition.MINT, TokenPermission.TOKEN_CREATION_ONLY, TokenTransition.BURN, TokenPermission.TOKEN_CREATION_ONLY)
		);

		groups.add(ParticleGroup.of(SpunParticle.up(unallocatedParticle)));

		UnsignedAtom unsignedAtom = api.buildAtomWithFee(groups);

		Observable<SubmitAtomAction> updates = api.getMyIdentity()
			.sign(unsignedAtom)
			.flatMapObservable(a -> api.getNetworkController().submitAtom(a));

		TestObserver<SubmitAtomResultAction> testObserver = TestObserver.create();
		updates
			.doOnNext(System.out::println)
			.ofType(SubmitAtomResultAction.class)
			.subscribe(testObserver);

		testObserver.awaitTerminalEvent();
		testObserver.assertValue(i -> i.getType() == SubmitAtomResultActionType.VALIDATION_ERROR);
	}

	@Test
	public void given_an_account__when_the_account_executes_a_token_creation_with_2_unallocated_particles__then_the_atom_will_be_rejected() {
		RadixApplicationAPI api = RadixApplicationAPI.create(Bootstrap.LOCALHOST_SINGLENODE, RadixIdentities.createNew());

		List<ParticleGroup> groups = new ArrayList<>();

		UnallocatedTokensParticle unallocatedParticle0 = new UnallocatedTokensParticle(
			UInt256.MAX_VALUE,
			UInt256.ONE,
			System.nanoTime(),
			TokenDefinitionReference.of(api.getMyAddress(), "JOSH"),
			ImmutableMap.of(TokenTransition.MINT, TokenPermission.TOKEN_CREATION_ONLY, TokenTransition.BURN, TokenPermission.TOKEN_CREATION_ONLY)
		);

		UnallocatedTokensParticle unallocatedParticle1 = new UnallocatedTokensParticle(
			UInt256.MAX_VALUE,
			UInt256.ONE,
			System.nanoTime(),
			TokenDefinitionReference.of(api.getMyAddress(), "JOSH"),
			ImmutableMap.of(TokenTransition.MINT, TokenPermission.TOKEN_CREATION_ONLY, TokenTransition.BURN, TokenPermission.TOKEN_CREATION_ONLY)
		);

		TokenDefinitionParticle tokenDefinitionParticle = new TokenDefinitionParticle(
			api.getMyAddress(),
			"Joshy Token",
			"JOSH",
			"Coolest token",
			UInt256.ONE,
			ImmutableMap.of(
				TokenTransition.MINT, TokenPermission.TOKEN_OWNER_ONLY,
				TokenTransition.BURN, TokenPermission.NONE
			),
			null
		);

		groups.add(ParticleGroup.of(
			SpunParticle.up(unallocatedParticle0),
			SpunParticle.up(unallocatedParticle1),
			SpunParticle.up(tokenDefinitionParticle)
		));

		UnsignedAtom unsignedAtom = api.buildAtomWithFee(groups);

		Observable<SubmitAtomAction> updates = api.getMyIdentity()
			.sign(unsignedAtom)
			.flatMapObservable(a -> api.getNetworkController().submitAtom(a));

		TestObserver<SubmitAtomResultAction> testObserver = TestObserver.create();
		updates
			.doOnNext(System.out::println)
			.ofType(SubmitAtomResultAction.class)
			.subscribe(testObserver);

		testObserver.awaitTerminalEvent();
		testObserver.assertValue(i -> i.getType() == SubmitAtomResultActionType.VALIDATION_ERROR);
	}
}
