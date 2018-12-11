package com.radix.acceptance.create_single_issuance_token_class;

import java.util.List;

import org.radix.utils.UInt256;

import com.google.common.collect.Lists;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.tokenclasses.CreateTokenAction;
import com.radixdlt.client.atommodel.tokens.TokenClassReference;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.network.AtomSubmissionUpdate;
import com.radixdlt.client.core.network.AtomSubmissionUpdate.AtomSubmissionState;

import static com.radixdlt.client.core.network.AtomSubmissionUpdate.AtomSubmissionState.COLLISION;
import static com.radixdlt.client.core.network.AtomSubmissionUpdate.AtomSubmissionState.STORED;
import static com.radixdlt.client.core.network.AtomSubmissionUpdate.AtomSubmissionState.SUBMITTED;
import static com.radixdlt.client.core.network.AtomSubmissionUpdate.AtomSubmissionState.SUBMITTING;
import static com.radixdlt.client.core.network.AtomSubmissionUpdate.AtomSubmissionState.VALIDATION_ERROR;

import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import io.reactivex.observers.BaseTestConsumer.TestWaitStrategy;
import io.reactivex.observers.TestObserver;

/**
 * See <a href="https://radixdlt.atlassian.net/browse/RLAU-40">RLAU-40</a>.
 */
public class CreateSingleIssuanceTokenClass {
	static {
		RadixUniverse.bootstrap(Bootstrap.BETANET);
	}

	private static final String NAME = "name";
	private static final String SYMBOL = "symbol";
	private static final String DESCRIPTION = "description";
	private static final String TOTAL_SUPPLY = "totalSupply";
	private static final String NEW_SUPPLY = "newSupply";
	private static final String GRANULARITY = "granularity";

	private static final long TIMEOUT_MS = 10_000L; // Timeout in milliseconds

	private RadixApplicationAPI api;
	private RadixIdentity identity;
	private final SpecificProperties properties = SpecificProperties.of(
		NAME,
		SYMBOL,
		DESCRIPTION,
		TOTAL_SUPPLY,
		NEW_SUPPLY,
		GRANULARITY
	);
	private final List<TestObserver<Object>> observers = Lists.newArrayList();

	@Given("^I have access to suitable development tools$")
	public void i_have_access_to_suitable_development_tools() {
		// Assumed correct, as this code needs to run somewhere
	}

	@Given("^I have included the radixdlt-java library$")
	public void i_have_included_the_radixdlt_java_library() {
		// Assumed correct, as compile time errors would have occurred
	}

	@Given("^I have access to a suitable Radix network$")
	public void i_have_access_to_a_suitable_Radix_network() {
		this.identity = RadixIdentities.createNew();
		this.api = RadixApplicationAPI.create(this.identity);
		this.api.pull();

		// Reset data
		this.properties.clear();
		this.observers.clear();
	}

	@When("^property \"([^\"]*)\" = \"([^\"]*)\"$")
	public void property(String name, String value) {
		this.properties.put(name, value);
	}

	@When("^property \"([^\"]*)\" = (\\d+)$")
	public void property(String name, int value) {
		this.properties.put(name, Integer.toString(value));
	}

	@When("^I submit a fixed-supply token-creation request$")
	public void i_submit_a_fixed_supply_token_creation_request() {
		createToken(CreateTokenAction.TokenSupplyType.FIXED);
	}

	@When("^I submit a mutable-supply token-creation request$")
	public void i_submit_a_mutable_supply_token_creation_request() {
		createToken(CreateTokenAction.TokenSupplyType.MUTABLE);
	}

	@When("^I submit a mint request$")
	public void i_submit_a_mint_request() {
		TestObserver<Object> observer = new TestObserver<>();
		api.mintTokens(
				this.properties.get(SYMBOL),
				TokenClassReference.unitsToSubunits(Long.valueOf(this.properties.get(TOTAL_SUPPLY))))
			.toObservable()
			.doOnNext(System.out::println)
			.map(AtomSubmissionUpdate::getState)
			.subscribe(observer);
		this.observers.add(observer);
	}

	@When("^the atom is accepted$")
	public void the_atom_is_accepted() throws Throwable {
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

	@Then("^I can observe atom (\\d+) being rejected as a collision$")
	public void i_can_observe_atom_being_rejected_as_a_collision(int atomNumber) {
		awaitAtomStatus(atomNumber, COLLISION);
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

	private void createToken(CreateTokenAction.TokenSupplyType tokenCreateSupplyType) {
		TestObserver<Object> observer = new TestObserver<>();
		api.createToken(
				this.properties.get(NAME),
				this.properties.get(SYMBOL),
				this.properties.get(DESCRIPTION),
				UInt256.from(this.properties.get(TOTAL_SUPPLY)),
				UInt256.from(this.properties.get(TOTAL_SUPPLY)),
				tokenCreateSupplyType)
			.toObservable()
			.map(Utils::print)
			.map(AtomSubmissionUpdate::getState)
			.subscribe(observer);
		this.observers.add(observer);
	}

	private void awaitAtomStatus(int atomNumber, AtomSubmissionState finalState) {
		this.observers.get(atomNumber - 1)
			.awaitCount(3, TestWaitStrategy.SLEEP_100MS, TIMEOUT_MS)
			.assertNoErrors()
			.assertNoTimeout()
			.assertValues(SUBMITTING, SUBMITTED, finalState);
	}
}
