package com.johntitor.koharu.jdbc;

import com.johntitor.koharu.exception.DataAccessException;
import com.johntitor.koharu.jdbc.mapper.RowMapper;
import com.johntitor.koharu.jdbc.mapper.impl.BeanRowMapper;
import com.johntitor.koharu.jdbc.mapper.impl.BooleanRowMapper;
import com.johntitor.koharu.jdbc.mapper.impl.NumberRowMapper;
import com.johntitor.koharu.jdbc.mapper.impl.StringRowMapper;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class JdbcTemplate {

    private final DataSource dataSource;

    public JdbcTemplate(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Connection自动连接释放
     * */
    public <T> T execute(ConnectionCallback<T> connectionAction) throws DataAccessException {
        try (Connection newConnection = dataSource.getConnection()){
            final boolean autoCommit = newConnection.getAutoCommit();
            if (!autoCommit) {
                newConnection.setAutoCommit(true);
            }
            T result = connectionAction.doInConnection(newConnection);
            if (!autoCommit){
                newConnection.setAutoCommit(false);
            }
            return result;
        } catch (SQLException e) {
            throw new DataAccessException(e);
        }
    }


    public <T> T execute(PreparedStatementCreator psc, PreparedStatementCallback<T> preparedStatementAction) {
        ConnectionCallback<T> connectionAction = new ConnectionCallback<T>() {
            @Override
            public T doInConnection(Connection connection) throws SQLException {
                try (PreparedStatement ps = psc.createPreparedStatement(connection)) {
                    return preparedStatementAction.doInPreparedStatement(ps);
                }
            }
        };
        return execute(connectionAction);
    }


    public <T> T queryForObject(String sql, Class<T> clazz, Object... args) throws DataAccessException {
        if (clazz == String.class) {
            return (T) queryForObject(sql, StringRowMapper.instance, args);
        }
        if (clazz == Boolean.class || clazz == boolean.class) {
            return (T) queryForObject(sql, BooleanRowMapper.instance, args);
        }
        if (Number.class.isAssignableFrom(clazz) || clazz.isPrimitive()) {
            return (T) queryForObject(sql, NumberRowMapper.instance, args);
        }
        return queryForObject(sql, new BeanRowMapper<>(clazz), args);
    }

    public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) throws DataAccessException {

        PreparedStatementCreator psc = preparedStatementCreator(sql, args);
        PreparedStatementCallback<T> preparedStatementAction = new PreparedStatementCallback<T>() {
            @Override
            public T doInPreparedStatement(PreparedStatement ps) throws SQLException {
                T t = null;
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        if (t == null) {
                            t = rowMapper.mapRow(rs, rs.getRow());
                        } else {
                            throw new DataAccessException("Multiple rows found.");
                        }
                    }
                }
                if (t == null) {
                    throw new DataAccessException("Empty result set.");
                }
                return t;
            }
        };
        return execute(psc, preparedStatementAction);
    }

    public <T> List<T> queryForList(String sql, Class<T> clazz, Object... args) throws DataAccessException {
        return queryForList(sql, new BeanRowMapper<>(clazz), args);
    }

    public <T> List<T> queryForList(String sql, RowMapper<T> rowMapper, Object... args) throws DataAccessException {
        PreparedStatementCreator psc = preparedStatementCreator(sql, args);
        PreparedStatementCallback<List<T>> preparedStatementAction = new PreparedStatementCallback<List<T>>() {
            @Override
            public List<T> doInPreparedStatement(PreparedStatement ps) throws SQLException {
                List<T> list = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(rowMapper.mapRow(rs, rs.getRow()));
                    }
                }
                return list;
            }
        };
        return execute(psc, preparedStatementAction);
    }

    public int update(String sql, Object... args) throws DataAccessException {
        return execute(
                // PreparedStatementCreator
                preparedStatementCreator(sql, args),
                // PreparedStatementCallback
                PreparedStatement::executeUpdate);
    }

    public Number updateAndReturnGeneratedKey(String sql, Object... args) throws DataAccessException {
        return execute(
                // PreparedStatementCreator
                (Connection con) -> {
                    var ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                    bindArgs(ps, args);
                    return ps;
                },
                // PreparedStatementCallback
                (PreparedStatement ps) -> {
                    int n = ps.executeUpdate();
                    if (n == 0) {
                        throw new DataAccessException("0 rows inserted.");
                    }
                    if (n > 1) {
                        throw new DataAccessException("Multiple rows inserted.");
                    }
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        while (keys.next()) {
                            return (Number) keys.getObject(1);
                        }
                    }
                    throw new DataAccessException("Should not reach here.");
                });
    }


    private PreparedStatementCreator preparedStatementCreator(String sql, Object... args) {
        PreparedStatementCreator psc = new PreparedStatementCreator() {
            @Override
            public PreparedStatement createPreparedStatement(Connection connection) throws SQLException {
                PreparedStatement ps = connection.prepareStatement(sql);
                // 绑定参数
                bindArgs(ps, args);
                return ps;
            }
        };
        return psc;
    }

    private void bindArgs(PreparedStatement ps, Object... args) throws SQLException {
        for (int i = 0; i < args.length; i++) {
            ps.setObject(i + 1, args[i]);
        }
    }


}
