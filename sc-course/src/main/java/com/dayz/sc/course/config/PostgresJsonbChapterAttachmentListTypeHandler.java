package com.dayz.sc.course.config;

import com.dayz.sc.course.model.entity.ChapterAttachment;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 章节附件元数据 JSONB 类型处理器
 *
 * @author DaYZ
 * @since 2026-06-18
 */
@MappedTypes(List.class)
@MappedJdbcTypes(value = JdbcType.OTHER, includeNullJdbcType = true)
public class PostgresJsonbChapterAttachmentListTypeHandler extends BaseTypeHandler<List<ChapterAttachment>> {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .findAndAddModules()
            .build();

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<ChapterAttachment> parameter, JdbcType jdbcType)
            throws SQLException {
        PGobject jsonb = new PGobject();
        jsonb.setType("jsonb");
        jsonb.setValue(writeJson(parameter));
        ps.setObject(i, jsonb);
    }

    @Override
    public List<ChapterAttachment> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return readJson(rs.getString(columnName));
    }

    @Override
    public List<ChapterAttachment> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return readJson(rs.getString(columnIndex));
    }

    @Override
    public List<ChapterAttachment> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return readJson(cs.getString(columnIndex));
    }

    private String writeJson(List<ChapterAttachment> value) throws SQLException {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new SQLException("序列化章节附件 JSONB 列表失败", exception);
        }
    }

    private List<ChapterAttachment> readJson(String value) throws SQLException {
        if (value == null) {
            return null;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(value);
            if (!root.isArray()) {
                throw new SQLException("章节附件 JSONB 值必须是数组");
            }

            List<ChapterAttachment> attachments = new ArrayList<>();
            int index = 1;
            for (JsonNode item : root) {
                if (item.isTextual()) {
                    String url = item.asText();
                    attachments.add(new ChapterAttachment(null, legacyDisplayName(url, index), legacyDisplayName(url, index), null, null, url));
                } else if (item.isObject()) {
                    attachments.add(OBJECT_MAPPER.treeToValue(item, ChapterAttachment.class));
                }
                index++;
            }
            return attachments;
        } catch (JsonProcessingException exception) {
            throw new SQLException("解析章节附件 JSONB 列表失败", exception);
        }
    }

    private String legacyDisplayName(String url, int index) {
        if (url == null || url.isBlank()) {
            return "Attachment " + index;
        }
        int queryIndex = url.indexOf('?');
        String normalized = queryIndex >= 0 ? url.substring(0, queryIndex) : url;
        int slashIndex = normalized.lastIndexOf('/');
        String name = slashIndex >= 0 ? normalized.substring(slashIndex + 1) : normalized;
        return name.isBlank() ? "Attachment " + index : name;
    }
}
