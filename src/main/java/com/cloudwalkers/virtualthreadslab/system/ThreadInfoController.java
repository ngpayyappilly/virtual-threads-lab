package com.cloudwalkers.virtualthreadslab.system;

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
	 * Load-test demo: blocks inside a native frame, which pins the virtual thread to its carrier
	 * and shows up as {@code jvm.threads.virtual.pinned}. ({@code synchronized} no longer pins
	 * on Java 24+, so it can't be used for this any more.)
	 */
	@GetMapping("/pinned")
	@Operation(summary = "Simulate a pinning bug: sleeps in a native frame, holding the carrier thread")
	ThreadInfo pinned(@RequestParam(defaultValue = "100") long millis) {
		NativePinning.sleepPinned(Math.clamp(millis, 0, MAX_MILLIS));
		return thread();
	}

}
