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

package org.radix.network.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.utils.Compress;
import com.radixdlt.utils.Ints;

import org.radix.containers.BasicContainer;
import org.radix.time.Time;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

public abstract class Message extends BasicContainer {
	@Override
	public short version() {
		return 100;
	}

	private static final AtomicLong instances = new AtomicLong();
	public static final int MAX_MESSAGE_SIZE = (4096 * 1024);

	private long instance = Message.instances.incrementAndGet();

	@JsonProperty("magic")
	@DsonOutput(value = Output.HASH, include = false)
	private int magic;

	@JsonProperty("timestamp")
	@DsonOutput(value = {Output.API, Output.PERSIST})
	private final long timestamp;

	protected Message(int magic) {
		this(magic, Time.currentTimestamp());
	}

	protected Message(int magic, long timestamp) {
		this.magic = magic;
		this.timestamp = timestamp;
	}

	public final int getMagic() {
		return this.magic;
	}

	public long getTimestamp() {
		return this.timestamp;
	}

	public byte[] toByteArray(Serialization serialization) throws IOException {
		byte[] bytes = serialization.toDson(this, Output.WIRE);
		byte[] data = Compress.compress(bytes);

		byte[] byteArray = new byte[data.length + Integer.BYTES];
		Ints.copyTo(data.length, byteArray, 0);
		System.arraycopy(data, 0, byteArray, Integer.BYTES, data.length);

		return byteArray;
	}

	@Override
	public String toString() {
		return this.instance + " -> " + this.getClass().getSimpleName() + ":" + this.hashCode() + " @ " + this.getTimestamp();
	}
}