/*
 *  (C) Copyright 2020 Radix DLT Ltd
 *
 *  Radix DLT Ltd licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License.  You may obtain a copy of the
 *  License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied.  See the License for the specific
 *  language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus;

import com.radixdlt.common.AID;

public class VertexMetadata {
	private final Round round;
	private final AID aid;
	private final Round parentRound;
	private final AID parentAid;

	public VertexMetadata(Round round, AID aid, Round parentRound, AID parentAid) {
		this.round = round;
		this.aid = aid;
		this.parentRound = parentRound;
		this.parentAid = parentAid;
	}

	public Round getRound() {
		return round;
	}

	public Round getParentRound() {
		return parentRound;
	}

	public AID getAID() {
		return aid;
	}

	public AID getParentAID() {
		return parentAid;
	}
}