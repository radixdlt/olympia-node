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
import com.radixdlt.atom.actions.RegisterValidator;
import com.radixdlt.atom.actions.SplitToken;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.atom.actions.UnregisterValidator;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;

import java.util.ArrayList;
import java.util.List;

public class TxActionListBuilder {
	private final List<TxAction> actions = new ArrayList<>();

	private TxActionListBuilder() {
	}

	public static TxActionListBuilder create() {
		return new TxActionListBuilder();
	}

	public TxActionListBuilder createMutableToken(MutableTokenDefinition def) {
		var action = new CreateMutableToken(
			def.getSymbol(),
			def.getName(),
			def.getDescription(),
			def.getIconUrl(),
			def.getTokenUrl()
		);
		actions.add(action);
		return this;
	}

	public TxActionListBuilder registerAsValidator(ECPublicKey validatorKey) {
		var action = new RegisterValidator(validatorKey, null, null);
		actions.add(action);
		return this;
	}

	public TxActionListBuilder unregisterAsValidator(ECPublicKey validatorKey) {
		var action = new UnregisterValidator(validatorKey, null, null);
		actions.add(action);
		return this;
	}

	public TxActionListBuilder splitNative(REAddr rri, UInt256 minSize) {
		var action = new SplitToken(rri, minSize);
		actions.add(action);
		return this;
	}

	public TxActionListBuilder transfer(REAddr rri, REAddr from, REAddr to, UInt256 amount) {
		var action = new TransferToken(rri, from, to, amount);
		actions.add(action);
		return this;
	}

	public TxActionListBuilder mint(REAddr rri, REAddr to, UInt256 amount) {
		var action = new MintToken(rri, to, amount);
		actions.add(action);
		return this;
	}

	public TxActionListBuilder burn(REAddr rri, REAddr from, UInt256 amount) {
		var action = new BurnToken(rri, from, amount);
		actions.add(action);
		return this;
	}

	public List<TxAction> build() {
		return actions;
	}
}