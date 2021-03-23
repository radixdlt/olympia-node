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

package com.radix.acceptance.mempool;

import com.radix.test.utils.TokenUtilities;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.RadixApplicationAPI.Result;
import com.radixdlt.client.application.RadixApplicationAPI.Transaction;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.unique.PutUniqueIdAction;
import com.radixdlt.client.core.RadixEnv;
import com.radixdlt.client.core.atoms.AtomStatus;
import com.radixdlt.client.core.network.actions.SubmitAtomStatusAction;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

public class SubmitToMempool {
	private static final String NAME = "name";
	private static final String SYMBOL = "symbol";
	private static final String DESCRIPTION = "description";
	private static final String INITIAL_SUPPLY = "initialSupply";
	private static final String NEW_SUPPLY = "newSupply";
	private static final String GRANULARITY = "granularity";

	private RadixApplicationAPI api;
	private RadixIdentity identity;
	private Result result;

	@Given("^I have access to a suitable Radix network$")
	public void i_have_access_to_a_suitable_Radix_network() {
		this.identity = RadixIdentities.createNew();
		this.api = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), this.identity);
		TokenUtilities.requestTokensFor(this.api);
		result = null;
	}

	@When("^I submit an illegal atom$")
	public void i_submit_an_illegal_atom() {
		RadixAddress address = api.getAddress();
		RRI rri = RRI.of(address, "test");
		Transaction tx = api.createTransaction();
		tx.stage(PutUniqueIdAction.create(rri));
		tx.stage(PutUniqueIdAction.create(rri));
		result = tx.commitAndPush();
	}

	@When("^I submit an atom conflicting with an atom already committed$")
	public void i_submit_an_atom_conflicting_with_an_atom_already_committed() {
		RadixAddress address = api.getAddress();
		RRI rri = RRI.of(address, "test");
		Transaction tx = api.createTransaction();
		tx.stage(PutUniqueIdAction.create(rri));
		tx.commitAndPush().blockUntilComplete();

		RRI rri2 = RRI.of(address, "test2");
		Transaction tx2 = api.createTransaction();
		tx2.stage(PutUniqueIdAction.create(rri));
		tx2.stage(PutUniqueIdAction.create(rri2));
		result = tx2.commitAndPush();
	}

	@Then("^I will receive an event letting me know the atom was not added to the mempool because it is illegal$")
	public void i_will_receive_an_event_letting_me_know_the_atom_was_not_added_to_the_mempool_because_it_is_illegal() {
		var testObserver = result.toObservable().test();
		testObserver.awaitTerminalEvent();
		testObserver.assertValueAt(testObserver.valueCount() - 1, e -> {
			var statusAction = (SubmitAtomStatusAction) e;
			return statusAction.getStatusNotification().getAtomStatus().equals(AtomStatus.CONFLICT_LOSER);
		});
	}

	@Then("^I will receive an event letting me know the atom was not added to the mempool because it is conflicting$")
	public void i_will_receive_an_event_letting_me_know_the_atom_was_not_added_to_the_mempool_because_it_is_conflicting() {
		var testObserver = result.toObservable().test();
		testObserver.awaitTerminalEvent();
		testObserver.assertValueAt(testObserver.valueCount() - 1, e -> {
			var statusAction = (SubmitAtomStatusAction) e;
			return statusAction.getStatusNotification().getAtomStatus().equals(AtomStatus.CONFLICT_LOSER);
		});
	}

}
