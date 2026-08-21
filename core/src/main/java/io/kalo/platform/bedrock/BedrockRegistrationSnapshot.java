package io.kalo.platform.bedrock;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Coordinates Kalo's asynchronous pack compiler with Geyser's startup palette event. */
public final class BedrockRegistrationSnapshot {

    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final AtomicReference<State> CURRENT = new AtomicReference<>(
            new State(new Generation(0), new CompletableFuture<>()));

    private BedrockRegistrationSnapshot() {
    }

    /** Starts a new output generation and invalidates the previous runtime snapshot. */
    static @NotNull Generation beginGeneration() {
        Generation generation = new Generation(SEQUENCE.incrementAndGet());
        CURRENT.set(new State(generation, new CompletableFuture<>()));
        return generation;
    }

    /**
     * Publishes only after both generated.mcpack and bedrock-mappings.json are durable.
     * An older overlapping generation cannot overwrite the current one.
     */
    public static void publishSuccess(@NotNull Generation generation,
                                      @NotNull List<BedrockBlockRegistration> registrations) {
        State state = CURRENT.get();
        if (!state.generation().equals(generation)) {
            return;
        }
        List<BedrockBlockRegistration> immutable = List.copyOf(registrations);
        state.ready().complete(immutable);
    }

    /**
     * Returns the latest successful compilation, waiting for the first one when Geyser
     * reaches its palette event before Kalo's asynchronous compiler has finished.
     */
    public static @NotNull Optional<List<BedrockBlockRegistration>> await(
            @NotNull Duration timeout) {
        State state = CURRENT.get();
        try {
            return Optional.of(state.ready().get(timeout.toMillis(), TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (TimeoutException | java.util.concurrent.ExecutionException e) {
            return Optional.empty();
        }
    }

    /** Opaque token tying durable output to the compiler generation that produced it. */
    public record Generation(long id) {
    }

    private record State(Generation generation,
                         CompletableFuture<List<BedrockBlockRegistration>> ready) {
    }
}
