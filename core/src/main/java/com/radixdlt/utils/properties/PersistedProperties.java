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

package com.radixdlt.utils.properties;

import static com.radixdlt.utils.RadixConstants.STANDARD_CHARSET;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Function;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Persisted properties are property sets that are committed to storage and may be reloaded on the
 * next application execution.
 */
public class PersistedProperties {
  private static final Logger log = LogManager.getLogger();

  private final Properties properties;

  PersistedProperties() {
    this.properties = new Properties();
  }

  /**
   * Loads properties from specified file.
   *
   * @param filename the file to load the properties from
   * @throws ParseException if the properties cannot be loaded
   */
  public void load(String filename) throws ParseException {
    var loaded = false;
    var file = new File(filename);

    try (var propertiesInput = new FileInputStream(file)) {
      loadProperties(propertiesInput);
      // No need to immediately save, we just loaded them
      log.info("Loaded properties from {}", file);
      loaded = true;
    } catch (FileNotFoundException ex) {
      log.info("Can not open properties file {}, using default", file);
    } catch (IOException ex) {
      log.error(String.format("Can not open properties file %s, using default", file), ex);
    }

    // Try in resource if not loaded
    if (!loaded) {
      try (var propertiesInput = this.getClass().getResourceAsStream("/" + filename)) {
        if (propertiesInput == null) {
          throw new FileNotFoundException("Default properties for " + file + " not found");
        }
        loadProperties(propertiesInput);
        log.info("Loaded default properties for {}", file);
        // Save to file, so they can be edited later
        save(filename);
        log.info("Saved default properties in {}", file);
      } catch (FileNotFoundException ex) {
        log.error("Can not find properties file {}", file);
        throw new ParseException("Can not find properties file " + file);
      } catch (IOException ex) {
        log.error(String.format("Can not load default properties for %s", file), ex);
        throw new ParseException(
            String.format("Can not load properties file '%s' (%s)", file, ex.getMessage()));
      }
    }
  }

  private void loadProperties(InputStream is) throws IOException {
    var out = new ByteArrayOutputStream();

    // Strip redundant whitespace characters
    try (var writer = new BufferedWriter(new OutputStreamWriter(out))) {
      try (var reader = new BufferedReader(new InputStreamReader(is, STANDARD_CHARSET))) {
        for (var line = reader.readLine(); line != null; line = reader.readLine()) {
          writer.write(line.trim());
          writer.newLine();
        }
      }
    }

    this.properties.load(new ByteArrayInputStream(out.toByteArray()));
  }

  /**
   * Saves properties to the specified file.
   *
   * @param filename The file to save the properties to
   * @throws IOException if the properties cannot be saved
   */
  public void save(String filename) throws IOException {
    try (var propertiesOutput = new FileOutputStream(filename)) {
      this.properties.store(propertiesOutput, "");
    }
  }

  /**
   * Returns the property if there is a property matching the key, otherwise {@code null}.
   *
   * @param key the property key
   * @return the property if there is a property matching the key, otherwise {@code null}
   */
  public String get(String key) {
    return this.properties.getProperty(key);
  }

  /**
   * Returns a property value as an {@code int}.
   *
   * @param key the property key
   * @param defaultValue the default value returned if no value is set
   * @return either the property value, or {@code defaultValue} if no value set
   */
  public int get(String key, int defaultValue) {
    return get(key, defaultValue, Integer::parseInt);
  }

  /**
   * Returns a property value as a {@code long}.
   *
   * @param key the property key
   * @param defaultValue the default value returned if no value is set
   * @return either the property value, or {@code defaultValue} if no value set
   */
  public long get(String key, long defaultValue) {
    return get(key, defaultValue, Long::parseLong);
  }

  /**
   * Returns a property value as a {@code double}.
   *
   * @param key the property key
   * @param defaultValue the default value returned if no value is set
   * @return either the property value, or {@code defaultValue} if no value set
   */
  public double get(String key, double defaultValue) {
    return get(key, defaultValue, Double::parseDouble);
  }

  /**
   * Returns a property value as a {@code boolean}. Note that the strings "true", ignoring case, and
   * correctly formatted non-zero integers are {@code true} values, all others are {@code false}.
   * Empty value handled as if value was missing.
   *
   * @param key the property key
   * @param defaultValue the default value returned if no value is set
   * @return either the property value, or {@code defaultValue} if no value set
   */
  public boolean get(String key, boolean defaultValue) {
    var value = get(key);
    return (value == null || value.trim().isEmpty()) ? defaultValue : parseBoolean(value);
  }

  /**
   * Returns a property value as a {@code String}.
   *
   * @param key the property key
   * @param defaultValue the default value returned if no value is set
   * @return either the property value, or {@code defaultValue} if no value set
   */
  public String get(String key, String defaultValue) {
    var value = get(key);
    return value == null ? defaultValue : value;
  }

  /**
   * Sets a property value.
   *
   * @param key the property name
   * @param value the property value
   */
  public void set(String key, Object value) {
    this.properties.setProperty(key, value.toString());
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.properties);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }

    if (obj == null || !this.getClass().equals(obj.getClass())) {
      return false;
    }

    var other = (PersistedProperties) obj;
    return Objects.equals(this.properties, other.properties);
  }

  @Override
  public String toString() {
    return this.properties.keySet().toString();
  }

  private <T> T get(String key, T defaultValue, Function<String, T> parser) {
    var value = get(key);
    try {
      return value == null ? defaultValue : parser.apply(value);
    } catch (NumberFormatException ex) {
      var msg = String.format("Exception while retrieving double value for %s: '%s'", key, value);
      throw new IllegalArgumentException(msg, ex);
    }
  }

  private boolean parseBoolean(String value) {
    if (value.trim().equalsIgnoreCase("true")) {
      return true;
    }
    // Try as 0 = false, not-0 = true
    try {
      int i = Integer.parseInt(value);
      return i != 0;
    } catch (NumberFormatException e) {
      return false;
    }
  }
}
