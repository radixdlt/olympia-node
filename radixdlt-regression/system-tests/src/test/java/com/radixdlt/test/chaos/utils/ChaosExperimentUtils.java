/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.test.chaos.utils;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.radixdlt.client.lib.network.HttpClientUtils;
import com.radixdlt.test.chaos.ansible.AnsibleImageWrapper;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.platform.commons.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

public class ChaosExperimentUtils {
	private ChaosExperimentUtils() {
	}

	private static final Logger logger = LogManager.getLogger();

	/**
	 * Will pseudo-randomly generate a number between 0 and 1 and will return true if its greater than the given threshold
	 */
	public static boolean isSmallerThanFractionOfOne(double threshold) {
		return BigDecimal.valueOf(Math.random()).compareTo(BigDecimal.valueOf(threshold)) == -1;
	}

	public static void annotateGrafana(String text) {
		var token = System.getenv("GRAFANA_TOKEN");
		var dashboardId = System.getenv("GRAFANA_DASHBOARD_ID");

		if (StringUtils.isBlank(token) || StringUtils.isBlank(dashboardId)) {
			logger.warn("No GRAFANA_TOKEN or GRAFANA_DASHBOARD_ID provided, will not annotate");
			return;
		}

		var client = HttpClientUtils.unsafeBuildHttpClient(Duration.of(30, ChronoUnit.SECONDS));
		var request = HttpRequest.newBuilder()
			.header("Authorization", "Bearer " + token)
			.header("Content-Type", "application/json")
			.POST(BodyPublishers.ofString(createJsonString(text, dashboardId)))
			.uri(URI.create("https://radixdlt.grafana.net/api/annotations"))
			.build();

		try {
			client.send(request, HttpResponse.BodyHandlers.discarding());
		} catch (IOException | InterruptedException e) {
			logger.error(e);
		}
	}

	public static void waitSeconds(int seconds) {
		long start = System.currentTimeMillis();
		await().atMost(50, TimeUnit.MINUTES)
			.until(() -> (System.currentTimeMillis() > start + (1000L * seconds)));
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

	public static void startMempoolFillers(AnsibleImageWrapper ansible, int numberOfFillersToStart) {
		ansible.getNodeAddressList().stream().limit(numberOfFillersToStart).forEach(host -> {
			String response = toggleMempoolfillerInContainer(host, true);
			logger.info("Response from {}: {}", host, response);
		});
	}

	public static void stopAllMempoolFillers(AnsibleImageWrapper ansible) {
		ansible.getNodeAddressList().forEach(host -> toggleMempoolfillerInContainer(host, false));
	}

	private static String toggleMempoolfillerInContainer(String host, boolean enable) {
		String command = " docker exec radixdlt_core_1 bash -c 'curl -s -X PUT localhost:3333/chaos/mempool-filler -d "
			+ "'{\"enabled\":" + enable + "}''";
		return runCommandOverSsh(host, command);
	}
}
