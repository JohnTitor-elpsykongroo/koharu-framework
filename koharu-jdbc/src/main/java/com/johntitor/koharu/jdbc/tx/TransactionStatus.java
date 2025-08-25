package com.johntitor.koharu.jdbc.tx;

import java.sql.Connection;

public class TransactionStatus {
    private final Connection connection;

    public TransactionStatus(Connection connection) {
        this.connection = connection;
    }

    public Connection getConnection() {
        return connection;
    }
}