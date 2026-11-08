package com.health.jinryohanjang.domain.stt.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.health.jinryohanjang.common.exception.GeneralException;
import com.health.jinryohanjang.common.exception.GeneralExceptionAdvice.FieldErrorDetail;
import com.health.jinryohanjang.common.status.ErrorStatus;
import com.health.jinryohanjang.common.util.RateLimiter;
import com.health.jinryohanjang.domain.stt.client.AudioConverter;
import com.health.jinryohanjang.domain.stt.client.AudioConverter.ConversionException;
import com.health.jinryohanjang.domain.stt.client.AudioConverter.Pcm;
import com.health.jinryohanjang.domain.stt.client.AudioConverter.UnsupportedAudioException;
import com.health.jinryohanjang.domain.stt.client.TranscribeClient;
import com.health.jinryohanjang.domain.stt.client.TranscribeClient.TranscribeUnavailableException;
import com.health.jinryohanjang.domain.stt.dto.SttResponse;

import lombok.RequiredArgsConstructor;

/**
 * 녹음을 글자로 바꾼다. ffmpeg로 PCM으로 바꾼 뒤 Transcribe 스트리밍으로 보낸다. 음성은 저장하지 않는다.
 * 말로 시작하면 문진 시작보다 STT가 먼저라, guestKey가 없으면 여기서 발급한다.
 */
@Service
@RequiredArgsConstructor
public class SttService {

	private static final int MAX_SECONDS = 60;
	/**
	 * 크롬은 audio/webm, 아이폰 사파리는 audio/mp4로 녹음한다. 형식에 맞는 확장자로 임시 파일을 만든다.
	 */
	private static final Map<String, String> EXTENSIONS = Map.of(
		"audio/webm", ".webm",
		"audio/mp4", ".m4a",
		"audio/m4a", ".m4a",
		"audio/x-m4a", ".m4a");

	private final AudioConverter audioConverter;
	private final TranscribeClient transcribeClient;
	private final RateLimiter rateLimiter;

	public SttResult recognize(MultipartFile audio, String guestKey, String clientIp) {
		rateLimiter.check(ErrorStatus.STT_TOO_MANY_REQUESTS, "stt:ip:" + clientIp, 10, Duration.ofMinutes(1));
		String ownerKey = StringUtils.hasText(guestKey) ? guestKey : UUID.randomUUID().toString();
		rateLimiter.check(ErrorStatus.STT_TOO_MANY_REQUESTS, "stt:guest:" + ownerKey, 60, Duration.ofHours(1));
		if (audio.isEmpty()) {
			throw new GeneralException(ErrorStatus.STT_FILE_REQUIRED,
				Map.of("errors", List.of(new FieldErrorDetail("audio", "녹음 파일이 비어 있어요"))));
		}
		String extension = extensionOf(audio.getContentType());

		Pcm pcm;
		try {
			pcm = audioConverter.toPcm(audio.getBytes(), extension, MAX_SECONDS);
		} catch (UnsupportedAudioException e) {
			throw new GeneralException(ErrorStatus.STT_UNSUPPORTED_FORMAT);
		} catch (ConversionException | IOException e) {
			throw new GeneralException(ErrorStatus.STT_UNAVAILABLE);
		}
		if (pcm.seconds() > MAX_SECONDS) {
			throw new GeneralException(ErrorStatus.STT_FILE_TOO_LARGE);
		}

		String text;
		try {
			text = transcribeClient.transcribe(pcm.bytes());
		} catch (TranscribeUnavailableException e) {
			throw new GeneralException(ErrorStatus.STT_UNAVAILABLE);
		}
		if (!StringUtils.hasText(text)) {
			throw new GeneralException(ErrorStatus.STT_NOT_RECOGNIZED);
		}
		double durationSec = BigDecimal.valueOf(pcm.seconds()).setScale(1, RoundingMode.HALF_UP).doubleValue();
		return new SttResult(new SttResponse(text, durationSec), ownerKey);
	}

	/**
	 * 브라우저가 붙인 codecs 같은 파라미터는 떼고 형식만 본다.
	 */
	private String extensionOf(String contentType) {
		String baseType = contentType == null ? "" : contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
		String extension = EXTENSIONS.get(baseType);
		if (extension == null) {
			throw new GeneralException(ErrorStatus.STT_UNSUPPORTED_FORMAT);
		}
		return extension;
	}

	public record SttResult(SttResponse response, String guestKey) {
	}
}
