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
import com.radixdlt.syncer.SyncExecutor.CommittedCommand;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Temporary class to hold committed results
 * TODO: Remove class
 */
public class CommittedCommands {
	private static class CommittedCommandSuccess implements CommittedCommand {
		private final CommittedAtom command;
		private final Object result;

		private CommittedCommandSuccess(CommittedAtom command, Object result) {
			this.command = command;
			this.result = result;
		}

		@Override
		public CommittedAtom getCommand() {
			return command;
		}

		@Override
		public <T> MaybeSuccessMapped<T> map(Function<Object, T> successMapper) {
			return errorMapper -> successMapper.apply(result);
		}

		@Override
		public CommittedCommand ifSuccess(Consumer<Object> successConsumer) {
			successConsumer.accept(result);
			return this;
		}

		@Override
		public CommittedCommand ifError(Consumer<Exception> errorConsumer) {
			return this;
		}
	}

	private static class CommittedCommandException implements CommittedCommand {
		private final CommittedAtom command;
		private final Exception exception;

		private CommittedCommandException(CommittedAtom command, Exception exception) {
			this.command = command;
			this.exception = exception;
		}

		@Override
		public CommittedAtom getCommand() {
			return command;
		}

		@Override
		public <T> MaybeSuccessMapped<T> map(Function<Object, T> successMapper) {
			return errorMapper -> errorMapper.apply(exception);
		}

		@Override
		public CommittedCommand ifSuccess(Consumer<Object> successConsumer) {
			return this;
		}

		@Override
		public CommittedCommand ifError(Consumer<Exception> errorConsumer) {
			errorConsumer.accept(exception);
			return this;
		}
	}

	public static CommittedCommand success(CommittedAtom command, Object result) {
		return new CommittedCommandSuccess(command, result);
	}

	public static CommittedCommand error(CommittedAtom command, Exception exception) {
		return new CommittedCommandException(command, exception);
	}

	private CommittedCommands() {
		throw new IllegalStateException("Should not be here.");
	}
}
