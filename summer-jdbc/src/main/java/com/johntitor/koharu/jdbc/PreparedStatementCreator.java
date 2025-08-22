package com.johntitor.koharu.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * 负责基于 Connection 创建一个 PreparedStatement，并绑定参数
 * */
@FunctionalInterface
public interface PreparedStatementCreator {
    PreparedStatement createPreparedStatement(Connection connection) throws SQLException;

}