package com.codingjudge.model.dto.response;

/**
 * Live traffic snapshot for the admin panel. Counts only — no names or
 * emails, so there is nothing sensitive to leak.
 */
public class PresenceResponse {

    /** Users with a heartbeat in the activity window. */
    private long activeUsers;
    /** Total registered accounts. */
    private long registeredUsers;
    /** Submissions currently being judged. */
    private long judgingInFlight;
    /** Submissions waiting in the queue. */
    private long pendingQueue;
    /** Accounts created since UTC midnight. */
    private long signupsToday;

    public PresenceResponse(long activeUsers, long registeredUsers,
                            long judgingInFlight, long pendingQueue,
                            long signupsToday) {
        this.activeUsers = activeUsers;
        this.registeredUsers = registeredUsers;
        this.judgingInFlight = judgingInFlight;
        this.pendingQueue = pendingQueue;
        this.signupsToday = signupsToday;
    }

    public long getActiveUsers() {
        return activeUsers;
    }

    public long getRegisteredUsers() {
        return registeredUsers;
    }

    public long getJudgingInFlight() {
        return judgingInFlight;
    }

    public long getPendingQueue() {
        return pendingQueue;
    }

    public long getSignupsToday() {
        return signupsToday;
    }
}
