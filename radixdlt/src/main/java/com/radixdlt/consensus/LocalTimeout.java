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

import java.util.Objects;

/**
 * A timeout for a given epoch and view
 */
public final class LocalTimeout {
	private final long epoch;
	private final View view;

	public LocalTimeout(long epoch, View view) {
		this.epoch = epoch;
		this.view = Objects.requireNonNull(view);
	}

	public long getEpoch() {
		return epoch;
	}

	public View getView() {
		return view;
	}

	@Override
	public int hashCode() {
		return Objects.hash(epoch, view);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof LocalTimeout)) {
			return false;
		}
		LocalTimeout other = (LocalTimeout) o;
		return other.epoch == this.epoch
			&& Objects.equals(other.view, this.view);
	}

	@Override
	public String toString() {
		return String.format("%s{epoch=%s view=%s}", this.getClass().getSimpleName(), epoch, view);
	}
}
