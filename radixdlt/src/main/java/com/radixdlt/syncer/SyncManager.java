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

package com.radixdlt.syncer;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.radixdlt.middleware2.CommittedAtom;
import com.radixdlt.utils.ThreadFactories;

public class SyncManager {

	private static final int MAX_REQUESTS_TO_SEND = 20;
	private final Consumer<CommittedAtom> atomProcessor;
	private final LongSupplier versionProvider;
	private final int batchSize;
	private final int maxAtomsQueueSize;
	private final long patience;

	private final TreeSet<CommittedAtom> commitedAtoms = new TreeSet<>(Comparator.comparingLong(a -> a.getVertexMetadata().getStateVersion()));
	private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor(ThreadFactories.daemonThreads("SyncManager"));

	private long targetVersion = -1;
	private ScheduledFuture<?> timeoutChecker;
	private LongConsumer onTarget = target -> { };
	private LongConsumer onVersion = version -> { };

	public SyncManager(
		Consumer<CommittedAtom> atomProcessor,
		LongSupplier versionProvider,
		int batchSize,
		long patience
	) {
		this.atomProcessor = Objects.requireNonNull(atomProcessor);
		this.versionProvider = Objects.requireNonNull(versionProvider);
		if (batchSize <= 0) {
			throw new IllegalArgumentException();
		}
		this.batchSize = batchSize;
		if (patience <= 0) {
			throw new IllegalArgumentException();
		}
		this.patience = patience;
		// we limit the size of the queue in order to avoid memory issues
		this.maxAtomsQueueSize = MAX_REQUESTS_TO_SEND * batchSize * 2;
	}

	public void syncToVersion(long targetVersion, LongConsumer requestSender) {
		executorService.execute(() -> {
			if (targetVersion <= this.targetVersion) {
				return;
			}
			long crtVersion = versionProvider.getAsLong();
			if (crtVersion >= targetVersion) {
				return;
			}
			this.targetVersion = targetVersion;
			sendSyncRequests(requestSender);
		});
	}

	public void syncAtoms(ImmutableList<CommittedAtom> atoms) {
		executorService.execute(() -> {
			long crtVersion = versionProvider.getAsLong();
			for (CommittedAtom atom : atoms) {
				long atomVersion = atom.getVertexMetadata().getStateVersion();
				if (atomVersion > crtVersion) {
					if (commitedAtoms.size() < maxAtomsQueueSize) { // check if there is enough space
						commitedAtoms.add(atom);
					} else { // not enough space available
						CommittedAtom last = commitedAtoms.last();
						// will added it only if it must be applied BEFORE the most recent atom we have
						if (last.getVertexMetadata().getStateVersion() > atomVersion) {
							commitedAtoms.pollLast(); // remove the most recent available
							commitedAtoms.add(atom);
						}
					}
				}
			}
			applyAtoms();
		});
	}

	private void applyAtoms() {
		long initialVersion = versionProvider.getAsLong();
		Iterator<CommittedAtom> it = commitedAtoms.iterator();
		while (it.hasNext()) {
			CommittedAtom crtAtom = it.next();
			long atomVersion = crtAtom.getVertexMetadata().getStateVersion();
			if (atomVersion <= versionProvider.getAsLong()) {
				it.remove();
			} else if (atomVersion == versionProvider.getAsLong() + 1) {
				atomProcessor.accept(crtAtom);
				it.remove();
			} else {
				break;
			}
		}

		long newVersion = versionProvider.getAsLong();
		if (newVersion > initialVersion) {
			onVersion.accept(newVersion);
			if (versionProvider.getAsLong() >= targetVersion) {
				if (timeoutChecker != null) {
					timeoutChecker.cancel(false);
				}
				if (initialVersion < targetVersion) {
					onTarget.accept(targetVersion);
				}
			}
		}
	}

	private void sendSyncRequests(LongConsumer requestSender) {
		long crtVersion = versionProvider.getAsLong();
		if (crtVersion >= targetVersion) {
			return;
		}
		long size = ((targetVersion - crtVersion) / batchSize);
		if ((targetVersion - crtVersion) % batchSize > 0) {
			size += 1;
		}
		size = Math.min(size, MAX_REQUESTS_TO_SEND);
		for (long i = 0; i < size; i++) {
			requestSender.accept(crtVersion + batchSize * i);
		}
		if (timeoutChecker != null) {
			timeoutChecker.cancel(false);
		}
		timeoutChecker = executorService.schedule(() -> sendSyncRequests(requestSender), patience, TimeUnit.SECONDS);
	}

	@VisibleForTesting
	long getTargetVersion() {
		return targetVersion;
	}

	void setTargetListener(LongConsumer onTarget) {
		this.onTarget = onTarget;
	}

	void setVersionListener(LongConsumer onVersion) {
		this.onVersion = onVersion;
	}

	@VisibleForTesting
	int getMaxAtomsQueueSize() {
		return maxAtomsQueueSize;
	}

	@VisibleForTesting
	long getQueueSize() {
		return commitedAtoms.size();
	}

	public void close() {
		executorService.shutdown();
	}
}