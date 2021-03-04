package com.radixdlt.test.chaos.utils;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.radixdlt.client.core.network.HttpClients;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.platform.commons.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

public class ChaosExperimentUtils {

    private static final Logger logger = LogManager.getLogger();

    /**
     * Will pseudo-randomly generate a number between 0 and 1 and will return true if its greater than the given threshold
     */
    public static boolean isSmallerThanFractionOfOne(double threshold) {
        return BigDecimal.valueOf(Math.random()).compareTo(BigDecimal.valueOf(threshold)) == -1;
    }

    public static void annotateGrafana(String text) {
        String token = System.getenv("GRAFANA_TOKEN");
        String dashboardId = System.getenv("GRAFANA_DASHBOARD_ID");
        if (StringUtils.isBlank(token) || StringUtils.isBlank(dashboardId)) {
            logger.warn("No GRAFANA_TOKEN or GRAFANA_DASHBOARD_ID provided, will not annotate");
        } else {
            String payload = createJsonString(text, dashboardId);
            Request annotationRequest = new Request.Builder()
                    .addHeader("Authorization", "Bearer " + token)
                    .addHeader("Content-Type", "application/json")
                    .method("POST", RequestBody.create(MediaType.get("application/json"), payload))
                    .url("https://radixdlt.grafana.net/api/annotations")
                    .build();
            try {
                HttpClients.getSslAllTrustingClient().newCall(annotationRequest).execute();
            } catch (IOException e) {
                logger.error(e);
            }
        }
    }

    public static void waitSeconds(int seconds) {
        long start = System.currentTimeMillis();
        await().atMost(10, TimeUnit.MINUTES)
                .until(() -> (System.currentTimeMillis() > start + 1000L * seconds));
    }

    public static String getSshIdentityLocation() {
        return Optional.ofNullable(System.getenv("SSH_IDENTITY")).orElse(System.getenv("HOME") + "/.ssh/id_rsa");
    }

    public static String runCommandOverSsh(String host, String command) {
        JSch jsch = new JSch();
        Session session = null;
        ChannelExec channelExec = null;
        try {
            jsch.addIdentity(getSshIdentityLocation());
            session = jsch.getSession("radix", host, 22);
            Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect();

            channelExec = (ChannelExec) session.openChannel("exec");
            channelExec.setCommand(command);
            channelExec.setErrStream(System.err);
            InputStream in = channelExec.getInputStream();
            channelExec.connect(5000);

            String commandOutput = IOUtils.toString(in, StandardCharsets.UTF_8);
            if (channelExec.getExitStatus() == 1) {
                throw new RuntimeException("Command " + command + " failed, see log.");
            }
            return commandOutput;
        } catch (JSchException | IOException e) {
            throw new RuntimeException(e);
        } finally {
            session.disconnect();
            if (channelExec != null) {
                channelExec.disconnect();
            }
        }
    }

    private static String createJsonString(String text, String dashboardId) {
        JSONObject requestJson = new JSONObject();
        requestJson.put("dashboardId", Integer.parseInt(dashboardId));
        requestJson.put("text", text);
        JSONArray tagArray = new JSONArray();
        tagArray.put("chaos");
        requestJson.put("tags", tagArray);
        return requestJson.toString();
    }

}
