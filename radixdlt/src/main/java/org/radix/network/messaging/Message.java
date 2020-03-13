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
import org.radix.containers.BasicContainer;
import org.radix.time.Time;
import org.radix.time.Timestamps;
import org.xerial.snappy.Snappy;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;

public abstract class Message extends BasicContainer
{
	@Override
	public short VERSION() { return 100; }

	private static final AtomicLong instances = new AtomicLong();
	public static final int MAX_MESSAGE_SIZE = (4096*1024);

	private long instance = Message.instances.incrementAndGet();

	@JsonProperty("magic")
	@DsonOutput(value = Output.HASH, include = false)
	private int magic;

	@JsonProperty("timestamps")
	@DsonOutput(value = {Output.API, Output.PERSIST})
	private final HashMap<String, Long> timestamps = new HashMap<>();

	private transient int size = 0;

	protected Message(int magic)
	{
		setTimestamp(Timestamps.DEFAULT, Time.currentTimestamp());
		this.magic = magic;
	}

	public final int getMagic() {
		return this.magic;
	}

	public final int getSize()
	{
		return this.size;
	}

	public long getTimestamp()
	{
		return this.timestamps.getOrDefault(Timestamps.DEFAULT, 0l);
	}

	public long getTimestamp(String type)
	{
		return this.timestamps.getOrDefault(type, 0l);
	}

	public void setTimestamp(String type, long timestamp)
	{
		this.timestamps.put(type, timestamp);
	}

	public byte[] toByteArray(Serialization serialization) throws IOException
	{
		byte[] bytes = serialization.toDson(this, Output.WIRE);
		byte[] data = Snappy.compress(bytes);

		ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length+4);
		DataOutputStream dos = new DataOutputStream(baos);
		dos.writeInt(data.length);
		dos.write(data);

		return baos.toByteArray();
	}

	@Override
	public String toString()
	{
		return this.instance+" -> "+this.getClass().getSimpleName()+":"+this.getHID()+" @ "+this.getTimestamp();
	}
}