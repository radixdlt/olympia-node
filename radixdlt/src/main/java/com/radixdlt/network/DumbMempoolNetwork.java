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

package com.radixdlt.network;

import java.util.Collection;
import com.radixdlt.common.Atom;
import com.radixdlt.consensus.Validator;
import com.radixdlt.network.MempoolSubmissionCallback;

/**
 * Overly simplistic network implementation that does absolutely nothing right now.
 */
public class DumbMempoolNetwork implements MempoolNetworkRx, MempoolNetworkTx {

	public DumbMempoolNetwork() {
		// Nothing for now, more to come later
	}

	@Override
	public void addMempoolSubmissionCallback(MempoolSubmissionCallback callback) {
		// FIXME: No mempool network submissions for now, as we are assuming no network
	}

	@Override
	public void sendMempoolSubmission(Collection<Validator> validators, Atom atom) {
		// FIXME: Implement mempool gossip.
	}
}
