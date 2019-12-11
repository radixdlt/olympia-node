package org.radix.time;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import org.radix.logging.Logger;
import org.radix.logging.Logging;

public class NtpService
{
	private static final Logger log = Logging.getLogger();



	private String 		server = null;
	private double 		roundTripDelay = 0;
	private	double 		localClockOffset = 0;

	private int			attempts = 0;
	private int			offset = 0;

	public NtpService(String server) 
	{
		this.server = server;

		roundTripDelay = 0;
		localClockOffset = 0;

		if (server != null)
			initFromServer();
	}

	private void initFromServer() 
	{
		if (server != null)
		{
			boolean success = false;

			while (attempts < 3 && !success)
			{
				DatagramSocket socket = null;

				try
				{
					// Send request
					socket = new DatagramSocket();
					socket.setSoTimeout(5000);

					InetAddress address = InetAddress.getByName(server);
					byte[] buf = new NtpMessage().toByteArray();
					DatagramPacket packet =	new DatagramPacket(buf, buf.length, address, 123);

					// Set the transmit timestamp *just* before sending the packet
					// ToDo: Does this actually improve performance or not?
					NtpMessage.encodeTimestamp(packet.getData(), 40, (System.currentTimeMillis()/1000.0) + 2208988800.0);

					socket.send(packet);

					// Get response
					log.info("NTP request sent, waiting for response...\n");
					packet = new DatagramPacket(buf, buf.length);
					socket.receive(packet);

					// Immediately record the incoming timestamp
					double destinationTimestamp = (System.currentTimeMillis()/1000.0) + 2208988800.0;

					// Process response
					NtpMessage msg = new NtpMessage(packet.getData());

					// Corrected, according to RFC2030 errata
					roundTripDelay = (destinationTimestamp-msg.originateTimestamp) - (msg.transmitTimestamp-msg.receiveTimestamp);
					localClockOffset = ((msg.receiveTimestamp - msg.originateTimestamp) + (msg.transmitTimestamp - destinationTimestamp)) / 2;

					log.info(msg.toString());
					success = true;
				}
				catch (Exception ex)
				{
					if (attempts >= 3)
						throw new RuntimeException("failed to start NTP service", ex);
				}
				finally
				{
					attempts++;

					if (socket != null)
						socket.close();
				}
			}

			if (!success)
				throw new RuntimeException("Unable to start NTP service using "+server);
		}
	}

	public boolean isSynchronized()
	{
		return server == null?false:true;
	}

	/**
	 * Returns the offset in seconds set in this NtpService
	 *
	 * @return
	 */
	public int getOffset()
	{
		return offset;
	}

	/**
	 * Sets the offset in seconds for this NtpService
	 *
	 * @param offset
	 */
	public void setOffset(int offset)
	{
		this.offset = offset;
	}

	/**
	 * Returns a corrected System time
	 *
	 * @return
	 */
	public long getSystemTime()
	{
		return (long) (System.currentTimeMillis()+(localClockOffset*1000.0));
	}

	/**
	 * Returns a corrected UTC time in seconds
	 *
	 * @return
	 */
	public synchronized int getUTCTimeSeconds()
	{
		return (int) (getUTCTimeMS() / 1000L);
	}

	/**
	 * Returns a corrected UTC time in milliseconds
	 *
	 * @return
	 */
	public synchronized long getUTCTimeMS()
	{
		return (long) (System.currentTimeMillis() + (localClockOffset * 1000.0) + (roundTripDelay * 1000.0) + (offset * 1000L));
	}
}
