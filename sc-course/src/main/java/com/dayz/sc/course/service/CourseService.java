package com.dayz.sc.course.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.feign.client.AuthInternalClient;
import com.dayz.sc.common.feign.client.StorageInternalClient;
import com.dayz.sc.common.feign.dto.StorageObjectInfo;
import com.dayz.sc.common.feign.dto.UserBasicInfo;
import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.common.response.PageResponse;
import com.dayz.sc.common.security.support.SecurityUtils;
import com.dayz.sc.common.util.PageUtils;
import com.dayz.sc.common.util.UuidV7Generator;
import com.dayz.sc.course.event.CourseEventPublisher;
import com.dayz.sc.course.model.dto.CoursePageRequest;
import com.dayz.sc.course.model.dto.CreateCourseRequest;
import com.dayz.sc.course.model.dto.UpdateCourseRequest;
import com.dayz.sc.course.model.entity.Course;
import com.dayz.sc.course.model.enums.CourseLevel;
import com.dayz.sc.course.model.enums.CourseStatus;
import com.dayz.sc.course.model.enums.EnrollmentStatus;
import com.dayz.sc.course.model.vo.CourseDetailVO;
import com.dayz.sc.course.model.vo.CourseVO;
import com.dayz.sc.course.repository.ClassSessionRepository;
import com.dayz.sc.course.repository.CourseRepository;
import com.dayz.sc.course.repository.CourseTeacherRepository;
import com.dayz.sc.course.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 业务服务
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@Service
@RequiredArgsConstructor
public class CourseService {

    private static final String STATUS_READY = "READY";
    private static final String USAGE_COURSE_COVER = "COURSE_COVER";
    private static final String SCOPE_TYPE_COURSE = "COURSE";

    private final CourseRepository courseRepository;
    private final CourseTeacherRepository courseTeacherRepository;
    private final ClassSessionRepository classSessionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final StorageInternalClient storageInternalClient;
    private final AuthInternalClient authInternalClient;
    private final CourseEventPublisher courseEventPublisher;

    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "courseDetail", allEntries = true)
    public UUID createCourse(CreateCourseRequest request, UUID teacherId) {
        CourseLevel.fromCode(request.level());
        if (request.assistantIds() != null) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "助教只能通过邀请加入");
        }

        Course course = new Course();
        course.setId(UuidV7Generator.generate());
        course.setTitle(request.title());
        course.setDescription(request.description());
        course.setTeacherId(teacherId);
        course.setLevel(request.level());
        course.setCoverUrl(request.coverUrl());
        course.setSemester(request.semester());
        course.setLocation(request.location());
        course.setCourseType(request.courseType());
        course.setIsPublic(request.isPublic());
        course.setMaxStudents(request.maxStudents());
        course.setTotalClassHours(request.totalClassHours());
        course.setStatus(CourseStatus.DRAFT.getCode());

        courseRepository.save(course);

        // 保存教师关联：主讲教师 + 助教
        courseTeacherRepository.batchSave(course.getId(), List.of(teacherId));

        if (request.coverFileId() != null) {
            validateCourseCoverFile(request.coverFileId(), course.getId());
            course.setCoverFileId(request.coverFileId());
            course.setCoverUrl(null);
            courseRepository.update(course);
        }

        courseEventPublisher.publishCourseCreated(course);
        return course.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "courseDetail", allEntries = true)
    public void updateCourse(UUID courseId, UpdateCourseRequest request, UUID userId, Integer role) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));

        boolean isTeacher = courseTeacherRepository.existsByCourseIdAndTeacherId(courseId, userId);
        if (!isTeacher && !SecurityUtils.isAdmin(role)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }

        if (request.teacherId() != null) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "主讲教师不允许通过课程表单更新");
        }
        if (request.assistantIds() != null) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "助教只能通过邀请加入");
        }

        if (request.title() != null) {
            course.setTitle(request.title());
        }
        if (request.description() != null) {
            course.setDescription(request.description());
        }
        if (request.level() != null) {
            CourseLevel.fromCode(request.level());
            course.setLevel(request.level());
        }
        if (request.coverUrl() != null) {
            course.setCoverUrl(request.coverUrl());
        }
        if (request.coverFileId() != null) {
            validateCourseCoverFile(request.coverFileId(), courseId);
            course.setCoverFileId(request.coverFileId());
            course.setCoverUrl(null);
        }
        if (request.maxStudents() != null) {
            course.setMaxStudents(request.maxStudents());
        }
        if (request.semester() != null) {
            course.setSemester(request.semester());
        }
        if (request.location() != null) {
            course.setLocation(request.location());
        }
        if (request.courseType() != null) {
            course.setCourseType(request.courseType());
        }
        if (request.totalClassHours() != null) {
            course.setTotalClassHours(request.totalClassHours());
        }
        // isPublic 在创建后不可修改，忽略请求中的值
        Integer previousStatus = course.getStatus();
        if (request.status() != null) {
            // 只有主讲教师或管理员才能变更课程状态
            if (!course.getTeacherId().equals(userId) && !SecurityUtils.isAdmin(role)) {
                throw new BusinessException(ErrorCodes.FORBIDDEN, "只有主讲教师才能变更课程状态");
            }
            CourseStatus.fromCode(request.status());
            course.setStatus(request.status());
        }

        courseRepository.update(course);

        // 发布课程状态变更事件
        if (request.status() != null && !Objects.equals(previousStatus, request.status())) {
            String action = CourseStatus.fromCode(request.status()).name();
            courseEventPublisher.publishCourseStatusChanged(
                    course.getId(), course.getTitle(), course.getTeacherId(), action);
        }

    }

    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "courseDetail", allEntries = true)
    public void deleteCourse(UUID courseId, UUID userId, Integer role) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));

        // 只有主讲教师或管理员才能删除课程
        if (!course.getTeacherId().equals(userId) && !SecurityUtils.isAdmin(role)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN, "只有主讲教师才能删除课程");
        }

        courseRepository.deleteById(courseId);
        courseTeacherRepository.deleteByCourseId(courseId);

        courseEventPublisher.publishCourseDeleted(course);
    }

    @Cacheable(value = "courseDetail", key = "#courseId")
    public CourseVO getCourse(UUID courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));

        long currentStudents = enrollmentRepository.countActiveByCourseId(courseId);
        List<UUID> teacherIds = courseTeacherRepository.findTeacherIdsByCourseId(courseId);
        long publishedClassSessionCount = classSessionRepository.countPublishedByCourseIds(List.of(courseId))
                .getOrDefault(courseId, 0L);

        // 获取主讲教师信息
        Map<UUID, UserBasicInfo> teacherInfoMap = loadTeacherInfoMap(List.of(course.getTeacherId()));


        return toCourseVO(course, currentStudents, publishedClassSessionCount,
                teacherIds, loadCoverUrls(List.of(course)), teacherInfoMap);
    }

    public CourseDetailVO getCourseDetail(UUID courseId, UUID userId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));

        long currentStudents = enrollmentRepository.countActiveByCourseId(courseId);
        List<UUID> teacherIds = courseTeacherRepository.findTeacherIdsByCourseId(courseId);
        Map<UUID, UserBasicInfo> teacherInfoMap = loadTeacherInfoMap(List.of(course.getTeacherId()));
        Map<UUID, String> coverUrls = loadCoverUrls(List.of(course));
        Map<UUID, UserBasicInfo> assistantInfoMap = teacherIds.isEmpty() ? Map.of() : loadTeacherInfoMap(teacherIds);

        // 判断当前用户是否已选课
        boolean enrolled = false;
        if (userId != null) {
            enrolled = enrollmentRepository.findByCourseIdAndStudentId(courseId, userId)
                    .map(e -> e.getStatus() == EnrollmentStatus.ACTIVE.getCode()
                            || e.getStatus() == EnrollmentStatus.COMPLETED.getCode())
                    .orElse(false);
        }

        String coverUrl = course.getCoverFileId() != null
                ? coverUrls.getOrDefault(course.getCoverFileId(), course.getCoverUrl())
                : course.getCoverUrl();

        UserBasicInfo teacherInfo = teacherInfoMap.get(course.getTeacherId());
        String teacherName = teacherInfo != null ? teacherInfo.displayName() : null;
        String teacherAvatar = teacherInfo != null ? teacherInfo.avatarUrl() : null;

        return new CourseDetailVO(
                course.getId(),
                course.getTitle(),
                course.getDescription(),
                course.getTeacherId(),
                teacherName,
                teacherAvatar,
                course.getLevel(),
                coverUrl,
                course.getCoverFileId(),
                teacherIds,
                teacherIds.stream().map(id -> assistantInfoMap.getOrDefault(id, new UserBasicInfo(id, null, null, null))).toList(),
                course.getSemester(),
                course.getLocation(),
                course.getCourseType(),
                course.getIsPublic(),
                course.getMaxStudents(),
                course.getTotalClassHours(),
                (int) currentStudents,
                course.getStatus(),
                enrolled,
                course.getCreatedAt(),
                course.getUpdatedAt()
        );
    }

    public PageResponse<@NonNull CourseVO> listCourses(CoursePageRequest request) {
        CoursePageRequest pageRequest = request != null
                ? request
                : new CoursePageRequest(null, null, null, null, null, null, null, null, null, null);
        int page = PageUtils.normalizePage(pageRequest.page());
        int size = PageUtils.normalizeSize(pageRequest.size());

        Page<Course> result = courseRepository.findAll(
                page, size,
                pageRequest.keyword(),
                pageRequest.level(),
                pageRequest.status(),
                pageRequest.isPublic(),
                pageRequest.createdAtStart(),
                pageRequest.createdAtEnd(),
                pageRequest.updatedAtStart(),
                pageRequest.updatedAtEnd()
        );

        List<Course> courses = result.getRecords();
        if (courses.isEmpty()) {
            return PageResponse.empty(page, size);
        }

        return enrichCourses(courses, result.getTotal(), page, size);
    }

    public PageResponse<@NonNull CourseVO> listTeacherCourses(UUID teacherId, String role, int page, int size) {
        int currentPage = PageUtils.normalizePage(page);
        int pageSize = PageUtils.normalizeSize(size);

        Page<Course> result = courseRepository.findTeacherCourses(teacherId, role, currentPage, pageSize);
        List<Course> courses = result.getRecords();
        if (courses.isEmpty()) {
            return PageResponse.empty(currentPage, pageSize);
        }

        return enrichCourses(courses, result.getTotal(), currentPage, pageSize);
    }

    private PageResponse<@NonNull CourseVO> enrichCourses(List<Course> courses, long total, int page, int size) {
        Map<UUID, Long> activeCounts = enrollmentRepository.countActiveByCourseIds(
                courses.stream().map(Course::getId).toList());
        Map<UUID, Long> publishedClassSessionCounts = classSessionRepository.countPublishedByCourseIds(
                courses.stream().map(Course::getId).toList());
        Map<UUID, String> coverUrls = loadCoverUrls(courses);
        Map<UUID, List<UUID>> teacherIdsMap = courseTeacherRepository.findTeacherIdsByCourseIds(
                courses.stream().map(Course::getId).toList());

        List<UUID> teacherIds = courses.stream()
                .map(Course::getTeacherId)
                .distinct()
                .toList();
        Map<UUID, UserBasicInfo> teacherInfoMap = loadTeacherInfoMap(teacherIds);

        List<CourseVO> voList = courses.stream()
                .map(course -> toCourseVO(course,
                        activeCounts.getOrDefault(course.getId(), 0L),
                        publishedClassSessionCounts.getOrDefault(course.getId(), 0L),
                        teacherIdsMap.getOrDefault(course.getId(), List.of()),
                        coverUrls,
                        teacherInfoMap))
                .toList();

        return new PageResponse<>(voList, total, page, size);
    }

    private CourseVO toCourseVO(Course course, long currentStudents, long publishedClassSessionCount, List<UUID> teacherIds,
                                Map<UUID, String> coverUrls, Map<UUID, UserBasicInfo> teacherInfoMap) {
        String coverUrl = course.getCoverFileId() != null
                ? coverUrls.getOrDefault(course.getCoverFileId(), course.getCoverUrl())
                : course.getCoverUrl();

        UserBasicInfo teacherInfo = teacherInfoMap.get(course.getTeacherId());
        String teacherName = teacherInfo != null ? teacherInfo.displayName() : null;
        String teacherAvatar = teacherInfo != null ? teacherInfo.avatarUrl() : null;

        return new CourseVO(
                course.getId(),
                course.getTitle(),
                course.getDescription(),
                course.getTeacherId(),
                teacherName,
                teacherAvatar,
                course.getLevel(),
                coverUrl,
                course.getCoverFileId(),
                teacherIds,
                course.getSemester(),
                course.getLocation(),
                course.getCourseType(),
                course.getIsPublic(),
                course.getMaxStudents(),
                course.getTotalClassHours(),
                (int) currentStudents,
                course.getStatus(),
                course.getCreatedAt(),
                course.getUpdatedAt(),
                publishedClassSessionCount,
                CourseProgressCalculator.calculate(course.getTotalClassHours(), publishedClassSessionCount)
        );
    }

    private void validateCourseCoverFile(UUID fileId, UUID courseId) {
        StorageObjectInfo file = internalFile(fileId);
        boolean isReady = STATUS_READY.equals(file.status());
        boolean isCover = USAGE_COURSE_COVER.equals(file.usage());
        boolean isCourseScope = SCOPE_TYPE_COURSE.equals(file.scopeType());
        boolean scopeMatches = file.scopeId() == null || courseId.equals(file.scopeId());

        if (!isReady || !isCover || !isCourseScope || !scopeMatches) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Invalid course cover file");
        }
    }

    private StorageObjectInfo internalFile(UUID fileId) {
        ApiResponse<@NonNull StorageObjectInfo> response = storageInternalClient.getFile(fileId);
        if (response == null || response.code() != ErrorCodes.SUCCESS.code() || response.data() == null) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Invalid storage file");
        }
        return response.data();
    }

    private Map<UUID, String> loadCoverUrls(List<Course> courses) {
        List<UUID> fileIds = courses.stream()
                .map(Course::getCoverFileId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (fileIds.isEmpty()) {
            return Map.of();
        }
        try {
            ApiResponse<@NonNull Map<@NonNull UUID, @NonNull String>> response = storageInternalClient.getUrls(fileIds);
            if (response != null && response.code() == 0 && response.data() != null) {
                return response.data();
            }
        } catch (Exception ignored) {
            return Map.of();
        }
        return Map.of();
    }

    private Map<UUID, UserBasicInfo> loadTeacherInfoMap(List<UUID> teacherIds) {
        if (teacherIds.isEmpty()) {
            return Map.of();
        }
        try {
            ApiResponse<@NonNull List<@NonNull UserBasicInfo>> response = authInternalClient.getUsersBasicInfo(teacherIds);
            if (response != null && response.code() == 0 && response.data() != null) {
                return response.data().stream()
                        .collect(Collectors.toMap(UserBasicInfo::id, info -> info));
            }
        } catch (Exception ignored) {
            return Map.of();
        }
        return Map.of();
    }
}
