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

package com.radixdlt.utils.time;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NtpService {
  private static final Logger log = LogManager.getLogger();

  private String server = null;
  private double roundTripDelay = 0;
  private double localClockOffset = 0;

  private int attempts = 0;
  private int offset = 0;

  public NtpService(String server) {
    this.server = server;

    roundTripDelay = 0;
    localClockOffset = 0;

    if (server != null) {
      initFromServer();
    }
  }

  private void initFromServer() {
    if (server != null) {
      boolean success = false;

      while (attempts < 3 && !success) {
        try (DatagramSocket socket = new DatagramSocket()) {
          // Send request
          socket.setSoTimeout(5000);

          var address = InetAddress.getByName(server);
          var buf = new NtpMessage().toByteArray();
          var packet = new DatagramPacket(buf, buf.length, address, 123);

          // Set the transmit timestamp *just* before sending the packet
          // ToDo: Does this actually improve performance or not?
          NtpMessage.encodeTimestamp(
              packet.getData(), 40, (System.currentTimeMillis() / 1000.0) + 2208988800.0);

          socket.send(packet);

          // Get response
          log.info("NTP request sent, waiting for response...\n");
          packet = new DatagramPacket(buf, buf.length);
          socket.receive(packet);

          // Immediately record the incoming timestamp
          double destinationTimestamp = (System.currentTimeMillis() / 1000.0) + 2208988800.0;

          // Process response
          NtpMessage msg = new NtpMessage(packet.getData());

          // Corrected, according to RFC2030 errata
          roundTripDelay =
              (destinationTimestamp - msg.originateTimestamp)
                  - (msg.transmitTimestamp - msg.receiveTimestamp);
          localClockOffset =
              ((msg.receiveTimestamp - msg.originateTimestamp)
                      + (msg.transmitTimestamp - destinationTimestamp))
                  / 2;

          log.info(msg.toString());
          success = true;
        } catch (Exception ex) {
          if (attempts >= 3) {
            throw new NtpException("failed to start NTP service", ex);
          }
        } finally {
          attempts++;
        }
      }

      if (!success) {
        throw new NtpException("Unable to start NTP service using " + server);
      }
    }
  }

  public boolean isSynchronized() {
    return server != null;
  }

  /**
   * Returns the offset in seconds set in this NtpService
   *
   * @return
   */
  public int getOffset() {
    return offset;
  }

  /**
   * Sets the offset in seconds for this NtpService
   *
   * @param offset
   */
  public void setOffset(int offset) {
    this.offset = offset;
  }

  /**
   * Returns a corrected System time
   *
   * @return
   */
  public long getSystemTime() {
    return (long) (System.currentTimeMillis() + (localClockOffset * 1000.0));
  }

  /**
   * Returns a corrected UTC time in seconds
   *
   * @return
   */
  public synchronized int getUTCTimeSeconds() {
    return (int) (getUTCTimeMS() / 1000L);
  }

  /**
   * Returns a corrected UTC time in milliseconds
   *
   * @return
   */
  public synchronized long getUTCTimeMS() {
    return (long)
        (System.currentTimeMillis()
            + (localClockOffset * 1000.0)
            + (roundTripDelay * 1000.0)
            + (offset * 1000L));
  }
}
