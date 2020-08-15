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

package com.radixdlt.syncer;

import com.radixdlt.middleware2.CommittedAtom;
import com.radixdlt.syncer.SyncExecutor.StateComputerExecutedCommand;
import java.util.function.BiConsumer;

public class StateComputerExecutedCommands {
	private static class StateComputerExecutedCommandSuccess implements StateComputerExecutedCommand {
		private final CommittedAtom command;
		private final Object result;

		private StateComputerExecutedCommandSuccess(CommittedAtom command, Object result) {
			this.command = command;
			this.result = result;
		}

		@Override
		public CommittedAtom getCommand() {
			return command;
		}

		@Override
		public StateComputerExecutedCommandMaybeError ifSuccess(BiConsumer<CommittedAtom, Object> successConsumer) {
			successConsumer.accept(command, result);
			return errorConsumer -> { };
		}
	}

	private static class StateComputerExecutedCommandException implements StateComputerExecutedCommand {
		private final CommittedAtom command;
		private final Exception exception;

		private StateComputerExecutedCommandException(CommittedAtom command, Exception exception) {
			this.command = command;
			this.exception = exception;
		}

		@Override
		public CommittedAtom getCommand() {
			return command;
		}

		@Override
		public StateComputerExecutedCommandMaybeError ifSuccess(BiConsumer<CommittedAtom, Object> successConsumer) {
			return errorConsumer -> errorConsumer.accept(command, exception);
		}
	}

	public static StateComputerExecutedCommand success(CommittedAtom command, Object result) {
		return new StateComputerExecutedCommandSuccess(command, result);
	}

	public static StateComputerExecutedCommand error(CommittedAtom command, Exception exception) {
		return new StateComputerExecutedCommandException(command, exception);
	}

	private StateComputerExecutedCommands() {
		throw new IllegalStateException("Should not be here.");
	}
}
