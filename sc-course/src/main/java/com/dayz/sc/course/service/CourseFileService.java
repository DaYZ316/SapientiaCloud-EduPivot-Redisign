package com.dayz.sc.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.common.security.support.SecurityUtils;
import com.dayz.sc.common.util.UuidV7Generator;
import com.dayz.sc.common.feign.client.StorageInternalClient;
import com.dayz.sc.course.mapper.CourseFileMapper;
import com.dayz.sc.course.model.dto.BindCourseFileRequest;
import com.dayz.sc.course.model.entity.Course;
import com.dayz.sc.course.model.entity.CourseFile;
import com.dayz.sc.course.model.enums.CourseFileVisibility;
import com.dayz.sc.course.model.enums.EnrollmentStatus;
import com.dayz.sc.course.model.vo.CourseFileVO;
import com.dayz.sc.common.feign.dto.StorageObjectInfo;
import com.dayz.sc.course.repository.CourseRepository;
import com.dayz.sc.course.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CourseFileService {

    private final CourseFileMapper courseFileMapper;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final StorageInternalClient storageInternalClient;

    @Transactional(rollbackFor = Exception.class)
    public CourseFileVO bindFile(UUID courseId, BindCourseFileRequest request, UUID userId, Integer role) {
        Course course = requireCourse(courseId);
        requireManageCourse(course, userId, role);
        StorageObjectInfo file = requireStorageFile(request.fileId());
        validateCourseFile(file, courseId, request.visibility());

        CourseFile courseFile = new CourseFile();
        courseFile.setCourseId(courseId);
        courseFile.setStorageObjectId(request.fileId());
        courseFile.setVisibility(request.visibility().name());
        courseFile.setDisplayName(StringUtils.hasText(request.displayName()) ? request.displayName() : file.fileName());
        courseFile.setCreatedBy(userId);
        courseFile.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        courseFile.setId(UuidV7Generator.generate());
        courseFileMapper.insert(courseFile);
        return toVO(courseFile, Map.of(request.fileId(), internalUrl(request.fileId())));
    }

    public List<CourseFileVO> listFiles(UUID courseId, UUID userId, Integer role) {
        Course course = requireCourse(courseId);
        boolean canReadPrivate = canReadPrivate(course, userId, role);

        LambdaQueryWrapper<CourseFile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CourseFile::getCourseId, courseId);
        if (!canReadPrivate) {
            wrapper.eq(CourseFile::getVisibility, CourseFileVisibility.PUBLIC.name());
        }
        wrapper.orderByAsc(CourseFile::getSortOrder).orderByDesc(CourseFile::getCreatedAt);
        List<CourseFile> files = courseFileMapper.selectList(wrapper);
        Map<UUID, String> urls = internalUrls(files.stream().map(CourseFile::getStorageObjectId).distinct().toList());
        return files.stream().map(file -> toVO(file, urls)).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteCourseFile(UUID courseId, UUID courseFileId, UUID userId, Integer role) {
        Course course = requireCourse(courseId);
        requireManageCourse(course, userId, role);
        CourseFile file = courseFileMapper.selectById(courseFileId);
        if (file == null || !courseId.equals(file.getCourseId())) {
            throw new BusinessException(ErrorCodes.NOT_FOUND);
        }
        courseFileMapper.deleteById(courseFileId);
    }

    private void validateCourseFile(StorageObjectInfo file, UUID courseId, CourseFileVisibility visibility) {
        String expectedUsage = visibility == CourseFileVisibility.PUBLIC
                ? "COURSE_PUBLIC_FILE"
                : "COURSE_PRIVATE_FILE";
        if (!"READY".equals(file.status())
                || !expectedUsage.equals(file.usage())
                || !"COURSE".equals(file.scopeType())
                || !courseId.equals(file.scopeId())) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Invalid course file");
        }
    }

    private Course requireCourse(UUID courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
    }

    private void requireManageCourse(Course course, UUID userId, Integer role) {
        if (!course.getTeacherId().equals(userId) && !SecurityUtils.isAdmin(role)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }
    }

    private boolean canReadPrivate(Course course, UUID userId, Integer role) {
        if (course.getTeacherId().equals(userId) || SecurityUtils.isAdmin(role)) {
            return true;
        }
        return enrollmentRepository.findByCourseIdAndStudentId(course.getId(), userId)
                .map(enrollment -> enrollment.getStatus() == EnrollmentStatus.ACTIVE.getCode()
                        || enrollment.getStatus() == EnrollmentStatus.COMPLETED.getCode())
                .orElse(false);
    }

    private StorageObjectInfo requireStorageFile(UUID fileId) {
        ApiResponse<StorageObjectInfo> response = storageInternalClient.getFile(fileId);
        if (response == null || response.code() != 0 || response.data() == null) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Invalid storage file");
        }
        return response.data();
    }

    private String internalUrl(UUID fileId) {
        Map<UUID, String> urls = internalUrls(List.of(fileId));
        return urls.get(fileId);
    }

    private Map<UUID, String> internalUrls(List<UUID> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return Map.of();
        }
        ApiResponse<Map<UUID, String>> response = storageInternalClient.getUrls(fileIds);
        if (response == null || response.code() != 0 || response.data() == null) {
            return Map.of();
        }
        return response.data();
    }

    private CourseFileVO toVO(CourseFile file, Map<UUID, String> urls) {
        return new CourseFileVO(
                file.getId(),
                file.getCourseId(),
                file.getStorageObjectId(),
                file.getVisibility(),
                file.getDisplayName(),
                urls.get(file.getStorageObjectId()),
                file.getCreatedBy(),
                file.getSortOrder(),
                file.getCreatedAt(),
                file.getUpdatedAt()
        );
    }
}
