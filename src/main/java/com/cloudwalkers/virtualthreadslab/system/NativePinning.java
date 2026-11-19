package com.cloudwalkers.virtualthreadslab.system;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * Demo of a virtual thread that really gets pinned on Java 25.
 * <p>
 * Since JEP 491 (Java 24) blocking inside {@code synchronized} no longer pins. What still pins
 * is blocking while a native frame is on the stack. This calls libc {@code qsort}, whose
 * comparator is a Java upcall that sleeps, so the virtual thread parks with a native frame
 * below it and cannot unmount from its carrier. Needs {@code --enable-native-access=ALL-UNNAMED}.
 */
final class NativePinning {

	private static final Linker LINKER = Linker.nativeLinker();

	private static final MethodHandle QSORT = LINKER.downcallHandle(LINKER.defaultLookup().find("qsort").orElseThrow(),
			FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG, JAVA_LONG, ADDRESS));

	private static final FunctionDescriptor COMPARATOR = FunctionDescriptor.of(JAVA_INT,
			ADDRESS.withTargetLayout(JAVA_INT), ADDRESS.withTargetLayout(JAVA_INT));

	private static final MethodHandle COMPARE;

	static {
		try {
			COMPARE = MethodHandles.lookup()
				.findStatic(NativePinning.class, "compare",
						MethodType.methodType(int.class, long.class, MemorySegment.class, MemorySegment.class));
		}
		catch (ReflectiveOperationException ex) {
			throw new ExceptionInInitializerError(ex);
		}
	}

	private NativePinning() {
	}

	/** Blocks for {@code millis} while pinned to the carrier thread. */
	static void sleepPinned(long millis) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment comparator = LINKER.upcallStub(MethodHandles.insertArguments(COMPARE, 0, millis), COMPARATOR,
					arena);
			MemorySegment values = arena.allocateFrom(JAVA_INT, 2, 1);
			QSORT.invoke(values, 2L, (long) Integer.BYTES, comparator);
		}
		catch (Throwable ex) {
			throw new IllegalStateException("Native pinning demo failed", ex);
		}
	}

	// Called from native code: must never throw, or the JVM terminates.
	@SuppressWarnings("unused")
	private static int compare(long millis, MemorySegment a, MemorySegment b) {
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
		return Integer.compare(a.get(JAVA_INT, 0), b.get(JAVA_INT, 0));
	}

}
