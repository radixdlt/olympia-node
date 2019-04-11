package com.radix.regression;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.FeeMapper;
import com.radixdlt.client.application.translate.PowFeeMapper;
import com.radixdlt.client.application.translate.tokens.TokenUnitConversions;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.atommodel.tokens.TokenDefinitionParticle;
import com.radixdlt.client.atommodel.tokens.TokenDefinitionParticle.TokenTransition;
import com.radixdlt.client.atommodel.tokens.TokenPermission;
import com.radixdlt.client.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.client.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.UnsignedAtom;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.core.network.HttpClients;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient;
import com.radixdlt.client.core.network.websocket.WebSocketClient;
import com.radixdlt.client.core.network.websocket.WebSocketStatus;
import com.radixdlt.client.core.pow.ProofOfWorkBuilder;
import io.reactivex.observers.TestObserver;
import okhttp3.Request;
import org.junit.Before;
import org.junit.Test;
import org.radix.utils.UInt256;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class MultipleTransitionsInSameGroupTest {
	private RadixUniverse universe = RadixUniverse.create(Bootstrap.LOCALHOST_SINGLENODE);
	private RadixIdentity identity;
	private FeeMapper feeMapper = new PowFeeMapper(Atom::getHash, new ProofOfWorkBuilder());
	private RadixJsonRpcClient jsonRpcClient;

	@Before
	public void setUp() {
		this.identity = RadixIdentities.createNew();
		Request localhost = new Request.Builder().url("ws://localhost:8080/rpc").build();
		WebSocketClient webSocketClient = new WebSocketClient(listener -> HttpClients.getSslAllTrustingClient().newWebSocket(localhost, listener));
		webSocketClient.connect();
		webSocketClient.getState()
			.filter(WebSocketStatus.CONNECTED::equals)
			.blockingFirst();
		this.jsonRpcClient = new RadixJsonRpcClient(webSocketClient);
	}

	@Test
	public void when_submitting_an_atom_with_one_down_of_same_consumable_within_a_group__then_atom_is_accepted() {
		RadixAddress myAddress = this.universe.getAddressFrom(this.identity.getPublicKey());
		TokenDefinitionParticle tokenDefinition = createTokenDefinition(myAddress);
		UnallocatedTokensParticle unallocatedTokens = createUnallocatedTokens(tokenDefinition);
		TransferrableTokensParticle mintedTokens = createTransferrableTokens(myAddress, tokenDefinition, unallocatedTokens.getAmount());
		ParticleGroup definitionGroup = ParticleGroup.of(
			SpunParticle.up(tokenDefinition),
			SpunParticle.up(unallocatedTokens)
		);
		ParticleGroup mintGroup = ParticleGroup.of(
			SpunParticle.down(unallocatedTokens),
			SpunParticle.up(mintedTokens)
		);
		TransferrableTokensParticle output = createTransferrableTokens(myAddress, tokenDefinition, mintedTokens.getAmount());

		ParticleGroup duplicateTransitionsGroup = ParticleGroup.of(
			SpunParticle.down(mintedTokens),
			SpunParticle.up(output)
		);

		TestObserver<RadixJsonRpcClient.NodeAtomSubmissionUpdate> result = submitAtom(Arrays.asList(
			definitionGroup,
			mintGroup,
			duplicateTransitionsGroup
		));
		result.awaitTerminalEvent(5, TimeUnit.SECONDS);
		result.assertNoErrors()
			.assertComplete()
			.assertValueAt(1, state
				-> state.getState() == RadixJsonRpcClient.NodeAtomSubmissionState.STORED);
	}

	@Test
	public void when_submitting_an_atom_with_two_downs_of_same_consumable_within_a_group__then_atom_is_rejected() {
		RadixAddress myAddress = this.universe.getAddressFrom(this.identity.getPublicKey());
		TokenDefinitionParticle tokenDefinition = createTokenDefinition(myAddress);
		UnallocatedTokensParticle unallocatedTokens = createUnallocatedTokens(tokenDefinition);
		TransferrableTokensParticle mintedTokens = createTransferrableTokens(myAddress, tokenDefinition, unallocatedTokens.getAmount());
		ParticleGroup definitionGroup = ParticleGroup.of(
			SpunParticle.up(tokenDefinition),
			SpunParticle.up(unallocatedTokens)
		);
		ParticleGroup mintGroup = ParticleGroup.of(
			SpunParticle.down(unallocatedTokens),
			SpunParticle.up(mintedTokens)
		);
		TransferrableTokensParticle output = createTransferrableTokens(myAddress, tokenDefinition, mintedTokens.getAmount().multiply(UInt256.TWO));

		ParticleGroup duplicateTransitionsGroup = ParticleGroup.of(
			SpunParticle.down(mintedTokens),
			SpunParticle.down(mintedTokens),
			SpunParticle.up(output)
		);

		TestObserver<RadixJsonRpcClient.NodeAtomSubmissionUpdate> result = submitAtom(Arrays.asList(
			definitionGroup,
			mintGroup,
			duplicateTransitionsGroup
		));
		result.awaitTerminalEvent(5, TimeUnit.SECONDS);
		result.assertNoErrors()
			.assertComplete()
			.assertValueAt(1, state
				-> state.getState() == RadixJsonRpcClient.NodeAtomSubmissionState.VALIDATION_ERROR
					&& state.getData().toString().contains("in group 2: [0, 1]"));
	}

	@Test
	public void when_submitting_an_atom_with_three_downs_of_same_consumable_within_a_group__then_atom_is_rejected() {
		RadixAddress myAddress = this.universe.getAddressFrom(this.identity.getPublicKey());
		TokenDefinitionParticle tokenDefinition = createTokenDefinition(myAddress);
		UnallocatedTokensParticle unallocatedTokens = createUnallocatedTokens(tokenDefinition);
		TransferrableTokensParticle mintedTokens = createTransferrableTokens(myAddress, tokenDefinition, unallocatedTokens.getAmount());
		ParticleGroup definitionGroup = ParticleGroup.of(
			SpunParticle.up(tokenDefinition),
			SpunParticle.up(unallocatedTokens)
		);
		ParticleGroup mintGroup = ParticleGroup.of(
			SpunParticle.down(unallocatedTokens),
			SpunParticle.up(mintedTokens)
		);
		TransferrableTokensParticle output = createTransferrableTokens(myAddress, tokenDefinition, mintedTokens.getAmount().multiply(UInt256.THREE));

		ParticleGroup duplicateTransitionsGroup = ParticleGroup.of(
			SpunParticle.down(mintedTokens),
			SpunParticle.down(mintedTokens),
			SpunParticle.down(mintedTokens),
			SpunParticle.up(output)
		);

		TestObserver<RadixJsonRpcClient.NodeAtomSubmissionUpdate> result = submitAtom(Arrays.asList(
			definitionGroup,
			mintGroup,
			duplicateTransitionsGroup
		));
		result.awaitTerminalEvent(5, TimeUnit.SECONDS);
		result.assertNoErrors()
			.assertComplete()
			.assertValueAt(1, state
				-> state.getState() == RadixJsonRpcClient.NodeAtomSubmissionState.VALIDATION_ERROR
					&& state.getData().toString().contains("in group 2: [0, 1, 2]"));
	}

	private TransferrableTokensParticle createTransferrableTokens(RadixAddress myAddress, TokenDefinitionParticle tokenDefinition, UInt256 amount) {
		return new TransferrableTokensParticle(
			amount,
			UInt256.ONE,
			myAddress,
			System.nanoTime(),
			tokenDefinition.getTokenDefinitionReference(),
			System.currentTimeMillis() / 60000L + 60000L,
			tokenDefinition.getTokenPermissions()
		);
	}

	public void when_submitting_an_atom_with_two_ups_of_same_consumable_within_a_group__then_atom_is_rejected() {
		RadixAddress myAddress = this.universe.getAddressFrom(this.identity.getPublicKey());
		TokenDefinitionParticle tokenDefinition = createTokenDefinition(myAddress);
		UnallocatedTokensParticle unallocatedTokens = createUnallocatedTokens(tokenDefinition);
		TransferrableTokensParticle mintedTokens = createTransferrableTokens(myAddress, tokenDefinition, unallocatedTokens.getAmount());
		ParticleGroup definitionGroup = ParticleGroup.of(
			SpunParticle.up(tokenDefinition),
			SpunParticle.up(unallocatedTokens)
		);
		ParticleGroup mintGroup = ParticleGroup.of(
			SpunParticle.down(unallocatedTokens),
			SpunParticle.up(mintedTokens)
		);
		TransferrableTokensParticle output = createTransferrableTokens(myAddress, tokenDefinition, mintedTokens.getAmount().divide(UInt256.TWO));

		ParticleGroup duplicateTransitionsGroup = ParticleGroup.of(
			SpunParticle.down(mintedTokens),
			SpunParticle.up(output),
			SpunParticle.up(output)
		);

		TestObserver<RadixJsonRpcClient.NodeAtomSubmissionUpdate> result = submitAtom(Arrays.asList(
			definitionGroup,
			mintGroup,
			duplicateTransitionsGroup
		));
		result.awaitTerminalEvent(5, TimeUnit.SECONDS);
		result.assertNoErrors()
			.assertComplete()
			.assertValueAt(1, state
				-> state.getState() == RadixJsonRpcClient.NodeAtomSubmissionState.VALIDATION_ERROR
				&& state.getData().toString().contains("in group 2: [1, 2]"));
	}

	private UnallocatedTokensParticle createUnallocatedTokens(TokenDefinitionParticle tokenDefinition) {
		return new UnallocatedTokensParticle(
			TokenUnitConversions.unitsToSubunits(100),
			UInt256.ONE,
			System.nanoTime(),
			tokenDefinition.getTokenDefinitionReference(),
			tokenDefinition.getTokenPermissions()
		);
	}

	private TokenDefinitionParticle createTokenDefinition(RadixAddress myAddress) {
		return new TokenDefinitionParticle(
			myAddress,
			"Cookie Token",
			"FLO",
			"Cookies!",
			UInt256.ONE,
			ImmutableMap.of(
				TokenTransition.MINT, TokenPermission.TOKEN_OWNER_ONLY,
				TokenTransition.BURN, TokenPermission.TOKEN_OWNER_ONLY
			),
			null
		);
	}

	private TestObserver<RadixJsonRpcClient.NodeAtomSubmissionUpdate> submitAtom(List<ParticleGroup> particleGroups) {
		Map<String, String> atomMetaData = new HashMap<>();
		atomMetaData.put("timestamp", String.valueOf(System.currentTimeMillis()));
		atomMetaData.putAll(feeMapper.map(new Atom(particleGroups, atomMetaData), universe, this.identity.getPublicKey()).getFirst());

		UnsignedAtom unsignedAtom = new UnsignedAtom(new Atom(particleGroups, atomMetaData));
		// Sign and submit
		Atom signedAtom = this.identity.sign(unsignedAtom).blockingGet();

		TestObserver<RadixJsonRpcClient.NodeAtomSubmissionUpdate> observer = TestObserver.create();
		jsonRpcClient.submitAtom(signedAtom).subscribe(observer);

		return observer;
	}
}
