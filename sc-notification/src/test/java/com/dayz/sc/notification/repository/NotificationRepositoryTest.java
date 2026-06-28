package com.dayz.sc.notification.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.dayz.sc.notification.mapper.NotificationMapper;
import com.dayz.sc.notification.mapper.NotificationTargetMapper;
import com.dayz.sc.notification.model.entity.Notification;
import com.dayz.sc.notification.repository.impl.MybatisNotificationRepository;
import com.dayz.sc.notification.repository.impl.MybatisNotificationTargetRepository;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationRepositoryTest {

    @Mock
    private NotificationMapper notificationMapper;

    @Mock
    private NotificationTargetMapper notificationTargetMapper;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, Notification.class);
    }

    @Test
    void findAll_shouldExcludeBroadcastNotificationsDeletedByCurrentUser() {
        MybatisNotificationRepository repository = new MybatisNotificationRepository(notificationMapper);
        when(notificationMapper.selectList(any())).thenReturn(List.of());

        repository.findAll(1, 10, null, null, UUID.randomUUID());

        ArgumentCaptor<LambdaQueryWrapper<Notification>> wrapperCaptor = ArgumentCaptor.captor();
        verify(notificationMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .contains("target_type", "NOT IN", "deleted = 1", "deleted = 0");
    }

    @Test
    void countAll_shouldExcludeBroadcastNotificationsDeletedByCurrentUser() {
        MybatisNotificationRepository repository = new MybatisNotificationRepository(notificationMapper);
        when(notificationMapper.selectCount(any())).thenReturn(0L);

        repository.countAll(null, null, UUID.randomUUID());

        ArgumentCaptor<LambdaQueryWrapper<Notification>> wrapperCaptor = ArgumentCaptor.captor();
        verify(notificationMapper).selectCount(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .contains("target_type", "NOT IN", "deleted = 1", "deleted = 0");
    }

    @Test
    void markDeleted_shouldUpsertPersonalDeletionMarker() {
        MybatisNotificationTargetRepository repository = new MybatisNotificationTargetRepository(notificationTargetMapper);
        UUID notificationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        repository.markDeleted(notificationId, userId);

        verify(notificationTargetMapper).markDeleted(notificationId, userId);
    }

    @Test
    void markAllDeleted_shouldAlsoMarkBroadcastNotificationsDeleted() {
        MybatisNotificationTargetRepository repository = new MybatisNotificationTargetRepository(notificationTargetMapper);
        UUID userId = UUID.randomUUID();

        repository.markAllDeleted(userId, 1);

        verify(notificationTargetMapper).markAllDeleted(userId, 1);
        verify(notificationTargetMapper).markAllBroadcastDeleted(userId, 1);
    }
}
