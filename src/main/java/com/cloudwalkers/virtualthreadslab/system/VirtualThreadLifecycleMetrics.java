package com.cloudwalkers.virtualthreadslab.system;

import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import jdk.jfr.consumer.RecordingStream;

/**
 * Counts virtual threads as they start and end, using the {@code jdk.VirtualThreadStart} and
 * {@code jdk.VirtualThreadEnd} JFR events (off by default in the JDK, enabled here).
 * <p>
 * JFR delivers events to a stream in batches about once a second, so these meters trail reality
 * by up to ~1 s. {@code jvm.threads.virtual.alive} is {@code started - ended}: every virtual thread
 * that has not terminated, including ones parked on I/O (Micrometer's own
 * {@code jvm.threads.virtual.live{scheduling.status}} only covers mounted and queued ones). It is
 * an estimate rather than an exact figure. Every virtual thread costs two JFR events; disable with
 * {@code app.virtual-threads.lifecycle-events.enabled=false} if that matters.
 */
class VirtualThreadLifecycleMetrics implements MeterBinder, AutoCloseable {

	private final AtomicLong started = new AtomicLong();

	private final AtomicLong ended = new AtomicLong();

	private volatile RecordingStream stream;

	@Override
	public void bindTo(MeterRegistry registry) {
		Counter startedCounter = Counter.builder("jvm.threads.virtual.started")
			.description("Virtual threads started")
			.baseUnit("threads")
			.register(registry);
		Counter endedCounter = Counter.builder("jvm.threads.virtual.ended")
			.description("Virtual threads that have terminated")
			.baseUnit("threads")
			.register(registry);
		Gauge.builder("jvm.threads.virtual.alive", () -> Math.max(0, started.get() - ended.get()))
			.description("Estimated virtual threads not yet terminated, including parked ones (started minus ended, from JFR events, up to ~1s behind)")
			.baseUnit("threads")
			.register(registry);

		RecordingStream recording = new RecordingStream();
		recording.enable("jdk.VirtualThreadStart");
		recording.enable("jdk.VirtualThreadEnd");
		recording.onEvent("jdk.VirtualThreadStart", event -> {
			started.incrementAndGet();
			startedCounter.increment();
		});
		recording.onEvent("jdk.VirtualThreadEnd", event -> {
			ended.incrementAndGet();
			endedCounter.increment();
		});
		recording.setReuse(false);
		recording.startAsync();
		this.stream = recording;
	}

	@Override
	public void close() {
		RecordingStream recording = this.stream;
		if (recording != null) {
			recording.close();
		}
	}

}
