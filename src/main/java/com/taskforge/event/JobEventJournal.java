package com.taskforge.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Thread-safe event journal and dispatcher.
 *
 * Stores all execution events in chronological order and broadcasts them
 * to registered listeners.
 */
public class JobEventJournal {
    private final List<JobEvent> events = new CopyOnWriteArrayList<>();
    private final List<JobEventListener> listeners = new CopyOnWriteArrayList<>();

    public void addListener(JobEventListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(JobEventListener listener) {
        listeners.remove(listener);
    }

    /**
     * Records an event and immediately notifies all registered listeners.
     */
    public void record(JobEvent event) {
        if (event == null) return;
        events.add(event);
        for (JobEventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                // Safeguard: listener errors must not disrupt the core execution pipeline
                System.err.println("Error in JobEventListener: " + e.getMessage());
            }
        }
    }

    /**
     * Returns an unmodifiable snapshot of all recorded events.
     */
    public List<JobEvent> getAllEvents() {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }

    /**
     * Returns all events associated with a specific Job ID.
     */
    public List<JobEvent> getEventsForJob(String jobId) {
        if (jobId == null) return List.of();
        return events.stream()
                .filter(e -> jobId.equals(e.jobId()))
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Clears recorded events. Useful between test executions.
     */
    public void clear() {
        events.clear();
    }
}
