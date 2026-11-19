package com.cloudwalkers.threadsapp.system;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.java21.instrument.binder.jdk.VirtualThreadMetrics;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Micrometer's built-in JVM binders only cover platform threads, so virtual threads need
 * their own metrics:
 * <ul>
 * <li>{@code jvm.threads.virtual.pinned} / {@code jvm.threads.virtual.submit.failed}: JFR
 * based, from {@link VirtualThreadMetrics}.</li>
 * <li>{@code threadsapp.http.requests.active{thread.type}}: requests currently being served,
 * split by virtual vs platform thread.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
class VirtualThreadMetricsConfig {

	@Bean
	VirtualThreadMetrics virtualThreadMetrics() {
		return new VirtualThreadMetrics();
	}

	@Bean
	ThreadTypeRequestFilter threadTypeRequestFilter(MeterRegistry registry) {
		return new ThreadTypeRequestFilter(registry);
	}

	static class ThreadTypeRequestFilter extends OncePerRequestFilter {

		private final AtomicInteger virtual = new AtomicInteger();

		private final AtomicInteger platform = new AtomicInteger();

		ThreadTypeRequestFilter(MeterRegistry registry) {
			register(registry, "virtual", virtual);
			register(registry, "platform", platform);
		}

		private static void register(MeterRegistry registry, String type, AtomicInteger value) {
			Gauge.builder("threadsapp.http.requests.active", value, AtomicInteger::get)
				.description("HTTP requests currently being served, by type of the serving thread")
				.tag("thread.type", type)
				.register(registry);
		}

		@Override
		protected boolean shouldNotFilter(HttpServletRequest request) {
			return request.getRequestURI().startsWith("/actuator");
		}

		@Override
		protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
				throws ServletException, IOException {
			AtomicInteger counter = Thread.currentThread().isVirtual() ? virtual : platform;
			counter.incrementAndGet();
			try {
				chain.doFilter(request, response);
			}
			finally {
				counter.decrementAndGet();
			}
		}

	}

}
