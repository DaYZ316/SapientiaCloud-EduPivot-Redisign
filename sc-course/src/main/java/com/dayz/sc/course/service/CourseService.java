package com.dayz.sc.course.service;

import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.common.response.PageResponse;
import com.dayz.sc.common.security.support.SecurityUtils;
import com.dayz.sc.common.util.PageUtils;
import com.dayz.sc.common.util.UuidV7Generator;
import com.dayz.sc.common.feign.client.AuthInternalClient;
import com.dayz.sc.common.feign.client.StorageInternalClient;
import com.dayz.sc.common.feign.dto.UserBasicInfo;
import com.dayz.sc.course.model.dto.CreateCourseRequest;
import com.dayz.sc.course.model.dto.CoursePageRequest;
import com.dayz.sc.course.model.dto.UpdateCourseRequest;
import com.dayz.sc.course.model.entity.Course;
import com.dayz.sc.course.model.enums.CourseLevel;
import com.dayz.sc.course.model.enums.CourseStatus;
import com.dayz.sc.course.model.vo.CourseVO;
import com.dayz.sc.common.feign.dto.StorageObjectInfo;
import com.dayz.sc.course.event.CourseEventPublisher;
import com.dayz.sc.course.repository.CourseRepository;
import com.dayz.sc.course.repository.CourseTeacherRepository;
import com.dayz.sc.course.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;
    private final CourseTeacherRepository courseTeacherRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final StorageInternalClient storageInternalClient;
    private final AuthInternalClient authInternalClient;
    private final CourseEventPublisher courseEventPublisher;

    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "courseDetail", allEntries = true)
    public UUID createCourse(CreateCourseRequest request, UUID teacherId) {
        CourseLevel.fromCode(request.level());

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
        course.setStatus(CourseStatus.DRAFT.getCode());

        courseRepository.save(course);

        // 保存教师关联：主讲教师 + 助教
        saveTeacherAssociations(course.getId(), teacherId, request.assistantIds());

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

        UUID previousTeacherId = course.getTeacherId();
        UUID effectiveTeacherId = previousTeacherId;

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
        if (request.teacherId() != null) {
            if (!SecurityUtils.isAdmin(role)) {
                throw new BusinessException(ErrorCodes.FORBIDDEN);
            }
            effectiveTeacherId = request.teacherId();
            course.setTeacherId(effectiveTeacherId);
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
        course.setIsPublic(request.isPublic());
        if (request.status() != null) {
            CourseStatus.fromCode(request.status());
            course.setStatus(request.status());
        }

        courseRepository.update(course);

        // 同步助教关联
        if (request.assistantIds() != null || !Objects.equals(previousTeacherId, effectiveTeacherId)) {
            List<UUID> assistantIds = request.assistantIds();
            if (assistantIds == null) {
                UUID finalEffectiveTeacherId = effectiveTeacherId;
                assistantIds = courseTeacherRepository.findTeacherIdsByCourseId(courseId).stream()
                        .filter(id -> !Objects.equals(id, previousTeacherId))
                        .filter(id -> !Objects.equals(id, finalEffectiveTeacherId))
                        .toList();
            }
            syncTeacherAssociations(courseId, effectiveTeacherId, assistantIds);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "courseDetail", allEntries = true)
    public void deleteCourse(UUID courseId, UUID userId, Integer role) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));

        boolean isTeacher = courseTeacherRepository.existsByCourseIdAndTeacherId(courseId, userId);
        if (!isTeacher && !SecurityUtils.isAdmin(role)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
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

        // 获取主讲教师信息
        Map<UUID, UserBasicInfo> teacherInfoMap = loadTeacherInfoMap(List.of(course.getTeacherId()));

        return toCourseVO(course, currentStudents, teacherIds, loadCoverUrls(List.of(course)), teacherInfoMap);
    }

    public PageResponse<CourseVO> listCourses(CoursePageRequest request) {
        CoursePageRequest pageRequest = request != null
                ? request
                : new CoursePageRequest(null, null, null, null, null, null, null, null, null, null);
        int page = PageUtils.normalizePage(pageRequest.page());
        int size = PageUtils.normalizeSize(pageRequest.size());

        List<Course> courses = courseRepository.findAll(
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

        long total = courseRepository.countAll(
                pageRequest.keyword(),
                pageRequest.level(),
                pageRequest.status(),
                pageRequest.isPublic(),
                pageRequest.createdAtStart(),
                pageRequest.createdAtEnd(),
                pageRequest.updatedAtStart(),
                pageRequest.updatedAtEnd()
        );

        Map<UUID, Long> activeCounts = enrollmentRepository.countActiveByCourseIds(
                courses.stream().map(Course::getId).toList());
        Map<UUID, String> coverUrls = loadCoverUrls(courses);
        Map<UUID, List<UUID>> teacherIdsMap = courseTeacherRepository.findTeacherIdsByCourseIds(
                courses.stream().map(Course::getId).toList());

        // 获取主讲教师信息
        List<UUID> teacherIds = courses.stream()
                .map(Course::getTeacherId)
                .distinct()
                .toList();
        Map<UUID, UserBasicInfo> teacherInfoMap = loadTeacherInfoMap(teacherIds);

        List<CourseVO> voList = courses.stream()
                .map(course -> toCourseVO(course,
                        activeCounts.getOrDefault(course.getId(), 0L),
                        teacherIdsMap.getOrDefault(course.getId(), List.of()),
                        coverUrls,
                        teacherInfoMap))
                .toList();

        return new PageResponse<>(voList, total, page, size);
    }

    public PageResponse<CourseVO> listTeacherCourses(UUID teacherId, String role, int page, int size) {
        int currentPage = PageUtils.normalizePage(page);
        int pageSize = PageUtils.normalizeSize(size);

        // 从关联表查找该教师（主讲或助教）的所有课程ID
        long total = courseRepository.countTeacherCourses(teacherId, role);
        if (total == 0) {
            return new PageResponse<>(List.of(), 0, currentPage, pageSize);
        }

        // SQL 层面排序 + 分页
        List<Course> courses = courseRepository.findTeacherCourses(teacherId, role, currentPage, pageSize);

        Map<UUID, Long> activeCounts = enrollmentRepository.countActiveByCourseIds(
                courses.stream().map(Course::getId).toList());
        Map<UUID, String> coverUrls = loadCoverUrls(courses);
        Map<UUID, List<UUID>> teacherIdsMap = courseTeacherRepository.findTeacherIdsByCourseIds(
                courses.stream().map(Course::getId).toList());

        // 获取主讲教师信息
        List<UUID> teacherIds = courses.stream()
                .map(Course::getTeacherId)
                .distinct()
                .toList();
        Map<UUID, UserBasicInfo> teacherInfoMap = loadTeacherInfoMap(teacherIds);

        List<CourseVO> voList = courses.stream()
                .map(course -> toCourseVO(course,
                        activeCounts.getOrDefault(course.getId(), 0L),
                        teacherIdsMap.getOrDefault(course.getId(), List.of()),
                        coverUrls,
                        teacherInfoMap))
                .toList();

        return new PageResponse<>(voList, total, currentPage, pageSize);
    }

    private void saveTeacherAssociations(UUID courseId, UUID mainTeacherId, List<UUID> assistantIds) {
        Set<UUID> allTeacherIds = new LinkedHashSet<>();
        allTeacherIds.add(mainTeacherId);
        if (assistantIds != null) {
            allTeacherIds.addAll(assistantIds);
        }
        courseTeacherRepository.batchSave(courseId, List.copyOf(allTeacherIds));
    }

    private void syncTeacherAssociations(UUID courseId, UUID mainTeacherId, List<UUID> newAssistantIds) {
        List<UUID> existingTeacherIds = courseTeacherRepository.findTeacherIdsByCourseId(courseId);

        Set<UUID> desiredIds = new LinkedHashSet<>();
        desiredIds.add(mainTeacherId);
        if (newAssistantIds != null) {
            newAssistantIds.stream()
                    .filter(Objects::nonNull)
                    .filter(id -> !Objects.equals(id, mainTeacherId))
                    .forEach(desiredIds::add);
        }

        // 需要删除的（排除主讲教师）
        List<UUID> toRemove = existingTeacherIds.stream()
                .filter(id -> !desiredIds.contains(id))
                .toList();
        if (!toRemove.isEmpty()) {
            courseTeacherRepository.deleteByCourseIdAndTeacherIds(courseId, toRemove);
        }

        // 需要添加的
        List<UUID> toAdd = desiredIds.stream()
                .filter(id -> !existingTeacherIds.contains(id))
                .toList();
        if (!toAdd.isEmpty()) {
            courseTeacherRepository.batchSave(courseId, toAdd);
        }
    }

    private CourseVO toCourseVO(Course course, long currentStudents, List<UUID> teacherIds,
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
                (int) currentStudents,
                course.getStatus(),
                course.getCreatedAt(),
                course.getUpdatedAt()
        );
    }

    private void validateCourseCoverFile(UUID fileId, UUID courseId) {
        StorageObjectInfo file = internalFile(fileId);
        if (!"READY".equals(file.status())
                || !"COURSE_COVER".equals(file.usage())
                || !"COURSE".equals(file.scopeType())
                || !courseId.equals(file.scopeId())) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Invalid course cover file");
        }
    }

    private StorageObjectInfo internalFile(UUID fileId) {
        ApiResponse<StorageObjectInfo> response = storageInternalClient.getFile(fileId);
        if (response == null || response.code() != 0 || response.data() == null) {
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
            ApiResponse<Map<UUID, String>> response = storageInternalClient.getUrls(fileIds);
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
            ApiResponse<List<UserBasicInfo>> response = authInternalClient.getUsersBasicInfo(teacherIds);
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
