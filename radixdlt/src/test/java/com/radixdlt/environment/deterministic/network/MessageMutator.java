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

package com.radixdlt.environment.deterministic.network;

import com.radixdlt.consensus.epoch.EpochScheduledLocalTimeout;

/**
 * A mutator called on new messages that can mutate the message rank,
 * the message itself, or the message queue.
 */
@FunctionalInterface
public interface MessageMutator {
	/**
	 * Mutates the message queue.  The simplest form of mutation is
	 * to add the supplied message to the queue at the specified rank, but
	 * other mutations are possible; the rank can be changed, and the message
	 * can be substituted for a different message.
	 *
	 * @param rank the proposed rank of the message
	 * @param message the message
	 * @param queue the queue to me mutated
	 * @return {@code true} if the message was processed, {@code false} otherwise.
	 */
	boolean mutate(ControlledMessage message, MessageQueue queue);

	/**
	 * Chains this mutator with another.  If this mutator does not
	 * handle the message, then the next mutator is called.
	 *
	 * @param next the next mutator in the chain to call if this
	 * 		mutator does not handle the message
	 * @return This mutator chained with the specified mutator
	 */
	default MessageMutator andThen(MessageMutator next) {
		return (message, queue) -> mutate(message, queue) || next.mutate(message, queue);
	}

	/**
	 * Returns default mutator that does not handle messages.
	 * By default, the underlying network code will add the message to the
	 * message queue.
	 *
	 * @return A {@code MessageMutator} that does nothing.
	 */
	static MessageMutator nothing() {
		return (message, queue) -> false;
	}

	/**
	 * Returns a mutator that drops {@link EpochScheduledLocalTimeout} messages.
	 * @return A {@code MessageMutator} that drops {@code LocalTimeout} messages.
	 */
	static MessageMutator dropTimeouts() {
		return (message, queue) -> message.message() instanceof EpochScheduledLocalTimeout;
	}

}
