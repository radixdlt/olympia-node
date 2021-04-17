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
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.REParsedTxn;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.utils.functional.Result;

import java.util.Objects;

public final class TxnParser {
	private final LogCMStore logCMStore;
	private final ConstraintMachine constraintMachine;

	@Inject
	public TxnParser(ConstraintMachine constraintMachine, LogCMStore logCMStore) {
		this.constraintMachine = Objects.requireNonNull(constraintMachine);
		this.logCMStore = Objects.requireNonNull(logCMStore);
	}

	public REParsedTxn parse(Txn txn) throws RadixEngineException {
		return constraintMachine.validate(logCMStore.createTransaction(), logCMStore, txn, PermissionLevel.SYSTEM);
	}

	public Result<REParsedTxn> parseTxn(Txn txn) {
		try {
			return Result.ok(parse(txn));
		} catch (RadixEngineException e) {
			return Result.fail(e);
		}
	}
}
