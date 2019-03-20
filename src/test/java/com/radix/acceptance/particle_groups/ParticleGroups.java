package com.radix.acceptance.particle_groups;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.radix.acceptance.SpecificProperties;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.ApplicationState;
import com.radixdlt.client.application.translate.StatefulActionToParticleGroupsMapper;
import com.radixdlt.client.application.translate.StatelessActionToParticleGroupsMapper;
import com.radixdlt.client.application.translate.atomic.AtomicAction;
import com.radixdlt.client.application.translate.data.SendMessageAction;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction;
import com.radixdlt.client.application.translate.tokens.TokenDefinitionReference;
import com.radixdlt.client.application.translate.tokens.TokenUnitConversions;
import com.radixdlt.client.application.translate.tokens.TransferTokensAction;
import com.radixdlt.client.application.translate.tokens.TransferTokensToParticleGroupsMapper;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.crypto.ECKeyPairGenerator;
import com.radixdlt.client.core.network.actions.SubmitAtomReceivedAction;
import com.radixdlt.client.core.network.actions.SubmitAtomRequestAction;
import com.radixdlt.client.core.network.actions.SubmitAtomResultAction;
import com.radixdlt.client.core.network.actions.SubmitAtomResultAction.SubmitAtomResultActionType;
import com.radixdlt.client.core.network.actions.SubmitAtomSendAction;
import cucumber.api.java.After;
import cucumber.api.java.en.And;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.observers.BaseTestConsumer.TestWaitStrategy;
import io.reactivex.observers.TestObserver;
import org.radix.utils.UInt256;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.radixdlt.client.core.network.actions.SubmitAtomResultAction.SubmitAtomResultActionType.COLLISION;
import static com.radixdlt.client.core.network.actions.SubmitAtomResultAction.SubmitAtomResultActionType.STORED;
import static com.radixdlt.client.core.network.actions.SubmitAtomResultAction.SubmitAtomResultActionType.VALIDATION_ERROR;
import static org.junit.Assert.assertEquals;

/**
 * See <a href="https://radixdlt.atlassian.net/browse/RLAU-326">RLAU-326</a>.
 */
public class ParticleGroups {
	static {
		if (!RadixUniverse.isInstantiated()) {
			RadixUniverse.bootstrap(Bootstrap.BETANET);
		}
	}

	private static final String NAME = "name";
	private static final String SYMBOL = "symbol";
	private static final String DESCRIPTION = "description";
	private static final String TOTAL_SUPPLY = "totalSupply";
	private static final String GRANULARITY = "granularity";

	private static final long TIMEOUT_MS = 10_000L; // Timeout in milliseconds

	private RadixApplicationAPI api;
	private RadixIdentity identity;
	private final SpecificProperties properties1 = SpecificProperties.of(
			NAME,           "RLAU-40 Test token",
			SYMBOL,			"RLAU",
			DESCRIPTION,	"RLAU-40 Test token",
			TOTAL_SUPPLY,	"1000000000",
			GRANULARITY,	"1"
	);
	private final SpecificProperties properties2 = SpecificProperties.of(
			NAME,           "RLAU-40 Test token",
			SYMBOL,			"RLAU",
			DESCRIPTION,	"RLAU-40 Test token",
			TOTAL_SUPPLY,	"1000000000",
			GRANULARITY,	"1"
	);
	private final List<TestObserver<Object>> observers = Lists.newArrayList();
	private final List<Disposable> disposables = Lists.newArrayList();
	private ECKeyPairGenerator ecKeyPairGenerator = ECKeyPairGenerator.newInstance();

	private class CreateEmptyGroupAction implements Action {

	}
	private class CreateEmptyGroupActionToParticleGroupsMapper implements StatelessActionToParticleGroupsMapper {

		@Override
		public Observable<ParticleGroup> mapToParticleGroups(Action action) {
			if (!(action instanceof CreateEmptyGroupAction)) {
				return Observable.empty();
			}

			return Observable.just(new ParticleGroup(Collections.emptyList()));
		}
	}

	private class MergeAction implements Action {
		private final Action[] actions;
		private MergeAction(Action... actions) {
			this.actions = actions;
		}
	}

	private class MergeStatefulActionToParticleGroupsMapper implements StatefulActionToParticleGroupsMapper {
		private final StatefulActionToParticleGroupsMapper[] mappers;
		private MergeStatefulActionToParticleGroupsMapper(StatefulActionToParticleGroupsMapper... mappers) {
			this.mappers = mappers;
		}

		@Override
		public Observable<RequiredShardState> requiredState(Action action) {
			return Observable.fromArray(mappers)
				.flatMap(mapper -> mapper.requiredState(action));
		}

		@Override
		public Observable<ParticleGroup> mapToParticleGroups(Action action, Observable<Observable<? extends ApplicationState>> store) {
			if (!(action instanceof MergeAction)) {
				return Observable.empty();
			}

			return Observable.fromArray(((MergeAction) action).actions)
				.flatMap(a -> Observable.fromArray(this.mappers)
					.flatMap(mapper -> {
						final Observable<Observable<? extends ApplicationState>> context =
							mapper.requiredState(a).map(ctx -> api.getState(ctx.stateClass(), ctx.address()));
						return mapper.mapToParticleGroups(a, context);
					}))
				.toList()
				.map(particleGroups -> particleGroups.stream().flatMap(ParticleGroup::spunParticles).collect(Collectors.toList()))
				.map(ParticleGroup::new)
				.toObservable();
		}
	}

	@Given("^I have access to a suitable Radix network$")
	public void i_have_access_to_a_suitable_Radix_network() {
		this.identity = RadixIdentities.createNew();
		this.api = RadixApplicationAPI.createDefaultBuilder()
			.identity(this.identity)
			.addStatelessParticlesMapper(new CreateEmptyGroupActionToParticleGroupsMapper())
			.addStatefulParticlesMapper(new MergeStatefulActionToParticleGroupsMapper(new TransferTokensToParticleGroupsMapper(RadixUniverse.getInstance())))
			.build();
		this.disposables.add(this.api.pull());

		// Reset data
		this.properties1.clear();
		this.properties2.clear();
		this.observers.clear();
	}

	@And("^I submit one token transfer requests of (\\d+) for \"([^\"]*)\" and of (\\d+) for \"([^\"]*)\" in one particle group$")
	public void i_submit_one_token_transfer_requests_of_for_and_of_for_in_one_particle_group(int amount1, String symbol1, int amount2, String symbol2) throws Throwable {
		MergeAction doubleSpendAction = new MergeAction(
			buildTransferTokenAction(symbol1, amount1),
			buildTransferTokenAction(symbol2, amount2)
		);

		createAtomic(doubleSpendAction);
	}

	@When("^I submit a fixed-supply token-creation request "
			+ "with name \"([^\"]*)\", symbol \"([^\"]*)\", totalSupply (\\d+) scaled and granularity (\\d+) scaled$")
	public void i_submit_a_fixed_supply_token_creation_request_with_name_symbol_totalSupply_scaled_and_granularity_scaled(
			String name, String symbol, int totalSupply, int granularity) {
		this.properties1.put(NAME, name);
		this.properties1.put(SYMBOL, symbol);
		this.properties1.put(TOTAL_SUPPLY, Long.toString(totalSupply));
		this.properties1.put(GRANULARITY, Long.toString(granularity));
		createToken(CreateTokenAction.TokenSupplyType.FIXED);
	}

	@When("^I submit two fixed-supply token-creation requests with symbol \"([^\"]*)\" and in separate particle groups$")
	public void i_submit_two_fixed_supply_token_creation_request_with_symbol_totalSupply_scaled_scaled(String symbol) {
		this.properties1.put(SYMBOL, symbol);
		this.properties2.put(SYMBOL, symbol);
		CreateTokenAction.TokenSupplyType tokenSupplyType = CreateTokenAction.TokenSupplyType.FIXED;

		createTwoTokens(tokenSupplyType);
	}

	@When("^I submit two fixed-supply token-creation requests with symbol \"([^\"]*)\" and \"([^\"]*)\" and in separate particle groups$")
	public void i_submit_two_fixed_supply_token_creation_request_with_symbol_and_totalSupply_scaled_scaled(String symbol1, String symbol2) {
		this.properties1.put(SYMBOL, symbol1);
		this.properties2.put(SYMBOL, symbol2);
		CreateTokenAction.TokenSupplyType tokenSupplyType = CreateTokenAction.TokenSupplyType.FIXED;

		createTwoTokens(tokenSupplyType);
	}

	@And("^I submit two token transfer requests of (\\d+) for \"([^\"]*)\" in separate particle groups$")
	public void i_submit_two_token_transfer_requests_of_for_in_separate_particle_groups(int amount, String symbol) {
		// this works because client does not check for double spends right now
		createAtomic(
			buildTransferTokenAction(symbol, 100L),
			buildTransferTokenAction(symbol, 100L)
		);
	}

	@When("^I submit an arbitrary atom with an empty particle group$")
	public void i_submit_an_arbitrary_atom_with_an_empty_particle_group() {
		createAtomic(new SendMessageAction("Hello!".getBytes(), this.api.getMyAddress(), this.api.getMyAddress(), false),
			new CreateEmptyGroupAction());
	}

	private void createTwoTokens(CreateTokenAction.TokenSupplyType tokenSupplyType) {
		createAtomic(buildCreateTokenAction(this.properties1, tokenSupplyType),
			buildCreateTokenAction(this.properties2, tokenSupplyType));
	}

	private void createAtomic(Action... actions) {
		TestObserver<Object> observer = new TestObserver<>();

		this.api.execute(new AtomicAction(
			actions
		))
			.toObservable()
			.doOnNext(System.out::println)
			.subscribe(observer);

		this.observers.add(observer);
	}

	private CreateTokenAction buildCreateTokenAction(SpecificProperties properties, CreateTokenAction.TokenSupplyType tokenSupplyType) {
		return CreateTokenAction.create(
			this.api.getMyAddress(),
			properties.get(NAME),
			properties.get(SYMBOL),
			properties.get(DESCRIPTION),
			BigDecimal.valueOf(Long.valueOf(properties.get(TOTAL_SUPPLY))),
			BigDecimal.valueOf(Long.valueOf(properties.get(GRANULARITY))),
			tokenSupplyType);
	}

	private TransferTokensAction buildTransferTokenAction(String symbol, long amount) {
		return TransferTokensAction.create(
			api.getMyAddress(),
			new RadixAddress(RadixUniverse.getInstance().getConfig(), ecKeyPairGenerator.generateKeyPair().getPublicKey()),
			BigDecimal.valueOf(amount),
			TokenDefinitionReference.of(api.getMyAddress(), symbol)
		);
	}

	@When("^I submit a fixed-supply token-creation request with symbol \"([^\"]*)\" and totalSupply (\\d+) scaled$")
	public void i_submit_a_fixed_supply_token_creation_request_with_symbol_totalSupply(String symbol, int totalSupply) {
		this.properties1.put(SYMBOL, symbol);
		this.properties1.put(TOTAL_SUPPLY, Long.toString(totalSupply));
		createToken(CreateTokenAction.TokenSupplyType.FIXED);
	}

	@When("^I submit a fixed-supply token-creation request with granularity (\\d+) scaled$")
	public void i_submit_a_fixed_supply_token_creation_request_with_granularity(int granularity) {
		this.properties1.put(GRANULARITY, Long.toString(granularity));
		createToken(CreateTokenAction.TokenSupplyType.FIXED);
	}

	@When("^I submit a fixed-supply token-creation request with granularity 0$")
	public void i_submit_a_fixed_supply_token_creation_request_with_granularity_0() {
		this.properties1.put(GRANULARITY, "0");
		createToken(CreateTokenAction.TokenSupplyType.FIXED);
	}

	@When("^I submit a fixed-supply token-creation request with symbol \"([^\"]*)\"$")
	public void i_submit_a_fixed_supply_token_creation_request_with_symbol(String symbol) {
		this.properties1.put(SYMBOL, symbol);
		createToken(CreateTokenAction.TokenSupplyType.FIXED);
	}

	@When("^I submit a token transfer request of (\\d+) scaled for \"([^\"]*)\" to an arbitrary account$")
	public void i_submit_a_token_transfer_request_of_for_to_an_arbitrary_account(int count, String symbol) {
		TokenDefinitionReference tokenClass = TokenDefinitionReference.of(api.getMyAddress(), symbol);
		RadixAddress arbitrary = RadixUniverse.getInstance().getAddressFrom(RadixIdentities.createNew().getPublicKey());
		// Ensure balance is up-to-date.
		api.getBalance(api.getMyAddress(), tokenClass)
			.firstOrError()
			.blockingGet();

		TestObserver<Object> observer = new TestObserver<>();
		api.transferTokens(api.getMyAddress(), arbitrary, BigDecimal.valueOf(count), tokenClass)
			.toObservable()
			.doOnNext(System.out::println)
			.subscribe(observer);
		this.observers.add(observer);
	}

	@When("^I observe the atom being accepted$")
	public void i_observe_the_atom_being_accepted() {
		// "the atom" = most recent atom
		i_can_observe_atom_being_accepted(observers.size());
	}

	@Then("^I can observe the atom being accepted$")
	public void i_can_observe_the_atom_being_accepted() {
		// "the atom" = most recent atom
		i_can_observe_atom_being_accepted(observers.size());
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

	@Then("^I can observe the atom being rejected with an error$")
	public void i_can_observe_atom_being_rejected_with_an_error() {
		// "the atom" = most recent atom
		i_can_observe_atom_being_rejected_with_an_error(observers.size());
	}

	@Then("^I can observe atom (\\d+) being rejected with an error$")
	public void i_can_observe_atom_being_rejected_with_an_error(int atomNumber) {
		awaitAtomStatus(atomNumber, COLLISION, VALIDATION_ERROR);
	}

	@Then("^I can observe token \"([^\"]*)\" balance equal to (\\d+) scaled$")
	public void i_can_observe_token_balance_equal_to_scaled(String symbol, int balance) {
		TokenDefinitionReference tokenClass = TokenDefinitionReference.of(api.getMyAddress(), symbol);
		// Ensure balance is up-to-date.
		BigDecimal tokenBalanceDecimal = api.getBalance(api.getMyAddress(), tokenClass)
			.firstOrError()
			.blockingGet();
		UInt256 tokenBalance = TokenUnitConversions.unitsToSubunits(tokenBalanceDecimal);
		UInt256 requiredBalance = TokenUnitConversions.unitsToSubunits(balance);
		assertEquals(requiredBalance, tokenBalance);
	}

	@After
	public void after() {
		this.disposables.forEach(Disposable::dispose);
		this.disposables.clear();
	}

	private void createToken(CreateTokenAction.TokenSupplyType tokenCreateSupplyType) {
		TestObserver<Object> observer = new TestObserver<>();
		api.createToken(
				this.properties1.get(NAME),
				this.properties1.get(SYMBOL),
				this.properties1.get(DESCRIPTION),
				BigDecimal.valueOf(Long.valueOf(this.properties1.get(TOTAL_SUPPLY))),
				BigDecimal.valueOf(Long.valueOf(this.properties1.get(GRANULARITY))),
				tokenCreateSupplyType)
			.toObservable()
			.doOnNext(System.out::println)
			.subscribe(observer);
		this.observers.add(observer);
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
}
