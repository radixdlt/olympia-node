package org.radix.time.RTP;

import com.radixdlt.common.EUID;
import org.radix.containers.BasicContainer;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;

import com.fasterxml.jackson.annotation.JsonProperty;

@SerializerId2("rtp.timestamp")
public class RTPTimestamp extends BasicContainer
{
    private static final Logger rtp = Logging.getLogger("RTP");

    @Override
    public short VERSION()
    {
        return 100;
    }

    @JsonProperty("nid")
    @DsonOutput(Output.ALL)
    private EUID nid;

    @JsonProperty("radix_time")
    @DsonOutput(Output.ALL)
    private long radix_time;

    @JsonProperty("node_time")
    @DsonOutput(Output.ALL)
    private long node_time;

    @JsonProperty("isSynced")
    @DsonOutput(Output.ALL)
    private boolean isSynced;

    @JsonProperty("ntp_deviation")
    @DsonOutput(Output.ALL)
    private int ntp_deviation;

    RTPTimestamp() {
    	// For serializer
    }

    public RTPTimestamp(EUID nid, long radixTime, long nodeTime, boolean isSynced, int ntpDeviation) {
		super();
		this.nid = nid;
		this.radix_time = radixTime;
		this.node_time = nodeTime;
		this.isSynced = isSynced;
		this.ntp_deviation = ntpDeviation;
	}

	public EUID getNid() {
		return nid;
	}

	public long getRadixTime() {
		return radix_time;
	}

	public long getNodeTime() {
		return node_time;
	}

	public boolean isSynced() {
		return isSynced;
	}

	public int getNtpDeviation() {
		return ntp_deviation;
	}

	public void dump()
    {
        rtp.info("\n\tnid : " + nid + "\n\tr_time : " + radix_time + "\n\tn_time : " + node_time + "\n\tisSynced : " + isSynced + "\n\tntp_deviation : " + ntp_deviation);
    }
}