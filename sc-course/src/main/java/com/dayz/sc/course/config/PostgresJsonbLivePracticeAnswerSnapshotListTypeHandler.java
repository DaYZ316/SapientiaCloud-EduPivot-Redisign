package com.dayz.sc.course.config;

import com.dayz.sc.course.model.value.LivePracticeAnswerSnapshot;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * PostgreSQL JSONB类型与LivePracticeAnswerSnapshot列表的类型处理器
 *
 * @author DaYZ
 * @since 2026-06-18
 */
@MappedJdbcTypes(JdbcType.OTHER)
public class PostgresJsonbLivePracticeAnswerSnapshotListTypeHandler extends BaseTypeHandler<List<LivePracticeAnswerSnapshot>> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<LivePracticeAnswerSnapshot>> VALUE_TYPE = new TypeReference<>() {
    };

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<LivePracticeAnswerSnapshot> parameter, JdbcType jdbcType)
            throws SQLException {
        PGobject jsonb = new PGobject();
        jsonb.setType("jsonb");
        jsonb.setValue(writeJson(parameter));
        ps.setObject(i, jsonb);
    }

    @Override
    public List<LivePracticeAnswerSnapshot> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return readJson(rs.getString(columnName));
    }

    @Override
    public List<LivePracticeAnswerSnapshot> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return readJson(rs.getString(columnIndex));
    }

    @Override
    public List<LivePracticeAnswerSnapshot> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return readJson(cs.getString(columnIndex));
    }

    private String writeJson(List<LivePracticeAnswerSnapshot> value) throws SQLException {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new SQLException("Failed to serialize live practice answer snapshots", exception);
        }
    }

    private List<LivePracticeAnswerSnapshot> readJson(String value) throws SQLException {
        if (value == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(value, VALUE_TYPE);
        } catch (JsonProcessingException exception) {
            throw new SQLException("Failed to parse live practice answer snapshots", exception);
        }
    }
}
