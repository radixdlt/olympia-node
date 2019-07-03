package com.radixdlt.serialization;

import java.util.Objects;

import org.radix.containers.BasicContainer;
import com.radixdlt.serialization.DsonOutput.Output;

import com.fasterxml.jackson.annotation.JsonProperty;

@SerializerId2("test.dummy_test_object_2")
public class DummyTestObject2 extends BasicContainer {

	@JsonProperty("name")
	@DsonOutput(Output.ALL)
	private String name = getClass().getName();

	public DummyTestObject2() {
		// Nothing to do
	}

	@Override
	public short VERSION() {
		return 100;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.name);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
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
