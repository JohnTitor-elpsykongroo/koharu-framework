package com.johntitor.koharu.jdbc;

import jakarta.annotation.Nullable;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * 基于获取到的PreparedStatement，执行 executeQuery() 或 executeUpdate()
 * */
@FunctionalInterface
public interface PreparedStatementCallback<T> {

    @Nullable
    T doInPreparedStatement(PreparedStatement ps) throws SQLException;

}
