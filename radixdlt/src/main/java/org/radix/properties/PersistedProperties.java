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
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * Persisted properties are property sets that are committed to storage and may be loaded on the next client execution
 * 
 * @author Dan Hughes
 *
 */

public class PersistedProperties
{
	private static final Logger log = Logging.getLogger ();
	
	Properties properties;
	
	PersistedProperties()
	{
		properties = new Properties();
	}

	PersistedProperties(Properties properties) 
	{
		this.properties = (Properties) properties.clone();
	}

	public void load(String filename) throws FileNotFoundException, ParseException 
	{
		InputStream propertiesInput = null;
		
		try
		{
			File file = new File(filename);

			if (file.exists()) {
				propertiesInput = new FileInputStream(file);
				log.info("Loaded "+file+" properties");
			} else {
				log.info("Properties file " + file + " not found, using default");
			}
		}
		catch (Exception ex)
		{
			log.error("Can not open properties file " + filename + ", using default", ex);
		}
			
		// Try in resource ? //
		if (propertiesInput == null)
		{
			propertiesInput = this.getClass().getResourceAsStream("/" + filename);
			
			if (propertiesInput == null)
				throw new FileNotFoundException(filename+" properties not found in resources");

			log.info("Loaded "+filename+" properties from resources");
		}
		
		if (propertiesInput != null)
		{
			try
			{
				properties.load(propertiesInput);
			
				propertiesInput.close();
	
				log.info("Finished parsing properties ");
				if (new File(filename).canWrite()) {
					save(filename);
				}
			}
			catch (Exception ex)
			{
				log.error("Can not load properties file, fatal!", ex);
				throw new ParseException("Can not load properties file, fatal!");
			}
		}
	}
	
	public void save(String filename) throws IOException
	{
		OutputStream propertiesOutput = new FileOutputStream(new File(filename));
		
		properties.store(propertiesOutput, "");
		
		propertiesOutput.close();
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
	
	/**
	 * Returns a property as a Set<String>
	 */
	public Set<String> getAsSet(String key)
	{
		Set<String> set = new HashSet<String>();
		
		if (properties.containsKey(key))
		{
			StringTokenizer strings = new StringTokenizer(properties.getProperty(key), ",");
			
			while(strings.hasMoreTokens())
				set.add(strings.nextToken());
		}
		
		return set;
	}
	
	/**
	 * Returns a property value with default if not found
	 */
	public <T> T get(String key, T defaultValue)
	{
		String value = properties.getProperty(key);
		
		if (value == null)
			return defaultValue;
		
		if (defaultValue == null)
			return (T) value;
		else if (defaultValue instanceof Byte)
			return (T) Byte.valueOf(value);
		else if (defaultValue instanceof Short)
			return (T) Short.valueOf(value);
		else if (defaultValue instanceof Integer)
			return (T) Integer.valueOf(value);
		else if (defaultValue instanceof Long)
			return (T) Long.valueOf(value);
		else if (defaultValue instanceof Float)
			return (T) Float.valueOf(value);
		else if (defaultValue instanceof Double)
			return (T) Double.valueOf(value);
		else if (defaultValue instanceof Boolean)
			return (T) Boolean.valueOf(value);
		else if (defaultValue instanceof String)
			return (T) value;
		
		return null;
	}
	
	public Enumeration<?> propertyNames()
	{
		return properties.propertyNames();
	}

	/**
	 * Sets a property value
	 */
	public void set(String key, Object value)
	{
		properties.setProperty(key, value.toString());
	}

	/**
	 * Returns an array of property values delimited by ","
	 */
/*	public String[]	tokenized(String query)
	{
		String propertyValue = properties.getProperty(key);
		
		String[] propertyValues = propertyValue.split(",");

		return propertyValues;
	}*/
	
	/**
	 * Sets an array of property values delimited by ","
	 */
/*	public Object	setProperties(String key, String[] values)
	{
		String propertyValues = "";

		for (String property : values)
		{
			propertyValues += property;
		}

		return properties.setProperty(key, propertyValues);
	}*/
}
