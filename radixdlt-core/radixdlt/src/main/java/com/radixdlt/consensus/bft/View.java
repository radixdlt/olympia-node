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

package com.radixdlt.consensus.bft;

/**
 * Represents a BFT view used by the Pacemaker of a BFT instance
 */
public final class View implements Comparable<View> {
	private static final View GENESIS_VIEW = View.of(0L);
	private final long view;

	private View(long view) {
		if (view < 0) {
			throw new IllegalArgumentException("view must be >= 0 but was " + view);
		}

		this.view = view;
	}

	public boolean gte(View other) {
		return this.view >= other.view;
	}

	public boolean gt(View other) {
		return this.view > other.view;
	}

	public boolean lt(View other) {
		return this.view < other.view;
	}

	public boolean lte(View other) {
		return this.view <= other.view;
	}

	public View previous() {
		if (this.view == 0) {
			throw new IllegalStateException("View Underflow");
		}

		return new View(view - 1);
	}

	public View next() {
		if (this.view == Long.MAX_VALUE) {
			throw new IllegalStateException("View Overflow");
		}

		return new View(view + 1);
	}

	public long number() {
		return this.view;
	}

	@Override
	public int compareTo(View otherView) {
		return Long.compare(this.view, otherView.view);
	}

	@Override
	public int hashCode() {
		return Long.hashCode(view);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof View)) {
			return false;
		}

		View other = (View) o;
		return other.view == this.view;
	}


	@Override
	public String toString() {
		return Long.toString(this.view);
	}

	public boolean isGenesis() {
		return GENESIS_VIEW.equals(this);
	}

	public static View genesis() {
		return GENESIS_VIEW;
	}

	public static View of(long view) {
		return new View(view);
	}
}
