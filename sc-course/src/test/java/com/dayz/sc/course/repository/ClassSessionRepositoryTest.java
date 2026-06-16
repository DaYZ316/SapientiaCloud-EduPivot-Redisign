package com.dayz.sc.course.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.course.mapper.ClassBarrageMapper;
import com.dayz.sc.course.mapper.ClassParticipantMapper;
import com.dayz.sc.course.mapper.ClassSessionMapper;
import com.dayz.sc.course.model.entity.ClassBarrage;
import com.dayz.sc.course.model.entity.ClassParticipant;
import com.dayz.sc.course.model.entity.ClassSession;
import com.dayz.sc.course.repository.impl.MybatisClassBarrageRepository;
import com.dayz.sc.course.repository.impl.MybatisClassParticipantRepository;
import com.dayz.sc.course.repository.impl.MybatisClassSessionRepository;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClassSessionRepositoryTest {

    @Mock
    private ClassSessionMapper classSessionMapper;

    @Mock
    private ClassParticipantMapper classParticipantMapper;

    @Mock
    private ClassBarrageMapper classBarrageMapper;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, ClassSession.class);
        TableInfoHelper.initTableInfo(assistant, ClassParticipant.class);
        TableInfoHelper.initTableInfo(assistant, ClassBarrage.class);
    }

    @Test
    void findByCourseId_shouldFilterDrafts_whenIncludeDraftsFalse() {
        MybatisClassSessionRepository repository = new MybatisClassSessionRepository(classSessionMapper);
        when(classSessionMapper.selectPage(any(), any())).thenReturn(new Page<>());

        repository.findByCourseId(UUID.randomUUID(), 1, 20, false);

        ArgumentCaptor<LambdaQueryWrapper<ClassSession>> wrapperCaptor = ArgumentCaptor.captor();
        verify(classSessionMapper).selectPage(any(), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("published_at IS NOT NULL");
    }

    @Test
    void findByCourseId_shouldNotFilterDrafts_whenIncludeDraftsTrue() {
        MybatisClassSessionRepository repository = new MybatisClassSessionRepository(classSessionMapper);
        when(classSessionMapper.selectPage(any(), any())).thenReturn(new Page<>());

        repository.findByCourseId(UUID.randomUUID(), 1, 20, true);

        ArgumentCaptor<LambdaQueryWrapper<ClassSession>> wrapperCaptor = ArgumentCaptor.captor();
        verify(classSessionMapper).selectPage(any(), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).doesNotContain("published_at IS NOT NULL");
    }

    @Test
    void countPublishedByCourseIds_shouldGroupByCourseAndFilterDrafts() {
        MybatisClassSessionRepository repository = new MybatisClassSessionRepository(classSessionMapper);
        UUID courseId = UUID.randomUUID();
        when(classSessionMapper.selectMaps(any())).thenReturn(List.of(
                Map.of("course_id", courseId, "published_count", 3L)
        ));

        Map<UUID, Long> result = repository.countPublishedByCourseIds(List.of(courseId));

        ArgumentCaptor<QueryWrapper<ClassSession>> wrapperCaptor = ArgumentCaptor.captor();
        verify(classSessionMapper).selectMaps(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains(
                "published_at IS NOT NULL",
                "deleted =",
                "GROUP BY course_id");
        assertThat(result).containsEntry(courseId, 3L);
    }

    @Test
    void countPublishedByCourseIds_shouldReadCamelCaseMapKeys() {
        MybatisClassSessionRepository repository = new MybatisClassSessionRepository(classSessionMapper);
        UUID courseId = UUID.randomUUID();
        when(classSessionMapper.selectMaps(any())).thenReturn(List.of(
                Map.of("courseId", courseId.toString(), "publishedCount", "4")
        ));

        Map<UUID, Long> result = repository.countPublishedByCourseIds(List.of(courseId));

        assertThat(result).containsEntry(courseId, 4L);
    }

    @Test
    void existsBySessionIdAndUserId_shouldQueryUniqueParticipantPair() {
        MybatisClassParticipantRepository repository = new MybatisClassParticipantRepository(classParticipantMapper);
        when(classParticipantMapper.selectCount(any())).thenReturn(1L);

        assertThat(repository.existsBySessionIdAndUserId(UUID.randomUUID(), UUID.randomUUID())).isTrue();

        ArgumentCaptor<LambdaQueryWrapper<ClassParticipant>> wrapperCaptor = ArgumentCaptor.captor();
        verify(classParticipantMapper).selectCount(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("session_id", "user_id");
    }

    @Test
    void findBySessionId_shouldOrderBarragesByNewestFirst() {
        MybatisClassBarrageRepository repository = new MybatisClassBarrageRepository(classBarrageMapper);
        when(classBarrageMapper.selectPage(any(), any())).thenReturn(new Page<>());

        repository.findBySessionId(UUID.randomUUID(), 1, 20);

        ArgumentCaptor<LambdaQueryWrapper<ClassBarrage>> wrapperCaptor = ArgumentCaptor.captor();
        verify(classBarrageMapper).selectPage(any(), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("session_id", "sent_at DESC");
    }
}
