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

package com.radixdlt.environment.deterministic.network;

import java.util.Comparator;

/**
 * Message rank.  Used to implement a sense of time in a deterministic model.
 * Messages with a particular rank are processed in arrival order, but
 * timeouts in particular will be put into the next rank to ensure
 * that they are processed after the current rank.
 */
public final class MessageRank implements Comparable<MessageRank> {
	static final MessageRank EARLIEST_POSSIBLE = new MessageRank(0L, 0L);

	private static final Comparator<MessageRank> COMPARATOR =
		Comparator.comparingLong((MessageRank mr) -> mr.major).thenComparingLong(mr -> mr.minor);

	private final long major;
	private final long minor;

	private MessageRank(long major, long minor) {
		this.major = major;
		this.minor = minor;
	}

	public static MessageRank of(long major, long minor) {
		return new MessageRank(major, minor);
	}

	public long major() {
		return this.major;
	}

	public long minor() {
		return this.minor;
	}

	@Override
	public int hashCode() {
		return Long.hashCode(this.major) * 31 + Long.hashCode(this.minor);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof MessageRank)) {
			return false;
		}
		MessageRank that = (MessageRank) o;
		return this.major == that.major && this.minor == that.minor;
	}

	@Override
	public int compareTo(MessageRank that) {
		return COMPARATOR.compare(this, that);
	}

	@Override
	public String toString() {
		return String.format("[%s:%s]", this.major, this.minor);
	}
}
