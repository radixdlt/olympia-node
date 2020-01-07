package org.radix.network.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import org.radix.containers.BasicContainer;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
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
	public enum Direction {
		OUTBOUND,
		INBOUND;

		@JsonValue
		@Override
		public String toString() {
			return this.name();
		}
	}

	private static final Logger messaginglog = Logging.getLogger("messaging");

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
	private transient Direction direction;

	protected Message(int magic)
	{
		setTimestamp(Timestamps.DEFAULT, Time.currentTimestamp());
	}

	@JsonProperty("command")
	@DsonOutput(value = Output.HASH, include = false)
	public abstract String getCommand();

	public final Direction getDirection()
	{
		return this.direction;
	}

	public final void setDirection(Direction direction)
	{
		this.direction = direction;
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
		return this.instance+" -> "+this.getCommand()+":"+this.getDirection()+":"+this.getHID()+" @ "+this.getTimestamp();
	}
}