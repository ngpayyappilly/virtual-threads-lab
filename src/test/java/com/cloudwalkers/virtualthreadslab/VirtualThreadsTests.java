package com.cloudwalkers.virtualthreadslab;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class VirtualThreadsTests {

	private final HttpClient client = HttpClient.newHttpClient();

	@LocalServerPort
	int port;

	@Autowired
	MeterRegistry registry;

	@Test
	void requestsAreServedOnVirtualThreads() throws Exception {
		HttpResponse<String> response = get("/api/v1/system/thread");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("\"virtual\":true");
	}

	@Test
	void schedulerMetricsDescribeTheCarrierPool() {
		// Micrometer binds VirtualThreadSchedulerMXBean automatically on Java 25
		assertThat(registry.get("jvm.threads.virtual.parallelism").gauge().value()).isPositive();
		assertThat(registry.get("jvm.threads.virtual.pool.size").gauge().value()).isNotNegative();
		assertThat(registry.get("jvm.threads.virtual.live").tag("scheduling.status", "mounted").gauge().value())
			.isNotNegative();
		assertThat(registry.get("jvm.threads.virtual.live").tag("scheduling.status", "queued").gauge().value())
			.isNotNegative();
	}

	@Test
	void lifecycleMetricsCountVirtualThreads() throws Exception {
		double before = registry.get("jvm.threads.virtual.started").counter().count();

		List<HttpResponse<String>> responses = IntStream.range(0, 20).parallel().mapToObj(i -> {
			try {
				return get("/api/v1/system/slow?millis=50");
			}
			catch (Exception ex) {
				throw new IllegalStateException(ex);
			}
		}).toList();

		assertThat(responses).allSatisfy(r -> assertThat(r.statusCode()).isEqualTo(200));
		// JFR delivers events in batches, so allow a couple of seconds
		await().atMost(Duration.ofSeconds(10))
			.untilAsserted(() -> assertThat(registry.get("jvm.threads.virtual.started").counter().count())
				.isGreaterThanOrEqualTo(before + 20));
		await().atMost(Duration.ofSeconds(10))
			.untilAsserted(() -> assertThat(registry.get("jvm.threads.virtual.ended").counter().count())
				.isGreaterThanOrEqualTo(before + 20));
	}

	@Test
	void aliveGaugeIncludesParkedVirtualThreads() throws Exception {
		List<Thread> parked = IntStream.range(0, 50)
			.mapToObj(i -> Thread.ofVirtual().start(() -> {
				try {
					Thread.sleep(4000);
				}
				catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				}
			}))
			.toList();

		// all 50 are parked in sleep, so they are alive but neither mounted nor queued
		await().atMost(Duration.ofSeconds(10))
			.untilAsserted(() -> assertThat(registry.get("jvm.threads.virtual.alive").gauge().value())
				.isGreaterThanOrEqualTo(50));
		assertThat(registry.get("jvm.threads.virtual.live").tag("scheduling.status", "mounted").gauge().value())
			.isLessThan(50);

		for (Thread thread : parked) {
			thread.interrupt();
		}
	}

	@Test
	void nativePinningDemoIsObservedAsPinnedVirtualThreads() throws Exception {
		long before = registry.get("jvm.threads.virtual.pinned").timer().count();

		for (int i = 0; i < 3; i++) {
			assertThat(get("/api/v1/system/pinned?millis=80").statusCode()).isEqualTo(200);
		}

		await().atMost(Duration.ofSeconds(10))
			.untilAsserted(() -> assertThat(registry.get("jvm.threads.virtual.pinned").timer().count())
				.isGreaterThanOrEqualTo(before + 3));
	}

	@Test
	void synchronizedBlockingDoesNotPinOnJava25() throws Exception {
		// JEP 491: blocking inside synchronized unmounts the virtual thread, so nothing is recorded.
		long before = registry.get("jvm.threads.virtual.pinned").timer().count();
		Object lock = new Object();

		Thread.ofVirtual().start(() -> {
			synchronized (lock) {
				try {
					Thread.sleep(80);
				}
				catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				}
			}
		}).join();

		Thread.sleep(2500); // longer than JFR's batching delay
		assertThat(registry.get("jvm.threads.virtual.pinned").timer().count()).isEqualTo(before);
	}

	private HttpResponse<String> get(String path) throws Exception {
		return client.send(HttpRequest.newBuilder(URI.create("http://localhost:%d%s".formatted(port, path))).build(),
				HttpResponse.BodyHandlers.ofString());
	}

}
