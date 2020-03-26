/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */
package com.radixdlt.mempool;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.radixdlt.common.AID;
import com.radixdlt.common.Atom;
import com.radixdlt.properties.RuntimeProperties;

/**
 * Local-only mempool.
 * <p>
 * Performs no validation and does not share contents with
 * network.  Threadsafe.
 */
final class LocalMempool implements Mempool {
	private final Object lock = new Object();
	@GuardedBy("lock")
	private final LinkedHashMap<AID, Atom> data = Maps.newLinkedHashMap();

	private final int maxSize;

	@Inject
	LocalMempool(RuntimeProperties config) {
		this(config.get("mempool.maxSize", 1000));
	}

	LocalMempool(int maxSize) {
		if (maxSize <= 0) {
			throw new IllegalArgumentException("mempool.maxSize must be positive: " + maxSize);
		}
		this.maxSize = maxSize;
	}

	@Override
	public void addAtom(Atom atom) throws MempoolFullException, MempoolDuplicateException {
		synchronized (this.lock) {
			if (this.data.size() >= this.maxSize) {
				throw new MempoolFullException(atom, String.format("Mempool full: %s of %s items", this.data.size(), this.maxSize));
			}
			if (null != this.data.put(atom.getAID(), atom)) {
				throw new MempoolDuplicateException(atom, String.format("Mempool already has atom %s", atom.getAID()));
			}
		}
	}

	@Override
	public void removeCommittedAtom(AID aid) {
		synchronized (this.lock) {
			this.data.remove(aid);
		}
	}

	@Override
	public void removeRejectedAtom(AID aid) {
		// For now we just treat this the same as committed atoms.
		// Once we have a more complete mempool implementation, we
		// can use this to remove dependent atoms too.
		removeCommittedAtom(aid);
	}

	@Override
	public List<Atom> getAtoms(int count, Set<AID> seen) {
		synchronized (this.lock) {
			int size = Math.min(count, this.data.size());
			if (size > 0) {
				List<Atom> atoms = Lists.newArrayList();
				Iterator<Atom> i = this.data.values().iterator();
				while (atoms.size() < size && i.hasNext()) {
					Atom a = i.next();
					if (seen.add(a.getAID())) {
						atoms.add(a);
					}
				}
				return atoms;
			} else {
				return Collections.emptyList();
			}
		}
	}

	@Override
	public int atomCount() {
		synchronized (this.lock) {
			return this.data.size();
		}
	}

	// Used by SharedMempool
	int maxCount() {
		return this.maxSize;
	}

	@Override
	public String toString() {
		return String.format("%s[%x:%s/%s]",
			getClass().getSimpleName(), System.identityHashCode(this), atomCount(), this.maxSize);
	}
}
