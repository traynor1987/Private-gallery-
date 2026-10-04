package uk.co.traynor.privategallery.core.security.staging;

import java.util.concurrent.*;
import java.util.function.*;

/** Non-completable view of one private root acknowledgement cell.
 * Allocated before the key array. Every transformation returns a dependent stage; callers
 * may mutate those stages without completing, cancelling or obtruding the private cell.
 * Only API24 methods are delegated. Later interface defaults compose these safe methods;
 * this class does not invoke API31 minimalCompletionStage/copy or newer default methods.
 */
final class StagingAcknowledgementView<T> implements CompletionStage<T> {
    private final CompletableFuture<T> source;
    StagingAcknowledgementView(CompletableFuture<T> source) { this.source = source; }

    @Override public CompletionStage<Void> acceptEither(CompletionStage<? extends T> p0, Consumer<? super T> p1) { return source.acceptEither(p0, p1); }
    @Override public CompletionStage<Void> acceptEitherAsync(CompletionStage<? extends T> p0, Consumer<? super T> p1) { return source.acceptEitherAsync(p0, p1); }
    @Override public CompletionStage<Void> acceptEitherAsync(CompletionStage<? extends T> p0, Consumer<? super T> p1, Executor p2) { return source.acceptEitherAsync(p0, p1, p2); }
    @Override public <U> CompletionStage<U> applyToEither(CompletionStage<? extends T> p0, Function<? super T, U> p1) { return source.applyToEither(p0, p1); }
    @Override public <U> CompletionStage<U> applyToEitherAsync(CompletionStage<? extends T> p0, Function<? super T, U> p1) { return source.applyToEitherAsync(p0, p1); }
    @Override public <U> CompletionStage<U> applyToEitherAsync(CompletionStage<? extends T> p0, Function<? super T, U> p1, Executor p2) { return source.applyToEitherAsync(p0, p1, p2); }
    @Override public CompletionStage<T> exceptionally(Function<Throwable, ? extends T> p0) { return source.exceptionally(p0); }
    @Override public <U> CompletionStage<U> handle(BiFunction<? super T, Throwable, ? extends U> p0) { return source.handle(p0); }
    @Override public <U> CompletionStage<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> p0) { return source.handleAsync(p0); }
    @Override public <U> CompletionStage<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> p0, Executor p1) { return source.handleAsync(p0, p1); }
    @Override public CompletionStage<Void> runAfterBoth(CompletionStage<?> p0, Runnable p1) { return source.runAfterBoth(p0, p1); }
    @Override public CompletionStage<Void> runAfterBothAsync(CompletionStage<?> p0, Runnable p1) { return source.runAfterBothAsync(p0, p1); }
    @Override public CompletionStage<Void> runAfterBothAsync(CompletionStage<?> p0, Runnable p1, Executor p2) { return source.runAfterBothAsync(p0, p1, p2); }
    @Override public CompletionStage<Void> runAfterEither(CompletionStage<?> p0, Runnable p1) { return source.runAfterEither(p0, p1); }
    @Override public CompletionStage<Void> runAfterEitherAsync(CompletionStage<?> p0, Runnable p1) { return source.runAfterEitherAsync(p0, p1); }
    @Override public CompletionStage<Void> runAfterEitherAsync(CompletionStage<?> p0, Runnable p1, Executor p2) { return source.runAfterEitherAsync(p0, p1, p2); }
    @Override public CompletionStage<Void> thenAccept(Consumer<? super T> p0) { return source.thenAccept(p0); }
    @Override public CompletionStage<Void> thenAcceptAsync(Consumer<? super T> p0) { return source.thenAcceptAsync(p0); }
    @Override public CompletionStage<Void> thenAcceptAsync(Consumer<? super T> p0, Executor p1) { return source.thenAcceptAsync(p0, p1); }
    @Override public <U> CompletionStage<Void> thenAcceptBoth(CompletionStage<? extends U> p0, BiConsumer<? super T, ? super U> p1) { return source.thenAcceptBoth(p0, p1); }
    @Override public <U> CompletionStage<Void> thenAcceptBothAsync(CompletionStage<? extends U> p0, BiConsumer<? super T, ? super U> p1) { return source.thenAcceptBothAsync(p0, p1); }
    @Override public <U> CompletionStage<Void> thenAcceptBothAsync(CompletionStage<? extends U> p0, BiConsumer<? super T, ? super U> p1, Executor p2) { return source.thenAcceptBothAsync(p0, p1, p2); }
    @Override public <U> CompletionStage<U> thenApply(Function<? super T, ? extends U> p0) { return source.thenApply(p0); }
    @Override public <U> CompletionStage<U> thenApplyAsync(Function<? super T, ? extends U> p0) { return source.thenApplyAsync(p0); }
    @Override public <U> CompletionStage<U> thenApplyAsync(Function<? super T, ? extends U> p0, Executor p1) { return source.thenApplyAsync(p0, p1); }
    @Override public <U, V> CompletionStage<V> thenCombine(CompletionStage<? extends U> p0, BiFunction<? super T, ? super U, ? extends V> p1) { return source.thenCombine(p0, p1); }
    @Override public <U, V> CompletionStage<V> thenCombineAsync(CompletionStage<? extends U> p0, BiFunction<? super T, ? super U, ? extends V> p1) { return source.thenCombineAsync(p0, p1); }
    @Override public <U, V> CompletionStage<V> thenCombineAsync(CompletionStage<? extends U> p0, BiFunction<? super T, ? super U, ? extends V> p1, Executor p2) { return source.thenCombineAsync(p0, p1, p2); }
    @Override public <U> CompletionStage<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> p0) { return source.thenCompose(p0); }
    @Override public <U> CompletionStage<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> p0) { return source.thenComposeAsync(p0); }
    @Override public <U> CompletionStage<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> p0, Executor p1) { return source.thenComposeAsync(p0, p1); }
    @Override public CompletionStage<Void> thenRun(Runnable p0) { return source.thenRun(p0); }
    @Override public CompletionStage<Void> thenRunAsync(Runnable p0) { return source.thenRunAsync(p0); }
    @Override public CompletionStage<Void> thenRunAsync(Runnable p0, Executor p1) { return source.thenRunAsync(p0, p1); }
    @Override public CompletableFuture<T> toCompletableFuture() { return source.thenApply(value -> value); }
    @Override public CompletionStage<T> whenComplete(BiConsumer<? super T, ? super Throwable> p0) { return source.whenComplete(p0); }
    @Override public CompletionStage<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> p0) { return source.whenCompleteAsync(p0); }
    @Override public CompletionStage<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> p0, Executor p1) { return source.whenCompleteAsync(p0, p1); }
}
