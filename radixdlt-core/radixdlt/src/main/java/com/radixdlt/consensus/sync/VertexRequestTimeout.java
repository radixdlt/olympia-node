/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus.sync;

import java.util.Objects;

/**
 * A scheduled timeout for a given vertex request
 */
public final class VertexRequestTimeout {
	private final GetVerticesRequest request;

	private VertexRequestTimeout(GetVerticesRequest request) {
		this.request = request;
	}

	public static VertexRequestTimeout create(GetVerticesRequest request) {
		return new VertexRequestTimeout(request);
	}

	public GetVerticesRequest getRequest() {
		return request;
	}

	@Override
	public int hashCode() {
		return Objects.hash(request);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof VertexRequestTimeout)) {
			return false;
		}

		VertexRequestTimeout other = (VertexRequestTimeout) o;
		return Objects.equals(this.request, other.request);
	}

	@Override
	public String toString() {
		return String.format("%s[%s]", getClass().getSimpleName(), this.request);
	}
}
