package com.radix.acceptance.atomic_transactions_with_dependence;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.radix.acceptance.SpecificProperties;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.tokens.AtomToTokenTransfersMapper;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction;
import com.radixdlt.client.application.translate.tokens.CreateTokenToParticleGroupsMapper;
import com.radixdlt.client.application.translate.tokens.MintAndTransferTokensAction;
import com.radixdlt.client.application.translate.tokens.MintAndTransferTokensActionMapper;
import com.radixdlt.client.application.translate.tokens.TokenBalanceReducer;
import com.radixdlt.client.application.translate.tokens.TokenDefinitionReference;
import com.radixdlt.client.application.translate.tokens.TokenDefinitionsReducer;
import com.radixdlt.client.application.translate.tokens.TransferTokensToParticleGroupsMapper;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.core.network.actions.SubmitAtomReceivedAction;
import com.radixdlt.client.core.network.actions.SubmitAtomRequestAction;
import com.radixdlt.client.core.network.actions.SubmitAtomResultAction;
import com.radixdlt.client.core.network.actions.SubmitAtomResultAction.SubmitAtomResultActionType;
import com.radixdlt.client.core.network.actions.SubmitAtomSendAction;
import cucumber.api.java.After;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import io.reactivex.disposables.Disposable;
import io.reactivex.observers.BaseTestConsumer.TestWaitStrategy;
import io.reactivex.observers.TestObserver;
import org.radix.utils.UInt256;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.radixdlt.client.core.network.actions.SubmitAtomResultAction.SubmitAtomResultActionType.COLLISION;
import static com.radixdlt.client.core.network.actions.SubmitAtomResultAction.SubmitAtomResultActionType.STORED;
import static com.radixdlt.client.core.network.actions.SubmitAtomResultAction.SubmitAtomResultActionType.VALIDATION_ERROR;
import static org.junit.Assert.assertEquals;

/**
 * See <a href="https://radixdlt.atlassian.net/browse/RLAU-645">RLAU-645</a>.
 */
public class AtomicTransactionsWithDependence {
	static {
		if (!RadixUniverse.isInstantiated()) {
			RadixUniverse.bootstrap(Bootstrap.BETANET);
		}
	}

	private static final String NAME = "name";
	private static final String SYMBOL = "symbol";
	private static final String DESCRIPTION = "description";
	private static final String INITIAL_SUPPLY = "initialSupply";
	private static final String NEW_SUPPLY = "newSupply";
	private static final String GRANULARITY = "granularity";

	private static final long TIMEOUT_MS = 10_000L; // Timeout in milliseconds

	private RadixApplicationAPI api;
	private RadixIdentity identity;
	private final SpecificProperties properties = SpecificProperties.of(
		NAME,           "RLAU-40 Test token",
		SYMBOL,			"RLAU",
		DESCRIPTION,	"RLAU-40 Test token",
		INITIAL_SUPPLY,	scaledToUnscaled(1000000000),
		NEW_SUPPLY,		scaledToUnscaled(1000000000),
		GRANULARITY,	"1"
	);
	private final List<TestObserver<Object>> observers = Lists.newArrayList();
	private final List<Disposable> disposables = Lists.newArrayList();

	@Given("^I have access to a suitable Radix network$")
	public void i_have_access_to_a_suitable_Radix_network() {
		this.identity = RadixIdentities.createNew();
		this.api = RadixApplicationAPI.create(this.identity);
		this.disposables.add(this.api.pull());

		// Reset data
		this.properties.clear();
		this.observers.clear();
	}

	private void mintAndTransferTokensWith(MintAndTransferTokensActionMapper actionMapper) {
		RadixApplicationAPI api = new RadixApplicationAPI.RadixApplicationAPIBuilder()
			.defaultFeeMapper()
			.addStatelessParticlesMapper(new CreateTokenToParticleGroupsMapper())
			.addStatefulParticlesMapper(actionMapper)
			.addStatefulParticlesMapper(new TransferTokensToParticleGroupsMapper(RadixUniverse.getInstance()))
			.addReducer(new TokenDefinitionsReducer())
			.addReducer(new TokenBalanceReducer())
			.addAtomMapper(new AtomToTokenTransfersMapper(RadixUniverse.getInstance()))
			.identity(RadixIdentities.createNew())
			.build();

		this.properties.put(SYMBOL, "TEST0");
		createToken(CreateTokenAction.TokenSupplyType.MUTABLE, api);
		TokenDefinitionReference tokenRef = TokenDefinitionReference.of(api.getMyAddress(), "TEST0");
		i_can_observe_atom_being_accepted(1);
		this.observers.clear();

		RadixIdentity toIdentity = RadixIdentities.createNew();
		RadixAddress toAddress = RadixUniverse.getInstance().getAddressFrom(toIdentity.getPublicKey());
		TestObserver observer = new TestObserver();
		api.execute(new MintAndTransferTokensAction(tokenRef, BigDecimal.TEN, toAddress, ImmutableMap.of()))
			.toObservable()
			.subscribe(observer);
		observers.add(observer);
	}

	@When("^I submit a particle group spending a consumable that was created in a group with a lower index$")
	public void iSubmitAParticleGroupSpendingAConsumableThatWasCreatedInAGroupWithALowerIndex() {
		mintAndTransferTokensWith(new MintAndTransferTokensActionMapper()); // correct behaviour is the default behaviour
	}

	@When("^I submit a particle group spending a consumable that was created in same group$")
	public void iSubmitAParticleGroupSpendingAConsumableThatWasCreatedInSameGroup() {
		mintAndTransferTokensWith(new MintAndTransferTokensActionMapper((mint, transfer) -> Collections.singletonList(
			ParticleGroup.of(SpunParticle.up(mint), SpunParticle.down(mint), SpunParticle.up(transfer)))
		));
	}

	@When("^I submit a particle group spending a consumable that was created in a group with a higher index$")
	public void iSubmitAParticleGroupSpendingAConsumableThatWasCreatedInAGroupWithAHigherIndex() {
		mintAndTransferTokensWith(new MintAndTransferTokensActionMapper((mint, transfer) -> Arrays.asList(
			ParticleGroup.of(SpunParticle.down(mint), SpunParticle.up(transfer)),
			ParticleGroup.of(SpunParticle.up(mint)))
		));
	}

	@Then("^I can observe atom (\\d+) being accepted$")
	public void i_can_observe_atom_being_accepted(int atomNumber) {
		awaitAtomStatus(atomNumber, STORED);
	}

	@Then("^I can observe the atom being rejected with a validation error$")
	public void i_can_observe_the_atom_being_rejected_as_a_validation_error() {
		// "the atom" = most recent atom
		i_can_observe_atom_being_rejected_as_a_validation_error(observers.size());
	}

	@Then("^I can observe atom (\\d+) being rejected with a validation error$")
	public void i_can_observe_atom_being_rejected_as_a_validation_error(int atomNumber) {
		awaitAtomStatus(atomNumber, VALIDATION_ERROR);
	}

	@After
	public void after() {
		this.disposables.forEach(Disposable::dispose);
		this.disposables.clear();
	}

	private void createToken(CreateTokenAction.TokenSupplyType tokenCreateSupplyType, RadixApplicationAPI api) {
		TestObserver<Object> observer = new TestObserver<>();
		api.createToken(
				this.properties.get(NAME),
				this.properties.get(SYMBOL),
				this.properties.get(DESCRIPTION),
				UInt256.from(this.properties.get(INITIAL_SUPPLY)),
				UInt256.from(this.properties.get(GRANULARITY)),
				tokenCreateSupplyType)
			.toObservable()
			.doOnNext(System.out::println)
			.subscribe(observer);
		observers.add(observer);
	}

	private void awaitAtomStatus(int atomNumber, SubmitAtomResultActionType... finalStates) {
		ImmutableSet<SubmitAtomResultActionType> finalStatesSet = ImmutableSet.<SubmitAtomResultActionType>builder()
			.addAll(Arrays.asList(finalStates))
			.build();

		this.observers.get(atomNumber - 1)
			.awaitCount(4, TestWaitStrategy.SLEEP_100MS, TIMEOUT_MS)
			.assertNoErrors()
			.assertNoTimeout()
			.assertValueAt(0, SubmitAtomRequestAction.class::isInstance)
			.assertValueAt(1, SubmitAtomSendAction.class::isInstance)
			.assertValueAt(2, SubmitAtomReceivedAction.class::isInstance)
			.assertValueAt(3, SubmitAtomResultAction.class::isInstance)
			.assertValueAt(3, i -> finalStatesSet.contains(SubmitAtomResultAction.class.cast(i).getType()));
	}

	private static String scaledToUnscaled(int amount) {
		return TokenDefinitionReference.unitsToSubunits(amount).toString();
	}
}
