package org.radix.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

public class FileUtils
{
	public static void copyResourcesRecursively(final URL url, final File destination) throws IOException
	{
		final URLConnection urlConnection = url.openConnection();

		if (urlConnection instanceof JarURLConnection) {
			try (final JarFile jarFile = ((JarURLConnection) urlConnection).getJarFile()) {
				for (final Enumeration<JarEntry> e = jarFile.entries(); e.hasMoreElements();) {
					final JarEntry entry = e.nextElement();

					if (entry.getName().startsWith(((JarURLConnection) urlConnection).getEntryName())) {
						String filename = entry.getName();

						if (filename.startsWith(((JarURLConnection) urlConnection).getEntryName()))
							filename = filename.substring(((JarURLConnection) urlConnection).getEntryName().length());

						final File f = new File(destination, filename);

						if (!entry.isDirectory()) {
							try (final InputStream entryInputStream = jarFile.getInputStream(entry);
									final FileOutputStream entryOutputStream = new FileOutputStream(f)) {
								ByteStreams.copy(entryInputStream, entryOutputStream);
							}
						} else {
							if (!(f.exists() || f.mkdir()))
								throw new IOException("Could not create directory: " + f.getAbsolutePath());
						}
					}
				}
			}
		} else {
	        copyDirectory(new File(url.getPath()), destination);
		}
	}

	public static void copyDirectory(File source, File destination) throws IOException {
	    if (source.isDirectory()) {
	        if (!destination.exists()) {
	            destination.mkdirs();
	        }

	        String files[] = source.list();
	        if (files != null) {
	        	for (String file : files) {
	        		File srcFile = new File(source, file);
	        		File destFile = new File(destination, file);

	        		copyDirectory(srcFile, destFile);
	        	}
	        }
	    } else {
	    	Files.copy(source, destination);
	    }
	}
}
