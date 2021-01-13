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
package com.radixdlt.mempool;

import com.radixdlt.consensus.Command;
import com.radixdlt.middleware2.ClientAtom;

/**
 * Handle atom submission.
 */
public interface SubmissionControl {

	/**
	 * TODO: This needs to be reworked and refactored
	 */
	void submitCommand(Command command) throws MempoolRejectedException;

	/**
	 * Handle atom submission from API or network as an {@link ClientAtom}.
	 *
	 * @param atom the {@link ClientAtom} for the atom
	 * @throws MempoolFullException if the underlying mempool is too full to accept new submissions
	 * @throws MempoolDuplicateException if the underlying mempool already has the specified atom
	 */
	void submitAtom(ClientAtom atom) throws MempoolFullException, MempoolDuplicateException;
}
