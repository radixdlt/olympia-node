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

package com.radixdlt.api;

import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.epoch.EpochView;
import com.radixdlt.systeminfo.InMemorySystemInfoManager;
import com.radixdlt.systeminfo.InfoRx;
import com.radixdlt.consensus.Timeout;
import io.reactivex.rxjava3.core.Observable;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;

public class InMemorySystemInfoManagerTest {
	private InfoRx infoRx;
	private InMemorySystemInfoManager runner;

	@Before
	public void setup() {
		this.infoRx = mock(InfoRx.class);
		when(infoRx.timeouts()).thenReturn(Observable.never());
		when(infoRx.currentViews()).thenReturn(Observable.never());
		when(infoRx.highQCs()).thenReturn(Observable.never());
		when(infoRx.committedVertices()).thenReturn(Observable.never());
		this.runner = new InMemorySystemInfoManager(infoRx, 1, 1);
	}

	@Test
	public void when_send_current_view_and_get_view__then_returns_sent_view() {
		EpochView currentView = mock(EpochView.class);
		when(infoRx.currentViews()).thenReturn(Observable.just(currentView));
		runner.start();
		await().atMost(1, TimeUnit.SECONDS).until(() -> runner.getCurrentView().equals(currentView));
	}

	@Test
	public void when_send_timeout_view_and_get_timeout_view__then_returns_sent_timeout_view() {
		Timeout timeout = mock(Timeout.class);
		when(infoRx.timeouts()).thenReturn(Observable.just(timeout));
		runner.start();
		await().atMost(1, TimeUnit.SECONDS).until(() -> runner.getLastTimeout().equals(timeout));
	}

	@Test
	public void when_send_high_qc_and_get_high_qc__then_returns_sent_high_qc() {
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(infoRx.highQCs()).thenReturn(Observable.just(qc));
		runner.start();
		await().atMost(1, TimeUnit.SECONDS).until(() -> runner.getHighestQC().equals(qc));
	}

	@Test
	public void when_send_three_committed_vertices_and_get_committed__then_returns_last_committed() {
		Vertex vertex0 = mock(Vertex.class);
		when(vertex0.getView()).thenReturn(View.of(0));
		Vertex vertex1 = mock(Vertex.class);
		when(vertex1.getView()).thenReturn(View.of(1));
		Vertex vertex2 = mock(Vertex.class);
		when(vertex2.getView()).thenReturn(View.of(2));
		when(infoRx.committedVertices()).thenReturn(Observable.just(vertex0, vertex1, vertex2));
		runner.start();
		await().atMost(1, TimeUnit.SECONDS).until(() -> runner.getCommittedVertices().size() == 1
			&& runner.getCommittedVertices().get(0).equals(vertex2));
	}
}