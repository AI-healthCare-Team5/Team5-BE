package com.health.jinryohanjang.global.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.Test;

class AesEncryptorTest {

	private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

	private final AesEncryptor aesEncryptor = new AesEncryptor(KEY);

	@Test
	void 암호화한_값을_복호화하면_원문이_나온다() {
		String encrypted = aesEncryptor.encrypt("무릎이 아파요");

		assertThat(encrypted).isNotEqualTo("무릎이 아파요");
		assertThat(aesEncryptor.decrypt(encrypted)).isEqualTo("무릎이 아파요");
	}

	@Test
	void 같은_값도_매번_다르게_암호화된다() {
		assertThat(aesEncryptor.encrypt("01012345678")).isNotEqualTo(aesEncryptor.encrypt("01012345678"));
	}

	@Test
	void null은_null로_둔다() {
		assertThat(aesEncryptor.encrypt(null)).isNull();
		assertThat(aesEncryptor.decrypt(null)).isNull();
	}

	@Test
	void 다른_키로는_복호화할_수_없다() {
		String otherKey = Base64.getEncoder().encodeToString(new byte[] {
			1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
			17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32});
		String encrypted = aesEncryptor.encrypt("알레르기");

		assertThatThrownBy(() -> new AesEncryptor(otherKey).decrypt(encrypted))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void 키가_없거나_32바이트가_아니면_시작하지_않는다() {
		assertThatThrownBy(() -> new AesEncryptor(""))
			.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new AesEncryptor(Base64.getEncoder().encodeToString(new byte[16])))
			.isInstanceOf(IllegalStateException.class);
	}
}
