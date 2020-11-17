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

package com.radixdlt.consensus.bft;

import com.radixdlt.consensus.QuorumCertificate;
import java.util.Objects;

public class FormedQC {
	private final QuorumCertificate qc;
	private final BFTNode lastAuthor;

	private FormedQC(QuorumCertificate qc, BFTNode lastAuthor) {
		this.qc = qc;
		this.lastAuthor = lastAuthor;
	}

	public QuorumCertificate qc() {
		return qc;
	}

	public BFTNode lastAuthor() {
		return lastAuthor;
	}

	public static FormedQC create(QuorumCertificate qc, BFTNode lastAuthor) {
		return new FormedQC(
			Objects.requireNonNull(qc),
			Objects.requireNonNull(lastAuthor)
		);
	}
}
