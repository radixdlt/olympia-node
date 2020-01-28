/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.client.core.network.jsonrpc;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.DsonOutput.Output;
import org.radix.serialization2.SerializerConstants;
import org.radix.serialization2.SerializerDummy;
import org.radix.serialization2.SerializerId2;

@SerializerId2("radix.shards.range")
public final class ShardRange extends Range<Long> {
	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	ShardRange() {
		// For serializer
	}

	public ShardRange(long low, long high) {
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
