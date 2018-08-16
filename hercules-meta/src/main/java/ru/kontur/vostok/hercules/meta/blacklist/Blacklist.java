package ru.kontur.vostok.hercules.meta.blacklist;

import ru.kontur.vostok.hercules.meta.curator.CuratorClient;
import ru.kontur.vostok.hercules.util.schedule.RenewableTask;
import ru.kontur.vostok.hercules.util.schedule.Scheduler;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Gregory Koshelev
 */
public class Blacklist {
    private final CuratorClient curatorClient;
    private final AtomicReference<ConcurrentHashMap<String, Object>> apiKeys = new AtomicReference<>(new ConcurrentHashMap<>());
    private final AtomicReference<State> state = new AtomicReference<>(State.INIT);
    private final RenewableTask updateTask;

    public Blacklist(CuratorClient curatorClient, Scheduler scheduler) {
        this.curatorClient = curatorClient;
        this.updateTask = scheduler.task(this::update, 60_000, false);
    }

    public boolean contains(String apiKey) {
        if (state.get() != State.RUNNING) {
            throw new IllegalStateException("Invalid state of blacklist");
        }
        return apiKeys.get().containsKey(apiKey);
    }

    public void start() throws Exception {
        if (!state.compareAndSet(State.INIT, State.STARTING)) {
            throw new IllegalStateException("Invalid state of blacklist");
        }

        updateTask.renew();

        state.set(State.RUNNING);
    }

    public void stop() {
        state.set(State.STOPPED);
        updateTask.disable();
    }

    private void update() {
        if (state.get() == State.STOPPED) {
            return;
        }

        List<String> children;
        try {
            children = curatorClient.children("/hercules/auth/blacklist", e -> {
                updateTask.renew();
            });
        } catch (Exception e) {
            e.printStackTrace();//TODO: Log it!
            return;
        }

        ConcurrentHashMap<String, Object> newApiKeys = new ConcurrentHashMap<>(children.size());
        for (String apiKey : children) {
            newApiKeys.put(apiKey, STUB);
        }
        apiKeys.set(newApiKeys);
    }

    private enum State {
        INIT,
        STARTING,
        RUNNING,
        STOPPED;
    }

    private static final Object STUB = new Object();
}
