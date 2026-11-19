package com.cloudwalkers.virtualthreadslab.system;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * Binds the {@code OTEL} Logback appender (see logback-spring.xml) to Spring Boot's
 * auto-configured {@link OpenTelemetry}, so logs are exported to the OTLP endpoint.
 */
@Component
class OpenTelemetryLogbackConfig implements InitializingBean {

	private final OpenTelemetry openTelemetry;

	OpenTelemetryLogbackConfig(OpenTelemetry openTelemetry) {
		this.openTelemetry = openTelemetry;
	}

	@Override
	public void afterPropertiesSet() {
		OpenTelemetryAppender.install(openTelemetry);
	}

}
