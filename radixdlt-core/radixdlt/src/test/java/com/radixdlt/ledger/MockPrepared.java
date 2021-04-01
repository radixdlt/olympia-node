/*
 * (C) Copyright 2020 Radix DLT Ltd
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
 */

package com.radixdlt.ledger;

import com.google.common.hash.HashCode;
import com.radixdlt.consensus.Command;
import com.radixdlt.ledger.StateComputerLedger.PreparedCommand;

public class MockPrepared implements PreparedCommand {

	private final Command command;

	public MockPrepared(Command command) {
		this.command = command;
	}

	@Override
	public Command command() {
		return command;
	}

	@Override
	public HashCode hash() {
		return command.getId().asHashCode();
	}
}
