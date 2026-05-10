package com.report.service;

public interface UserContextService {

    String getCurrentUserId();

    boolean isCurrentUserAdmin();

    boolean hasCurrentUserPermission(String permissionUsers);
}
