# 剩余代码规范修复提示词

> 以下提示词可直接粘贴给 Claude Code 使用，按 PR 拆分，每个 PR 独立可验证。

---

## PR-1: VO 补全 @JsonProperty（22 个文件）

```
深度检查项目中所有 VO record（位于各服务的 model/vo/ 包），为每个字段添加 @JsonProperty 注解。

参考合规样例：CourseVO.java、CourseDetailVO.java（已有 @JsonProperty）。

需要修改的文件清单：

sc-auth:
- LoginResponse.java
- UserProfile.java
- StudentInfo.java
- TeacherInfo.java

sc-course:
- ChapterVO.java
- EnrollmentVO.java
- ForumVO.java
- ForumPostVO.java
- ForumReplyVO.java
- QuestionBankVO.java
- QuestionVO.java
- QuestionOptionVO.java
- QuestionAnswerVO.java
- CourseFileVO.java
- CourseInvitationVO.java
- CourseAccessVO.java

sc-notification:
- NotificationVO.java
- UnreadCountVO.java

sc-storage:
- FileAsset.java
- UploadTicket.java
- DownloadUrlResponse.java
- StorageObjectInfo.java

规范要求：
1. 每个 record 组件参数前加 @JsonProperty
2. @Nullable 字段保持 @Nullable 注解在 @JsonProperty 之后
3. 不要修改字段名或类型
4. 不要修改方法体或其他代码

修改后运行 mvn compile 验证编译通过。
```

---

## PR-2: 写端点添加 @RateLimited（30+ 个方法）

```
为项目中所有 POST/PUT/DELETE 写端点添加 @RateLimited 限流注解。

已有限流的端点（不要重复添加）：
- CourseController.createCourse
- ChapterController.createChapter
- EnrollmentController.enroll

需要添加限流的控制器和方法：

sc-auth:
- AuthController.logout — @RateLimited(maxRequests = 10, windowSeconds = 60)
- UserManagementController — 所有 PUT 端点均添加相同限流

sc-course:
- CourseController.updateCourse, deleteCourse
- ChapterController.updateChapter, deleteChapter
- EnrollmentController.updateStatus, dropCourse
- ForumController — 所有写端点（updateForum, deleteForum, updatePost, deletePost, toggleTop, toggleEssence, toggleLock, likePost, unlikePost, viewPost, acceptReply, unacceptReply, likeReply, unlikeReply）
- QuestionBankController — 所有写端点（updateQuestionBank, deleteQuestionBank, updateQuestion, deleteQuestion, publishQuestion, unpublishQuestion, viewQuestion）
- InvitationController — 所有端点（invite, accept, decline, withdraw）
- CourseFileController — bindFile, deleteFile

sc-notification:
- NotificationController — 所有端点（sendNotification, markAsRead, markAllAsRead, deleteNotification, deleteAllNotifications, recallNotification）

sc-storage:
- StorageController — 所有端点（createUpload, completeUpload, deleteFile）

限流参数建议：
- 普通写操作：@RateLimited(maxRequests = 10, windowSeconds = 60)
- 高频操作（如 likePost, viewPost）：@RateLimited(maxRequests = 30, windowSeconds = 60)
- 批量操作（如 markAllAsRead, deleteAllNotifications）：@RateLimited(maxRequests = 5, windowSeconds = 60)

规范：注解放在 @PostMapping/@PutMapping/@DeleteMapping 之后、方法定义之前。
修改后运行 mvn compile 验证。
```

---

## PR-3: Entity 补全 @TableField（15+ 个文件）

```
为项目中所有缺少 @TableField 注解的实体字段补全注解。

需要修改的实体文件：

sc-auth:
- Student.java — userId, studentNo, grade, major, school
- Teacher.java — userId, employeeNo, department, title, school

sc-course:
- Course.java — teacherId, coverUrl, coverFileId, courseType, maxStudents, isPublic, semester, location
- Enrollment.java — courseId, studentId, enrolledAt, completedAt
- Forum.java — courseId, forumName, forumType, allowAnonymous, postCount, replyCount
- ForumPost.java — 所有非 @TableId 字段
- ForumReply.java — 所有非 @TableId 字段
- Question.java — 所有非 @TableId 字段
- CourseTeacher.java — courseId, teacherId（id 已有 @TableId）
- CourseFile.java — courseId, storageObjectId, displayName, createdBy, sortOrder

sc-notification:
- Notification.java — senderId, targetType, createdAt, updatedAt
- NotificationTarget.java — notificationId, userId
- NotificationReadStatus.java — notificationId, userId, readAt

sc-storage:
- StorageObject.java — 所有非 @TableId 字段
- StorageUploadSession.java — 所有非 @TableId 字段

规范：
- @TableField("snake_case_column_name")
- 已有 @TableField 的字段不要修改
- @TableId 标注的主键字段不需要 @TableField
- 驼峰转蛇形：userId → user_id, courseId → course_id, createdAt → created_at
- Boolean 字段如 isPublic → is_public, isAnonymous → is_anonymous

修改后运行 mvn compile 验证。
```

---

## PR-4: Service 层抽象 Repository（4 个 Service）

```
以下 Service 直接使用 Mapper，违反分层架构。需要为它们创建 Repository 接口和 MyBatis 实现。

1. sc-storage: StorageService + StorageCleanupService
   - 需要创建 StorageObjectRepository 和 StorageUploadSessionRepository
   - 接口放 com.dayz.sc.storage.repository
   - 实现放 com.dayz.sc.storage.repository.impl，类名前缀 Mybatis
   - 注解 @Repository + @RequiredArgsConstructor
   - 将 Service 中所有 mapper.selectXxx / mapper.insert / mapper.deleteById 调用迁移到 Repository
   - Repository 方法签名用 Optional<T> 返回单结果

2. sc-course: CourseFileService
   - 需要创建 CourseFileRepository 接口 + MybatisCourseFileRepository 实现
   - 将 CourseFileMapper 的直接调用迁移到 Repository

3. sc-auth: UserManagementService
   - 注入了 UserMapper 和 UserIdentityMapper 直接查询
   - 检查是否可以复用已有的 UserRepository，如不能则创建

规范：
- Repository 接口方法命名：findById, save, deleteById, findByXxx, countByXxx
- 分页查询用 wrapper.last("LIMIT " + size + " OFFSET " + offset)
- 单结果返回 Optional<T>
- 批量操作用 saveAll / deleteByIds

修改后运行 mvn compile 验证。
```

---

## PR-5: Kafka 事件统一（3 个维度）

```
修复 Kafka 事件相关的 3 个问题：

5A. 事件记录继承 ScEvent
   - sc-common/sc-common-events 中的 ScEvent 定义了 eventId, eventType, timestamp, source
   - 但所有具体事件（UserRegisteredEvent, UserDeactivatedEvent, CourseCreatedEvent 等）均未实现
   - Java record 不支持继承，改用组合模式：在每个事件中增加 eventType, timestamp, source 字段
   - 或者将 ScEvent 改为接口，事件 record 实现该接口
   - 发布者在发送时填充 eventType（如 "USER_REGISTERED"）、timestamp（Instant.now()）、source（服务名）

5B. UserEventConsumer 补充 UserDeactivatedEvent 处理
   - sc-notification 的 UserEventConsumer 仅处理 UserRegisteredEvent
   - 添加 UserDeactivatedEvent 分支：创建系统通知，通知管理员该用户已停用
   - 遵循现有模式：idempotencyGuard.tryAcquire → 构建 Notification → repository.save → sseEmitter

5C. Kafka topic 名改用常量
   - 3 个 Consumer 的 @KafkaListener 使用硬编码字符串 "sc.user.events" / "sc.course.events"
   - 改为 SpEL 引用 KafkaTopicConstants：topics = "#{T(com.dayz.sc.common.events.config.KafkaTopicConstants).USER_EVENTS}"
   - 或在 @KafkaListener 中使用常量属性引用

修改后运行 mvn compile 验证。
```

---

## PR-6: 配置清理与文档更新

```
6A. 删除 3 个孤立 Nacos 配置文件
   - nacos-config/sc-edupivot-common.yaml（旧 JWT schema，无 application.yaml 引用）
   - nacos-config/local/sc-edupivot-auth-local.yaml（旧 HS256 JWT，已被 infra-local.yaml 替代）
   - nacos-config/docker/sc-edupivot-auth-docker.yaml（同上）
   删除前确认无任何 application.yaml 引用这些文件。

6B. 更新 nacos-config/README.md
   - 修正"不使用 Nacos Discovery"为"同时使用 Nacos Config 和 Discovery"
   - 更新 auth 配置文件名：sc-edupivot-auth-local.yaml → sc-edupivot-auth-infra-local.yaml
   - 更新路由列表：补充 notification、course、storage 和 4 个 openapi 路由
   - 更新内部端点保护说明

6C. 清理死代码 Resilience4j 配置
   - nacos-config/sc-edupivot-auth-common.yaml 中删除 resilience4j.circuitbreaker.instances.sc-storage
   - nacos-config/sc-edupivot-course-common.yaml 中删除 resilience4j.circuitbreaker.instances.sc-storage
   - nacos-config/sc-edupivot-storage-common.yaml 中删除 resilience4j.circuitbreaker.instances.sc-storage（保留 courseAccess）

6D. Gateway scalar sources 补充 storage
   - nacos-config/sc-edupivot-gateway-common.yaml 的 scalar.sources 添加：
     - title: 存储服务
       slug: storage
       url: /openapi/storage

6E. 清理 .env 中的 EDUPIVOT_WEB_PORT
   - .env 中 EDUPIVOT_WEB_PORT=28083 未被任何服务使用，删除该行
```

---

## PR-7: EventPublisher 统一 + 零散修复

```
7A. UserEventPublisher 改用 ObjectProvider
   - sc-auth 的 UserEventPublisher 直接注入 KafkaTemplate<String, Object>
   - 改为 ObjectProvider<KafkaTemplate<String, Object>>，参考 CourseEventPublisher 的写法
   - 发送前检查 getIfAvailable()，为 null 时 log.warn 并跳过

7B. BusinessException 构造函数安全化
   - BusinessException(int code, String message) 内部的 LegacyErrorCode.httpStatus() 永远返回 BAD_REQUEST
   - 改为根据 code 范围推断 HTTP 状态码：4xx 保持原样，5xx 返回 INTERNAL_SERVER_ERROR
   - 或者直接移除此构造函数，强制使用 BusinessException(ErrorCode) 形式

7C. ErrorCodes 补充专用错误码
   - 添加存储相关：STORAGE_OBJECT_NOT_FOUND(40409), STORAGE_UPLOAD_FAILED(40010), STORAGE_UNAUTHORIZED(40303)
   - 添加选课相关：ENROLLMENT_ALREADY_EXISTS(40011), ENROLLMENT_COURSE_FULL(40012)
   - 添加邀请相关：INVITATION_NOT_PENDING(40013), INVITATION_ALREADY_INVITED(40014)
   - 在对应 Service 中替换 generic ErrorCodes.BAD_REQUEST 为专用错误码

7D. sc-common-core pom.xml 移除 mybatis 直接依赖
   - org.mybatis:mybatis 是 ORM 关注点，不应在 core 模块
   - UuidTypeHandler 使用了 BaseTypeHandler 和 JdbcType
   - 将 UuidTypeHandler 移到使用它的模块（如 sc-common-redis 或各服务模块）
   - 或者保留依赖但添加注释说明原因

7E. sc-course 移除未使用的 sc-common-feign 依赖
   - sc-course 没有任何 @FeignClient，依赖 sc-common-feign 是多余的
   - 从 sc-course/pom.xml 移除该依赖
   - 同时移除 nacos sc-edupivot-course-common.yaml 中的 spring.cloud.openfeign.circuitbreaker.enabled
```

---

## 使用说明

1. 每个 PR 独立可验证，可并行开发
2. 每个 PR 修改后必须 `mvn compile` + `pnpm build` 通过
3. PR-1 ~ PR-3 是纯注解添加，风险最低，建议先做
4. PR-4 涉及架构重构，需要仔细测试
5. PR-5 涉及 Kafka 事件契约变更，需要同步修改发布者和消费者
6. PR-6 ~ PR-7 是配置和清理，风险低但需要验证部署不受影响
