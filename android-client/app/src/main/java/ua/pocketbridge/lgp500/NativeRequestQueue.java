package ua.pocketbridge.lgp500;

import android.os.Handler;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class NativeRequestQueue {
    interface Request { P500ApiClient.Result run(); }
    interface Callback { void complete(P500ApiClient.Result result); }

    private final Handler handler;
    private final ThreadPoolExecutor executor;
    private volatile boolean closed;

    NativeRequestQueue(Handler handler, int capacity) {
        this.handler = handler;
        executor = new ThreadPoolExecutor(
                1, 1, 20L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(Math.max(2, capacity)),
                new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
    }

    boolean submit(final Request request, final Callback callback) {
        if (closed) return false;
        try {
            executor.execute(new Runnable() {
                @Override public void run() {
                    final P500ApiClient.Result result = request.run();
                    if (callback != null && !closed) {
                        handler.post(new Runnable() {
                            @Override public void run() {
                                if (!closed) callback.complete(result);
                            }
                        });
                    }
                }
            });
            return true;
        } catch (RejectedExecutionException exception) {
            if (callback != null && !closed) {
                handler.post(new Runnable() {
                    @Override public void run() {
                        if (!closed) callback.complete(new P500ApiClient.Result(
                                false, 0, 0L, null, "Черга команд переповнена"));
                    }
                });
            }
            return false;
        }
    }

    int pending() {
        return executor.getQueue().size();
    }

    void close() {
        closed = true;
        executor.shutdownNow();
    }
}
