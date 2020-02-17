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

import org.radix.properties.RuntimeProperties;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.radixdlt.common.AID;
import com.radixdlt.common.Atom;

/**
 * Local-only mempool.
 * <p>
 * Performs no validation and does not share contents with
 * network.  Threadsafe.
 */
final class LocalMempool implements Mempool {
	// The following data structure is more complicated than it really should be.
	// Here we are trying for amortized O(1) addAtom(...) and removeXxxAtom(...),
	// with getAtoms(...) linear wrt "count" and size of "seen", and with overall
	// ordering by insertion order.  Oh, and maintaining duplicates.
	//
	// In the longer term, the requirement for duplicates should be lifted, and
	// this can be more simply implemented as a LinkedHashMap<AID, Atom>.
	private final Object lock = new Object();
	@GuardedBy("lock")
	private final LinkedHashMap<Long, Atom> data = Maps.newLinkedHashMap();
	@GuardedBy("lock")
	private final LinkedListMultimap<AID, Long> index = LinkedListMultimap.create();
	@GuardedBy("lock")
	private long atomCount = 0L;

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
	public void addAtom(Atom atom) throws MempoolFullException {
		synchronized (this.lock) {
			if (this.data.size() >= this.maxSize) {
				throw new MempoolFullException(
					String.format("Mempool full: %s of %s items", this.data.size(), this.maxSize));
			}
			Long indexCount = Long.valueOf(this.atomCount++);
			// Do this first so that NPE on null atom doesn't corrupt index
			this.index.put(atom.getAID(), indexCount);
			this.data.put(indexCount, atom);
		}
	}

	@Override
	public void removeCommittedAtom(AID aid) {
		synchronized (this.lock) {
			List<Long> items = this.index.get(aid);
			if (!items.isEmpty()) {
				Long indexCount = items.remove(0);
				this.data.remove(indexCount);
			}
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

	private int size() {
		synchronized (this.lock) {
			return this.data.size();
		}
	}

	@Override
	public String toString() {
		return String.format("%s[%x:%s/%s]",
			getClass().getSimpleName(), System.identityHashCode(this), size(), this.maxSize);
	}

}
