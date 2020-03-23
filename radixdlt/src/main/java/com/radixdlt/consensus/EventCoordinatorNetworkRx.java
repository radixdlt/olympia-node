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

import io.reactivex.rxjava3.core.Observable;

/**
 * Network accessor for the EventCoordinator
 */
public interface EventCoordinatorNetworkRx {

	/**
	 * Accessor to the stream of proposal messages as they are received from the
	 * network.
	 *
	 * @return hot observable of proposal messages
	 */
	Observable<Vertex> proposalMessages();

	/**
	 * Accessor to the stream of new-view messages as they are received from the
	 * network.
	 *
	 * @return hot observable of new view messages
	 */
	Observable<NewView> newViewMessages();

	/**
	 * Accessor to the stream of vote messages as they are received from the
	 * network.
	 *
	 * @return hot observable of votes messages
	 */
	Observable<Vote> voteMessages();
}
