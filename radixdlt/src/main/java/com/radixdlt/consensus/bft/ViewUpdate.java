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

import java.util.Objects;

/**
 * Represents an internal (local) view update.
 */
public final class ViewUpdate {

	private final View currentView;
	// Highest view in which a commit happened
	private final View highestCommitView;

	public ViewUpdate(View currentView, View highestCommitView) {
		this.currentView = Objects.requireNonNull(currentView);
		this.highestCommitView = Objects.requireNonNull(highestCommitView);
	}

	public View getCurrentView() {
		return currentView;
	}

	public View getHighestCommitView() {
		return highestCommitView;
	}

	public long uncommittedViewsCount() {
		return Math.max(0L, currentView.number() - highestCommitView.number() - 1);
	}

	@Override
	public String toString() {
		return String.format("%s[%s,%s]",
			getClass().getSimpleName(),
			this.currentView,
			this.highestCommitView);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		final ViewUpdate that = (ViewUpdate) o;
		return Objects.equals(currentView, that.currentView)
				&& Objects.equals(highestCommitView, that.highestCommitView);
	}

	@Override
	public int hashCode() {
		return Objects.hash(currentView, highestCommitView);
	}
}
