package com.demonz.velocitynavigator.health;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

public final class MaintenanceEvacuation {
    private final BooleanSupplier required;
    private final Supplier<CompletableFuture<List<String>>> destinations;
    private final Function<String, CompletableFuture<Boolean>> connect;
    private final Runnable disconnect;
    private final int maxAttempts;
    private final long timeoutMillis;
    private final Set<String> attempted = new HashSet<>();

    public MaintenanceEvacuation(BooleanSupplier required, Supplier<CompletableFuture<List<String>>> destinations,
                                 Function<String, CompletableFuture<Boolean>> connect, Runnable disconnect,
                                 int maxAttempts, Duration timeout) {
        this.required = required;
        this.destinations = destinations;
        this.connect = connect;
        this.disconnect = disconnect;
        this.maxAttempts = Math.max(1, Math.min(maxAttempts, 16));
        this.timeoutMillis = Math.max(1, timeout.toMillis());
    }

    public CompletableFuture<Void> run() {
        if (!required.getAsBoolean()) return CompletableFuture.completedFuture(null);
        if (attempted.size() >= maxAttempts) return finish();
        CompletableFuture<List<String>> plan;
        try {
            plan = destinations.get();
        } catch (Exception error) {
            return finish();
        }
        return plan.orTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .handle((candidates, error) -> error == null && candidates != null ? candidates : List.<String>of())
                .thenCompose(candidates -> {
                    if (!required.getAsBoolean()) return CompletableFuture.completedFuture(null);
                    String target = candidates.stream().filter(name -> name != null && !name.isBlank())
                            .filter(name -> !attempted.contains(name.toLowerCase(Locale.ROOT)))
                            .findFirst().orElse(null);
                    if (target == null) return finish();
                    attempted.add(target.toLowerCase(Locale.ROOT));
                    CompletableFuture<Boolean> transfer;
                    try {
                        transfer = connect.apply(target);
                    } catch (Exception error) {
                        return run();
                    }
                    return transfer.orTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                            .handle((success, error) -> error == null && Boolean.TRUE.equals(success))
                            .thenCompose(success -> success ? CompletableFuture.completedFuture(null) : run());
                });
    }

    private CompletableFuture<Void> finish() {
        if (required.getAsBoolean()) disconnect.run();
        return CompletableFuture.completedFuture(null);
    }
}
