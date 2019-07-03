package org.radix.time.RTP.messages;

import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.network.messaging.Message;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;

import com.fasterxml.jackson.annotation.JsonProperty;

@SerializerId2("rtp.message")
public class RTPMessage extends Message
{
    private static final Logger rtp = Logging.getLogger("RTP");

    @JsonProperty("message_type")
    @DsonOutput(Output.ALL)
    private long message_type = 0;

    @JsonProperty("seq")
    @DsonOutput(Output.ALL)
    private long seq = 0;

    @JsonProperty("sent_n")
    @DsonOutput(Output.ALL)
    private long sent_n = 0;

    @JsonProperty("sent_r")
    @DsonOutput(Output.ALL)
    private long sent_r = 0;

    @JsonProperty("received_n")
    @DsonOutput(Output.ALL)
    private long received_n = 0;

    @JsonProperty("received_r")
    @DsonOutput(Output.ALL)
    private long received_r = 0;

    RTPMessage () {
    	// For serializer
    }

    public RTPMessage(long messageType, long seq, long sentN, long sentR, long receivedN, long receivedR) {
    	this.message_type = messageType;
    	this.seq = seq;
    	this.sent_n = sentN;
    	this.sent_r = sentR;
    	this.received_n = receivedN;
    	this.received_r = receivedR;
	}

	public RTPMessage(long messageType, long seq) {
    	this.message_type = messageType;
    	this.seq = seq;
	}

	public long getMessageType() {
		return message_type;
	}

	public long getSeq() {
		return seq;
	}

	public long getSentN() {
		return sent_n;
	}

	public long getSentR() {
		return sent_r;
	}

	public long getReceivedN() {
		return received_n;
	}

	public long getReceivedR() {
		return received_r;
	}

	public void dump()
    {
        rtp.info("message_type = " + message_type);
        rtp.info("seq = " + seq);
        rtp.info("sent_n = " + sent_n);
        rtp.info("sent_r = " + sent_r);
        rtp.info("received_n = " + received_n);
        rtp.info("received_r = " + received_r);
    }

    @Override
    public String getCommand()
    {
        return "rtp.message";
    }
}
