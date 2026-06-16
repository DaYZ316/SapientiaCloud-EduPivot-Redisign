package com.dayz.sc.course.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.course.mapper.QuestionMapper;
import com.dayz.sc.course.model.entity.Question;
import com.dayz.sc.course.model.enums.QuestionStatus;
import com.dayz.sc.course.repository.impl.MybatisQuestionRepository;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionRepositoryTest {

    @Mock
    private QuestionMapper questionMapper;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, Question.class);
    }

    @Test
    void findAll_shouldIncludePublishedAndOwnDrafts_whenStatusMissingForUser() {
        MybatisQuestionRepository repository = new MybatisQuestionRepository(questionMapper);
        when(questionMapper.selectPage(any(), any())).thenReturn(new Page<>());

        repository.findAll(1, 10, null, UUID.randomUUID(),
                null, null, null, null, UUID.randomUUID());

        ArgumentCaptor<LambdaQueryWrapper<Question>> wrapperCaptor = ArgumentCaptor.captor();
        verify(questionMapper).selectPage(any(), wrapperCaptor.capture());

        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .contains("status", "OR", "sys_user_id");
    }

    @Test
    void findAll_shouldLimitDraftStatusToCurrentUser() {
        MybatisQuestionRepository repository = new MybatisQuestionRepository(questionMapper);
        when(questionMapper.selectPage(any(), any())).thenReturn(new Page<>());

        repository.findAll(1, 10, null, UUID.randomUUID(),
                null, null, QuestionStatus.DRAFT.getCode(), null, UUID.randomUUID());

        ArgumentCaptor<LambdaQueryWrapper<Question>> wrapperCaptor = ArgumentCaptor.captor();
        verify(questionMapper).selectPage(any(), wrapperCaptor.capture());

        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .contains("status", "sys_user_id");
    }
}
