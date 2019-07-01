package com.radix.acceptance.create_multi_issuance_token_class;

import com.radix.TestEnv;
import com.radixdlt.client.application.translate.tokens.TokenUnitConversions;
import com.radixdlt.client.core.atoms.AtomStatus;
import com.radixdlt.client.core.atoms.particles.RRI;
import com.radixdlt.client.core.network.actions.SubmitAtomRequestAction;
import com.radixdlt.client.core.network.actions.SubmitAtomStatusAction;
import com.radixdlt.client.core.network.actions.SubmitAtomSendAction;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import java.util.concurrent.TimeUnit;
import org.radix.utils.UInt256;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.radix.acceptance.SpecificProperties;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction;
import com.radixdlt.client.atommodel.accounts.RadixAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import io.reactivex.observers.TestObserver;

/**
 * See <a href="https://radixdlt.atlassian.net/browse/RLAU-93">RLAU-93</a>.
 */
public class CreateMultiIssuanceTokenClass {
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
		INITIAL_SUPPLY,	"1000000000",
		NEW_SUPPLY,		"1000000000",
		GRANULARITY,	"1"
	);
	private final List<TestObserver<Object>> observers = Lists.newArrayList();

	@Given("^I have access to a suitable Radix network$")
	public void i_have_access_to_a_suitable_Radix_network() {
		this.identity = RadixIdentities.createNew();
		this.api = RadixApplicationAPI.create(TestEnv.getBootstrapConfig(), this.identity);

		// Reset data
		this.properties.clear();
		this.observers.clear();
	}

	@When("^I submit a mutable-supply token-creation request with symbol \"([^\"]*)\" and granularity (\\d+)$")
	public void i_submit_a_mutable_supply_token_creation_request_with_symbol_and_granularity(String symbol, int granularity) {
		this.properties.put(SYMBOL, symbol);
		this.properties.put(GRANULARITY, Long.toString(granularity));
		createToken(CreateTokenAction.TokenSupplyType.MUTABLE);
	}

	@When("^I submit a mutable-supply token-creation request with name \"([^\"]*)\", symbol \"([^\"]*)\", initialSupply (\\d+) and granularity (\\d+)$")
	public void i_submit_a_mutable_supply_token_creation_request_with_name_symbol_initialSupply_and_granularity(
			String name, String symbol, int initialSupply, int granularity) {
		this.properties.put(NAME, name);
		this.properties.put(SYMBOL, symbol);
		this.properties.put(INITIAL_SUPPLY, Long.toString(initialSupply));
		this.properties.put(GRANULARITY, Long.toString(granularity));
		createToken(CreateTokenAction.TokenSupplyType.MUTABLE);
	}

	@When("^I submit a mutable-supply token-creation request with symbol \"([^\"]*)\" and initialSupply (\\d+)$")
	public void i_submit_a_mutable_supply_token_creation_request_with_symbol_and_initialSupply(String symbol, int initialSupply) {
		this.properties.put(SYMBOL, symbol);
		this.properties.put(INITIAL_SUPPLY, Long.toString(initialSupply));
		createToken(CreateTokenAction.TokenSupplyType.MUTABLE);
	}

	@When("^I submit a mutable-supply token-creation request with granularity (\\d+)$")
	public void i_submit_a_mutable_supply_token_creation_request_with_granularity(int granularity) {
		this.properties.put(GRANULARITY, Long.toString(granularity));
		createToken(CreateTokenAction.TokenSupplyType.MUTABLE);
	}

	@When("^I submit a mutable-supply token-creation request with symbol \"([^\"]*)\"$")
	public void i_submit_a_mutable_supply_token_creation_request_with_symbol(String symbol) {
		this.properties.put(SYMBOL, symbol);
		createToken(CreateTokenAction.TokenSupplyType.MUTABLE);
	}

	@When("^I submit a mint request of (\\d+) for \"([^\"]*)\"$")
	public void i_submit_a_mint_request_of_for(int count, String symbol) {
		TestObserver<Object> observer = new TestObserver<>();
		api.mintTokens(RRI.of(api.getMyAddress(), symbol), new BigDecimal(count))
			.toObservable()
			.doOnNext(System.out::println)
			.subscribe(observer);
		this.observers.add(observer);
	}

	@When("^I submit a burn request of (\\d+) for \"([^\"]*)\"$")
	public void i_submit_a_burn_request_of_for(int count, String symbol) {
		TestObserver<Object> observer = new TestObserver<>();
		api.burnTokens(RRI.of(api.getMyAddress(), symbol), BigDecimal.valueOf(count))
			.toObservable()
			.doOnNext(System.out::println)
			.subscribe(observer);
		this.observers.add(observer);
	}

	@When("^I submit a token transfer request of (\\d+) for \"([^\"]*)\" to an arbitrary account$")
	public void i_submit_a_token_transfer_request_of_for_to_an_arbitrary_account(int count, String symbol) {
		RRI tokenClass = RRI.of(api.getMyAddress(), symbol);
		RadixAddress arbitrary = api.getAddressFromKey(RadixIdentities.createNew().getPublicKey());

		// Ensure balance is up-to-date.
		api.observeBalance(api.getMyAddress(), tokenClass)
			.firstOrError()
			.blockingGet();

		TestObserver<Object> observer = new TestObserver<>();
		api.sendTokens(tokenClass, api.getMyAddress(), arbitrary, BigDecimal.valueOf(count))
			.toObservable()
			.doOnNext(System.out::println)
			.subscribe(observer);
		this.observers.add(observer);
	}

	@Then("^I observe the atom being accepted$")
	public void i_observe_the_atom_being_accepted() throws InterruptedException {
		// "the atom" = most recent atom
		i_can_observe_atom_being_accepted(observers.size());

		// Wait for atom to be propagated throughout network
		TimeUnit.SECONDS.sleep(2);
	}

	@Then("^I can observe the atom being accepted$")
	public void i_can_observe_the_atom_being_accepted() {
		// "the atom" = most recent atom
		i_can_observe_atom_being_accepted(observers.size());
	}

	@Then("^I can observe atom (\\d+) being accepted$")
	public void i_can_observe_atom_being_accepted(int atomNumber) {
		awaitAtomStatus(atomNumber, AtomStatus.STORED);
	}

	@Then("^I can observe the atom being rejected with a validation error$")
	public void i_can_observe_the_atom_being_rejected_as_a_validation_error() {
		// "the atom" = most recent atom
		i_can_observe_atom_being_rejected_as_a_validation_error(observers.size());
	}

	@Then("^I can observe atom (\\d+) being rejected with a validation error$")
	public void i_can_observe_atom_being_rejected_as_a_validation_error(int atomNumber) {
		awaitAtomStatus(atomNumber, AtomStatus.EVICTED_FAILED_CM_VERIFICATION);
	}

	@Then("^I can observe the atom being rejected with an error$")
	public void i_can_observe_atom_being_rejected_with_an_error() {
		// "the atom" = most recent atom
		i_can_observe_atom_being_rejected_with_an_error(observers.size());
	}

	@Then("^I can observe atom (\\d+) being rejected with an error$")
	public void i_can_observe_atom_being_rejected_with_an_error(int atomNumber) {
		awaitAtomStatus(atomNumber, AtomStatus.EVICTED_CONFLICT_LOSER, AtomStatus.CONFLICT_LOSER);
	}

	@Then("^I can observe token \"([^\"]*)\" balance equal to (\\d+)$")
	public void i_can_observe_token_balance_equal_to(String symbol, int balance) {
		RRI tokenClass = RRI.of(api.getMyAddress(), symbol);
		// Ensure balance is up-to-date.
		BigDecimal tokenBalanceDecimal = api.observeBalance(api.getMyAddress(), tokenClass)
			.firstOrError()
			.blockingGet();
		UInt256 tokenBalance = TokenUnitConversions.unitsToSubunits(tokenBalanceDecimal);
		UInt256 requiredBalance = TokenUnitConversions.unitsToSubunits(balance);
		assertEquals(requiredBalance, tokenBalance);
	}

	private void createToken(CreateTokenAction.TokenSupplyType tokenCreateSupplyType) {
		TestObserver<Object> observer = new TestObserver<>();
		api.createToken(
				RRI.of(api.getMyAddress(), this.properties.get(SYMBOL)),
				this.properties.get(NAME),
				this.properties.get(DESCRIPTION),
				BigDecimal.valueOf(Long.valueOf(this.properties.get(INITIAL_SUPPLY))),
				BigDecimal.valueOf(Long.valueOf(this.properties.get(GRANULARITY))),
				tokenCreateSupplyType)
			.toObservable()
			.doOnNext(System.out::println)
			.subscribe(observer);
		this.observers.add(observer);
	}

	private void awaitAtomStatus(int atomNumber, AtomStatus... finalStates) {
		ImmutableSet<AtomStatus> finalStatesSet = ImmutableSet.<AtomStatus>builder()
			.addAll(Arrays.asList(finalStates))
			.build();

		TestObserver<Object> testObserver = this.observers.get(atomNumber - 1);
		testObserver.awaitTerminalEvent();
		testObserver.assertNoErrors();
		testObserver.assertNoTimeout();
		List<Object> events = testObserver.values();
		assertThat(events).extracting(o -> o.getClass().toString())
			.startsWith(
				SubmitAtomRequestAction.class.toString(),
				SubmitAtomSendAction.class.toString()
			);
		assertThat(events).last()
			.isInstanceOf(SubmitAtomStatusAction.class)
			.<AtomStatus>extracting(o -> SubmitAtomStatusAction.class.cast(o).getStatusNotification().getAtomStatus())
			.isIn(finalStatesSet);
	}
}
