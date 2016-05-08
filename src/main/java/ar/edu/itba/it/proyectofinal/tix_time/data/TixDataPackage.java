package ar.edu.itba.it.proyectofinal.tix_time.data;

import static org.assertj.core.api.Assertions.*;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.Base64;
import java.util.function.Function;

public class TixDataPackage extends TixTimestampPackage {
	public static final int TIX_DATA_PACKAGE_SIZE = TIX_TIMESTAMP_PACKAGE_SIZE + 4400;
	public static final Function<String, String> DECODER = (String s) -> new String(Base64.getDecoder().decode(s));
	public static final Function<String, String> ENCODER = (String s) -> Base64.getEncoder().encodeToString(s.getBytes());
	public static final String DATA_DELIMITER = ";;";
	public static final String DATA_HEADER = "DATA";

	private final String publicKey;
	private final String signature;
	private final String filename;
	private final String message;

	public TixDataPackage(long initialTimestamp, long finalTimestamp, String publicKey, String signature,
	                      String filename, String message) {
		super(initialTimestamp, finalTimestamp);
		assertThat(publicKey).isNotNull().isNotEmpty();
		assertThat(signature).isNotNull().isNotEmpty();
		assertThat(filename).isNotNull().isNotEmpty();
		assertThat(message).isNotNull().isNotEmpty();
		this.publicKey = publicKey;
		this.signature = signature;
		this.filename = filename;
		this.message = message;
	}

	public String getPublicKey() {
		return publicKey;
	}

	public String getSignature() {
		return signature;
	}

	public String getFilename() {
		return filename;
	}

	public String getMessage() {
		return message;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null || !obj.getClass().equals(this.getClass())) {
			return false;
		}
		if (obj == this) {
			return true;
		}
		TixDataPackage other = (TixDataPackage) obj;
		return new EqualsBuilder()
				.appendSuper(super.equals(other))
				.append(this.getPublicKey(), other.getPublicKey())
				.append(this.getSignature(), other.getSignature())
				.append(this.getFilename(), other.getFilename())
				.append(this.getMessage(), other.getMessage())
				.isEquals();
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder()
				.append(super.hashCode())
				.append(this.getPublicKey())
				.append(this.getSignature())
				.append(this.getFilename())
				.append(this.getMessage())
				.hashCode();
	}

	@Override
	public String toString() {
		return new ToStringBuilder(this, ToStringStyle.JSON_STYLE)
				.appendSuper(super.toString())
				.append("publicKey", this.getPublicKey())
				.append("signature", this.getSignature())
				.append("filename", this.getFilename())
				.append("message", this.getMessage())
				.toString();
	}
}
