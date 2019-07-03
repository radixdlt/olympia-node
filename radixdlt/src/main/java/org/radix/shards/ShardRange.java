package org.radix.shards;

import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.serialization.DsonOutput.Output;
import org.radix.utils.Range;

import com.fasterxml.jackson.annotation.JsonProperty;

@SerializerId2("radix.shards.range")
public final class ShardRange extends Range<Long>
{
	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	ShardRange()
	{
		// For serializer
	}

	public ShardRange(long low, long high)
	{
		super(low, high);
	}

	@JsonProperty("low")
	@DsonOutput(Output.ALL)
	long getJsonLow() {
		return this.getLow();
	}

	@JsonProperty("high")
	@DsonOutput(Output.ALL)
	long getJsonHigh() {
		return this.getHigh();
	}

	@JsonProperty("low")
	void setJsonLow(long low) {
		this.setLow(low);
	}

	@JsonProperty("high")
	void setJsonHigh(long high) {
		this.setHigh(high);
	}
}
