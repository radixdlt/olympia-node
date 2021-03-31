/*
 * (C) Copyright 2021 Radix DLT Ltd
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

import com.radixdlt.atom.Atom;

import java.util.Objects;
import java.util.Optional;

/**
 * An atom with additional information stored in a mempool.
 */
public final class MempoolAtom {

	private final Atom atom;
	private final long inserted;
	private final Optional<Long> lastRelayed;

	private MempoolAtom(Atom atom, long inserted, Optional<Long> lastRelayed) {
		this.atom = Objects.requireNonNull(atom);
		this.inserted = Objects.requireNonNull(inserted);
		this.lastRelayed = Objects.requireNonNull(lastRelayed);
	}

	public static MempoolAtom create(Atom atom, long inserted, Optional<Long> lastRelayed) {
		return new MempoolAtom(atom, inserted, lastRelayed);
	}

	public Atom getAtom() {
		return atom;
	}

	public long getInserted() {
		return inserted;
	}

	public Optional<Long> getLastRelayed() {
		return lastRelayed;
	}

	public MempoolAtom withLastRelayed(long lastRelayed) {
		return new MempoolAtom(atom, inserted, Optional.of(lastRelayed));
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		final var that = (MempoolAtom) o;
		return inserted == that.inserted
			&& Objects.equals(lastRelayed, that.lastRelayed)
			&& Objects.equals(atom, that.atom);
	}

	@Override
	public int hashCode() {
		return Objects.hash(atom, inserted, lastRelayed);
	}

	@Override
	public String toString() {
		return String.format("%s{atom=%s inserted=%s lastRelayed=%s}",
			getClass().getSimpleName(), this.atom, this.inserted, this.lastRelayed);
	}
}
