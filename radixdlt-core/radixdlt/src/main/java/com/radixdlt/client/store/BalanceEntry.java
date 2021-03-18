/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.client.store;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.utils.UInt256;

import java.util.Objects;

@SerializerId2("radix.api.balance")
public class BalanceEntry {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(DsonOutput.Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("owner")
	@DsonOutput(DsonOutput.Output.ALL)
	private final RadixAddress owner;

	@JsonProperty("delegate")
	@DsonOutput(DsonOutput.Output.ALL)
	private final RadixAddress delegate;

	@JsonProperty("rri")
	@DsonOutput(DsonOutput.Output.ALL)
	private final RRI rri;

	@JsonProperty("granularity")
	@DsonOutput(DsonOutput.Output.ALL)
	private final UInt256 granularity;

	@JsonProperty("amount")
	@DsonOutput(DsonOutput.Output.ALL)
	private final UInt256 amount;

	private BalanceEntry(RadixAddress owner, RadixAddress delegate, RRI rri, UInt256 granularity, UInt256 amount) {
		this.owner = owner;
		this.delegate = delegate;
		this.rri = rri;
		this.granularity = granularity;
		this.amount = amount;
	}

	@JsonCreator
	public static BalanceEntry create(RadixAddress owner, RadixAddress delegate, RRI rri, UInt256 granularity, UInt256 amount) {
		Objects.requireNonNull(owner);
		Objects.requireNonNull(rri);
		Objects.requireNonNull(granularity);
		Objects.requireNonNull(amount);

		return new BalanceEntry(owner, delegate, rri, granularity, amount);
	}

	public String accountName() {
		return  "/" + owner.toString() + "/" + rri.getName() + (delegate == null ? "" : "/" + delegate.toString());
	}

	public RadixAddress getOwner() {
		return owner;
	}

	public RadixAddress getDelegate() {
		return delegate;
	}

	public RRI getRri() {
		return rri;
	}

	public UInt256 getGranularity() {
		return granularity;
	}

	public UInt256 getAmount() {
		return amount;
	}
}
