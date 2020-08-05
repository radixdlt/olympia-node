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

package com.radixdlt.middleware2;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.syncer.EpochChangeSender;
import com.radixdlt.api.DeserializationFailure;
import com.radixdlt.api.InfoRx;
import com.radixdlt.api.LedgerRx;
import com.radixdlt.api.StoredAtom;
import com.radixdlt.api.SubmissionErrorsRx;
import com.radixdlt.api.SubmissionFailure;
import com.radixdlt.api.Timeout;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.consensus.CommittedStateSync;
import com.radixdlt.consensus.CommittedStateSyncRx;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.consensus.EpochChangeRx;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexStoreEventsRx;
import com.radixdlt.consensus.bft.VertexStore.VertexStoreEventSender;
import com.radixdlt.consensus.epoch.EpochManager.EpochInfoSender;
import com.radixdlt.consensus.epoch.EpochView;
import com.radixdlt.syncer.SyncedRadixEngine.CommittedStateSyncSender;
import com.radixdlt.syncer.SyncedRadixEngine.SyncedRadixEngineEventSender;
import com.radixdlt.crypto.Hash;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.api.StoredFailure;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.mempool.SubmissionControlImpl.SubmissionControlSender;
import com.radixdlt.middleware2.converters.AtomConversionException;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.Subject;

/**
 * Acts as the "ether" to messages passed from sender to receiver
 */
public final class InternalMessagePasser implements
	VertexStoreEventsRx,
	VertexStoreEventSender,
	CommittedStateSyncSender,
	CommittedStateSyncRx,
	EpochChangeSender,
	EpochChangeRx,
	EpochInfoSender,
	InfoRx,
	SyncedRadixEngineEventSender,
	LedgerRx,
	SubmissionControlSender,
	SubmissionErrorsRx {
	private final Subject<Hash> localSyncsSubject = BehaviorSubject.<Hash>create().toSerialized();
	private final Subject<CommittedStateSync> committedStateSyncsSubject = BehaviorSubject.<CommittedStateSync>create().toSerialized();
	private final Observable<Hash> localSyncs;
	private final Observable<CommittedStateSync> committedStateSyncs;
	private final Subject<Vertex> committedVertices = BehaviorSubject.<Vertex>create().toSerialized();
	private final Subject<EpochChange> epochChanges = BehaviorSubject.<EpochChange>create().toSerialized();
	private final Subject<Timeout> timeouts = BehaviorSubject.<Timeout>create().toSerialized();
	private final Subject<EpochView> currentEpochViews = BehaviorSubject.<EpochView>create().toSerialized();
	private final Subject<QuorumCertificate> highQCs = BehaviorSubject.<QuorumCertificate>create().toSerialized();
	private final Subject<StoredAtom> storedAtoms = BehaviorSubject.<StoredAtom>create().toSerialized();
	private final Subject<StoredFailure> storedExceptions = BehaviorSubject.<StoredFailure>create().toSerialized();
	private final Subject<SubmissionFailure> submissionFailures = BehaviorSubject.<SubmissionFailure>create().toSerialized();
	private final Subject<DeserializationFailure> deserializationFailures = BehaviorSubject.<DeserializationFailure>create().toSerialized();

	public InternalMessagePasser() {
		this.localSyncs = localSyncsSubject.publish().refCount();
		this.committedStateSyncs = committedStateSyncsSubject.publish().refCount();
	}

	@Override
	public Observable<Hash> syncedVertices() {
		return localSyncs;
	}

	@Override
	public Observable<Vertex> committedVertices() {
		return committedVertices;
	}

	@Override
	public Observable<QuorumCertificate> highQCs() {
		return highQCs;
	}

	@Override
	public void sendSyncedVertex(Vertex vertex) {
		localSyncsSubject.onNext(vertex.getId());
	}

	@Override
	public Observable<CommittedStateSync> committedStateSyncs() {
		return committedStateSyncs;
	}

	@Override
	public void sendCommittedStateSync(long stateVersion, Object opaque) {
		committedStateSyncsSubject.onNext(new CommittedStateSync(stateVersion, opaque));
	}

	@Override
	public void sendCommittedVertex(Vertex vertex) {
		committedVertices.onNext(vertex);
	}

	@Override
	public void highQC(QuorumCertificate qc) {
		highQCs.onNext(qc);
	}

	@Override
	public void epochChange(EpochChange epochChange) {
		epochChanges.onNext(epochChange);
	}

	@Override
	public Observable<EpochChange> epochChanges() {
		return epochChanges;
	}

	@Override
	public void sendStored(CommittedAtom committedAtom, ImmutableSet<EUID> indicies) {
		storedAtoms.onNext(new StoredAtom(committedAtom, indicies));
	}

	@Override
	public Observable<StoredAtom> storedAtoms() {
		return storedAtoms;
	}

	@Override
	public void sendStoredFailure(CommittedAtom committedAtom, RadixEngineException e) {
		storedExceptions.onNext(new StoredFailure(committedAtom, e));
	}

	@Override
	public Observable<StoredFailure> storedExceptions() {
		return storedExceptions;
	}

	@Override
	public void sendDeserializeFailure(Atom rawAtom, AtomConversionException e) {
		deserializationFailures.onNext(new DeserializationFailure(rawAtom, e));
	}

	@Override
	public void sendRadixEngineFailure(ClientAtom clientAtom, RadixEngineException e) {
		submissionFailures.onNext(new SubmissionFailure(clientAtom, e));

	}

	@Override
	public Observable<SubmissionFailure> submissionFailures() {
		return submissionFailures;
	}

	@Override
	public Observable<DeserializationFailure> deserializationFailures() {
		return deserializationFailures;
	}

	@Override
	public void sendCurrentView(EpochView epochView) {
		currentEpochViews.onNext(epochView);
	}

	@Override
	public Observable<EpochView> currentViews() {
		return currentEpochViews;
	}

	@Override
	public void sendTimeoutProcessed(EpochView epochView, BFTNode leader) {
		timeouts.onNext(new Timeout(epochView, leader));
	}

	@Override
	public Observable<Timeout> timeouts() {
		return timeouts;
	}
}
