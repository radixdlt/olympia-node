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

package com.radixdlt.client.lib.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

public class Proof {
	private final String opaque;
	private final List<SignatureDetails> sigs;
	private final ProofHeader header;

	private Proof(String opaque, List<SignatureDetails> sigs, ProofHeader header) {
		this.opaque = opaque;
		this.sigs = sigs;
		this.header = header;
	}

	@JsonCreator
	public static Proof create(
		@JsonProperty(value = "opaque", required = true) String opaque,
		@JsonProperty(value = "sigs", required = true) List<SignatureDetails> sigs,
		@JsonProperty(value = "header", required = true) ProofHeader header
	) {
		return new Proof(opaque, sigs, header);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof Proof)) {
			return false;
		}

		var proof = (Proof) o;
		return opaque.equals(proof.opaque) && sigs.equals(proof.sigs) && header.equals(proof.header);
	}

	@Override
	public int hashCode() {
		return Objects.hash(opaque, sigs, header);
	}

	@Override
	public String toString() {
		return "{opaque:" + opaque
			+ ", sigs:" + sigs
			+ ", header:" + header + '}';
	}

	public String getOpaque() {
		return opaque;
	}

	public List<SignatureDetails> getSigs() {
		return sigs;
	}

	public ProofHeader getHeader() {
		return header;
	}
}
