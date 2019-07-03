package org.radix.modules;

import java.util.Objects;

public final class ModuleStatus
{
	public enum Status {
		UNKNOWN,		/** Service status is unknown */
		AVAILABLE, 		/** Service is available and operational */
		LIMITED,		/** Service is available but may have limited operations */
		BUSY,			/** Service is currently busy */
		LOCKED,			/** Service is currently in lock */
		UNAVAILABLE		/** Service is unavailable */
	}

	public static final int		NONE				= 0;	/** Service has no sub-flags */
	public static final int		OFFLINE				= 1;	/** Service is offline */
	public static final int		ERRROR				= 2;	/** Service has errors */
	public static final int		CRITICAL			= 4;	/** Service has critical errors */

	private final Status	status;
	private final String	message;
	private int				flags = 0;

	public ModuleStatus(Status status)
	{
		this(status, 0, null);
	}

	public ModuleStatus(Status status, String message)
	{
		this(status, 0, message);
	}

	public ModuleStatus(Status status, int flags, String message)
	{
		this.status = status;
		this.flags = flags;
		this.message = message;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.status, this.message, this.flags);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}

		if (obj instanceof Status) {
			return this.status.equals(obj);
		}

		if (obj instanceof ModuleStatus) {
			ModuleStatus other = (ModuleStatus) obj;
			return this.flags == other.flags &&
				Objects.equals(this.status, other.status) &&
				Objects.equals(this.message, other.message);
		}
		return false;
	}

	public Status getStatus() { return status; }

	public int getFlags() { return flags; }
	public void setFlags(int flags) { this.flags = flags; }

	public void setFlag(int flag, boolean has)
	{
		if (has)
			this.flags |= flag;
		else
			this.flags &= ~flag;
	}

	public boolean hasFlag(int flag)
	{
		if ((flags & flag) == flag)
			return true;

		return false;
	}

	public String getMessage() { return this.message; }
}
