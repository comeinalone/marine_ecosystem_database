package com.marine.ui;

import com.marine.db.ConnectionProvider;
import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

final class RoleUiTestSupport {
    private RoleUiTestSupport() { }

    static <T> T onEdt(Callable<T> work) throws Exception {
        FutureTask<T> task = new FutureTask<>(work);
        SwingUtilities.invokeAndWait(task);
        return task.get();
    }

    static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (!onEdt(condition::getAsBoolean)) {
            if (System.nanoTime() > deadline) fail("Timed out waiting for Swing worker");
            Thread.sleep(10);
        }
    }

    static <T extends Component> List<T> components(Container root, Class<T> type) {
        List<T> matches = new ArrayList<>();
        for (Component component : root.getComponents()) {
            if (type.isInstance(component)) matches.add(type.cast(component));
            if (component instanceof Container container) matches.addAll(components(container, type));
        }
        return matches;
    }

    static JButton button(Container root, String text) {
        return components(root, JButton.class).stream().filter(button -> text.equals(button.getText()))
                .findFirst().orElseThrow();
    }

    static boolean hasLabel(Container root, String text) {
        return components(root, JLabel.class).stream().anyMatch(label -> text.equals(label.getText()));
    }

    /** Exercises the real DAOs against a JDBC double, without requiring a MySQL server. */
    static final class JdbcStub implements ConnectionProvider {
        final List<String> queries = new CopyOnWriteArrayList<>();
        final AtomicInteger writes = new AtomicInteger();
        final AtomicInteger dashboardReads = new AtomicInteger();
        final CountDownLatch firstDashboardStarted = new CountDownLatch(1);
        final CountDownLatch firstEntityStarted = new CountDownLatch(1);
        volatile CountDownLatch releaseFirstDashboard;
        volatile CountDownLatch releaseFirstEntity;

        @Override public Connection open() {
            return proxy(Connection.class, (method, args) -> switch (method) {
                case "prepareStatement" -> statement((String) args[0]);
                case "close" -> null;
                default -> throw new UnsupportedOperationException(method);
            });
        }

        private PreparedStatement statement(String sql) {
            return proxy(PreparedStatement.class, (method, args) -> switch (method) {
                case "executeQuery" -> {
                    queries.add(sql);
                    if (sql.contains("AS researchers")) {
                        int read = dashboardReads.incrementAndGet();
                        if (read == 1) {
                            firstDashboardStarted.countDown();
                            if (releaseFirstDashboard != null && !releaseFirstDashboard.await(8, TimeUnit.SECONDS))
                                throw new SQLException("Timed out waiting for test release");
                        }
                        yield result(List.of("researchers", "species", "samples", "endangered"),
                                List.of(List.of(11L * read, 22L * read, 33L * read, 44L * read)));
                    }
                    if (releaseFirstEntity != null && firstEntityStarted.getCount() > 0) {
                        firstEntityStarted.countDown();
                        if (!releaseFirstEntity.await(8, TimeUnit.SECONDS))
                            throw new SQLException("Timed out waiting for test release");
                    }
                    yield result(List.of("id"), List.of());
                }
                case "executeUpdate" -> { writes.incrementAndGet(); yield 1; }
                case "setObject", "setDate", "close" -> null;
                default -> throw new UnsupportedOperationException(method);
            });
        }

        private ResultSet result(List<String> columns, List<List<Object>> rows) {
            AtomicInteger index = new AtomicInteger(-1);
            ResultSetMetaData metadata = proxy(ResultSetMetaData.class, (method, args) -> switch (method) {
                case "getColumnCount" -> columns.size();
                case "getColumnLabel" -> columns.get((Integer) args[0] - 1);
                default -> throw new UnsupportedOperationException(method);
            });
            return proxy(ResultSet.class, (method, args) -> switch (method) {
                case "next" -> index.incrementAndGet() < rows.size();
                case "getMetaData" -> metadata;
                case "getObject" -> rows.get(index.get()).get((Integer) args[0] - 1);
                case "close" -> null;
                default -> throw new UnsupportedOperationException(method);
            });
        }

        private interface Call { Object invoke(String method, Object[] args) throws Exception; }
        private <T> T proxy(Class<T> type, Call call) {
            return type.cast(Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{type},
                    (proxy, method, args) -> call.invoke(method.getName(), args)));
        }
    }
}
