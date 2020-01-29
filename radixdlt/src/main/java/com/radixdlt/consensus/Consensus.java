/*
 * (C) Copyright 2020 Radix DLT Ltd
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

package com.radixdlt.consensus;

/**
 * An instance of a consensus protocol which may be a participant in a network of nodes.
 * TODO this has been gutted and is now a temporary intermediate for consensus events
 */
public interface Consensus {
	/**
	 * Observes consensus, blocking until an observations becomes available.
	 *
	 * @return The consensus observation
	 */
	ConsensusObservation observe() throws InterruptedException;
}
