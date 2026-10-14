package com.marine.ui;

import javax.swing.*;
import java.awt.Component;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/** All JDBC work is submitted here, never executed on Swing's event thread. */
public final class UiTasks {
    private UiTasks() { }
    public static <T> void run(Component parent, Callable<T> work, Consumer<T> success, Runnable finished) {
        run(parent, work, success, finished, error ->
                JOptionPane.showMessageDialog(parent, Errors.message(error), "Operation failed", JOptionPane.ERROR_MESSAGE));
    }

    public static <T> void run(Component parent, Callable<T> work, Consumer<T> success, Runnable finished,
                               Consumer<Throwable> failure) {
        new SwingWorker<T, Void>() {
            @Override protected T doInBackground() throws Exception { return work.call(); }
            @Override protected void done() {
                T result;
                try {
                    result = get();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    finished.run();
                    return;
                } catch (ExecutionException ex) {
                    finished.run();
                    failure.accept(ex.getCause());
                    return;
                }
                // Release loading controls before a success callback opens a
                // modal editor or starts the next asynchronous refresh.
                finished.run();
                success.accept(result);
            }
        }.execute();
    }
}
