package org.radix.network.messaging;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.radix.containers.BasicContainer;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.network.exceptions.BanException;
import com.radixdlt.utils.WireIO;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import org.radix.time.Time;
import org.radix.time.Timestamps;
import com.radixdlt.universe.Universe;
import org.xerial.snappy.Snappy;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

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

	public static Message parse(InputStream inputStream) throws IOException, BanException
	{
		WireIO.Reader reader = new WireIO.Reader(inputStream);

		// Save the data items so that we can pre-calculate the hash //
		int length = reader.readInt();
		byte[] compressed = reader.readBytes(length);

		byte[] bytes = Snappy.uncompress(compressed);
		Message message = Modules.get(Serialization.class).fromDson(bytes, Message.class);
		if (message.getMagic() != Modules.get(Universe.class).getMagic()) {
			throw new BanException("Wrong magic for this universe");
		}

		message.setDirection(Direction.INBOUND);
		message.setTimestamp(Timestamps.RECEIVED, Time.currentTimestamp());
		message.setTimestamp(Timestamps.LATENCY, java.lang.System.nanoTime());
		messaginglog.debug(message.toString()+" bytes "+length);

		return message;
	}

	private long 		instance = Message.instances.incrementAndGet();

	@JsonProperty("magic")
	@DsonOutput(value = Output.HASH, include = false)
	private int magic = Modules.get(Universe.class).getMagic();

	@JsonProperty("timestamps")
	@DsonOutput(value = {Output.API, Output.PERSIST})
	private final HashMap<String, Long> timestamps = new HashMap<>();

	// Transients //
	private transient 	int			size = 0;
	private transient 	Direction 	direction;

	protected Message()
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

	public byte[] toByteArray() throws IOException
	{
		byte[] bytes = Modules.get(Serialization.class).toDson(this, Output.WIRE);
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