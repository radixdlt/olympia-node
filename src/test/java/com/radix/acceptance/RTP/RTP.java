package com.radix.acceptance.RTP;

import com.radixdlt.client.application.RadixApplicationAPI.Result;
import com.radixdlt.client.application.RadixApplicationAPI.Transaction;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction;
import com.radixdlt.client.core.BootstrapConfig;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.particles.RRI;
import com.radixdlt.client.core.network.HttpClients;
import com.radixdlt.client.core.network.RadixNode;
import java.math.BigDecimal;
import java.util.Iterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.Test;

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction.TokenSupplyType;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.network.actions.SubmitAtomAction;
import com.radixdlt.client.core.network.actions.SubmitAtomStatusAction;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.reactivex.observers.TestObserver;

public class RTP {
	private static final BootstrapConfig BOOTSTRAP_CONFIG;
	static {
		String bootstrapConfigName = System.getenv("RADIX_BOOTSTRAP_CONFIG");
		if (bootstrapConfigName != null) {
			BOOTSTRAP_CONFIG = Bootstrap.valueOf(bootstrapConfigName);
		} else {
			BOOTSTRAP_CONFIG = Bootstrap.LOCALHOST;
		}
	}

	// FIXME: Seems to be *very* long now
	// Fix once synchronisation speed resolved
	private static final long SYNC_TIME_MS = 10_000;

	private static RadixApplicationAPI api;
	private static RadixNode node0;
	private static RadixNode node1;

	@BeforeClass
	public static void setUp() {
		RadixIdentity identity = RadixIdentities.createNew();
		api = RadixApplicationAPI.create(BOOTSTRAP_CONFIG, identity);
		api.discoverNodes();
		Iterator<RadixNode> nodes = api.getNetworkState()
			.doOnNext(System.out::println)
			.filter(state -> state.getNodes().size() > 1)
			.map(state -> state.getNodes().keySet())
			.timeout(15, TimeUnit.SECONDS)
			.blockingFirst()
			.iterator();

		node0 = nodes.next();
		node1 = nodes.next();
	}

    @Test
    public void test_submitted_atom_two_vertex_timestamps_are_close() throws Exception {
		TestObserver<SubmitAtomAction> observer = new TestObserver<>();
		RRI tokenRRI = RRI.of(api.getMyAddress(), "TOKEN");
		Transaction tx = api.createTransaction();
		tx.execute(CreateTokenAction.create(tokenRRI, "Token", "Token", BigDecimal.ZERO, BigDecimal.ONE, TokenSupplyType.MUTABLE));
		tx.commit(node0)
			.toObservable()
			.subscribe(observer);
		observer.awaitTerminalEvent();
		observer
			.assertSubscribed()
			.assertNoTimeout()
			.assertNoErrors()
			.assertComplete();

		// Make sure last event was a SubmitAtomResult
		observer.assertValueAt(observer.valueCount() - 1, saa -> saa instanceof SubmitAtomStatusAction);

		// Extract HID for query of "other" node
		String atomHID = observer.values().get(observer.valueCount() - 1).getAtom().getAid().toString();

		long timeout = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10);
		JSONArray atomArray;
		do {
			if (System.currentTimeMillis() > timeout) {
				fail("Took too long for second node to sync atom " + atomHID);
			}

			// Wait for a little bit to give the node a chance to do some stuff
			TimeUnit.MILLISECONDS.sleep(100);

			// Get atom from the "other" node
			Call call = HttpClients.getSslAllTrustingClient().newCall(node1.getHttpEndpoint("/api/atoms?aid=" + atomHID));
			final String result;
			try (Response response = call.execute()) {
				// Authorization required, skip for now
				if (response.code() == 401) {
					return;
				}

				result = response.body().string();
			}
			JSONObject json = new JSONObject(result);
			atomArray = json.getJSONArray("data");
		} while (atomArray.isEmpty());

		assertTrue("Only one atom", atomArray.length() == 1);

        JSONObject atom = atomArray.getJSONObject(0);
    	JSONObject temporalProof = atom.getJSONObject("temporalProof");
    	JSONArray verticesArray = temporalProof.getJSONArray("vertices");
    	assertTrue("Vertices array has two elements", verticesArray.length() == 2);

        JSONObject firstVertex = verticesArray.getJSONObject(0);
        JSONObject secondVertex = verticesArray.getJSONObject(1);

        long rclock1 = firstVertex.getLong("rclock");
        long rclock2 = secondVertex.getLong("rclock");

        assertTrue("Timestamps in order", rclock1 <= rclock2);
        assertTrue("Timestamps fairly close", Math.abs(rclock1 - rclock2) < SYNC_TIME_MS);
    }

    // Given that I have a connection to a Radix node,
    // When I query the API for an atom that exists,
    // I can see the timestamp included in the temporal proof vertices

    @Test
    public void test_atom_has_an_rclock_in_its_tp() throws Exception {
    	RRI tokenRRI = RRI.of(api.getMyAddress(), "HI");
		Transaction tx = api.createTransaction();
		tx.execute(CreateTokenAction.create(tokenRRI, "Token", "Token", BigDecimal.ZERO, BigDecimal.ONE, TokenSupplyType.MUTABLE));
		Result r = tx.commit(node0);
		Atom atom = r.getAtom();
		r.blockUntilComplete();

		Call call = HttpClients.getSslAllTrustingClient().newCall(node0.getHttpEndpoint("/api/atoms?aid=" + atom.getAid()));
		final String result;
		try (Response response = call.execute()) {
			// Authorization required, skip for now
			if (response.code() == 401) {
				return;
			}

			result = response.body().string();
		}

        JSONObject json = new JSONObject(result);
        assertTrue("Has data element", json.has("data"));

        JSONArray atomArray = json.getJSONArray("data");
        assertFalse("Atom array is not empty", atomArray.isEmpty());

        Object firstAtomObject = atomArray.get(0);
        assertTrue("First atom object is a JSONObject", firstAtomObject instanceof JSONObject);

        JSONObject firstAtom = (JSONObject) firstAtomObject;
        assertTrue("First atom has temporalProof", firstAtom.has("temporalProof"));

        JSONObject temporalProof = firstAtom.getJSONObject("temporalProof");
        assertTrue("Temporal proof has vertices", temporalProof.has("vertices"));

        JSONArray verticesArray = temporalProof.getJSONArray("vertices");
        assertFalse("Vertices array is not empty", verticesArray.isEmpty());

        Object firstVertexObject = verticesArray.get(0);
        assertTrue("First vertex object is a JSONObject", firstAtomObject instanceof JSONObject);

        JSONObject firstVertex = (JSONObject) firstVertexObject;
        assertTrue("Vertex has an rclock", firstVertex.has("rclock"));
    }


    // Given that I have a connection to a Radix node,
    // When I query the API for an atom that does not exist,
    // I can see an error returned -> I get an empty list returned

    @Test
    public void test_non_existent_atom_returns_an_error() throws Exception {
		Call call = HttpClients.getSslAllTrustingClient().newCall(node0.getHttpEndpoint("/api/atoms?uid=1234567890abcdef1234567890abcdef"));
		final String result;
		try (Response response = call.execute()) {
			// Authorization required, skip for now
			if (response.code() == 401) {
				return;
			}

			result = response.body().string();
		}

        JSONObject json = new JSONObject(result);
        assertTrue("Has data element", json.has("data"));

        //####  No error is returned, just an empty list
        JSONArray atomArray = json.getJSONArray("data");
        assertTrue("Atom array is empty", atomArray.isEmpty());
    }

    // Given that I have a connection to a Radix node,
    // When I query the API for the current RTP time,
    // I can see the time being returned

    @Test
    public void test_rtp_timestamp() throws Exception {
		Call call = HttpClients.getSslAllTrustingClient().newCall(node0.getHttpEndpoint("/api/rtp/timestamp"));
		final String result;
		try (Response response = call.execute()) {
			// Authorization required, skip for now
			if (response.code() == 401) {
				return;
			}
			result = response.body().string();
		}
        JSONObject json = new JSONObject(result);
        assertTrue("Has radix time", json.has("radix_time"));

        long rclock = json.getLong("radix_time");
        long now = System.currentTimeMillis() + 50; // Some padding for TCP round trip
        assertTrue("RTP Clock is too far in the past", rclock < now);
    }

    // Given that I have a connection to two different Radix nodes,
    // When I query the API for the current RTP time from both nodes,
    // I can see the time is the same within 5ms

    @Test
    public void test_rtp_timestamp_on_two_nodes() throws Exception {
		Call call = HttpClients.getSslAllTrustingClient().newCall(node0.getHttpEndpoint("/api/rtp/timestamp"));
		try (Response response = call.execute()) {
			// Authorization required, skip for now
			if (response.code() == 401) {
				return;
			}
		}

        // Note that we try multiple times here to avoid test unreliability
        // due to scheduling and network timing vagaries.
        for (int i = 1; i <= 10; ++i) {
            long diff = timeDifference();
            if (diff < 5) {
                return; // Success
            }
            System.out.format("Retrying attempt %s, result %sms%n", i, diff);
        }
        fail("No time difference less than 5ms seen");
    }

    private long timeDifference() throws InterruptedException, ExecutionException {
        ExecutorService exec = Executors.newFixedThreadPool(2);
        Semaphore sem = new Semaphore(0);

        Future<JSONObject> futureResult1 = triggeredQuery(exec, sem, node0, "/api/rtp/timestamp");
        Future<JSONObject> futureResult2 = triggeredQuery(exec, sem, node1, "/api/rtp/timestamp");

        // Sync up and release threads
        TimeUnit.MILLISECONDS.sleep(50);
        sem.release(2);

        JSONObject json1 = futureResult1.get();
        JSONObject json2 = futureResult2.get();

        exec.shutdown();

        assertTrue("Has radix time", json1.has("radix_time"));
        assertTrue("Has radix time", json2.has("radix_time"));

        long rclock1 = json1.getLong("radix_time");
        long rclock2 = json2.getLong("radix_time");

        return rclock1 - rclock2;
    }

    private Future<JSONObject> triggeredQuery(ExecutorService exec, Semaphore sem, RadixNode node, String path) {
        return exec.submit(() -> {
            if (!sem.tryAcquire(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timeout waiting for semaphore");
            }
			Call call = HttpClients.getSslAllTrustingClient().newCall(node.getHttpEndpoint(path));
			final String result;
			try (Response response = call.execute()) {
				result = response.body().string();
			}
            return new JSONObject(result);
        });
    }
}
