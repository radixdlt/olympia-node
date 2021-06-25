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

package com.radixdlt.api.construction;

import com.google.inject.Inject;
import com.radixdlt.atom.Txn;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.ConstraintMachineException;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.constraintmachine.TxnParseException;
import com.radixdlt.statecomputer.forks.RERules;
import com.radixdlt.utils.functional.Result;

import java.util.Objects;

public final class TxnParser {
	private final LogCMStore logCMStore;
	private final RERules rules;

	@Inject
	public TxnParser(
		RERules rules,
		LogCMStore logCMStore
	) {
		this.rules = rules;
		this.logCMStore = Objects.requireNonNull(logCMStore);
	}

	public REProcessedTxn parse(Txn txn) throws TxnParseException, ConstraintMachineException {
		var parser = rules.getParser();
		var parsedTxn = parser.parse(txn);
		var cmConfig = rules.getConstraintMachineConfig();
		var cm = new ConstraintMachine(
			cmConfig.getVirtualStoreLayer(),
			cmConfig.getProcedures(),
			cmConfig.getMetering()
		);
		var stateUpdates = cm.verify(
			logCMStore.createTransaction(),
			parser.getSubstateDeserialization(),
			logCMStore,
			PermissionLevel.SYSTEM,
			parsedTxn.instructions(),
			parsedTxn.getSignedBy(),
			parsedTxn.disableResourceAllocAndDestroy()
		);

		return new REProcessedTxn(parsedTxn, stateUpdates);
	}

	public Result<REProcessedTxn> parseTxn(Txn txn) {
		return Result.wrap(TxnParserErrors.TRANSACTION_PARSING_ERROR, () -> parse(txn));
	}
}
