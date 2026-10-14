package com.marine.search;

import com.marine.db.ConnectionProvider;
import com.marine.model.TableData;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** Contract fixture only. It does not pretend to implement MySQL matching semantics. */
public final class SearchJdbcStub implements ConnectionProvider {
    @FunctionalInterface public interface Reply { TableData query(String sql, List<Object> parameters) throws Exception; }
    public record Call(String sql, List<Object> parameters) { }
    public final List<Call> calls = new CopyOnWriteArrayList<>();
    public final AtomicInteger connectionsClosed = new AtomicInteger(), statementsClosed = new AtomicInteger(), resultsClosed = new AtomicInteger();
    private final Reply reply;
    public SearchJdbcStub(Reply reply) { this.reply = reply; }
    public static TableData count(long value) { return new TableData(List.of("match_count"), List.of(List.of(value))); }
    public static TableData rows(SearchDefinition def, List<Map<String, Object>> rows) {
        return new TableData(def.fields().stream().map(SearchField::name).toList(),
                rows.stream().map(row -> def.fields().stream().map(f -> row.get(f.name())).toList()).toList());
    }
    @Override public Connection open() {
        return proxy(Connection.class, (method, args) -> switch (method) {
            case "prepareStatement" -> statement((String) args[0]);
            case "close" -> { connectionsClosed.incrementAndGet(); yield null; }
            default -> throw new UnsupportedOperationException(method);
        });
    }
    private PreparedStatement statement(String sql) {
        Map<Integer, Object> bindings = new TreeMap<>();
        return proxy(PreparedStatement.class, (method, args) -> switch (method) {
            case "setObject", "setDate" -> { bindings.put((Integer) args[0], args[1]); yield null; }
            case "executeQuery" -> {
                List<Object> parameters = new ArrayList<>(bindings.values()); calls.add(new Call(sql, parameters));
                yield result(reply.query(sql, parameters));
            }
            case "close" -> { statementsClosed.incrementAndGet(); yield null; }
            default -> throw new UnsupportedOperationException(method);
        });
    }
    private ResultSet result(TableData data) {
        int[] index = {-1};
        return proxy(ResultSet.class, (method, args) -> switch (method) {
            case "next" -> ++index[0] < data.rows().size();
            case "getObject" -> data.rows().get(index[0]).get(args[0] instanceof Integer i ? i - 1 : data.columns().indexOf(args[0]));
            case "getMetaData" -> proxy(ResultSetMetaData.class, (name, values) -> switch (name) {
                case "getColumnCount" -> data.columns().size();
                case "getColumnLabel" -> data.columns().get((Integer) values[0] - 1);
                default -> throw new UnsupportedOperationException(name);
            });
            case "close" -> { resultsClosed.incrementAndGet(); yield null; }
            default -> throw new UnsupportedOperationException(method);
        });
    }
    @FunctionalInterface private interface Invoke { Object invoke(String method, Object[] args) throws Exception; }
    private <T> T proxy(Class<T> type, Invoke call) {
        return type.cast(Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{type},
                (object, method, args) -> call.invoke(method.getName(), args)));
    }
}
