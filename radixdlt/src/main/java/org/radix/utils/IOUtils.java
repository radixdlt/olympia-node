package org.radix.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

public class IOUtils 
{

	public static String toString(InputStream inputStream) throws IOException
	{
		return toString(inputStream, Charset.defaultCharset());
	}
	
	public static String toString(InputStream inputStream, Charset charset) throws IOException
	{
		StringBuilder builder = new StringBuilder();
		
		byte[] bytes = new byte[8192];
		int read = -1;
		
		while ((read = inputStream.read(bytes)) != -1)
		{
			builder.append(new String(bytes, 0, read, charset));
		}
		
		return builder.toString();
	}
}
