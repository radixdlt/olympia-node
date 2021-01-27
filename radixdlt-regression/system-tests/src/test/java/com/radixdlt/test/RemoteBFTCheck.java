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
 *
 */

package com.radixdlt.test;

import io.reactivex.Single;

/**
 * Checks for verifying certain conditions in {@link RemoteBFTNetworkBridge}s
 */
public interface RemoteBFTCheck {
	/**
	 * Creates a cold {@link Single} that runs this check once against the given network when subscribed to,
	 * returning either a success or error {@link RemoteBFTCheckResult} value.
	 * Calling this method or the produced {@link Single} should only throw an exception in the case of internal error,
	 * not in the general case of the check itself failing.
	 *
	 * @param network The network to check
	 * @return A {@link RemoteBFTCheckResult} encapsulating the result of this check
	 */
	Single<RemoteBFTCheckResult> check(RemoteBFTNetworkBridge network);
}
