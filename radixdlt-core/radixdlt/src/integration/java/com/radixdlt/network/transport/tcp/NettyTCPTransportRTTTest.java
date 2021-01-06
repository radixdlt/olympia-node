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

package com.radixdlt.network.transport.tcp;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.primitives.Longs;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.network.messaging.InboundMessage;
import com.radixdlt.network.transport.TransportControl;
import com.radixdlt.network.transport.TransportOutboundConnection;
import static org.mockito.Mockito.*;

public class NettyTCPTransportRTTTest {

	static class Statistics {
		long minRoundTrip = Long.MAX_VALUE;
		long maxRoundTrip = 0L;
		long sumRoundTrip = 0L;
		double sumSquareRoundTrip = 0.0;
		long countTrips = 0L;
		long countLongDuration = 0L;

		double mu() {
			return 1.0 * this.sumRoundTrip / this.countTrips;
		}

		double sigma() {
			return Math.sqrt(this.sumSquareRoundTrip / this.countTrips - Math.pow(mu(), 2.0));
		}

		void update(long duration) {
			this.minRoundTrip = Math.min(this.minRoundTrip, duration);
			this.maxRoundTrip = Math.max(this.maxRoundTrip, duration);
			this.sumRoundTrip += duration;
			this.sumSquareRoundTrip += Math.pow(duration, 2.0);
			this.countTrips += 1;
			if (duration > 10L * 1000L * 1000L) {
				// More than 10ms
				this.countLongDuration += 1;
			}
		}
	}

	private NettyTCPTransport transport1;
	private NettyTCPTransport transport2;
	private TransportOutboundConnection obc1;
	private TransportOutboundConnection obc2;
	private Statistics statistics;
	private final Semaphore roundTripComplete = new Semaphore(0);

	@Before
	public void setup() {
		this.transport1 = createTransport("127.0.0.1", 12345);
		this.transport2 = createTransport("127.0.0.1", 23456);

		this.obc1 = null;
		this.obc2 = null;
		this.statistics = new Statistics();
		this.roundTripComplete.drainPermits();
	}

	@After
	public void teardown() throws IOException, InterruptedException {
		if (this.transport2 != null) {
			this.transport2.close();
		}
		if (this.transport1 != null) {
			this.transport1.close();
		}
		Thread.sleep(500);
	}

	@Test
	public void testRoundtripTime() throws InterruptedException, ExecutionException, IOException {
		final int runCount = 100_000;

		this.transport1.start(this::inboundReceiver);
		this.transport2.start(this::outboundReceiver);

		try (TransportControl control1 = this.transport1.control();
			 TransportControl control2 = this.transport2.control()) {
			this.obc1 = control1.open(this.transport2.localMetadata()).get();
			this.obc2 = control2.open(this.transport1.localMetadata()).get();

			obc1.send(Longs.toByteArray(0L));
			if (!this.roundTripComplete.tryAcquire(1, TimeUnit.SECONDS)) {
				throw new IllegalStateException("Round trip took more than one second");
			}

			this.statistics = new Statistics();

			for (int i = 0; i < runCount; ++i) {
				obc1.send(Longs.toByteArray(System.nanoTime()));
				if (!this.roundTripComplete.tryAcquire(1, TimeUnit.SECONDS)) {
					throw new IllegalStateException("Round trip took more than one second");
				}
			}
		} finally {
			if (this.obc1 != null) {
				this.obc1.close();
			}
			if (this.obc2 != null) {
				this.obc2.close();
			}
		}
		if (this.statistics.countTrips == 0L) {
			throw new IllegalStateException("No round trips happened?!?");
		} else {
			System.out.format("Min/Max/µ/σ = %s/%s/%.0f/%.0f nanoseconds%n",
				this.statistics.minRoundTrip, this.statistics.maxRoundTrip, this.statistics.mu(), this.statistics.sigma());
			System.out.format("Count = %s, long duration = %s%n", this.statistics.countTrips, this.statistics.countLongDuration);
		}
	}

	private void outboundReceiver(InboundMessage message) {
		this.obc2.send(message.message());
	}

	private void inboundReceiver(InboundMessage message) {
		long rxTime = System.nanoTime();
		byte[] msg = message.message();
		if (msg == null || msg.length != Long.BYTES) {
			throw new IllegalArgumentException("Unexpected message type");
		}
		long txTime = Longs.fromByteArray(msg);
		this.statistics.update(rxTime - txTime);
		this.roundTripComplete.release();
	}

	private NettyTCPTransport createTransport(String host, int port) {
		TCPConfiguration config = new TCPConfiguration() {
			@Override
			public int networkPort(int defaultValue) {
				return port;
			}

			@Override
			public String networkAddress(String defaultValue) {
				return host;
			}

			@Override
			public int maxChannelCount(int defaultValue) {
				return 1024;
			}

			@Override
			public int priority(int defaultValue) {
				return 0;
			}

			@Override
			public boolean debugData(boolean defaultValue) {
				return false;
			}
		};
		Module systemCounterModule = new AbstractModule() {
			@Override
			protected void configure() {
				bind(SystemCounters.class).toInstance(mock(SystemCounters.class));
			}
		};
		Injector injector = Guice.createInjector(new TCPTransportModule(config), systemCounterModule);
		return injector.getInstance(NettyTCPTransport.class);
	}
}
