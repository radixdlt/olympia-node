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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

        long rclock = firstVertex.getLong("rclock");
        assertTrue("RTP Clock is in the past", rclock <= System.currentTimeMillis());
    }


    // Given that I have a connection to a Radix node,
    // When I query the API for an atom that does not exist,
    // I can see an error returned

    //####  No error is returned, just an empty list

    @Test
    public void test_non_existent_atom_returns_an_error() {
        String result = getURL("http://localhost:8080/api/atoms?uid=1234567890abcdef1234567890abcdef");
        JSONObject json = new JSONObject(result);
        assertFalse("Has data element", json.has("data"));
    }

    // Given that I have a connection to a Radix node,
    // When I query the API for the current RTP time,
    // I can see the time being returned

    @Test
    public void test_rtp_timestamp() {
        String result = getURL("http://localhost:8080/api/rtp/timestamp");
        JSONObject json = new JSONObject(result);
        assertTrue("Has radix time", json.has("radix_time"));

        long rclock = json.getLong("radix_time");
        assertTrue("RTP Clock is in the past", rclock <= System.currentTimeMillis());
    }

    // Given that I have a connection to two different Radix nodes,
    // When I query the API for the current RTP time from both nodes,
    // I can see the time is the same within 5ms

    @Test
    public void test_rtp_timestamp_on_two_nodes() {
        String result1 = getURL("http://localhost:8080/api/rtp/timestamp");
        JSONObject json1 = new JSONObject(result1);
        assertTrue("Has radix time", json1.has("radix_time"));

        long rclock1 = json1.getLong("radix_time");
        assertTrue("RTP Clock 1 is in the past", rclock1 <= System.currentTimeMillis());

        String result2 = getURL("http://localhost:8081/api/rtp/timestamp");
        JSONObject json2 = new JSONObject(result2);
        assertTrue("Has radix time", json2.has("radix_time"));

        long rclock2 = json2.getLong("radix_time");
        assertTrue("RTP Clock 2 is in the past", rclock2 <= System.currentTimeMillis());

        assertTrue("RTP clockks differ by less than 5ms", abs(rclock1 - rclock2) < 5);
    }
}
