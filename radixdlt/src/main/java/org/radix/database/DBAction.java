package org.radix.database;

import java.util.concurrent.Semaphore;

/**
 * DBAction defines the type of operation to execute (DELETE, STORE, UPDATE} and the object
 * being operated upon.
 *
 * The operation and object type are used extensively to determine which SQL batch statements
 * should be appended to and executed.
 *
 * @author Administrator
 *
 */
public class DBAction
{
	public static final String UPDATE = "update";
	public static final String REPLACE = "replace";
	public static final String DELETE = "delete";
	public static final String STORE = "store";

	private final String		action;
	private final Object 		object;
	private final String 		variant;

	private final Semaphore		completed;  // TODO can we do this with an AtomicBoolean?

	public DBAction(String action, Object object)
	{
		this.action = action;
		this.object = object;
		this.variant = null;
		this.completed = new Semaphore(0);
	}

	public DBAction(String action, Object object, boolean completed)
	{
		this.action = action;
		this.object = object;
		this.variant = null;
		this.completed = new Semaphore(completed?1:0);
	}

	public DBAction(String action, Object object, String variant)
	{
		this.action = action;
		this.object = object;
		this.variant = variant;
		this.completed = new Semaphore(0);
	}

	public DBAction(String action, Object object, String variant, boolean completed)
	{
		this.action = action;
		this.object = object;
		this.variant = variant;
		this.completed = new Semaphore(completed?1:0);
	}

	public String getAction()
	{
		return action;
	}

	public String getVariant()
	{
		return variant;
	}

	public Object getObject()
	{
		return object;
	}

	public boolean isCompleted()
	{
		return completed.tryAcquire(1);
	}

	public void waitForCompletion() throws InterruptedException
	{
		completed.acquire();
	}

	public void setCompleted()
	{
		completed.release();
	}

	@Override
	public boolean equals(Object obj)
	{
		if (obj == null) return false;

		if (obj == this) return true;

		if (!(obj instanceof DBAction)) return false;

		if (variant != null)
		{
			if (action.equals(((DBAction)obj).action) && object.equals(((DBAction)obj).getObject()) && variant.equals(((DBAction)obj).variant)) return true;
		}
		else
		{
			if (action.equals(((DBAction)obj).action) && object.equals(((DBAction)obj).getObject())) return true;
		}

		return false;
	}

	@Override
	public int hashCode()
	{
		if (variant != null)
		{
			return 31 * action.hashCode() * object.hashCode() * variant.hashCode();
		}
		else
		{
			return 31 * action.hashCode() * object.hashCode();
		}
	}

	@Override
	public String toString()
	{
		return action+" : "+object.toString()+(variant == null?"":":"+variant);
	}
}
