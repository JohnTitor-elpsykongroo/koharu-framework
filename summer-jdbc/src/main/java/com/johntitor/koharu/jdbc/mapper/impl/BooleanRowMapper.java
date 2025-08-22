package com.johntitor.koharu.jdbc.mapper.impl;

import com.johntitor.koharu.jdbc.mapper.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class BooleanRowMapper implements RowMapper<Boolean> {

    public static BooleanRowMapper instance = new BooleanRowMapper();

    private BooleanRowMapper() {}

    @Override
    public Boolean mapRow(ResultSet rs, int rowNum) throws SQLException {
        return rs.getBoolean(1);
    }
}