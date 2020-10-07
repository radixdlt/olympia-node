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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.bft.SignedNewViewToLeaderSender.BFTNewViewSender;
import com.radixdlt.consensus.liveness.ProposerElection;
import org.junit.Before;
import org.junit.Test;

public class SignedNewViewToLeaderSenderTest {
	private SignedNewViewToLeaderSender sender;
	private NewViewSigner newViewSigner = mock(NewViewSigner.class);
	private ProposerElection proposerElection = mock(ProposerElection.class);
	private BFTNewViewSender newViewSender = mock(BFTNewViewSender.class);

	@Before
	public void setup() {
		this.sender = new SignedNewViewToLeaderSender(
			newViewSigner,
			proposerElection,
			newViewSender
		);
	}

	@Test
	public void testSend() {
		View view = mock(View.class);
		NewView newView = mock(NewView.class);
		when(newViewSigner.signNewView(eq(view), any())).thenReturn(newView);
		BFTNode node = mock(BFTNode.class);
		when(proposerElection.getProposer(eq(view))).thenReturn(node);

		this.sender.sendProceedToNextView(view, mock(HighQC.class));

		verify(newViewSender, times(1)).sendNewView(eq(newView), eq(node));
	}
}