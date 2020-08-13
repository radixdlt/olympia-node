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

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.bft.BFTNode;
import java.util.Objects;

/**
 * Round Robin leaders
 */
public final class RotatingLeaders implements ProposerElection {
	private final ImmutableList<BFTNode> leaders;

	public RotatingLeaders(ImmutableList<BFTNode> leaders) {
		this.leaders = Objects.requireNonNull(leaders);
	}

	@Override
	public BFTNode getProposer(View view) {
		int index = (int) (view.number() % leaders.size());
		return leaders.get(index);
	}

	@Override
	public String toString() {
		return String.format("%s{leaders=%s}", getClass().getSimpleName(), leaders);
	}
}
