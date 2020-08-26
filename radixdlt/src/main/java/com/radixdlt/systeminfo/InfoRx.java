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

package com.radixdlt.systeminfo;

import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.Timeout;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.epoch.EpochView;
import com.radixdlt.ledger.CommittedCommand;
import io.reactivex.rxjava3.core.Observable;

/**
 * Flows of various information from consensus/state-syncer
 */
public interface InfoRx {
	/**
	 * Retrieve rx flow of current epoch+views
	 * @return flow of epochviews
	 */
	Observable<EpochView> currentViews();

	/**
	 * Retrieve rx flow of timeouts occurring
	 * @return flow of timeouts
	 */
	Observable<Timeout> timeouts();

	/**
	 * Retrieve rx flow of high quorum certificates
	 * @return flow of qcs
	 */
	Observable<QuorumCertificate> highQCs();

	/**
	 * Retrieve rx flow of vertices which have been committed
	 * @return flow of vertices
	 */
	Observable<Vertex> committedVertices();

	Observable<CommittedCommand> committedCommands();
}
