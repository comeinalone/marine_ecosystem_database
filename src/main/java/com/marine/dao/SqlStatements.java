package com.marine.dao;

import com.marine.model.EntityType;
import com.marine.model.Field;
import java.util.List;
import java.util.stream.Collectors;

/** SQL identifiers are drawn exclusively from EntityType's fixed whitelist. */
final class SqlStatements {
    private SqlStatements() { }
    static String names(List<Field> fields) {
        return fields.stream().map(f -> "`" + f.name() + "`").collect(Collectors.joining(", "));
    }
    static String predicate(EntityType type) {
        return type.keys().stream().map(f -> "`" + f.name() + "` = ?").collect(Collectors.joining(" AND "));
    }
    static String select(EntityType type) {
        return "SELECT " + names(type.fields()) + " FROM `" + type.table() + "` ORDER BY " + names(type.keys());
    }
    static String insert(EntityType type) {
        return "INSERT INTO `" + type.table() + "` (" + names(type.fields()) + ") VALUES ("
                + type.fields().stream().map(f -> "?").collect(Collectors.joining(", ")) + ")";
    }
    static String update(EntityType type) {
        return "UPDATE `" + type.table() + "` SET " + type.editableFields().stream()
                .map(f -> "`" + f.name() + "` = ?").collect(Collectors.joining(", ")) + " WHERE " + predicate(type);
    }
    static String delete(EntityType type) {
        return "DELETE FROM `" + type.table() + "` WHERE " + predicate(type);
    }
}
