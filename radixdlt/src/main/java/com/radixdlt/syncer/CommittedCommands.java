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

import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.syncer.SyncExecutor.CommittedCommand;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Temporary class to hold committed results
 * TODO: Remove class
 */
public final class CommittedCommands {
	private static class CommittedCommandSuccess implements CommittedCommand {
		private final Command command;
		private final VertexMetadata vertexMetadata;
		private final Object result;

		private CommittedCommandSuccess(Command command, VertexMetadata vertexMetadata, Object result) {
			this.command = command;
			this.vertexMetadata = vertexMetadata;
			this.result = result;
		}

		@Override
		public Command getCommand() {
			return command;
		}

		@Override
		public VertexMetadata getVertexMetadata() {
			return vertexMetadata;
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
		private final Command command;
		private final VertexMetadata vertexMetadata;
		private final Exception exception;

		private CommittedCommandException(Command command, VertexMetadata vertexMetadata, Exception exception) {
			this.command = command;
			this.vertexMetadata = vertexMetadata;
			this.exception = exception;
		}

		@Override
		public Command getCommand() {
			return command;
		}

		@Override
		public VertexMetadata getVertexMetadata() {
			return vertexMetadata;
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

	public static CommittedCommand success(Command command, VertexMetadata vertexMetadata, Object result) {
		return new CommittedCommandSuccess(command, vertexMetadata, result);
	}

	public static CommittedCommand error(Command command, VertexMetadata vertexMetadata, Exception exception) {
		return new CommittedCommandException(command, vertexMetadata, exception);
	}

	private CommittedCommands() {
		throw new IllegalStateException("Should not be here.");
	}
}
