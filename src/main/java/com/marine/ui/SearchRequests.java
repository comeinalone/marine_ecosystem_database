package com.marine.ui;

import java.awt.Component;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/** EDT-owned generation gate shared by searches and autocomplete. */
final class SearchRequests {
    private long generation;
    private boolean closed;
    void invalidate() { generation++; }
    void close() { closed = true; invalidate(); }
    <T> void run(Component owner, RoleContext roles, Callable<T> work, Consumer<T> show, Consumer<Throwable> failure) {
        if (closed) return;
        long request = ++generation, revision = roles.revision();
        UiTasks.run(owner, work, value -> {
            if (!closed && request == generation && revision == roles.revision()) show.accept(value);
        }, () -> { }, error -> {
            if (!closed && request == generation && revision == roles.revision()) failure.accept(error);
        });
    }
}
