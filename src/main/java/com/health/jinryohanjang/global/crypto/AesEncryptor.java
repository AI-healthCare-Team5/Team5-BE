package com.health.jinryohanjang.global.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 건강정보·보호자 전화번호 암호화 (AES-256-GCM)
 * - 키 : DATA_ENCRYPTION_KEY 환경 변수, 32바이트를 Base64로 인코딩한 값
 * - 저장 형식 : Base64(IV 12바이트 + 암호문)
 */
@Component
public class AesEncryptor {

	private static final String ALGORITHM = "AES";
	private static final String TRANSFORMATION = "AES/GCM/NoPadding";
	private static final int KEY_LENGTH = 32;
	private static final int IV_LENGTH = 12;
	private static final int TAG_LENGTH_BIT = 128;

	private final SecretKey key;
	private final SecureRandom secureRandom = new SecureRandom();

	public AesEncryptor(@Value("${app.encryption.key:}") String encodedKey) {
		this.key = toKey(encodedKey);
	}

	public String encrypt(String plainText) {
		if (plainText == null) {
			return null;
		}
		try {
			byte[] iv = new byte[IV_LENGTH];
			secureRandom.nextBytes(iv);
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BIT, iv));
			byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
			byte[] combined = ByteBuffer.allocate(IV_LENGTH + cipherText.length).put(iv).put(cipherText).array();
			return Base64.getEncoder().encodeToString(combined);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("암호화에 실패했습니다.", e);
		}
	}

	public String decrypt(String encrypted) {
		if (encrypted == null) {
			return null;
		}
		try {
			byte[] combined = Base64.getDecoder().decode(encrypted);
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BIT, combined, 0, IV_LENGTH));
			byte[] plainText = cipher.doFinal(combined, IV_LENGTH, combined.length - IV_LENGTH);
			return new String(plainText, StandardCharsets.UTF_8);
		} catch (GeneralSecurityException | IllegalArgumentException e) {
			throw new IllegalStateException("복호화에 실패했습니다.", e);
		}
	}

	private static SecretKey toKey(String encodedKey) {
		if (encodedKey == null || encodedKey.isBlank()) {
			throw new IllegalStateException("DATA_ENCRYPTION_KEY 환경 변수가 없습니다.");
		}
		byte[] keyBytes;
		try {
			keyBytes = Base64.getDecoder().decode(encodedKey);
		} catch (IllegalArgumentException e) {
			throw new IllegalStateException("DATA_ENCRYPTION_KEY는 Base64 값이어야 합니다.", e);
		}
		if (keyBytes.length != KEY_LENGTH) {
			throw new IllegalStateException("DATA_ENCRYPTION_KEY는 32바이트 키를 Base64로 인코딩한 값이어야 합니다.");
		}
		return new SecretKeySpec(keyBytes, ALGORITHM);
	}
}
