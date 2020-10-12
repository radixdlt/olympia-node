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

import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.View;

/**
 * A consensus event which requires syncing to be effectively
 * processed
 */
public interface ConsensusEvent {

	/**
	 * Retrieve the epoch number the consensus message is a part of
	 * @return the epoch number
	 */
	long getEpoch();

	/**
	 * Get the view the consensus event is meant for
	 * @return view of consensus event
	 */
	View getView();

	/**
	 * Get the node author of this consensus message
	 * @return the node author
	 */
	BFTNode getAuthor();

	/**
	 * Retrieves the {@link HighQC} associated with the event.
	 *
	 * @return {@link HighQC} associated with event
	 */
	HighQC highQC();
}
