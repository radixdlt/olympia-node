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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.radixdlt.consensus.VertexStore.SyncSender;
import com.radixdlt.consensus.sync.SyncedRadixEngine.CommittedStateSyncSender;
import com.radixdlt.crypto.Hash;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.Subject;

/**
 * Acts as the "ether" to messages passed from sender to receiver
 */
@Singleton
public final class InternalMessagePasser implements LocalSyncRx, SyncSender, CommittedStateSyncSender, CommittedStateSyncRx {
	private final Subject<Hash> localSyncsSubject = BehaviorSubject.<Hash>create().toSerialized();
	private final Subject<CommittedStateSync> committedStateSyncsSubject = BehaviorSubject.<CommittedStateSync>create().toSerialized();
	private final Observable<Hash> localSyncs;
	private final Observable<CommittedStateSync> committedStateSyncs;

	@Inject
	public InternalMessagePasser() {
		this.localSyncs = localSyncsSubject.publish().refCount();
		this.committedStateSyncs = committedStateSyncsSubject.publish().refCount();
	}

	@Override
	public Observable<Hash> localSyncs() {
		return localSyncs;
	}

	@Override
	public void synced(Hash vertexId) {
		localSyncsSubject.onNext(vertexId);
	}

	@Override
	public Observable<CommittedStateSync> committedStateSyncs() {
		return committedStateSyncs;
	}

	@Override
	public void sendCommittedStateSync(long stateVersion, Object opaque) {
		committedStateSyncsSubject.onNext(new CommittedStateSync(stateVersion, opaque));
	}
}
