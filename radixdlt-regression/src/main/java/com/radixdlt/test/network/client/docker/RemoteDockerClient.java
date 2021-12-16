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

package com.radixdlt.test.network.client.docker;

import com.github.dockerjava.api.exception.DockerClientException;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.radixdlt.test.network.RadixNetworkConfiguration;
import com.radixdlt.test.network.SshConfiguration;
import com.radixdlt.test.utils.TestingUtils;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A class that can run commands (including docker commands e.g. 'docker restart') over ssh */
public class RemoteDockerClient implements DockerClient {

  // testing the remote client
  public static void main(String[] args) {
    var configuration = RadixNetworkConfiguration.fromEnv();
    DockerNetworkCreator.initializeAndStartNodeFromTrustedNode(
        configuration, "host", "trusted host");
  }

  private static Logger logger = LoggerFactory.getLogger(RemoteDockerClient.class);

  private final SshConfiguration sshConfiguration;
  private final String containerName;

  public RemoteDockerClient(RadixNetworkConfiguration configuration) {
    this(
        configuration.getSshConfiguration(),
        configuration.getDockerConfiguration().getContainerName());
  }

  public RemoteDockerClient(SshConfiguration sshConfiguration, String containerName) {
    this.sshConfiguration = sshConfiguration;
    this.containerName = containerName;
  }

  /** this is basically the official example from jsch, copied */
  public String runCommand(String nodeLocator, String... commands) {
    logger.debug("Run command [{}] at {}", commands, nodeLocator);
    checkProperties();
    var jsch = new JSch();
    Session session = null;
    ChannelExec channel = null;
    try {
      jsch.addIdentity(
          sshConfiguration.getSshKeyLocation(), sshConfiguration.getSshKeyPassphrase());
      session =
          jsch.getSession(sshConfiguration.getUser(), nodeLocator, sshConfiguration.getPort());
      var config = new java.util.Properties();
      config.put("StrictHostKeyChecking", "no");
      session.setConfig(config);
      session.connect(10000);
      logger.trace("Connected via ssh to {}", nodeLocator);

      channel = (ChannelExec) session.openChannel("exec");
      channel.setCommand(String.join(" ", commands));
      try (var outputBuffer = new ByteArrayOutputStream();
          var errorBuffer = new ByteArrayOutputStream()) {
        var inputStream = channel.getInputStream();
        var errorStream = channel.getExtInputStream();
        channel.connect();

        byte[] tmp = new byte[1024];
        while (true) {
          while (inputStream.available() > 0) {
            int i = inputStream.read(tmp, 0, 1024);
            if (i < 0) {
              break;
            }
            outputBuffer.write(tmp, 0, i);
          }
          while (errorStream.available() > 0) {
            int i = errorStream.read(tmp, 0, 1024);
            if (i < 0) {
              break;
            }
            errorBuffer.write(tmp, 0, i);
          }
          if (channel.isClosed()) {
            if ((inputStream.available() > 0) || (errorStream.available() > 0)) {
              continue;
            }
            logger.debug("Command exit-status: {}", channel.getExitStatus());
            break;
          }
          TestingUtils.sleepMillis(250);
        }
        var error = errorBuffer.toString(StandardCharsets.UTF_8);
        var output = outputBuffer.toString(StandardCharsets.UTF_8);
        return (StringUtils.isBlank(output)) ? error : output;
      }
    } catch (IOException | JSchException e) {
      // any error here should be escalated
      throw new DockerClientException(e.getMessage(), e);
    } finally {
      if (session != null) {
        session.disconnect();
      }
      if (channel != null) {
        channel.disconnect();
      }
    }
  }

  private void checkProperties() {
    if (sshConfiguration.getPort() == -1 || StringUtils.isBlank(sshConfiguration.getUser())) {
      throw new IllegalArgumentException(
          "You need to set RADIXDLT_SYSTEM_TESTING_SSH_KEY_USER and "
              + "RADIXDLT_SYSTEM_TESTING_SSH_KEY_PORT to run commands over SSH");
    }
  }

  @Override
  public void restartNode(String nodeLocator) {
    runCommand(nodeLocator, "docker restart " + containerName);
  }

  @Override
  public void stopNode(String nodeLocator) {
    runCommand(nodeLocator, "docker stop " + containerName);
  }

  @Override
  public void cleanup(String... parameters) {
    List.of(parameters).forEach(host -> runCommand(host, "docker start " + containerName));
  }
}
