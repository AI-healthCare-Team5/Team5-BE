package com.health.jinryohanjang.domain.stt.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 녹음 파일(webm·mp4)을 ffmpeg로 Transcribe 스트리밍이 받는 PCM(16kHz, mono, 16bit)으로 바꾼다.
 * mp4는 파일 끝에 정보가 있어 파이프로 읽을 수 없으므로 임시 파일에 쓰고, 변환이 끝나면 바로 지운다.
 */
@Slf4j
@Component
public class AudioConverter {

	public static final int SAMPLE_RATE = 16_000;
	private static final int BYTES_PER_SECOND = SAMPLE_RATE * 2;
	private static final long TIMEOUT_SECONDS = 20;

	private final String ffmpegPath;

	public AudioConverter(@Value("${app.stt.ffmpeg-path:ffmpeg}") String ffmpegPath) {
		this.ffmpegPath = ffmpegPath;
	}

	/**
	 * maxSeconds를 조금 넘는 데까지만 변환한다. 길이 확인은 결과의 seconds로 한다.
	 */
	public Pcm toPcm(byte[] audio, String extension, int maxSeconds) {
		Path input = null;
		Process process = null;
		try {
			input = Files.createTempFile("stt-", extension);
			Files.write(input, audio);
			process = new ProcessBuilder(List.of(ffmpegPath, "-nostdin", "-hide_banner", "-loglevel", "error",
				"-i", input.toString(), "-t", String.valueOf(maxSeconds + 1),
				"-ac", "1", "-ar", String.valueOf(SAMPLE_RATE), "-f", "s16le", "-acodec", "pcm_s16le", "pipe:1"))
				.redirectError(ProcessBuilder.Redirect.DISCARD)
				.start();
			byte[] pcm;
			try (InputStream output = process.getInputStream()) {
				pcm = output.readAllBytes();
			}
			if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
				throw new ConversionException("ffmpeg 변환 시간이 초과됐습니다.");
			}
			if (process.exitValue() != 0 || pcm.length == 0) {
				throw new UnsupportedAudioException();
			}
			return new Pcm(pcm, (double)pcm.length / BYTES_PER_SECOND);
		} catch (IOException e) {
			log.error("ffmpeg를 실행하지 못했습니다. FFMPEG_PATH를 확인해 주세요.", e);
			throw new ConversionException("ffmpeg를 실행하지 못했습니다.");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ConversionException("ffmpeg 변환이 중단됐습니다.");
		} finally {
			if (process != null && process.isAlive()) {
				process.destroyForcibly();
			}
			deleteQuietly(input);
		}
	}

	private void deleteQuietly(Path path) {
		if (path == null) {
			return;
		}
		try {
			Files.deleteIfExists(path);
		} catch (IOException e) {
			log.warn("변환용 임시 파일을 지우지 못했습니다.", e);
		}
	}

	public record Pcm(byte[] bytes, double seconds) {
	}

	/**
	 * 녹음 형식이 맞지 않거나 깨져서 변환할 수 없을 때 던진다.
	 */
	public static class UnsupportedAudioException extends RuntimeException {
	}

	/**
	 * ffmpeg가 없거나 시간이 초과되는 등 서버 쪽 문제로 변환하지 못했을 때 던진다.
	 */
	public static class ConversionException extends RuntimeException {

		public ConversionException(String message) {
			super(message);
		}
	}
}
