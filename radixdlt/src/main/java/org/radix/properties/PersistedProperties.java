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

package org.radix.properties;

import org.apache.commons.cli.ParseException;
import org.radix.logging.Logger;
import org.radix.logging.Logging;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

/**
 * Persisted properties are property sets that are committed to storage and may be loaded on the next client execution
 *
 * @author Dan Hughes
 *
 */

public class PersistedProperties {
	private static final Logger log = Logging.getLogger ();

	private final Properties properties;

	PersistedProperties()
	{
		this.properties = new Properties();
	}

	public void load(String filename) throws ParseException {
		boolean loaded = false;
		File file = new File(filename);

		if (file.exists()) {
			try (FileInputStream propertiesInput = new FileInputStream(file)) {
				this.properties.load(propertiesInput);
				// No need to immediately save, we just loaded them
				log.info("Loaded properties from " + file);
				loaded = true;
			} catch (IOException ex) {
				log.error("Can not open properties file " + file + ", using default", ex);
			}
		} else {
			log.info("Properties file " + file + " not found, using default");
		}

		// Try in resource if not loaded
		if (!loaded) {
			try (InputStream propertiesInput = this.getClass().getResourceAsStream("/" + filename)) {
				if (propertiesInput == null) {
					throw new FileNotFoundException("Default properties for " + file + " not found");
				}
				this.properties.load(propertiesInput);
				log.info("Loaded default properties for " + file);
				// Save to file, so they can be edited later
				if (file.canWrite()) {
					save(filename);
				}
				log.info("Saved default properties in " + file);
			} catch (IOException ex) {
				log.fatal("Can not load default properties for " + file, ex);
				throw new ParseException("Can not load properties file, fatal!");
			}
		}
	}

	public void save(String filename) throws IOException {
		try (OutputStream propertiesOutput = new FileOutputStream(new File(filename))) {
			properties.store(propertiesOutput, "");
		}
	}

	/**
	 * Returns true of property contains the key, otherwise false
	 */
	public boolean has(String key)
	{
		return properties.containsKey(key);
	}

	/**
	 * Returns a property value
	 */
	public String get(String key)
	{
		return properties.getProperty(key);
	}

	public byte get(String key, byte defaultValue) {
		String value = get(key);
		return value == null ? defaultValue : Byte.parseByte(value);
	}

	public short get(String key, short defaultValue) {
		String value = get(key);
		return value == null ? defaultValue : Short.parseShort(value);
	}

	public int get(String key, int defaultValue) {
		String value = get(key);
		return value == null ? defaultValue : Integer.parseInt(value);
	}

	public long get(String key, long defaultValue) {
		String value = get(key);
		return value == null ? defaultValue : Long.parseLong(value);
	}

	public boolean get(String key, boolean defaultValue) {
		String value = get(key);
		return value == null ? defaultValue : parseBoolean(value);
	}

	public String get(String key, String defaultValue) {
		String value = get(key);
		return value == null ? defaultValue : value;
	}

	private boolean parseBoolean(String value) {
		if (value == null) {
			return false;
		}
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

	/**
	 * Sets a property value
	 */
	public void set(String key, Object value)
	{
		properties.setProperty(key, value.toString());
	}
}
