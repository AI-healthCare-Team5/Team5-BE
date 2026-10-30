package com.health.jinryohanjang.global.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;

/**
 * 암호화해서 저장할 컬럼에 붙이는 컨버터
 * 사용 : @Convert(converter = EncryptedStringConverter.class), 컬럼 타입은 TEXT
 */
@Converter
@RequiredArgsConstructor
public class EncryptedStringConverter implements AttributeConverter<String, String> {

	private final AesEncryptor aesEncryptor;

	@Override
	public String convertToDatabaseColumn(String attribute) {
		return aesEncryptor.encrypt(attribute);
	}

	@Override
	public String convertToEntityAttribute(String dbData) {
		return aesEncryptor.decrypt(dbData);
	}
}
