package org.radix.validation;

public final class Validator
{
	// TODO move out Validator.Mode after Dan's changes are in -Florian
	public enum Mode
	{
		/** Validates form and signatures */
		PREPARE(1),
		/** Validates dependencies and any execution */
		EXECUTE(4),
		/** Full validation */
		COMPLETE(7);

		private byte mode;

		Mode(int mode)
		{
			this.mode = (byte)mode;
		}

		public byte mode() { return mode; }

		public boolean contains(Mode mode)
		{
			if ((mode.mode & this.mode) != 0)
				return true;

			return false;
		}
	}
}
