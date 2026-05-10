package com.report.service.impl;

import com.report.service.UserContextService;
import com.report.util.PermissionUsers;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ConfiguredUserContextService implements UserContextService {

    @Value("${report.admin-user-ids:admin,zhangshan}")
    private String adminUserIds;

    @Override
    public String getCurrentUserId() {
        return "admin";
    }

    @Override
    public boolean isCurrentUserAdmin() {
        return PermissionUsers.matches(adminUserIds, getCurrentUserId());
    }

    @Override
    public boolean hasCurrentUserPermission(String permissionUsers) {
        return PermissionUsers.matches(permissionUsers, getCurrentUserId());
    }
}
