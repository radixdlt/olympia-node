package com.radixdlt.utils;

import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.crypto.Hash;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class WireIO
{
	private WireIO() {
		throw new IllegalStateException("Can't construct");
	}

	public static class Reader
	{
		private DataInputStream	inputStream;

		public Reader (byte[] bytes)
		{
			inputStream = new DataInputStream(new ByteArrayInputStream(bytes));
		}

		public Reader (InputStream inputStream)
		{
			this.inputStream = new DataInputStream(inputStream);
		}

		public boolean readBoolean() throws IOException {
			return inputStream.read() != 0;
		}

		public byte readByte () throws IOException { return inputStream.readByte(); }

		public short readShort() throws IOException { return inputStream.readShort(); }

		public int readInt() throws IOException { return inputStream.readInt(); }

		public long readLong() throws IOException { return inputStream.readLong(); }

		public long readVarInt() throws IOException
		{
			byte flag = inputStream.readByte();

			if ( flag == 1)
				return readByte();
			else if ( flag == 2 )
				return readShort();
			else if ( flag == 4 )
				return readInt();
			else
				return readLong();
		}

		public void skipBytes (int length) throws IOException { inputStream.skipBytes(length); }

		public byte[] readBytes (int length) throws IOException
		{
			byte[] b = new byte[length];

			if ( length > 0 )
				inputStream.readFully(b);

			return b;
		}

		public Hash readHash () throws IOException { return new Hash(readBytes (32)); }

		public byte[] readVarBytes () throws IOException
		{
			long len = readVarInt ();
			return readBytes ((int) len);
		}

		public String readString () throws IOException
		{
			long len = readVarInt();

			if (len > 0)
				return new String(readBytes((int)len), StandardCharsets.UTF_8);

			return "";
		}

		public EUID readEUID() throws IOException
		{
			return new EUID(readVarBytes());
		}

		public AID readAID() throws IOException {
			return AID.from(readBytes(AID.BYTES));
		}

		public boolean eof()
		{
			try
			{
				if (inputStream.available() > 0)
					return false;
			}
			catch (IOException e)
			{
				return true;
			}

			return true;
		}

		public double readFloat() throws IOException { return Float.intBitsToFloat(readInt()); }

		public double readDouble() throws IOException { return Double.longBitsToDouble(readLong()); }
	}

	public static class Writer
	{
		private DataOutputStream outputStream;

		public Writer(OutputStream outputStream)
		{
			this.outputStream = new DataOutputStream(outputStream);
		}

		public int size() { return outputStream.size(); }

		public void writeBoolean (boolean bool) throws IOException { outputStream.writeByte(bool?1:0); }

		public void writeByte (int b) throws IOException { outputStream.writeByte(b); }

		public void writeShort (int s) throws IOException { outputStream.writeShort(s); }

		public void writeInt (int i) throws IOException { outputStream.writeInt(i); }

		public void writeLong (long l) throws IOException { outputStream.writeLong(l); }

		public void writeVarInt (long n) throws IOException
		{
			if (n >= 0 && n < 128)
			{
				writeByte(1);
				writeByte((byte) (n & 0xFF));
			}
			else if (n > 127 && n < 32768)
			{
				writeByte(2);
				writeShort((short) (n & 0xFFFF));
			}
			else if (n > 32767 && n < 16777216)
			{
				writeByte(4);
				writeInt((int) (n & 0xFFFFFFFF));
			}
			else
			{
				writeByte(8);
				writeLong(n);
			}
		}

		public void writeVarBytes (byte[] b) throws IOException
		{
			writeVarInt(b.length);

			outputStream.write(b);
		}

		public void writeBytes (byte[] b) throws IOException { outputStream.write(b); }

		public void writeEUID (EUID euid) throws IOException { writeVarBytes(euid.toByteArray()); }

		public void writeAID (AID aid) throws IOException { writeBytes(aid.getBytes()); }

		public void writeHash (Hash h) throws IOException { writeBytes(h.toByteArray ()); }

		public void writeString (String s) throws IOException
		{
			byte[] stringBytes = s.getBytes(StandardCharsets.UTF_8);

			writeVarInt(stringBytes.length);

			if (stringBytes.length > 0)
				writeBytes(stringBytes);
		}

		public void writeFloat(float value) throws IOException {
			writeInt(Float.floatToIntBits(value));
		}

		public void writeDouble(double value) throws IOException {
			writeLong(Double.doubleToLongBits(value));
		}
	}
}
