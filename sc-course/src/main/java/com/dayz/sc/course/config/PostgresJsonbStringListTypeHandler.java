package com.dayz.sc.course.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 类型处理器
 *
 * @author DaYZ
 * @since 2026-06-15
 */
@MappedTypes(List.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class PostgresJsonbStringListTypeHandler extends BaseTypeHandler<List<String>> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<String> parameter, JdbcType jdbcType)
            throws SQLException {
        PGobject jsonb = new PGobject();
        jsonb.setType("jsonb");
        jsonb.setValue(writeJson(parameter));
        ps.setObject(i, jsonb);
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return readJson(rs.getString(columnName));
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return readJson(rs.getString(columnIndex));
    }

    @Override
    public List<String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return readJson(cs.getString(columnIndex));
    }

    private String writeJson(List<String> value) throws SQLException {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new SQLException("Failed to serialize JSONB string list", exception);
        }
    }

    private List<String> readJson(String value) throws SQLException {
        if (value == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(value, STRING_LIST_TYPE);
        } catch (JsonProcessingException exception) {
            throw new SQLException("Failed to parse JSONB string list", exception);
        }
    }
}
