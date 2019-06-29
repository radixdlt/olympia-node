package com.radix.regression;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.FeeMapper;
import com.radixdlt.client.application.translate.PowFeeMapper;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.atommodel.rri.RRIParticle;
import com.radixdlt.client.atommodel.tokens.TokenDefinitionParticle;
import com.radixdlt.client.atommodel.tokens.TokenDefinitionParticle.TokenTransition;
import com.radixdlt.client.atommodel.tokens.TokenPermission;
import com.radixdlt.client.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.client.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.BootstrapConfig;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.AtomStatus;
import com.radixdlt.client.core.atoms.AtomStatusNotification;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.UnsignedAtom;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.core.network.HttpClients;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient;
import com.radixdlt.client.core.network.websocket.WebSocketClient;
import com.radixdlt.client.core.network.websocket.WebSocketStatus;
import com.radixdlt.client.core.pow.ProofOfWorkBuilder;
import io.reactivex.observers.TestObserver;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import org.radix.utils.UInt256;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MultipleTransitionsInSameGroupTest {
	private static final BootstrapConfig BOOTSTRAP_CONFIG;
	static {
		String bootstrapConfigName = System.getenv("RADIX_BOOTSTRAP_CONFIG");
		if (bootstrapConfigName != null) {
			BOOTSTRAP_CONFIG = Bootstrap.valueOf(bootstrapConfigName);
		} else {
			BOOTSTRAP_CONFIG = Bootstrap.LOCALHOST_SINGLENODE;
		}
	}

	private RadixUniverse universe = RadixUniverse.create(BOOTSTRAP_CONFIG);
	private RadixIdentity identity;
	private FeeMapper feeMapper = new PowFeeMapper(Atom::getHash, new ProofOfWorkBuilder());
	private RadixJsonRpcClient jsonRpcClient;

	@Before
	public void setUp() {
		this.identity = RadixIdentities.createNew();
		RadixApplicationAPI api = RadixApplicationAPI.create(BOOTSTRAP_CONFIG, this.identity);
		api.discoverNodes();
		RadixNode node = api.getNetworkState()
			.filter(state -> !state.getNodes().isEmpty())
			.map(state -> state.getNodes().keySet().iterator().next())
			.blockingFirst();

		WebSocketClient webSocketClient = new WebSocketClient(listener ->
			HttpClients.getSslAllTrustingClient().newWebSocket(node.getWebSocketEndpoint(), listener)
		);
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
			SpunParticle.down(new RRIParticle(tokenDefinition.getRRI())),
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

		TestObserver<AtomStatusNotification> result = submitAtom(Arrays.asList(
			definitionGroup,
			mintGroup,
			duplicateTransitionsGroup
		));

		result.awaitCount(1);
		result.assertValue(n -> n.getAtomStatus() == AtomStatus.STORED);
		result.dispose();
	}

	@Test
	public void when_submitting_an_atom_with_two_downs_of_same_consumable_within_a_group__then_atom_is_rejected() {
		RadixAddress myAddress = this.universe.getAddressFrom(this.identity.getPublicKey());
		TokenDefinitionParticle tokenDefinition = createTokenDefinition(myAddress);
		UnallocatedTokensParticle unallocatedTokens = createUnallocatedTokens(tokenDefinition);
		TransferrableTokensParticle mintedTokens = createTransferrableTokens(myAddress, tokenDefinition, unallocatedTokens.getAmount());
		ParticleGroup definitionGroup = ParticleGroup.of(
			SpunParticle.down(new RRIParticle(tokenDefinition.getRRI())),
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

		TestObserver<AtomStatusNotification> result = submitAtom(Arrays.asList(
			definitionGroup,
			mintGroup,
			duplicateTransitionsGroup
		));

		result.awaitCount(1);
		result.assertValue(n -> n.getAtomStatus() == AtomStatus.EVICTED_FAILED_CM_VERIFICATION);
		result.dispose();
	}

	@Test
	public void when_submitting_an_atom_with_three_downs_of_same_consumable_within_a_group__then_atom_is_rejected() {
		RadixAddress myAddress = this.universe.getAddressFrom(this.identity.getPublicKey());
		TokenDefinitionParticle tokenDefinition = createTokenDefinition(myAddress);
		UnallocatedTokensParticle unallocatedTokens = createUnallocatedTokens(tokenDefinition);
		TransferrableTokensParticle mintedTokens = createTransferrableTokens(myAddress, tokenDefinition, unallocatedTokens.getAmount());
		ParticleGroup definitionGroup = ParticleGroup.of(
			SpunParticle.down(new RRIParticle(tokenDefinition.getRRI())),
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

		TestObserver<AtomStatusNotification> result = submitAtom(Arrays.asList(
			definitionGroup,
			mintGroup,
			duplicateTransitionsGroup
		));

		result.awaitCount(1);
		result.assertValue(n -> n.getAtomStatus() == AtomStatus.EVICTED_FAILED_CM_VERIFICATION);
		result.dispose();
	}

	private TransferrableTokensParticle createTransferrableTokens(RadixAddress myAddress, TokenDefinitionParticle tokenDefinition, UInt256 amount) {
		return new TransferrableTokensParticle(
			amount,
			UInt256.ONE,
			myAddress,
			System.nanoTime(),
			tokenDefinition.getRRI(),
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
			SpunParticle.down(new RRIParticle(tokenDefinition.getRRI())),
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

		TestObserver<AtomStatusNotification> result = submitAtom(Arrays.asList(
			definitionGroup,
			mintGroup,
			duplicateTransitionsGroup
		));
		result.awaitCount(1);
		result.assertValue(n -> n.getAtomStatus() == AtomStatus.EVICTED_FAILED_CM_VERIFICATION);
		result.dispose();
	}

	private UnallocatedTokensParticle createUnallocatedTokens(TokenDefinitionParticle tokenDefinition) {
		return new UnallocatedTokensParticle(
			UInt256.MAX_VALUE,
			UInt256.ONE,
			System.nanoTime(),
			tokenDefinition.getRRI(),
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

	private TestObserver<AtomStatusNotification> submitAtom(List<ParticleGroup> particleGroups) {
		Map<String, String> atomMetaData = new HashMap<>();
		atomMetaData.put("timestamp", String.valueOf(System.currentTimeMillis()));
		atomMetaData.putAll(feeMapper.map(new Atom(particleGroups, atomMetaData), universe, this.identity.getPublicKey()).getFirst());

		UnsignedAtom unsignedAtom = new UnsignedAtom(new Atom(particleGroups, atomMetaData));
		// Sign and submit
		Atom signedAtom = this.identity.sign(unsignedAtom).blockingGet();

		TestObserver<AtomStatusNotification> observer = TestObserver.create();

		final String subscriberId = UUID.randomUUID().toString();
		this.jsonRpcClient.observeAtomStatusNotifications(subscriberId).subscribe(observer);
		this.jsonRpcClient.sendGetAtomStatusNotifications(subscriberId, signedAtom.getAid()).blockingAwait();
		this.jsonRpcClient.pushAtom(signedAtom).blockingAwait();

		return observer;
	}
}
