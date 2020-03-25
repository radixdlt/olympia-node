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

import com.radixdlt.crypto.Hash;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.serialization.DsonOutput.Output;

@Immutable
@SerializerId2("consensus.vertex_metadata")
public final class VertexMetadata {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	private View view;

	@JsonProperty("id")
	@DsonOutput(Output.ALL)
	private final Hash id;

	private View parentView;

	@JsonProperty("parent_id")
	@DsonOutput(Output.ALL)
	private final Hash parentId;

	VertexMetadata() {
		// Serializer only
		this.view = null;
		this.id = null;
		this.parentView = null;
		this.parentId = null;
	}

	public VertexMetadata(View view, Hash id, View parentView, Hash parentId) {
		if (parentId == null && view.number() != 0) {
			throw new IllegalArgumentException("Root vertex must be view 0.");
		}
        this.view = view;
		this.id = id;
		this.parentView = parentView;
		this.parentId = parentId;
	}

	public View getView() {
		return view;
	}

	public View getParentView() {
		return parentView;
	}

	public Hash getParentId() {
		return parentId;
	}

	public Hash getId() {
		return id;
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

	@JsonProperty("parent_view")
	@DsonOutput(Output.ALL)
	private Long getSerializerParentView() {
		return this.parentView == null ? null : this.parentView.number();
	}

	@JsonProperty("parent_view")
	private void setSerializerParentView(Long number) {
		this.parentView = number == null ? null : View.of(number.longValue());
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.view, this.id, this.parentView, this.parentId);
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (o instanceof VertexMetadata) {
			VertexMetadata other = (VertexMetadata) o;
			return
				Objects.equals(this.view, other.view)
				&& Objects.equals(this.id, other.id)
				&& Objects.equals(this.parentView, other.parentView)
				&& Objects.equals(this.parentId, other.parentId);
		}
		return false;
	}
}