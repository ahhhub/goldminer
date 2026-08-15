package com.godminer.model;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 小队数据模型
 */
public class Team {
    private final String name;
    private final UUID leaderUuid;
    private final Set<UUID> members;
    private final Set<UUID> pendingApplications;
    private final Map<UUID, String> offlinePendingMessages;
    private final long createdAt;

    public Team(String name, UUID leaderUuid) {
        this.name = name;
        this.leaderUuid = leaderUuid;
        this.members = ConcurrentHashMap.newKeySet();
        this.pendingApplications = ConcurrentHashMap.newKeySet();
        this.offlinePendingMessages = new ConcurrentHashMap<>();
        this.members.add(leaderUuid); // 队长也是成员
        this.createdAt = System.currentTimeMillis();
    }

    public String getName() { return name; }
    public UUID getLeaderUuid() { return leaderUuid; }
    public Set<UUID> getMembers() { return members; }
    public Set<UUID> getPendingApplications() { return pendingApplications; }
    public long getCreatedAt() { return createdAt; }

    public boolean isMember(UUID uuid) {
        return members.contains(uuid);
    }

    public boolean isLeader(UUID uuid) {
        return leaderUuid.equals(uuid);
    }

    public void addMember(UUID uuid) {
        members.add(uuid);
        pendingApplications.remove(uuid);
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
    }

    public int getSize() {
        return members.size();
    }

    public void addPendingApplication(UUID uuid) {
        pendingApplications.add(uuid);
    }

    public void removePendingApplication(UUID uuid) {
        pendingApplications.remove(uuid);
    }

    public boolean hasPendingApplication(UUID uuid) {
        return pendingApplications.contains(uuid);
    }

    public void addOfflinePendingMessage(UUID applicantUuid, String message) {
        offlinePendingMessages.put(applicantUuid, message);
    }

    public Map<UUID, String> getOfflinePendingMessages() {
        return offlinePendingMessages;
    }

    public void clearOfflinePendingMessages() {
        offlinePendingMessages.clear();
    }
}
