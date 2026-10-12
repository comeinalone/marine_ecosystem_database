package com.marine.ui;

import java.sql.SQLException;

public final class Errors {
    private Errors() { }
    public static String message(Throwable error) {
        if (error instanceof SQLException sql) {
            if (sql.getSQLState() != null && sql.getSQLState().startsWith("08"))
                return "Cannot connect to MySQL. Check the server, database initialization and connection configuration, then retry.";
            return switch (sql.getErrorCode()) {
                case 1062 -> "That key, scientific name or email is already in use. Choose a unique value.";
                case 1451 -> "This record is still referenced. Remove dependent samples, contacts or subtype records first. Species audit history also prevents deletion.";
                case 1452 -> "A referenced record no longer exists. Refresh the lists and choose valid references.";
                case 3819, 1048 -> "A required value or database constraint was violated. Check the form values.";
                case 1045 -> "MySQL rejected the login. Check the username, password and whether the account is unlocked.";
                case 1142, 1370 -> "Your database account does not have permission for this operation.";
                case 1146, 1305 -> "A required table, view or routine is missing. Complete database initialization first.";
                default -> "Database operation failed (SQLState " + sql.getSQLState() + ", error " + sql.getErrorCode()
                        + "). Check the data and database setup, then refresh.";
            };
        }
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
