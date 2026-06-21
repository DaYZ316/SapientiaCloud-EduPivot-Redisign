package com.dayz.sc.course.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.course.model.entity.Chapter;
import com.dayz.sc.course.model.entity.Course;
import com.dayz.sc.course.model.entity.CourseFile;
import com.dayz.sc.course.model.entity.Enrollment;
import com.dayz.sc.course.model.entity.LivePracticeGroup;
import com.dayz.sc.course.model.entity.LivePracticeQuestion;
import com.dayz.sc.course.model.entity.PracticeSession;
import com.dayz.sc.course.model.entity.Question;
import com.dayz.sc.course.model.entity.QuestionBank;
import com.dayz.sc.course.model.enums.ChapterStatus;
import com.dayz.sc.course.model.enums.EnrollmentStatus;
import com.dayz.sc.course.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiCourseContextServiceTest {

    private CourseRepository courseRepository;
    private ChapterRepository chapterRepository;
    private QuestionBankRepository questionBankRepository;
    private QuestionRepository questionRepository;
    private EnrollmentRepository enrollmentRepository;
    private CourseTeacherRepository courseTeacherRepository;
    private CourseFileRepository courseFileRepository;
    private PracticeSessionRepository practiceSessionRepository;
    private LivePracticeGroupRepository livePracticeGroupRepository;
    private LivePracticeQuestionRepository livePracticeQuestionRepository;
    private LivePracticeSubmissionRepository livePracticeSubmissionRepository;
    private AiCourseContextService service;

    @BeforeEach
    void setUp() {
        courseRepository = mock(CourseRepository.class);
        chapterRepository = mock(ChapterRepository.class);
        questionBankRepository = mock(QuestionBankRepository.class);
        questionRepository = mock(QuestionRepository.class);
        enrollmentRepository = mock(EnrollmentRepository.class);
        courseTeacherRepository = mock(CourseTeacherRepository.class);
        courseFileRepository = mock(CourseFileRepository.class);
        practiceSessionRepository = mock(PracticeSessionRepository.class);
        livePracticeGroupRepository = mock(LivePracticeGroupRepository.class);
        livePracticeQuestionRepository = mock(LivePracticeQuestionRepository.class);
        livePracticeSubmissionRepository = mock(LivePracticeSubmissionRepository.class);
        service = new AiCourseContextService(
                courseRepository,
                chapterRepository,
                questionBankRepository,
                questionRepository,
                enrollmentRepository,
                courseTeacherRepository,
                courseFileRepository,
                practiceSessionRepository,
                livePracticeGroupRepository,
                livePracticeQuestionRepository,
                livePracticeSubmissionRepository);
    }

    @Test
    void searchShouldOnlyUseActiveStudentCourses() {
        UUID studentId = UUID.randomUUID();
        UUID activeCourseId = UUID.randomUUID();
        UUID pendingCourseId = UUID.randomUUID();
        Enrollment active = enrollment(activeCourseId, EnrollmentStatus.ACTIVE.getCode());
        Enrollment pending = enrollment(pendingCourseId, 0);
        Page<Enrollment> enrollments = new Page<>(1, 20);
        enrollments.setRecords(List.of(active, pending));
        Course course = course(activeCourseId, "Machine Learning");
        when(enrollmentRepository.findByStudentId(studentId, 1, 20)).thenReturn(enrollments);
        when(courseRepository.findByIds(List.of(activeCourseId))).thenReturn(List.of(course));
        emptyCourseChildren(activeCourseId);

        var results = service.search("machine", null, 5, studentId, 1);

        assertThat(results).extracting("sourceId").containsExactly(activeCourseId);
        verify(courseRepository).findByIds(List.of(activeCourseId));
    }

    @Test
    void searchShouldUseTeacherCourses() {
        UUID teacherId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Course course = course(courseId, "Teaching AI");
        Page<Course> courses = new Page<>(1, 20);
        courses.setRecords(List.of(course));
        when(courseRepository.findTeacherCourses(teacherId, null, 1, 20)).thenReturn(courses);
        emptyCourseChildren(courseId);

        var results = service.search("teaching", null, 5, teacherId, 2);

        assertThat(results).extracting("sourceId").containsExactly(courseId);
        verify(courseRepository).findTeacherCourses(teacherId, null, 1, 20);
    }

    @Test
    void searchShouldUseAdminCourses() {
        UUID adminId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Course course = course(courseId, "Admin Course");
        Page<Course> courses = new Page<>(1, 20);
        courses.setRecords(List.of(course));
        when(courseRepository.findAll(1, 20, null, null, null, null,
                null, null, null, null)).thenReturn(courses);
        emptyCourseChildren(courseId);

        var results = service.search("admin", null, 5, adminId, 0);

        assertThat(results).extracting("sourceId").containsExactly(courseId);
        verify(courseRepository).findAll(1, 20, null, null, null, null,
                null, null, null, null);
    }

    @Test
    void listCoursesShouldIncludeNavigationIdentifiersAndIndexInfo() {
        UUID teacherId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Course course = course(courseId, "Teaching AI");
        Page<Course> courses = new Page<>(1, 5);
        courses.setRecords(List.of(course));
        when(courseRepository.findTeacherCourses(teacherId, "primary", 1, 5)).thenReturn(courses);

        var results = service.listCourses("primaryTeaching", 5, teacherId, 2);

        assertThat(results).singleElement().satisfies(item -> {
            assertThat(item.sourceType()).isEqualTo("COURSE");
            assertThat(item.sourceId()).isEqualTo(courseId.toString());
            assertThat(item.courseId()).isEqualTo(courseId.toString());
            assertThat(item.indexInfo())
                    .containsEntry("sourceType", "COURSE")
                    .containsEntry("sourceId", courseId)
                    .containsEntry("courseId", courseId);
        });
    }

    @Test
    void searchShouldRejectInaccessibleCourseId() {
        UUID courseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course(courseId, "Private Course")));
        when(courseTeacherRepository.existsByCourseIdAndTeacherId(courseId, userId)).thenReturn(false);
        when(enrollmentRepository.findByCourseIdAndStudentId(courseId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.search("private", courseId, 5, userId, 1))
                .isInstanceOf(BusinessException.class);
        verify(questionRepository, never()).findAll(1, 100, null, courseId, null, null, null, "private", null);
    }

    @Test
    void searchShouldFindQuestionContentWithinAuthorizedCourse() {
        UUID courseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course(courseId, "Algorithms")));
        when(enrollmentRepository.findByCourseIdAndStudentId(courseId, userId))
                .thenReturn(Optional.of(enrollment(courseId, EnrollmentStatus.ACTIVE.getCode())));
        when(chapterRepository.findByCourseId(courseId)).thenReturn(List.of());
        when(questionBankRepository.findByCourseId(courseId)).thenReturn(List.of());
        Question question = new Question();
        question.setId(UUID.randomUUID());
        question.setCourseId(courseId);
        question.setQuestionTitle("Shortest path");
        question.setQuestionContent("Explain Dijkstra algorithm");
        Page<Question> questions = new Page<>(1, 100);
        questions.setRecords(List.of(question));
        when(questionRepository.findAll(1, 100, null, courseId, null, null, null, "dijkstra", null)).thenReturn(questions);
        when(livePracticeGroupRepository.findByCourseId(courseId)).thenReturn(List.of());

        var results = service.search("dijkstra", courseId, 5, userId, 1);

        assertThat(results).extracting("sourceType").containsExactly("QUESTION");
        assertThat(results.get(0).snippet()).contains("Dijkstra");
    }

    @Test
    void searchResourcesShouldFindCourseFilesLivePracticeQuestionsAndPracticeSessions() {
        UUID courseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID bankId = UUID.randomUUID();
        Course course = course(courseId, "Game Audio");
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(enrollmentRepository.findByCourseIdAndStudentId(courseId, userId))
                .thenReturn(Optional.of(enrollment(courseId, EnrollmentStatus.ACTIVE.getCode())));
        when(chapterRepository.findByCourseId(courseId)).thenReturn(List.of());
        when(questionBankRepository.findByCourseId(courseId)).thenReturn(List.of(questionBank(courseId, bankId, "Mix Bank")));
        when(questionRepository.findAll(1, 100, null, courseId, null, null, null, "mix", null))
                .thenReturn(new Page<>(1, 100));
        when(courseFileRepository.findAllByCourseId(courseId))
                .thenReturn(List.of(courseFile(courseId, "mix reference.pdf")));
        LivePracticeGroup group = livePracticeGroup(courseId, "Mix critique");
        when(livePracticeGroupRepository.findByCourseId(courseId)).thenReturn(List.of(group));
        when(livePracticeQuestionRepository.findByGroupId(group.getId()))
                .thenReturn(List.of(livePracticeQuestion(group, "How should the mix be balanced?")));
        when(practiceSessionRepository.findBySysUserId(userId))
                .thenReturn(List.of(practiceSession(courseId, bankId)));

        var results = service.searchResources("mix", courseId, null, null, 10, userId, 1);

        assertThat(results).extracting("sourceType")
                .contains("QUESTION_BANK", "COURSE_FILE", "LIVE_PRACTICE");

        var practiceResults = service.searchResources(
                "练习记录", courseId, null, List.of("PRACTICE_SESSION"), 10, userId, 1);

        assertThat(practiceResults).extracting("sourceType").containsExactly("PRACTICE_SESSION");
    }

    @Test
    void listChaptersShouldResolveCourseTitleWithinAuthorizedCourses() {
        UUID teacherId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Course course = course(courseId, "中文游戏原型与关卡叙事实训");
        Page<Course> courses = new Page<>(1, 20);
        courses.setRecords(List.of(course));
        when(courseRepository.findTeacherCourses(teacherId, null, 1, 20)).thenReturn(courses);
        when(chapterRepository.findByCourseId(courseId)).thenReturn(List.of(
                chapter(courseId, "第一章 原型设计"),
                chapter(courseId, "第二章 关卡叙事")));

        var results = service.listChapters(null, "中文游戏原型与关卡叙事实训", null, teacherId, 2);

        assertThat(results).extracting("title")
                .containsExactly("第一章 原型设计", "第二章 关卡叙事");
        assertThat(results).allSatisfy(item -> {
            assertThat(item.sourceType()).isEqualTo("CHAPTER");
            assertThat(item.sourceId()).isNotBlank();
            assertThat(item.courseId()).isEqualTo(courseId.toString());
            assertThat(item.contextLabel()).isEqualTo("中文游戏原型与关卡叙事实训");
            assertThat(item.indexInfo())
                    .containsEntry("sourceType", "CHAPTER")
                    .containsEntry("courseId", courseId);
        });
    }

    @Test
    void listChaptersShouldReturnEmptyWhenCourseTitleIsNotAccessible() {
        UUID studentId = UUID.randomUUID();
        when(enrollmentRepository.findByStudentId(studentId, 1, 20)).thenReturn(new Page<>(1, 20));
        when(courseRepository.findByIds(List.of())).thenReturn(List.of());

        var results = service.listChapters(null, "中文游戏原型与关卡叙事实训", null, studentId, 1);

        assertThat(results).isEmpty();
        verify(chapterRepository, never()).findByCourseId(org.mockito.ArgumentMatchers.any());
    }

    private void emptyCourseChildren(UUID courseId) {
        when(chapterRepository.findByCourseId(courseId)).thenReturn(List.of());
        when(questionBankRepository.findByCourseId(courseId)).thenReturn(List.of());
        when(questionRepository.findAll(1, 100, null, courseId, null, null, null, "machine", null))
                .thenReturn(new Page<>(1, 100));
        when(questionRepository.findAll(1, 100, null, courseId, null, null, null, "teaching", null))
                .thenReturn(new Page<>(1, 100));
        when(questionRepository.findAll(1, 100, null, courseId, null, null, null, "admin", null))
                .thenReturn(new Page<>(1, 100));
        when(courseFileRepository.findAllByCourseId(courseId)).thenReturn(List.of());
        when(livePracticeGroupRepository.findByCourseId(courseId)).thenReturn(List.of());
    }

    private Course course(UUID courseId, String title) {
        Course course = new Course();
        course.setId(courseId);
        course.setTitle(title);
        course.setDescription(title + " description");
        return course;
    }

    private Enrollment enrollment(UUID courseId, int status) {
        Enrollment enrollment = new Enrollment();
        enrollment.setCourseId(courseId);
        enrollment.setStatus(status);
        return enrollment;
    }

    private Chapter chapter(UUID courseId, String name) {
        Chapter chapter = new Chapter();
        chapter.setId(UUID.randomUUID());
        chapter.setCourseId(courseId);
        chapter.setChapterName(name);
        chapter.setDescription(name + " description");
        chapter.setStatus(ChapterStatus.PUBLISHED.getCode());
        return chapter;
    }

    private QuestionBank questionBank(UUID courseId, UUID bankId, String name) {
        QuestionBank bank = new QuestionBank();
        bank.setId(bankId);
        bank.setCourseId(courseId);
        bank.setBankName(name);
        bank.setDescription(name + " description");
        return bank;
    }

    private CourseFile courseFile(UUID courseId, String displayName) {
        CourseFile file = new CourseFile();
        file.setId(UUID.randomUUID());
        file.setCourseId(courseId);
        file.setStorageObjectId(UUID.randomUUID());
        file.setDisplayName(displayName);
        file.setVisibility("PUBLIC");
        return file;
    }

    private LivePracticeGroup livePracticeGroup(UUID courseId, String title) {
        LivePracticeGroup group = new LivePracticeGroup();
        group.setId(UUID.randomUUID());
        group.setCourseId(courseId);
        group.setClassSessionId(UUID.randomUUID());
        group.setTitle(title);
        return group;
    }

    private LivePracticeQuestion livePracticeQuestion(LivePracticeGroup group, String content) {
        LivePracticeQuestion question = new LivePracticeQuestion();
        question.setId(UUID.randomUUID());
        question.setGroupId(group.getId());
        question.setCourseId(group.getCourseId());
        question.setQuestionTitle("Mix balance");
        question.setQuestionContent(content);
        return question;
    }

    private PracticeSession practiceSession(UUID courseId, UUID bankId) {
        PracticeSession session = new PracticeSession();
        session.setId(UUID.randomUUID());
        session.setCourseId(courseId);
        session.setQuestionBankId(bankId);
        session.setAnsweredCount(3);
        session.setTotalQuestions(5);
        session.setCorrectCount(2);
        session.setStatus(1);
        return session;
    }
}
