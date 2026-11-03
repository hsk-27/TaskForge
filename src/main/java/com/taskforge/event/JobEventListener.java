package com.taskforge.event;

/**
 * Consumer callback invoked whenever a JobEvent occurs.
 */
@FunctionalInterface
public interface JobEventListener {
    void onEvent(JobEvent event);
}
