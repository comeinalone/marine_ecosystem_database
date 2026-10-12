package com.marine.ui;

import com.marine.dao.*;
import com.marine.db.ConnectionProvider;
import com.marine.model.EntityType;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import static org.junit.jupiter.api.Assertions.*;

class SwingPanelsTest {
    @Test void allPanelsConstructWithoutConnectingToTheDatabase() throws Exception {
        ConnectionProvider unavailable = () -> { throw new AssertionError("Construction must not perform JDBC work"); };
        SwingUtilities.invokeAndWait(() -> {
            EntityDao dao = new EntityDao(unavailable);
            for (EntityType type : EntityType.values()) assertNotNull(new CrudPanel(type, dao));
            assertNotNull(new ResearchersPanel(dao));
            assertNotNull(new ReportsPanel(new ReportDao(unavailable)));
            assertNotNull(new DashboardPanel(new ReportDao(unavailable), page -> { }));
        });
    }
}
