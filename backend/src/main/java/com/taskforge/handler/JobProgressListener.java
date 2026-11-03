package com.taskforge.handler;

/**
 * Callback interface for job handlers to report execution progress percentage
 * and current stage descriptions in real-time.
 */
@FunctionalInterface
public interface JobProgressListener {
    /**
     * Reports progress update.
     *
     * @param percent Progress percentage from 0 to 100
     * @param stage Description of the current execution stage
     */
    void onProgress(int percent, String stage);
}
