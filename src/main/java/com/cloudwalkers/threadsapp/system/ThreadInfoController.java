package com.cloudwalkers.threadsapp.system;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
@Tag(name = "System")
class ThreadInfoController {

	private static final long MAX_MILLIS = 10_000;

	record ThreadInfo(String name, boolean virtual, long threadId) {
	}

	@GetMapping("/thread")
	@Operation(summary = "Report which thread served this request (confirms virtual threads)")
	ThreadInfo thread() {
		Thread current = Thread.currentThread();
		return new ThreadInfo(current.getName(), current.isVirtual(), current.threadId());
	}

	@GetMapping("/slow")
	@Operation(summary = "Simulate blocking I/O: sleeps, releasing the carrier thread when virtual")
	ThreadInfo slow(@RequestParam(defaultValue = "500") long millis) throws InterruptedException {
		Thread.sleep(Math.clamp(millis, 0, MAX_MILLIS));
		return thread();
	}

	/**
	 * Load-test demo: blocking inside {@code synchronized} pins the virtual thread to its
	 * carrier on Java 21, which shows up as {@code jvm.threads.virtual.pinned}.
	 */
	@GetMapping("/pinned")
	@Operation(summary = "Simulate a pinning bug: sleeps while holding a monitor")
	ThreadInfo pinned(@RequestParam(defaultValue = "100") long millis) throws InterruptedException {
		synchronized (new Object()) {
			Thread.sleep(Math.clamp(millis, 0, MAX_MILLIS));
		}
		return thread();
	}

}
