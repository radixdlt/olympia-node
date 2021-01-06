package com.radixdlt.middleware2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;

@SerializerId2("embedded_interface_atom")
public class TestEmbeddedInterfaceAtom {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput({DsonOutput.Output.ALL})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("atom")
	@DsonOutput({DsonOutput.Output.ALL})
	private final TestLedgerAtom ledgerAtom;

	@JsonCreator
	private TestEmbeddedInterfaceAtom(@JsonProperty("atom") TestLedgerAtom ledgerAtom) {
		this.ledgerAtom = ledgerAtom;
	}

	public static TestEmbeddedInterfaceAtom create(final TestLedgerAtom ledgerAtom) {
		return new TestEmbeddedInterfaceAtom(ledgerAtom);
	}

	public TestLedgerAtom ledgerAtom() {
		return ledgerAtom;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		return o instanceof TestEmbeddedInterfaceAtom
			&& ledgerAtom.equals(((TestEmbeddedInterfaceAtom) o).ledgerAtom);
	}

	@Override
	public int hashCode() {
		return ledgerAtom.hashCode();
	}

	@Override
	public String toString() {
		return "TestEmbeddedInterfaceAtom(" + ledgerAtom + ')';
	}
}
