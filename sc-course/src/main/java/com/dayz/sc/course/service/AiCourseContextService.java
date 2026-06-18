package com.dayz.sc.course.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.feign.dto.AiCourseContext;
import com.dayz.sc.common.security.support.SecurityUtils;
import com.dayz.sc.course.model.entity.*;
import com.dayz.sc.course.model.enums.EnrollmentStatus;
import com.dayz.sc.course.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class AiCourseContextService {

    private static final int MAX_CONTEXT_COURSES = 20;
    private static final int MAX_CONTEXT_QUESTIONS_PER_COURSE = 20;

    private final CourseRepository courseRepository;
    private final ChapterRepository chapterRepository;
    private final QuestionBankRepository questionBankRepository;
    private final QuestionRepository questionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseTeacherRepository courseTeacherRepository;
    private final LivePracticeGroupRepository livePracticeGroupRepository;
    private final LivePracticeQuestionRepository livePracticeQuestionRepository;
    private final LivePracticeSubmissionRepository livePracticeSubmissionRepository;

    public AiCourseContext buildContext(UUID courseId, UUID userId, Integer role) {
        List<Course> courses = courseId != null
                ? List.of(requireAccessibleCourse(courseId, userId, role))
                : accessibleCourses(userId, role);
        List<UUID> courseIds = courses.stream().map(Course::getId).toList();
        Map<UUID, Long> activeCounts = enrollmentRepository.countActiveByCourseIds(courseIds);

        List<AiCourseContext.CourseSummary> courseSummaries = courses.stream()
                .map(course -> toCourseSummary(course, activeCounts.getOrDefault(course.getId(), 0L), userId, role))
                .toList();
        List<AiCourseContext.ChapterSummary> chapters = new ArrayList<>();
        List<AiCourseContext.QuestionBankSummary> banks = new ArrayList<>();
        List<AiCourseContext.QuestionSummary> questions = new ArrayList<>();
        List<AiCourseContext.LivePracticeSummary> practices = new ArrayList<>();

        for (Course course : courses) {
            UUID id = course.getId();
            chapters.addAll(chapterRepository.findByCourseId(id).stream()
                    .map(this::toChapterSummary)
                    .toList());
            List<QuestionBank> courseBanks = questionBankRepository.findByCourseId(id);
            Map<UUID, Long> countMap = questionRepository.countByQuestionBankIds(courseBanks.stream().map(QuestionBank::getId).toList());
            banks.addAll(courseBanks.stream()
                    .map(bank -> toQuestionBankSummary(bank, countMap.getOrDefault(bank.getId(), 0L)))
                    .toList());
            questions.addAll(questionRepository.findAll(
                            1,
                            MAX_CONTEXT_QUESTIONS_PER_COURSE,
                            null,
                            id,
                            null,
                            null,
                            null,
                            null,
                            null)
                    .getRecords()
                    .stream()
                    .map(this::toQuestionSummary)
                    .toList());
            practices.addAll(livePracticeGroupRepository.findByCourseId(id).stream()
                    .map(this::toLivePracticeSummary)
                    .toList());
        }

        return new AiCourseContext(courseSummaries, chapters, banks, questions, practices);
    }

    private List<Course> accessibleCourses(UUID userId, Integer role) {
        if (SecurityUtils.isAdmin(role)) {
            return courseRepository.findAll(1, MAX_CONTEXT_COURSES, null, null, null, null,
                    null, null, null, null).getRecords();
        }
        if (SecurityUtils.isTeacher(role)) {
            return courseRepository.findTeacherCourses(userId, null, 1, MAX_CONTEXT_COURSES).getRecords();
        }
        Page<Enrollment> enrollments = enrollmentRepository.findByStudentId(userId, 1, MAX_CONTEXT_COURSES);
        List<UUID> courseIds = enrollments.getRecords().stream()
                .filter(this::activeOrCompleted)
                .map(Enrollment::getCourseId)
                .distinct()
                .toList();
        return courseRepository.findByIds(courseIds);
    }

    private Course requireAccessibleCourse(UUID courseId, UUID userId, Integer role) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        if (SecurityUtils.isAdmin(role)
                || courseTeacherRepository.existsByCourseIdAndTeacherId(courseId, userId)
                || enrollmentRepository.findByCourseIdAndStudentId(courseId, userId).filter(this::activeOrCompleted).isPresent()) {
            return course;
        }
        throw new BusinessException(ErrorCodes.FORBIDDEN);
    }

    private boolean activeOrCompleted(Enrollment enrollment) {
        return enrollment != null
                && (Objects.equals(enrollment.getStatus(), EnrollmentStatus.ACTIVE.getCode())
                || Objects.equals(enrollment.getStatus(), EnrollmentStatus.COMPLETED.getCode()));
    }

    private AiCourseContext.CourseSummary toCourseSummary(Course course, long activeStudentCount, UUID userId, Integer role) {
        return new AiCourseContext.CourseSummary(
                course.getId(),
                course.getTitle(),
                course.getDescription(),
                course.getSemester(),
                course.getLocation(),
                course.getLevel(),
                course.getStatus(),
                activeStudentCount,
                accessRole(course.getId(), userId, role)
        );
    }

    private String accessRole(UUID courseId, UUID userId, Integer role) {
        if (SecurityUtils.isAdmin(role)) {
            return "admin";
        }
        if (courseTeacherRepository.existsByCourseIdAndTeacherId(courseId, userId)) {
            return "teacher";
        }
        return "student";
    }

    private AiCourseContext.ChapterSummary toChapterSummary(Chapter chapter) {
        return new AiCourseContext.ChapterSummary(
                chapter.getId(),
                chapter.getCourseId(),
                chapter.getChapterName(),
                chapter.getDescription(),
                chapter.getSortOrder(),
                chapter.getStatus()
        );
    }

    private AiCourseContext.QuestionBankSummary toQuestionBankSummary(QuestionBank bank, long questionCount) {
        return new AiCourseContext.QuestionBankSummary(
                bank.getId(),
                bank.getCourseId(),
                bank.getBankName(),
                bank.getDescription(),
                bank.getBankType(),
                bank.getDifficulty(),
                questionCount
        );
    }

    private AiCourseContext.QuestionSummary toQuestionSummary(Question question) {
        return new AiCourseContext.QuestionSummary(
                question.getId(),
                question.getCourseId(),
                question.getQuestionBankId(),
                question.getQuestionTitle(),
                question.getQuestionContent(),
                question.getQuestionType(),
                question.getDifficulty(),
                question.getStatus()
        );
    }

    private AiCourseContext.LivePracticeSummary toLivePracticeSummary(LivePracticeGroup group) {
        List<LivePracticeQuestion> questions = livePracticeQuestionRepository.findByGroupId(group.getId());
        List<LivePracticeSubmission> submissions = livePracticeSubmissionRepository.findByGroupId(group.getId());
        return new AiCourseContext.LivePracticeSummary(
                group.getId(),
                group.getCourseId(),
                group.getClassSessionId(),
                group.getTitle(),
                group.getPublishOrder(),
                questions.size(),
                submissions.size()
        );
    }
}
