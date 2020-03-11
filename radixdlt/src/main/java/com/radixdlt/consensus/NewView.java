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

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;

/**
 * Represents a new-view message in the pacemaker
 */
@SerializerId2("consensus.newview")
public final class NewView {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	private View view;

	NewView() {
		// Serializer only
		this.view = View.of(Long.MAX_VALUE);
	}

	public NewView(View view) {
		this.view = view;
	}

	public View getView() {
		return view;
	}

	@JsonProperty("view")
	@DsonOutput(Output.ALL)
	private Long getSerializerView() {
		return this.view == null ? null : this.view.number();
	}

	@JsonProperty("view")
	private void setSerializerView(Long number) {
		this.view = number == null ? null : View.of(number.longValue());
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof NewView)) {
			return false;
		}
		NewView newView = (NewView) o;
		return Objects.equals(this.view, newView.view);
	}

	@Override
	public int hashCode() {
		return this.view.hashCode();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "{" +
			"view=" + view +
			'}';
	}
}
