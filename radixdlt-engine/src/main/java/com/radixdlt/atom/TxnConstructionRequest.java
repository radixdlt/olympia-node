/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 */

package com.radixdlt.atom;

import com.radixdlt.atom.actions.BurnToken;
import com.radixdlt.atom.actions.CreateMutableToken;
import com.radixdlt.atom.actions.MintToken;
import com.radixdlt.atom.actions.FeeReservePut;
import com.radixdlt.atom.actions.RegisterValidator;
import com.radixdlt.atom.actions.SplitToken;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.atom.actions.UnregisterValidator;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class TxnConstructionRequest {
	private boolean disableResourceAllocAndDestroy = false;
	private final List<TxAction> actions = new ArrayList<>();
	private byte[] msg = null;
	private Set<SubstateId> toAvoid;

	private TxnConstructionRequest() {
	}

	public static TxnConstructionRequest create() {
		return new TxnConstructionRequest();
	}

	public Optional<byte[]> getMsg() {
		return Optional.ofNullable(msg);
	}

	public TxnConstructionRequest msg(byte[] msg) {
		this.msg = msg;
		return this;
	}

	public boolean isDisableResourceAllocAndDestroy() {
		return disableResourceAllocAndDestroy;
	}

	public TxnConstructionRequest disableResourceAllocAndDestroy() {
		this.disableResourceAllocAndDestroy = true;
		return this;
	}

	public TxnConstructionRequest action(TxAction txAction) {
		actions.add(txAction);
		return this;
	}

	public TxnConstructionRequest actions(List<TxAction> actions) {
		this.actions.addAll(actions);
		return this;
	}

	public TxnConstructionRequest createMutableToken(MutableTokenDefinition def) {
		actions.add(new CreateMutableToken(def));
		return this;
	}

	public TxnConstructionRequest registerAsValidator(ECPublicKey validatorKey) {
		var action = new RegisterValidator(validatorKey);
		actions.add(action);
		return this;
	}

	public TxnConstructionRequest unregisterAsValidator(ECPublicKey validatorKey) {
		var action = new UnregisterValidator(validatorKey);
		actions.add(action);
		return this;
	}

	public TxnConstructionRequest splitNative(REAddr rri, REAddr userAcct, UInt256 minSize) {
		var action = new SplitToken(rri, userAcct, minSize);
		actions.add(action);
		return this;
	}

	public TxnConstructionRequest transfer(REAddr rri, REAddr from, REAddr to, UInt256 amount) {
		var action = new TransferToken(rri, from, to, amount);
		actions.add(action);
		return this;
	}

	public TxnConstructionRequest mint(REAddr rri, REAddr to, UInt256 amount) {
		var action = new MintToken(rri, to, amount);
		actions.add(action);
		return this;
	}

	public TxnConstructionRequest payFee(REAddr from, UInt256 amount) {
		var action = new FeeReservePut(from, amount);
		actions.add(action);
		return this;
	}

	public TxnConstructionRequest burn(REAddr rri, REAddr from, UInt256 amount) {
		var action = new BurnToken(rri, from, amount);
		actions.add(action);
		return this;
	}

	public TxnConstructionRequest avoidSubstates(Set<SubstateId> toAvoid) {
		this.toAvoid = toAvoid;
		return this;
	}

	public Set<SubstateId> getSubstatesToAvoid() {
		return this.toAvoid == null ? Set.of() : this.toAvoid;
	}

	public List<TxAction> getActions() {
		return actions;
	}

	@Override
	public String toString() {
		return String.format("%s{actions=%s}", this.getClass().getSimpleName(), actions);
	}
}