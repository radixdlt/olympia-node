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

package com.radixdlt.consensus.simulation;

import com.radixdlt.consensus.EpochChange;
import com.radixdlt.consensus.EpochChangeRx;
import com.radixdlt.consensus.SyncedStateComputer;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.View;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.middleware2.CommittedAtom;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.Subject;
import java.util.List;
import java.util.function.Function;

public class ChangingEpochSyncedStateComputer implements SyncedStateComputer<CommittedAtom>, EpochChangeRx {
	private VertexMetadata lastEpochChange = null;
	private final Subject<EpochChange> epochChanges = BehaviorSubject.<EpochChange>create().toSerialized();
	private final Function<Long, ValidatorSet> validatorSetMapping;

	public ChangingEpochSyncedStateComputer(Function<Long, ValidatorSet> validatorSetMapping) {
		this.validatorSetMapping = validatorSetMapping;
	}

	@Override
	public boolean syncTo(VertexMetadata vertexMetadata, List<ECPublicKey> target, Object opaque) {
		return false;
	}

	@Override
	public boolean compute(Vertex vertex) {
		return vertex.getView().compareTo(View.of(100)) >= 0;
	}

	@Override
	public void execute(CommittedAtom atom) {
		if (atom.getVertexMetadata().isEndOfEpoch()
			&& (lastEpochChange == null || lastEpochChange.getEpoch() != atom.getVertexMetadata().getEpoch())) {
			VertexMetadata ancestor = atom.getVertexMetadata();
			this.lastEpochChange = ancestor;
			EpochChange epochChange = new EpochChange(ancestor, validatorSetMapping.apply(ancestor.getEpoch() + 1));
			this.epochChanges.onNext(epochChange);
		}
	}

	@Override
	public Observable<EpochChange> epochChanges() {
		return epochChanges;
	}
}
