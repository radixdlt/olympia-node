/*
 *  (C) Copyright 2020 Radix DLT Ltd
 *
 *  Radix DLT Ltd licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License.  You may obtain a copy of the
 *  License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied.  See the License for the specific
 *  language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.Immutable;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;

import java.util.Objects;

/**
 * The signed data contained in a {@link ViewTimeout} message.
 * <p>
 * A view timeout message signals to the receiver that a view timeout has occurred for the author.
 */
@Immutable
@SerializerId2("consensus.view_timeout_data")
public final class ViewTimeoutData {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(Output.ALL)
	SerializerDummy serializer = SerializerDummy.DUMMY;

	private final BFTNode author;

	@JsonProperty("epoch")
	@DsonOutput(Output.ALL)
	private final long epoch;

	private final View view;

	/**
	 * Creates a the data to be signed for a view timeout from the specified
	 * arguments.
	 *
	 * @param author The author of the timeout message
	 * @param epoch The epoch for the view that timed out
	 * @param view The view that timed out
	 */
	public static ViewTimeoutData from(BFTNode author, long epoch, View view) {
		return new ViewTimeoutData(author, epoch, view);
	}

	@JsonCreator
	private ViewTimeoutData(
		@JsonProperty("author") byte[] author,
		@JsonProperty("epoch") long epoch,
		@JsonProperty("view") long view
	) throws PublicKeyException {
		this(BFTNode.create(ECPublicKey.fromBytes(author)), epoch, View.of(view));
	}

	private ViewTimeoutData(BFTNode author, long epoch, View view) {
		this.author = Objects.requireNonNull(author);
		this.epoch = epoch;
		this.view = Objects.requireNonNull(view);
	}

	public BFTNode author() {
		return this.author;
	}

	public long epoch() {
		return this.epoch;
	}

	public View view() {
		return this.view;
	}

	@JsonProperty("view")
	@DsonOutput(Output.ALL)
	private Long getSerializerView() {
		return this.view == null ? null : this.view.number();
	}

	@JsonProperty("author")
	@DsonOutput(Output.ALL)
	private byte[] getSerializerAuthor() {
		return this.author == null ? null : this.author.getKey().getBytes();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o instanceof ViewTimeoutData) {
			ViewTimeoutData that = (ViewTimeoutData) o;
			return this.epoch == that.epoch
				&& Objects.equals(this.author, that.author)
				&& Objects.equals(this.view, that.view);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.author, this.epoch, this.view);
	}

	@Override
	public String toString() {
		return String.format("%s{author=%s epoch=%s view=%s}", getClass().getSimpleName(), this.author, this.epoch, this.view);
	}
}
