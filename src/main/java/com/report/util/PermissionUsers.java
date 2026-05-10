package com.report.util;

import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Set;

public final class PermissionUsers {

    public static final String ALL = "*";

    private PermissionUsers() {
    }

    public static String normalize(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String trimmed = raw.trim();
        if (ALL.equals(trimmed)) {
            return ALL;
        }
        Set<String> values = new LinkedHashSet<String>();
        String[] parts = trimmed.split(",");
        for (String part : parts) {
            String value = part == null ? "" : part.trim();
            if (!StringUtils.hasText(value)) {
                continue;
            }
            if (ALL.equals(value)) {
                return ALL;
            }
            values.add(value);
        }
        if (values.isEmpty()) {
            return "";
        }
        return String.join(",", values);
    }

    public static boolean matches(String configuredUsers, String userId) {
        String normalizedUsers = normalize(configuredUsers);
        if (!StringUtils.hasText(normalizedUsers)) {
            return false;
        }
        if (ALL.equals(normalizedUsers)) {
            return true;
        }
        String safeUserId = userId == null ? "" : userId.trim();
        if (!StringUtils.hasText(safeUserId)) {
            return false;
        }
        String[] values = normalizedUsers.split(",");
        for (String value : values) {
            if (safeUserId.equals(value)) {
                return true;
            }
        }
        return false;
    }
}
