package org.radix.time;

import java.util.Map;

import org.radix.modules.Modules;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import org.radix.validation.ValidatableObject;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

public abstract class ChronologicObject extends ValidatableObject implements Cloneable, Chronologic
{
	@JsonProperty("timestamps")
	@DsonOutput(Output.ALL)
	@JsonInclude(Include.NON_EMPTY)
	private Timestamps timestamps = new Timestamps();

	protected ChronologicObject()
	{
		super();

		long timestamp = Modules.isAvailable(NtpService.class) ? Modules.get(NtpService.class).getUTCTimeMS() : System.currentTimeMillis();
		this.setTimestamp(Timestamps.DEFAULT, timestamp);
	}

	public Map<String, Long> getTimestamps()
	{
		return timestamps;
	}

	@Override
	public long getTimestamp()
	{
		return timestamps.getOrDefault(Timestamps.DEFAULT, 0l);
	}

	@Override
	public long getTimestamp(String type)
	{
		return timestamps.getOrDefault(type, 0l);
	}

	@Override
	public void setTimestamp(String type, long timestamp)
	{
		timestamps.put(type, timestamp);
	}
}
