package com.johntitor.koharu.jdbc.mapper.impl;

import com.johntitor.koharu.jdbc.mapper.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class NumberRowMapper implements RowMapper<Number> {

    public static NumberRowMapper instance = new NumberRowMapper();

    private NumberRowMapper() {}

    @Override
    public Number mapRow(ResultSet rs, int rowNum) throws SQLException {
        return (Number) rs.getObject(1);
    }
}

