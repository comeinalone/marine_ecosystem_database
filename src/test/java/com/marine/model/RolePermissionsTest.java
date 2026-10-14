package com.marine.model;

import com.marine.model.RolePermissions.Operation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class RolePermissionsTest {
    static Stream<Arguments> permissions() {
        return Stream.of(UserRole.values()).flatMap(role -> Stream.of(EntityType.values())
                .flatMap(entity -> Stream.of(Operation.values()).map(operation -> Arguments.of(role, entity, operation))));
    }

    @ParameterizedTest @MethodSource("permissions")
    void completeEntityPermissionMatrix(UserRole role, EntityType entity, Operation operation) {
        boolean expected = operation == Operation.READ || role == UserRole.ADMIN
                || (role == UserRole.RESEARCHER && entity == EntityType.SAMPLE);
        assertEquals(expected, RolePermissions.allows(role, entity, operation));
    }

    @Test void everyRoleCanBrowseAllModulesAndReports() {
        for (UserRole role : UserRole.values()) {
            for (String page : new String[]{"Dashboard", "Fish Species", "Researchers", "DNA Samples",
                    "Research Vessels", "Sampling Locations", "Reports"})
                assertTrue(RolePermissions.canAccessPage(role, page), role + ": " + page);
        }
    }

    @Test void unknownOrMissingPermissionTargetsAreDenied() {
        assertFalse(RolePermissions.canAccessPage(UserRole.ADMIN, "Unknown"));
        assertFalse(RolePermissions.canAccessPage(UserRole.ADMIN, null));
        assertFalse(RolePermissions.canAccessPage(null, "Reports"));
        assertFalse(RolePermissions.allows(null, EntityType.SAMPLE, Operation.CREATE));
        assertFalse(RolePermissions.allows(UserRole.ADMIN, null, Operation.READ));
        assertFalse(RolePermissions.allows(UserRole.ADMIN, EntityType.SAMPLE, null));
    }

    @Test void allRolesCanOpenEveryResearchTool() {
        for (UserRole role : UserRole.values()) for (ResearchTool tool : ResearchTool.values())
            assertTrue(RolePermissions.canAccessPage(role, tool.toString()));
    }
}
