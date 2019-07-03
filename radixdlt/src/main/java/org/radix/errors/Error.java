package org.radix.errors;

import org.radix.common.Criticality;
import org.radix.containers.BasicContainer;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import org.radix.time.Chronologic;

import com.fasterxml.jackson.annotation.JsonProperty;

@SerializerId2("\u0002\u0006\u0006\u0001") // 65535 in base 31
public final class Error extends BasicContainer implements Chronologic
{
	@Override
	public short VERSION() { return 100; }

	@JsonProperty("criticality")
	@DsonOutput(Output.ALL)
	private Criticality	criticality = Criticality.NONE;

	@JsonProperty("name")
	@DsonOutput(Output.ALL)
	private Errors		error = Errors.NO_ERROR;

	@JsonProperty("timestamp")
	@DsonOutput(Output.ALL)
	private long		timestamp = (int) (System.currentTimeMillis()/1000);

	private boolean		display = false;

	@JsonProperty("title")
	@DsonOutput(Output.ALL)
	private String		title = null;

	@JsonProperty("message")
	@DsonOutput(Output.ALL)
	private String		message = null;

	private Throwable	throwable = null;

	public Error()
	{}

	public Error(Errors error, Throwable throwable)
	{
		this();

		this.error = error;
		setThrowable(throwable);
	}

	/*	public Error(String title, String text, boolean display, Criticality criticality)
	{
		this(ErrorCode.UNKNOWN, title, text, display, criticality);
	}

	public Error(ErrorCode code, String title, String text, boolean display, Criticality criticality)
	{
		this();

		this.code = code;
		this.title = title;
		this.text = text;
		this.display = display;
		this.criticality = criticality;
	}

	public Error(String title, Throwable exception, boolean display, Criticality criticality)
	{
		this(ErrorCode.UNKNOWN, title, exception, display, criticality);
	}

	public Error(ErrorCode code, String title, Throwable exception, boolean display, Criticality criticality)
	{
		this();

		this.code = code;
		this.title = title;
		this.display = display;
		this.criticality = criticality;
		this.exception = exception;

		// Convert the exception message text to be a bit more readable //
		if (exception != null && exception.getLocalizedMessage() != null)
			text = exception.getLocalizedMessage().substring(exception.getLocalizedMessage().lastIndexOf(":")+1).trim();
		else
			text = exception.toString();
	}

	public Error(String title, String text, Throwable exception, boolean display, Criticality criticality)
	{
		this(ErrorCode.UNKNOWN, title, text, display, criticality);
	}

	public Error(ErrorCode code, String title, String text, Throwable exception, boolean display, Criticality criticality)
	{
		this(code, title, text, display, criticality);

		this.exception = exception;
	}*/

	public int getCode() { return error.code(); }

	public String getHint() { return error.hint(); }

	public String getName() { return error.name(); }

	public String getTitle() { return title; }

	public String getMessage() { return message; }

	public Throwable getThrowable() { return throwable; }

	private void setThrowable(Throwable throwable)
	{
		this.throwable = throwable;
		if (throwable != null) {
			this.title = throwable.getClass().getSimpleName();
			if (throwable.getLocalizedMessage() != null) {
				this.message = throwable.getLocalizedMessage().substring(throwable.getLocalizedMessage().lastIndexOf(':')+1).trim();
			} else {
				this.message = throwable.toString();
			}
		} else {
			this.title = null;
			this.message = null;
		}
	}

	public Criticality getCriticality() { return criticality; }

	public boolean isDisplay() { return display; }

	@Override
	public long getTimestamp() { return timestamp; }

	@Override
	public long getTimestamp(String type) { return timestamp; }

	@Override
	public void setTimestamp(String type, long timestamp) { throw new UnsupportedOperationException(); }

	@Override
	public String toString()
	{
		StringBuilder builder = new StringBuilder();

		builder.append(error.toString());

		if (title != null)
			builder.append(" "+title);

		if (message != null)
			builder.append(" -> "+message);

		return builder.toString();
	}

	@JsonProperty("display")
	@DsonOutput(Output.ALL)
	private Boolean getJsonDisplay() {
		return this.display ? Boolean.TRUE : null;
	}

	@JsonProperty("display")
	private void setJsonDisplay(Boolean value) {
		this.display = (value != null) && value.booleanValue();
	}

	@JsonProperty("code")
	@DsonOutput(Output.ALL)
	private int getJsonCode() {
		return this.error.code();
	}
}
