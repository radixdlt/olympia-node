/*
 * (C) Copyright 2020 Radix DLT Ltd
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

package org.radix.serialization;

import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;
import java.util.Objects;

import org.radix.containers.BasicContainer;
import com.radixdlt.serialization.DsonOutput.Output;

import com.fasterxml.jackson.annotation.JsonProperty;

@SerializerId2("test.dummy_test_object_2")
public final class DummyTestObject2 extends BasicContainer {

	@JsonProperty("name")
	@DsonOutput(Output.ALL)
	private String name = getClass().getName();

	public DummyTestObject2() {
		// Nothing to do
	}

	@Override
	public short version() {
		return 100;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.name);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj instanceof DummyTestObject2) {
			DummyTestObject2 other = (DummyTestObject2) obj;
			return Objects.equals(this.name, other.name);
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format("%s[%s]", getClass().getSimpleName(), name);
	}
}
