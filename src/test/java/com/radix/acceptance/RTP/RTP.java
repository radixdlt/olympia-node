package com.radix.acceptance.RTP;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import com.google.common.io.CharStreams;

import static java.lang.Math.abs;
import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class RTP {

    public String getURL(String url) {
        try {
            URL requestUrl = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) requestUrl.openConnection();
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Accept-Charset", "utf-8");
            try (InputStream inputStream = connection.getInputStream()) {
                return CharStreams.toString(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // Given that I have a connection to a Radix node,
    // When I query the API for an atom that exists,
    // I can see the timestamp included in the temporal proof vertices

    @Test
    public void test_atom_has_an_rclock_in_its_tp() {
        String result = getURL("http://localhost:8080/api/atoms");
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
    public void test_non_existent_atom_returns_an_error() {
        String result = getURL("http://localhost:8080/api/atoms?uid=1234567890abcdef1234567890abcdef");
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
    public void test_rtp_timestamp() {
        String result = getURL("http://localhost:8080/api/rtp/timestamp");
        JSONObject json = new JSONObject(result);
        assertTrue("Has radix time", json.has("radix_time"));
    }

    // Given that I have a connection to two different Radix nodes,
    // When I query the API for the current RTP time from both nodes,
    // I can see the time is the same within 5ms

    @Test
    public void test_rtp_timestamp_on_two_nodes() throws InterruptedException, ExecutionException {
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

        Future<JSONObject> futureResult1 = triggeredQuery(exec, sem, "http://localhost:8080/api/rtp/timestamp");
        Future<JSONObject> futureResult2 = triggeredQuery(exec, sem, "http://localhost:8081/api/rtp/timestamp");

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

    private Future<JSONObject> triggeredQuery(ExecutorService exec, Semaphore sem, String url) {
        return exec.submit(() -> {
            if (!sem.tryAcquire(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timeout waiting for semaphore");
            }
            return new JSONObject(getURL(url));
        });
    }
}
