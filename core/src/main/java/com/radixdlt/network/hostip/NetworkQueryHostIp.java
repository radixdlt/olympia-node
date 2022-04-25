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

package com.radixdlt.network.hostip;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.io.CharStreams;
import com.google.common.net.HostAndPort;
import com.radixdlt.utils.properties.RuntimeProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Query for a public IP address using an oracle. This class can be used to query a single oracle,
 * or if a number of oracles are provided, a simple majority vote is used.
 */
final class NetworkQueryHostIp implements HostIp {
  private static final Logger log = LogManager.getLogger();

  @VisibleForTesting static final String QUERY_URLS_PROPERTY = "network.host_ip_query_urls";

  @VisibleForTesting
  static final ImmutableList<URL> DEFAULT_QUERY_URLS =
      ImmutableList.of(
          makeurl("https://checkip.amazonaws.com/"),
          makeurl("https://ipv4.icanhazip.com/"),
          makeurl("https://myexternalip.com/raw"),
          makeurl("https://ipecho.net/plain"),
          makeurl("https://bot.whatismyipaddress.com/"),
          makeurl("https://www.trackip.net/ip"),
          makeurl("https://ifconfig.co/ip"));

  static HostIp create(Collection<URL> urls) {
    return new NetworkQueryHostIp(urls);
  }

  static HostIp create(RuntimeProperties properties) {
    String urlsProperty = properties.get(QUERY_URLS_PROPERTY, "");
    if (urlsProperty == null || urlsProperty.trim().isEmpty()) {
      return create(DEFAULT_QUERY_URLS);
    }
    ImmutableList<URL> urls =
        Arrays.asList(urlsProperty.split(",")).stream()
            .map(NetworkQueryHostIp::makeurl)
            .collect(ImmutableList.toImmutableList());
    return create(urls);
  }

  private final List<URL> hosts;
  private final Supplier<Optional<String>> result = Suppliers.memoize(this::get);

  NetworkQueryHostIp(Collection<URL> urls) {
    if (urls.isEmpty()) {
      throw new IllegalArgumentException("At least one URL must be specified");
    }
    this.hosts = new ArrayList<>(urls);
  }

  int count() {
    return this.hosts.size();
  }

  @Override
  public Optional<String> hostIp() {
    return result.get();
  }

  Optional<String> get() {
    return publicIp((count() + 1) / 2); // Round up
  }

  Optional<String> publicIp(int threshold) {
    // Make sure we don't DoS the first one on the list
    Collections.shuffle(this.hosts);
    log.debug("Using hosts {}", this.hosts);
    final Map<HostAndPort, AtomicInteger> ips = Maps.newHashMap();
    for (URL url : this.hosts) {
      HostAndPort q = query(url);
      if (q != null) {
        int newValue = ips.computeIfAbsent(q, k -> new AtomicInteger()).incrementAndGet();
        if (newValue >= threshold) {
          log.info("Found address {}", q);
          return Optional.of(q.getHost());
        }
      }
    }
    log.info("No suitable address found");
    return Optional.empty();
  }

  @VisibleForTesting
  static HostAndPort query(URL url) {
    try {
      HttpURLConnection con = (HttpURLConnection) url.openConnection();
      con.setRequestMethod("GET");
      // Pretty much required by shared hosting
      con.setRequestProperty("Host", url.getHost());

      // A user agent is required by some hosts
      con.setRequestProperty("User-Agent", "curl/7.58.0");

      // Some don't like it unless an accept is set
      con.setRequestProperty("Accept", "*/*");

      int status = con.getResponseCode();
      if (status > 299) {
        log.debug("Host {} failed with status {}", url, status);
        return null;
      }

      try (InputStream is = con.getInputStream();
          InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8)) {
        String result = CharStreams.toString(isr).trim();
        log.debug("Host {} returned {}", url, result);
        return HostAndPort.fromHost(result);
      }
    } catch (IOException | IllegalArgumentException ex) {
      // Ignored
      if (log.isDebugEnabled()) {
        log.debug(String.format("Host %s failed with exception", url), ex);
      }
      return null;
    }
  }

  private static URL makeurl(String s) {
    try {
      return new URL(s);
    } catch (MalformedURLException ex) {
      throw new IllegalStateException("While constructing URL for " + s, ex);
    }
  }
}
