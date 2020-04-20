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

package com.radixdlt.consensus;

import io.reactivex.rxjava3.core.Observable;

/**
 * A running BFT check given access to network
 */
public interface BFTCheck {

	/**
	 * Creates an observable which runs assertions against a bft network.
	 * Assertions errors are expected to propagate down the observable.
	 * TODO: Cleanup interface a bit
	 *
	 * @param network network to check
	 * @return observable to subscribe to enable checking
	 */
	Observable<Object> check(BFTTestNetwork network);
}
