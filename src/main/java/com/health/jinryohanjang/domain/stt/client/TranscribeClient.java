package com.health.jinryohanjang.domain.stt.client;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.transcribestreaming.TranscribeStreamingAsyncClient;
import software.amazon.awssdk.services.transcribestreaming.model.AudioEvent;
import software.amazon.awssdk.services.transcribestreaming.model.AudioStream;
import software.amazon.awssdk.services.transcribestreaming.model.LanguageCode;
import software.amazon.awssdk.services.transcribestreaming.model.MediaEncoding;
import software.amazon.awssdk.services.transcribestreaming.model.Result;
import software.amazon.awssdk.services.transcribestreaming.model.StartStreamTranscriptionRequest;
import software.amazon.awssdk.services.transcribestreaming.model.StartStreamTranscriptionResponseHandler;
import software.amazon.awssdk.services.transcribestreaming.model.TranscriptEvent;

/**
 * Amazon Transcribe 스트리밍으로 PCM 음성을 한국어 글자로 바꾼다. 음성은 저장하지 않고 바로 보낸다.
 * 자격 증명은 Bedrock과 같다 (로컬은 프로필, 서버는 EC2 IAM Role).
 */
@Slf4j
@Component
public class TranscribeClient {

	private static final int CHUNK_BYTES = 8 * 1024;
	private static final Duration TIMEOUT = Duration.ofSeconds(30);

	private final TranscribeStreamingAsyncClient client;

	public TranscribeClient(
		@Value("${app.stt.region}") String region,
		@Value("${app.bedrock.profile:}") String profile) {
		AwsCredentialsProvider credentials = StringUtils.hasText(profile)
			? ProfileCredentialsProvider.builder().profileName(profile).build()
			: DefaultCredentialsProvider.builder().build();
		this.client = TranscribeStreamingAsyncClient.builder()
			.region(Region.of(region))
			.credentialsProvider(credentials)
			.build();
	}

	/**
	 * 최종 결과(partial이 아닌 결과)만 이어 붙인다. 알아들은 말이 없으면 빈 문자열이다.
	 */
	public String transcribe(byte[] pcm) {
		StartStreamTranscriptionRequest request = StartStreamTranscriptionRequest.builder()
			.languageCode(LanguageCode.KO_KR)
			.mediaEncoding(MediaEncoding.PCM)
			.mediaSampleRateHertz(AudioConverter.SAMPLE_RATE)
			.build();
		StringBuffer text = new StringBuffer();
		StartStreamTranscriptionResponseHandler handler = StartStreamTranscriptionResponseHandler.builder()
			.subscriber(event -> {
				if (event instanceof TranscriptEvent transcript) {
					for (Result result : transcript.transcript().results()) {
						if (!result.isPartial() && !result.alternatives().isEmpty()) {
							text.append(result.alternatives().get(0).transcript()).append(' ');
						}
					}
				}
			})
			.build();
		try {
			client.startStreamTranscription(request, new AudioPublisher(chunks(pcm)), handler)
				.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
		} catch (ExecutionException | TimeoutException e) {
			log.error("Transcribe 호출 실패", e);
			throw new TranscribeUnavailableException();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new TranscribeUnavailableException();
		}
		return text.toString().trim();
	}

	@PreDestroy
	public void close() {
		client.close();
	}

	private List<AudioEvent> chunks(byte[] pcm) {
		List<AudioEvent> chunks = new ArrayList<>();
		for (int start = 0; start < pcm.length; start += CHUNK_BYTES) {
			int length = Math.min(CHUNK_BYTES, pcm.length - start);
			byte[] chunk = Arrays.copyOfRange(pcm, start, start + length);
			chunks.add(AudioEvent.builder().audioChunk(SdkBytes.fromByteArray(chunk)).build());
		}
		return chunks;
	}

	/**
	 * 이미 받은 녹음을 요청받은 만큼씩 보내는 Publisher다.
	 */
	private static final class AudioPublisher implements Publisher<AudioStream> {

		private final List<AudioEvent> chunks;

		private AudioPublisher(List<AudioEvent> chunks) {
			this.chunks = chunks;
		}

		@Override
		public void subscribe(Subscriber<? super AudioStream> subscriber) {
			subscriber.onSubscribe(new Subscription() {
				private int index;
				private boolean done;

				@Override
				public synchronized void request(long n) {
					for (long sent = 0; sent < n && index < chunks.size() && !done; sent++) {
						subscriber.onNext(chunks.get(index++));
					}
					if (index >= chunks.size() && !done) {
						done = true;
						subscriber.onComplete();
					}
				}

				@Override
				public synchronized void cancel() {
					done = true;
				}
			});
		}
	}

	/**
	 * Transcribe 호출이 실패하거나 시간이 초과됐을 때 던진다.
	 */
	public static class TranscribeUnavailableException extends RuntimeException {
	}
}
