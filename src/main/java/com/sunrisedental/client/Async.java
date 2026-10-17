package com.sunrisedental.client;

import java.awt.Component;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

/** Runs a server call off the Swing event thread so the window never freezes. */
final class Async {

    private Async() {
    }

    /**
     * Runs {@code work} in the background and delivers the result on the event thread.
     *
     * @param owner     component used to show error dialogs
     * @param work      the call to the server
     * @param onSuccess receives the result on the event thread
     * @param <T>       result type
     */
    static <T> void run(Component owner, Supplier<T> work, Consumer<T> onSuccess) {
        run(owner, work, onSuccess, null);
    }

    /**
     * Like {@link #run(Component, Supplier, Consumer)} but also calls {@code onFailure} after showing the error.
     *
     * @param owner     component used to show error dialogs
     * @param work      the call to the server
     * @param onSuccess receives the result on the event thread
     * @param onFailure runs on the event thread after a failure (may be null)
     * @param <T>       result type
     */
    static <T> void run(Component owner, Supplier<T> work, Consumer<T> onSuccess, Runnable onFailure) {
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() {
                return work.get();
            }

            @Override
            protected void done() {
                try {
                    onSuccess.accept(get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    showError(owner, e.getCause());
                    if (onFailure != null) {
                        onFailure.run();
                    }
                }
            }
        }.execute();
    }

    /**
     * Shows an error in a dialog.
     *
     * @param owner parent component
     * @param error what went wrong
     */
    static void showError(Component owner, Throwable error) {
        String message = error instanceof ApiClientException api ? api.fullMessage()
                : "Unexpected error: " + error.getMessage();
        JOptionPane.showMessageDialog(owner, message, "Sunrise Dental", JOptionPane.ERROR_MESSAGE);
    }
}
