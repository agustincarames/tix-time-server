package ar.edu.itba.it.proyectofinal.tix_time.data;

import static org.assertj.core.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.function.BiFunction;
import java.util.function.Function;

public class TixTimestampPackage implements TixPackage {

	public static final int TIX_TIMESTAMP_PACKAGE_SIZE = Long.BYTES * 4;
	public static final Function<ByteBuf, Long> TIMESTAMP_READER = ByteBuf::readLong;
	public static final BiFunction<ByteBuf, Long, ByteBuf> TIMESTAMP_WRITER = ByteBuf::writeLong;

	private final long initalTimestamp;
	private final long finalTimestamp;
	private long receptionTimestamp;
	private long sentTimestamp;

	public TixTimestampPackage(long initialTimestamp, long finalTimestamp) {
		assertThat(initialTimestamp).isNotNegative();
		assertThat(finalTimestamp).isNotNegative();
		this.initalTimestamp = initialTimestamp;
		this.finalTimestamp = finalTimestamp;
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
				.append(this.getInitalTimestamp())
				.append(this.getReceptionTimestamp())
				.append(this.getSentTimestamp())
				.append(this.getFinalTimestamp())
				.toString();
	}
}
