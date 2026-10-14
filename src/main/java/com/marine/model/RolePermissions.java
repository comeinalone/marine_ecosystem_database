package com.marine.model;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Single source of permission decisions for navigation, controls and actions. */
public final class RolePermissions {
    public enum Operation { READ, CREATE, UPDATE, DELETE }

    private static final Map<UserRole, Set<EntityType>> WRITABLE = Map.of(
            UserRole.ADMIN, EnumSet.allOf(EntityType.class),
            UserRole.RESEARCHER, EnumSet.of(EntityType.SAMPLE),
            UserRole.VIEWER, EnumSet.noneOf(EntityType.class));
    private static final Map<String, EntityType> ENTITY_PAGES = Map.of(
            "Fish Species", EntityType.SPECIES,
            "Researchers", EntityType.RESEARCHER,
            "DNA Samples", EntityType.SAMPLE,
            "Research Vessels", EntityType.VESSEL,
            "Sampling Locations", EntityType.LOCATION);

    private RolePermissions() { }

    public static boolean allows(UserRole role, EntityType entity, Operation operation) {
        if (role == null || entity == null || operation == null) return false;
        return operation == Operation.READ || WRITABLE.get(role).contains(entity);
    }

    public static boolean canAccessPage(UserRole role, String page) {
        if (role == null || page == null) return false;
        if (page.equals("Dashboard") || page.equals("Reports")) return true;
        for (ResearchTool tool : ResearchTool.values()) if (page.equals(tool.toString())) return true;
        return allows(role, ENTITY_PAGES.get(page), Operation.READ);
    }
}
