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

package com.radixdlt.consensus.simulation.configuration;

import com.radixdlt.consensus.EpochChange;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.View;
import com.radixdlt.consensus.simulation.network.SimulationNodes.SimulatedStateComputer;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.middleware2.CommittedAtom;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.Subject;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * State computer which changes epochs after some number of views
 */
public class ChangingEpochSyncedStateComputer implements SimulatedStateComputer {
	private final Subject<EpochChange> epochChanges = BehaviorSubject.<EpochChange>create().toSerialized();
	private final View epochHighView;
	private final Function<Long, ValidatorSet> validatorSetMapping;
	private VertexMetadata currentAncestor = null;

	public ChangingEpochSyncedStateComputer(View epochHighView, Function<Long, ValidatorSet> validatorSetMapping) {
		this.epochHighView = Objects.requireNonNull(epochHighView);
		this.validatorSetMapping = validatorSetMapping;
		VertexMetadata ancestor = VertexMetadata.ofGenesisAncestor();
		this.epochChanges.onNext(new EpochChange(ancestor, validatorSetMapping.apply(ancestor.getEpoch() + 1)));
	}

	private void nextEpoch(VertexMetadata ancestor) {
		if (this.currentAncestor != null && ancestor.getEpoch() <= this.currentAncestor.getEpoch()) {
			return;
		}

		this.currentAncestor = ancestor;
		EpochChange epochChange = new EpochChange(ancestor, validatorSetMapping.apply(ancestor.getEpoch() + 1));
		this.epochChanges.onNext(epochChange);
	}

	@Override
	public boolean syncTo(VertexMetadata vertexMetadata, List<ECPublicKey> target, Object opaque) {
		if (vertexMetadata.isEndOfEpoch()) {
			this.nextEpoch(vertexMetadata);
		}

		return false;
	}

	@Override
	public boolean compute(Vertex vertex) {
		return vertex.getView().compareTo(epochHighView) >= 0;
	}

	@Override
	public void execute(CommittedAtom atom) {
		if (atom.getVertexMetadata().isEndOfEpoch()) {
			this.nextEpoch(atom.getVertexMetadata());
		}
	}

	@Override
	public Observable<EpochChange> epochChanges() {
		return epochChanges.observeOn(Schedulers.io());
	}
}
