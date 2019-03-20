package com.radix.acceptance.create_single_issuance_token_class;

import com.radixdlt.client.application.translate.tokens.TokenDefinitionReference;
import com.radixdlt.client.core.network.actions.SubmitAtomAction;
import com.radixdlt.client.core.network.actions.SubmitAtomReceivedAction;
import com.radixdlt.client.core.network.actions.SubmitAtomRequestAction;
import com.radixdlt.client.core.network.actions.SubmitAtomResultAction;
import com.radixdlt.client.core.network.actions.SubmitAtomResultAction.SubmitAtomResultActionType;
import com.radixdlt.client.core.network.actions.SubmitAtomSendAction;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.radix.utils.UInt256;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.radix.acceptance.SpecificProperties;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction;
import com.radixdlt.client.application.translate.tokens.TokenDefinitionReference;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.RadixUniverse;


import static com.radixdlt.client.core.network.actions.SubmitAtomResultAction.SubmitAtomResultActionType.STORED;
import static com.radixdlt.client.core.network.actions.SubmitAtomResultAction.SubmitAtomResultActionType.VALIDATION_ERROR;
import static com.radixdlt.client.core.network.actions.SubmitAtomResultAction.SubmitAtomResultActionType.COLLISION;


import static org.junit.Assert.assertEquals;

import cucumber.api.java.After;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import io.reactivex.disposables.Disposable;
import io.reactivex.observers.BaseTestConsumer.TestWaitStrategy;
import io.reactivex.observers.TestObserver;

/**
 * See <a href="https://radixdlt.atlassian.net/browse/RLAU-40">RLAU-40</a>.
 */
public class CreateSingleIssuanceTokenClass {
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
	private final SpecificProperties properties = SpecificProperties.of(
		NAME,           "RLAU-40 Test token",
		SYMBOL,			"RLAU",
		DESCRIPTION,	"RLAU-40 Test token",
		TOTAL_SUPPLY,	"1000000000",
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

	@When("^I submit a fixed-supply token-creation request "
			+ "with name \"([^\"]*)\", symbol \"([^\"]*)\", totalSupply (\\d+) scaled and granularity (\\d+) scaled$")
	public void i_submit_a_fixed_supply_token_creation_request_with_name_symbol_totalSupply_scaled_and_granularity_scaled(
			String name, String symbol, int totalSupply, int granularity) {
		this.properties.put(NAME, name);
		this.properties.put(SYMBOL, symbol);
		this.properties.put(TOTAL_SUPPLY, Integer.toString(totalSupply));
		this.properties.put(GRANULARITY, Integer.toString(granularity));
		createToken(CreateTokenAction.TokenSupplyType.FIXED);
	}

	@When("^I submit a fixed-supply token-creation request with symbol \"([^\"]*)\" and totalSupply (\\d+) scaled$")
	public void i_submit_a_fixed_supply_token_creation_request_with_symbol_totalSupply(String symbol, int totalSupply) {
		this.properties.put(SYMBOL, symbol);
		this.properties.put(TOTAL_SUPPLY, Integer.toString(totalSupply));
		createToken(CreateTokenAction.TokenSupplyType.FIXED);
	}

	@When("^I submit a fixed-supply token-creation request with granularity (\\d+) scaled$")
	public void i_submit_a_fixed_supply_token_creation_request_with_granularity(int granularity) {
		this.properties.put(GRANULARITY, Integer.toString(granularity));
		createToken(CreateTokenAction.TokenSupplyType.FIXED);
	}

	@When("^I submit a fixed-supply token-creation request with granularity 0$")
	public void i_submit_a_fixed_supply_token_creation_request_with_granularity_0() {
		this.properties.put(GRANULARITY, "0");
		createToken(CreateTokenAction.TokenSupplyType.FIXED);
	}

	@When("^I submit a fixed-supply token-creation request with symbol \"([^\"]*)\"$")
	public void i_submit_a_fixed_supply_token_creation_request_with_symbol(String symbol) {
		this.properties.put(SYMBOL, symbol);
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
		UInt256 tokenBalance = TokenDefinitionReference.unitsToSubunits(tokenBalanceDecimal);
		UInt256 requiredBalance = TokenDefinitionReference.unitsToSubunits(balance);
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
				this.properties.get(NAME),
				this.properties.get(SYMBOL),
				this.properties.get(DESCRIPTION),
				BigDecimal.valueOf(Long.valueOf(this.properties.get(TOTAL_SUPPLY))),
				BigDecimal.valueOf(Long.valueOf(this.properties.get(GRANULARITY))),
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
