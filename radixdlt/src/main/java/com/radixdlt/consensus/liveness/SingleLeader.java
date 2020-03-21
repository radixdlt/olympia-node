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

package com.radixdlt.consensus.liveness;

import com.radixdlt.common.EUID;
import com.radixdlt.consensus.View;
import java.util.Objects;

public final class SingleLeader implements ProposerElection {

	private final EUID leader;

	public SingleLeader(EUID leader) {
		this.leader = Objects.requireNonNull(leader);
	}

	@Override
	public boolean isValidProposer(EUID nid, View view) {
		return nid.equals(leader);
	}

	@Override
	public EUID getProposer(View view) {
		return leader;
	}
}
