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

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.Hash;
import com.radixdlt.utils.Longs;
import java.util.Objects;

public final class NewViewSigner {
	private final HashSigner signer;
	private final BFTNode self;

	@Inject
	public NewViewSigner(@Named("self") BFTNode self, HashSigner signer) {
		this.self = Objects.requireNonNull(self);
		this.signer = Objects.requireNonNull(signer);
	}

	/**
	 * Create a signed new-view
	 * @param nextView the view of the new-view
	 * @param highestQC highest known qc
	 * @param highestCommittedQC highest known committed qc
	 * @return a signed new-view
	 */
	public NewView signNewView(View nextView, QuorumCertificate highestQC, QuorumCertificate highestCommittedQC) {
		// TODO make signing more robust by including author in signed hash
		ECDSASignature signature = this.signer.sign(Hash.hash256(Longs.toByteArray(nextView.number())));
		return new NewView(
			this.self,
			nextView,
			highestQC,
			highestCommittedQC,
			signature
		);
	}

}
