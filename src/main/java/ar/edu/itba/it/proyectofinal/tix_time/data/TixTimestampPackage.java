package ar.edu.itba.it.proyectofinal.tix_time.data;

import static org.assertj.core.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.net.InetSocketAddress;
import java.util.function.BiFunction;
import java.util.function.Function;

public class TixTimestampPackage implements TixPackage {

	public static final int TIX_TIMESTAMP_PACKAGE_SIZE = Long.BYTES * 4;
	public static final Function<ByteBuf, Long> TIMESTAMP_READER = ByteBuf::readLong;
	public static final BiFunction<ByteBuf, Long, ByteBuf> TIMESTAMP_WRITER = ByteBuf::writeLong;

	private final InetSocketAddress from;
	private final InetSocketAddress to;
	private final long initalTimestamp;
	private long finalTimestamp;
	private long receptionTimestamp;
	private long sentTimestamp;

	public TixTimestampPackage(InetSocketAddress from, InetSocketAddress to, long initialTimestamp) {
		assertThat(from).isNotNull();
		assertThat(to).isNotNull();
		assertThat(initialTimestamp).isNotNegative();
		this.from = from;
		this.to = to;
		this.initalTimestamp = initialTimestamp;
	}

	public InetSocketAddress getFrom() {
		return from;
	}

	public InetSocketAddress getTo() {
		return to;
	}

	public long getInitalTimestamp() {
		return initalTimestamp;
	}

	public long getFinalTimestamp() {
		return finalTimestamp;
	}

	public long getReceptionTimestamp() {
		return receptionTimestamp;
	}

	public long getSentTimestamp() {
		return sentTimestamp;
	}

	public void setSentTimestamp(long sentTimestamp) {
		assertThat(sentTimestamp).isNotNegative();
		this.sentTimestamp = sentTimestamp;
	}

	public void setReceptionTimestamp(long receptionTimestamp) {
		assertThat(receptionTimestamp).isNotNegative();
		this.receptionTimestamp = receptionTimestamp;
	}

	public void setFinalTimestamp(long finalTimestamp) {
		assertThat(finalTimestamp).isNotNegative();
		this.finalTimestamp = finalTimestamp;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null || !obj.getClass().equals(this.getClass())) {
			return false;
		}
		if (obj == this) {
			return true;
		}
		TixTimestampPackage other = (TixTimestampPackage) obj;
		return new EqualsBuilder()
				.append(this.getInitalTimestamp(), other.getInitalTimestamp())
				.append(this.getReceptionTimestamp(), other.getReceptionTimestamp())
				.append(this.getSentTimestamp(), other.getSentTimestamp())
				.append(this.getFinalTimestamp(), other.getFinalTimestamp())
				.isEquals();
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder()
				.append(this.getInitalTimestamp())
				.append(this.getReceptionTimestamp())
				.append(this.getSentTimestamp())
				.append(this.getFinalTimestamp())
				.hashCode();
	}

	@Override
	public String toString() {
		return new ToStringBuilder(this, ToStringStyle.JSON_STYLE)
				.append("initialTimestamp", this.getInitalTimestamp())
				.append("receptionTimestamp", this.getReceptionTimestamp())
				.append("sentTimestamp", this.getSentTimestamp())
				.append("finalTimestamp", this.getFinalTimestamp())
				.toString();
	}
}
