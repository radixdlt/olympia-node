/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radix.acceptance.atom_timestamp;

import com.radixdlt.client.core.RadixEnv;
import com.radixdlt.client.core.ledger.AtomObservation;
import com.radixdlt.identifiers.RRI;
import io.reactivex.disposables.Disposable;
import java.math.BigDecimal;
import java.util.List;
import com.google.common.collect.Lists;
import com.radix.test.utils.TokenUtilities;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.identifiers.RadixAddress;

import io.cucumber.java.After;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.reactivex.observers.TestObserver;
import io.reactivex.observers.BaseTestConsumer.TestWaitStrategy;

public class AtomTimestamp {

	private List<RadixIdentity> identities = Lists.newArrayList();
	private List<RadixApplicationAPI> apis = Lists.newArrayList();
	private final List<Disposable> disposables = Lists.newArrayList();

	@After
	public void cleanUp() {
		disposables.forEach(Disposable::dispose);
	}


	// SCENARIO 1
	@Given("^a library client with an active connection to a server$")
	public void a_library_client_with_an_active_connection_to_a_server() {
		setupApi(0);
	}

	@When("^the client creates a token$")
	public void the_client_creates_a_token() {
		createToken(this.apis.get(0));
		this.disposables.add(this.apis.get(0).pull());
	}

	@Then("^the client should be notified of the token creation atom with a timestamp$")
	public void the_client_should_be_notified_of_the_token_creation_atom_with_a_timestamp() {
		checkAtomObservations(this.apis.get(this.apis.size() - 1), this.apis.get(0).getAddress());
	}



	// SCENARIO 2
	@Given("^a library client with an active connection to a server that has previously created a token$")
	public void a_library_client_with_an_active_connection_to_a_server_that_has_previously_created_a_token() {
		RadixApplicationAPI api = setupApi(0);
		createToken(api);

		// New API with same identity
		setupApi(1, api.getIdentity());
	}

	@When("^the client requests updates for that account$")
	public void the_client_requests_updates_for_that_account() {
		RadixApplicationAPI api = this.apis.get(1);
		this.disposables.add(api.pull());
	}

	// Same "Then" clause as scenario 1



	// SCENARIO 3
	@Given("^identity 1 with an active connection to the network$")
	public void identity_1_with_an_active_connection_to_the_network() {
		setupApi(0);
	}

	@And("^identity 2 with an active connection to the network$")
	public void identity_2_with_an_active_connection_to_the_network() {
		setupApi(1);
	}

	@When("^identity 1 requests updates for identity 2$")
	public void identity_1_requests_updates_for_identity_2() {
		this.apis.get(0).pull(this.apis.get(1).getAddress());
	}

	@And("^identity 2 creates a token$")
	public void identity_2_creates_a_token() {
		createToken(this.apis.get(1));
	}

	@Then("^identity 1 should be notified of the token creation atom with a timestamp$")
	public void identity_1_should_be_notified_of_the_token_creation_atom_with_a_timestamp() {
		checkAtomObservations(this.apis.get(0), this.apis.get(1).getAddress());
	}

	private RadixApplicationAPI setupApi(int expectedNumber) {
		return setupApi(expectedNumber, RadixIdentities.createNew());
	}

	private RadixApplicationAPI setupApi(int expectedNumber, RadixIdentity identity) {
		boolean existingIdentity = this.identities.stream()
			.anyMatch(i -> identity.getPublicKey().equals(i.getPublicKey()));
		if (this.identities.size() != expectedNumber) {
			throw new IllegalStateException(
				String.format("Expected %s identities and there are %s", expectedNumber, this.identities.size())
			);
		}
		this.identities.add(identity);
		RadixApplicationAPI api = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), identity);
		this.apis.add(api);

		if (!existingIdentity) {
			TokenUtilities.requestTokensFor(api);
		}

		return api;
	}

	private void createToken(RadixApplicationAPI api) {
		api.createFixedSupplyToken(
			RRI.of(api.getAddress(), "TEST"),
			"Test token",
			"Test token",
			BigDecimal.valueOf(100L)
		).blockUntilComplete();
	}

	private void checkAtomObservations(RadixApplicationAPI api, RadixAddress address) {
		TestObserver<AtomObservation> observer = new TestObserver<>();
		api.getAtomStore().getAtomObservations(address)
			.filter(TokenUtilities::isNotFaucetAtomObservation)
			.filter(AtomObservation::hasAtom)
			.subscribe(observer);
		observer.awaitCount(1, TestWaitStrategy.SLEEP_10MS, 10000);
		long now = System.currentTimeMillis();
		observer.assertNoTimeout();
		observer.assertNoErrors();
		long minTime = now - 15_000L;
		observer.assertValue(ao -> ao.atomTimestamp() <= now && ao.atomTimestamp() > minTime);
	}
}
