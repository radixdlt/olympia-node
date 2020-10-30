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

package com.radixdlt.properties;

import org.apache.commons.cli.ParseException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.Properties;

/**
 * Persisted properties are property sets that are committed to storage
 * and may be reloaded on the next application execution.
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
		boolean loaded = false;
		File file = new File(filename);

		try (FileInputStream propertiesInput = new FileInputStream(file)) {
			this.properties.load(propertiesInput);
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
			try (InputStream propertiesInput = this.getClass().getResourceAsStream("/" + filename)) {
				if (propertiesInput == null) {
					throw new FileNotFoundException("Default properties for " + file + " not found");
				}
				this.properties.load(propertiesInput);
				log.info("Loaded default properties for {}", file);
				// Save to file, so they can be edited later
				save(filename);
				log.info("Saved default properties in {}", file);
			} catch (FileNotFoundException ex) {
				log.error("Can not find properties file {}", file);
				throw new ParseException("Can not find properties file " + file);
			} catch (IOException ex) {
				log.error(String.format("Can not load default properties for %s", file), ex);
				throw new ParseException(String.format("Can not load properties file '%s' (%s)", file, ex.getMessage()));
			}
		}
	}

	/**
	 * Saves properties to the specified file.
	 *
	 * @param filename The file to save the properties to
	 * @throws IOException if the properties cannot be saved
	 */
	public void save(String filename) throws IOException {
		try (OutputStream propertiesOutput = new FileOutputStream(new File(filename))) {
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
		String value = get(key);
		try {
			return value == null ? defaultValue : Integer.parseInt(value);
		} catch (NumberFormatException ex) {
			String msg = String.format("Exception while retrieving integer value for %s: '%s'", key, value);
			throw new IllegalArgumentException(msg, ex);
		}
	}

	/**
	 * Returns a property value as a {@code long}.
	 *
	 * @param key the property key
	 * @param defaultValue the default value returned if no value is set
	 * @return either the property value, or {@code defaultValue} if no value set
	 */
	public long get(String key, long defaultValue) {
		String value = get(key);
		try {
			return value == null ? defaultValue : Long.parseLong(value);
		} catch (NumberFormatException ex) {
			String msg = String.format("Exception while retrieving long value for %s: '%s'", key, value);
			throw new IllegalArgumentException(msg, ex);
		}
	}

	/**
	 * Returns a property value as a {@code double}.
	 *
	 * @param key the property key
	 * @param defaultValue the default value returned if no value is set
	 * @return either the property value, or {@code defaultValue} if no value set
	 */
	public double get(String key, double defaultValue) {
		String value = get(key);
		try {
			return value == null ? defaultValue : Double.parseDouble(value);
		} catch (NumberFormatException ex) {
			String msg = String.format("Exception while retrieving double value for %s: '%s'", key, value);
			throw new IllegalArgumentException(msg, ex);
		}
	}

	/**
	 * Returns a property value as a {@code boolean}.
	 * Note that the strings "true", ignoring case, and correctly formatted non-zero
	 * integers are {@code true} values, all others are {@code false}.
	 *
	 * @param key the property key
	 * @param defaultValue the default value returned if no value is set
	 * @return either the property value, or {@code defaultValue} if no value set
	 */
	public boolean get(String key, boolean defaultValue) {
		String value = get(key);
		return value == null ? defaultValue : parseBoolean(value);
	}

	/**
	 * Returns a property value as a {@code String}.
	 *
	 * @param key the property key
	 * @param defaultValue the default value returned if no value is set
	 * @return either the property value, or {@code defaultValue} if no value set
	 */
	public String get(String key, String defaultValue) {
		String value = get(key);
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
		PersistedProperties other = (PersistedProperties) obj;
		return Objects.equals(this.properties, other.properties);
	}

	@Override
	public String toString() {
		return this.properties.keySet().toString();
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
