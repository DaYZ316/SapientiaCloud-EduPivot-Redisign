package com.dayz.sc.course.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.feign.dto.AgentSearchItem;
import com.dayz.sc.common.feign.dto.AgentSearchResult;
import com.dayz.sc.common.feign.dto.AiCourseContext;
import com.dayz.sc.common.feign.dto.ClassSessionAiAccess;
import com.dayz.sc.common.security.support.SecurityUtils;
import com.dayz.sc.course.model.entity.*;
import com.dayz.sc.course.model.enums.ChapterStatus;
import com.dayz.sc.course.model.enums.ClassLiveStatus;
import com.dayz.sc.course.model.enums.ClassSessionStatus;
import com.dayz.sc.course.model.enums.EnrollmentStatus;
import com.dayz.sc.course.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * AiCourseContextService.
 *
 * @author DaYZ
 */
@Service
@RequiredArgsConstructor
public class AiCourseContextService {

    private static final int MAX_CONTEXT_COURSES = 20;
    private static final int MAX_CONTEXT_QUESTIONS_PER_COURSE = 20;
    private static final int DEFAULT_SEARCH_LIMIT = 5;
    private static final int MAX_SEARCH_LIMIT = 10;
    private static final int DEFAULT_CHAPTER_LIMIT = 20;
    private static final int MAX_CHAPTER_LIMIT = 50;
    private static final int MAX_SNIPPET_LENGTH = 240;
    private static final int RESOURCE_QUERY_PAGE_SIZE = 100;
    private static final String TYPE_COURSE = "COURSE";
    private static final String TYPE_CHAPTER = "CHAPTER";
    private static final String TYPE_QUESTION_BANK = "QUESTION_BANK";
    private static final String TYPE_QUESTION = "QUESTION";
    private static final String TYPE_COURSE_FILE = "COURSE_FILE";
    private static final String TYPE_LIVE_PRACTICE = "LIVE_PRACTICE";
    private static final String TYPE_PRACTICE_SESSION = "PRACTICE_SESSION";
    private static final Set<String> DEFAULT_RESOURCE_TYPES = Set.of(
            TYPE_COURSE,
            TYPE_CHAPTER,
            TYPE_QUESTION_BANK,
            TYPE_QUESTION,
            TYPE_COURSE_FILE,
            TYPE_LIVE_PRACTICE,
            TYPE_PRACTICE_SESSION);
    private static final Set<String> LEGACY_SEARCH_TYPES = Set.of(
            TYPE_COURSE,
            TYPE_CHAPTER,
            TYPE_QUESTION_BANK,
            TYPE_QUESTION,
            TYPE_LIVE_PRACTICE);
    private static final String SCOPE_PRIMARY_TEACHING = "primaryTeaching";
    private static final String SCOPE_ASSISTING = "assisting";
    private static final String SCOPE_TEACHING = "teaching";
    private static final String SCOPE_LEARNING = "learning";
    private static final String SCOPE_ALL = "all";

    private final CourseRepository courseRepository;
    private final ChapterRepository chapterRepository;
    private final QuestionBankRepository questionBankRepository;
    private final QuestionRepository questionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseTeacherRepository courseTeacherRepository;
    private final ClassSessionRepository classSessionRepository;
    private final CourseFileRepository courseFileRepository;
    private final PracticeSessionRepository practiceSessionRepository;
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

    public List<AgentSearchResult> search(String keyword, UUID courseId, Integer limit, UUID userId, Integer role) {
        return searchResources(keyword, courseId, null, List.copyOf(LEGACY_SEARCH_TYPES), limit, userId, role);
    }

    public List<AgentSearchResult> searchResources(String keyword,
                                                   UUID courseId,
                                                   String courseTitle,
                                                   List<String> types,
                                                   Integer limit,
                                                   UUID userId,
                                                   Integer role) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        int resultLimit = normalizeLimit(limit);
        String normalizedKeyword = keyword.strip().toLowerCase(Locale.ROOT);
        Set<String> resourceTypes = normalizeResourceTypes(types);
        List<Course> courses = resolveSearchCourses(courseId, courseTitle, userId, role);
        List<AgentSearchResult> results = new ArrayList<>();
        for (Course course : courses) {
            String relationLabel = courseRelationLabel(course, "", userId, role);
            addCourseMatch(results, course, normalizedKeyword, relationLabel, resourceTypes);
            addChapterMatches(results, course, normalizedKeyword, resultLimit, relationLabel, resourceTypes);
            addQuestionBankMatches(results, course, normalizedKeyword, resultLimit, relationLabel, resourceTypes);
            addQuestionMatches(results, course, normalizedKeyword, resultLimit, relationLabel, resourceTypes);
            addCourseFileMatches(results, course, normalizedKeyword, resultLimit, relationLabel, resourceTypes);
            addLivePracticeMatches(results, course, normalizedKeyword, resultLimit, relationLabel, resourceTypes);
            addPracticeSessionMatches(results, course, normalizedKeyword, resultLimit, relationLabel, resourceTypes, userId);
            if (results.size() >= resultLimit) {
                break;
            }
        }
        return trim(results, resultLimit);
    }

    public List<AgentSearchItem> listCourses(String scope, Integer limit, UUID userId, Integer role) {
        int resultLimit = normalizeLimit(limit);
        String normalizedScope = normalizeScope(scope, role);
        List<Course> courses = switch (normalizedScope) {
            case SCOPE_PRIMARY_TEACHING -> teacherCourses(userId, "primary", resultLimit);
            case SCOPE_ASSISTING -> teacherCourses(userId, "assistant", resultLimit);
            case SCOPE_TEACHING -> teacherCourses(userId, null, resultLimit);
            case SCOPE_ALL -> SecurityUtils.isAdmin(role)
                    ? courseRepository.findAll(1, resultLimit, null, null, null, null,
                    null, null, null, null).getRecords()
                    : accessibleCourses(userId, role).stream().limit(resultLimit).toList();
            case SCOPE_LEARNING -> learningCourses(userId, resultLimit);
            default -> accessibleCourses(userId, role).stream().limit(resultLimit).toList();
        };
        return courses.stream()
                .limit(resultLimit)
                .map(course -> courseItem(course, normalizedScope, userId, role))
                .toList();
    }

    public List<AgentSearchItem> listChapters(UUID courseId, String courseTitle, Integer limit, UUID userId, Integer role) {
        Course course = resolveAccessibleCourse(courseId, courseTitle, userId, role);
        if (course == null) {
            return List.of();
        }
        int resultLimit = normalizeChapterLimit(limit);
        String relationLabel = courseRelationLabel(course, "", userId, role);
        return chapterRepository.findByCourseId(course.getId()).stream()
                .filter(chapter -> canViewChapter(chapter, userId, role))
                .limit(resultLimit)
                .map(chapter -> chapterItem(chapter, course, relationLabel))
                .toList();
    }

    public ClassSessionAiAccess classSessionAccess(UUID classSessionId, UUID userId, Integer role) {
        ClassSession session = classSessionRepository.findById(classSessionId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        boolean canManage = SecurityUtils.isAdmin(role)
                || courseTeacherRepository.existsByCourseIdAndTeacherId(session.getCourseId(), userId);
        boolean canView = canManage || enrollmentRepository.findByCourseIdAndStudentId(session.getCourseId(), userId)
                .filter(this::activeOrCompleted)
                .isPresent();
        if (!canView) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }
        ClassSessionStatus classStatus = ClassSessionStatus.calculate(
                session.getPublishedAt(),
                session.getScheduledStartAt(),
                session.getScheduledEndAt(),
                java.time.Instant.now());
        ClassLiveStatus liveStatus = ClassLiveStatus.fromCode(session.getLiveStatus());
        return new ClassSessionAiAccess(
                session.getId(),
                session.getCourseId(),
                session.getTeacherId(),
                session.getTitle(),
                classStatus.getCode(),
                classStatus.getDescription(),
                liveStatus.getCode(),
                liveStatus.getDescription(),
                session.getScheduledStartAt(),
                session.getScheduledEndAt(),
                true,
                canManage);
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

    private List<Course> teacherCourses(UUID userId, String role, int limit) {
        return courseRepository.findTeacherCourses(userId, role, 1, limit).getRecords();
    }

    private List<Course> learningCourses(UUID userId, int limit) {
        Page<Enrollment> enrollments = enrollmentRepository.findByStudentId(userId, 1, limit);
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

    private Course resolveAccessibleCourse(UUID courseId, String courseTitle, UUID userId, Integer role) {
        if (courseId != null) {
            return requireAccessibleCourse(courseId, userId, role);
        }
        if (!StringUtils.hasText(courseTitle)) {
            return null;
        }
        String normalizedTitle = normalizeText(courseTitle);
        List<Course> matches = accessibleCourses(userId, role).stream()
                .filter(course -> courseTitleMatches(course, normalizedTitle))
                .toList();
        return matches.stream()
                .filter(course -> normalizeText(course.getTitle()).equals(normalizedTitle))
                .findFirst()
                .orElseGet(() -> matches.stream().findFirst().orElse(null));
    }

    private List<Course> resolveSearchCourses(UUID courseId, String courseTitle, UUID userId, Integer role) {
        if (courseId != null) {
            return List.of(requireAccessibleCourse(courseId, userId, role));
        }
        if (StringUtils.hasText(courseTitle)) {
            Course course = resolveAccessibleCourse(null, courseTitle, userId, role);
            return course == null ? List.of() : List.of(course);
        }
        return accessibleCourses(userId, role);
    }

    private boolean courseTitleMatches(Course course, String normalizedTitle) {
        String title = normalizeText(course.getTitle());
        if (!StringUtils.hasText(title)) {
            return false;
        }
        return title.equals(normalizedTitle)
                || title.contains(normalizedTitle)
                || normalizedTitle.contains(title);
    }

    private boolean canViewChapter(Chapter chapter, UUID userId, Integer role) {
        if (Objects.equals(chapter.getStatus(), ChapterStatus.PUBLISHED.getCode())) {
            return true;
        }
        return SecurityUtils.isAdmin(role) || Objects.equals(chapter.getTeacherId(), userId);
    }

    private void addCourseMatch(List<AgentSearchResult> results,
                                Course course,
                                String keyword,
                                String relationLabel,
                                Set<String> resourceTypes) {
        if (!resourceTypes.contains(TYPE_COURSE)) {
            return;
        }
        if (!matches(keyword, course.getTitle(), course.getDescription(), course.getSemester(), course.getLocation())) {
            return;
        }
        results.add(result(
                TYPE_COURSE,
                course.getId(),
                course.getId(),
                course.getTitle(),
                firstText(course.getDescription(), course.getSemester(), course.getLocation()),
                displayMetadata(TYPE_COURSE, course.getTitle(), relationLabel,
                        Map.of("level", metadataValue(course.getLevel()), "status", metadataValue(course.getStatus())))));
    }

    private void addChapterMatches(List<AgentSearchResult> results,
                                   Course course,
                                   String keyword,
                                   int limit,
                                   String relationLabel,
                                   Set<String> resourceTypes) {
        if (!resourceTypes.contains(TYPE_CHAPTER)) {
            return;
        }
        for (Chapter chapter : chapterRepository.findByCourseId(course.getId())) {
            if (results.size() >= limit) {
                return;
            }
            if (matches(keyword, chapter.getChapterName(), chapter.getDescription(), chapter.getContent())) {
                results.add(result(
                        TYPE_CHAPTER,
                        chapter.getId(),
                        course.getId(),
                        chapter.getChapterName(),
                        firstText(chapter.getDescription(), chapter.getContent()),
                        displayMetadata(TYPE_CHAPTER, course.getTitle(), relationLabel,
                                Map.of("sortOrder", metadataValue(chapter.getSortOrder()),
                                        "status", metadataValue(chapter.getStatus())))));
            }
        }
    }

    private void addQuestionBankMatches(List<AgentSearchResult> results,
                                        Course course,
                                        String keyword,
                                        int limit,
                                        String relationLabel,
                                        Set<String> resourceTypes) {
        if (!resourceTypes.contains(TYPE_QUESTION_BANK)) {
            return;
        }
        for (QuestionBank bank : questionBankRepository.findByCourseId(course.getId())) {
            if (results.size() >= limit) {
                return;
            }
            if (matches(keyword, bank.getBankName(), bank.getDescription(), tagsText(bank.getTags()))) {
                results.add(result(
                        TYPE_QUESTION_BANK,
                        bank.getId(),
                        course.getId(),
                        bank.getBankName(),
                        firstText(bank.getDescription(), tagsText(bank.getTags())),
                        displayMetadata(TYPE_QUESTION_BANK, course.getTitle(), relationLabel,
                                Map.of("bankType", metadataValue(bank.getBankType()),
                                        "difficulty", metadataValue(bank.getDifficulty())))));
            }
        }
    }

    private void addQuestionMatches(List<AgentSearchResult> results,
                                    Course course,
                                    String keyword,
                                    int limit,
                                    String relationLabel,
                                    Set<String> resourceTypes) {
        if (!resourceTypes.contains(TYPE_QUESTION)) {
            return;
        }
        Page<Question> page = questionRepository.findAll(1, RESOURCE_QUERY_PAGE_SIZE, null, course.getId(),
                null, null, null, keyword, null);
        for (Question question : page.getRecords()) {
            if (results.size() >= limit) {
                return;
            }
            if (matches(keyword, question.getQuestionTitle(), question.getQuestionContent(), tagsText(question.getTags()))) {
                results.add(result(
                        TYPE_QUESTION,
                        question.getId(),
                        course.getId(),
                        question.getQuestionTitle(),
                        firstText(question.getQuestionContent(), tagsText(question.getTags())),
                        displayMetadata(TYPE_QUESTION, course.getTitle(), relationLabel,
                                Map.of("questionBankId", metadataValue(question.getQuestionBankId()),
                                        "questionType", metadataValue(question.getQuestionType()),
                                        "difficulty", metadataValue(question.getDifficulty())))));
            }
        }
    }

    private void addCourseFileMatches(List<AgentSearchResult> results,
                                      Course course,
                                      String keyword,
                                      int limit,
                                      String relationLabel,
                                      Set<String> resourceTypes) {
        if (!resourceTypes.contains(TYPE_COURSE_FILE)) {
            return;
        }
        for (CourseFile file : courseFileRepository.findAllByCourseId(course.getId())) {
            if (results.size() >= limit) {
                return;
            }
            if (matches(keyword, file.getDisplayName())) {
                results.add(result(
                        TYPE_COURSE_FILE,
                        file.getId(),
                        course.getId(),
                        file.getDisplayName(),
                        file.getDisplayName(),
                        displayMetadata(TYPE_COURSE_FILE, course.getTitle(), relationLabel,
                                Map.of("storageObjectId", metadataValue(file.getStorageObjectId()),
                                        "visibility", metadataValue(file.getVisibility()),
                                        "sortOrder", metadataValue(file.getSortOrder())))));
            }
        }
    }

    private void addLivePracticeMatches(List<AgentSearchResult> results,
                                        Course course,
                                        String keyword,
                                        int limit,
                                        String relationLabel,
                                        Set<String> resourceTypes) {
        if (!resourceTypes.contains(TYPE_LIVE_PRACTICE)) {
            return;
        }
        for (LivePracticeGroup practice : livePracticeGroupRepository.findByCourseId(course.getId())) {
            if (results.size() >= limit) {
                return;
            }
            if (matches(keyword, practice.getTitle())) {
                results.add(result(
                        TYPE_LIVE_PRACTICE,
                        practice.getId(),
                        course.getId(),
                        practice.getTitle(),
                        "Live practice",
                        displayMetadata(TYPE_LIVE_PRACTICE, course.getTitle(), relationLabel,
                                Map.of("classSessionId", metadataValue(practice.getClassSessionId()),
                                        "publishOrder", metadataValue(practice.getPublishOrder())))));
            }
            addLivePracticeQuestionMatches(results, practice, course, keyword, limit, relationLabel);
        }
    }

    private void addLivePracticeQuestionMatches(List<AgentSearchResult> results,
                                                LivePracticeGroup practice,
                                                Course course,
                                                String keyword,
                                                int limit,
                                                String relationLabel) {
        for (LivePracticeQuestion question : livePracticeQuestionRepository.findByGroupId(practice.getId())) {
            if (results.size() >= limit) {
                return;
            }
            if (matches(keyword, question.getQuestionTitle(), question.getQuestionContent(), tagsText(question.getTags()))) {
                results.add(result(
                        TYPE_LIVE_PRACTICE,
                        question.getId(),
                        course.getId(),
                        firstText(question.getQuestionTitle(), practice.getTitle()),
                        firstText(question.getQuestionContent(), practice.getTitle()),
                        displayMetadata(TYPE_LIVE_PRACTICE, course.getTitle(), relationLabel,
                                Map.of("groupId", metadataValue(practice.getId()),
                                        "classSessionId", metadataValue(practice.getClassSessionId()),
                                        "questionOrder", metadataValue(question.getQuestionOrder())))));
            }
        }
    }

    private void addPracticeSessionMatches(List<AgentSearchResult> results,
                                           Course course,
                                           String keyword,
                                           int limit,
                                           String relationLabel,
                                           Set<String> resourceTypes,
                                           UUID userId) {
        if (!resourceTypes.contains(TYPE_PRACTICE_SESSION) || userId == null) {
            return;
        }
        for (PracticeSession session : practiceSessionRepository.findBySysUserId(userId)) {
            if (results.size() >= limit) {
                return;
            }
            if (!course.getId().equals(session.getCourseId())) {
                continue;
            }
            String title = "练习记录";
            String snippet = "已答 %s/%s，正确 %s，得分 %s/%s".formatted(
                    metadataValue(session.getAnsweredCount()),
                    metadataValue(session.getTotalQuestions()),
                    metadataValue(session.getCorrectCount()),
                    metadataValue(session.getEarnedScore()),
                    metadataValue(session.getTotalScore()));
            if (matches(keyword, title, snippet)) {
                results.add(result(
                        TYPE_PRACTICE_SESSION,
                        session.getId(),
                        course.getId(),
                        title,
                        snippet,
                        displayMetadata(TYPE_PRACTICE_SESSION, course.getTitle(), relationLabel,
                                Map.of("questionBankId", metadataValue(session.getQuestionBankId()),
                                        "status", metadataValue(session.getStatus())))));
            }
        }
    }

    private AgentSearchResult result(String sourceType,
                                     UUID sourceId,
                                     UUID courseId,
                                     String title,
                                     String snippet,
                                     Map<String, Object> metadata) {
        return new AgentSearchResult(sourceType, sourceId, courseId,
                StringUtils.hasText(title) ? title : sourceType,
                snippet(snippet),
                metadata);
    }

    private boolean matches(String keyword, String... values) {
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String tagsText(List<String> tags) {
        return tags == null ? "" : String.join(", ", tags);
    }

    private String snippet(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.strip().replaceAll("\\s+", " ");
        if (normalized.length() <= MAX_SNIPPET_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_SNIPPET_LENGTH);
    }

    private Object metadataValue(Object value) {
        return value == null ? "" : value;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_SEARCH_LIMIT;
        }
        return Math.min(limit, MAX_SEARCH_LIMIT);
    }

    private int normalizeChapterLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_CHAPTER_LIMIT;
        }
        return Math.min(limit, MAX_CHAPTER_LIMIT);
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.strip().toLowerCase(Locale.ROOT) : "";
    }

    private Set<String> normalizeResourceTypes(List<String> types) {
        if (types == null || types.isEmpty()) {
            return DEFAULT_RESOURCE_TYPES;
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String type : types) {
            if (!StringUtils.hasText(type)) {
                continue;
            }
            String value = type.strip().toUpperCase(Locale.ROOT);
            if (DEFAULT_RESOURCE_TYPES.contains(value)) {
                normalized.add(value);
            }
        }
        return normalized.isEmpty() ? DEFAULT_RESOURCE_TYPES : normalized;
    }

    private String normalizeScope(String scope, Integer role) {
        if (StringUtils.hasText(scope)) {
            return scope.strip();
        }
        if (SecurityUtils.isTeacher(role)) {
            return SCOPE_TEACHING;
        }
        if (SecurityUtils.isAdmin(role)) {
            return SCOPE_ALL;
        }
        return SCOPE_LEARNING;
    }

    private AgentSearchItem courseItem(Course course, String scope, UUID userId, Integer role) {
        String relation = courseRelationLabel(course, scope, userId, role);
        String snippet = firstText(course.getDescription(), course.getSemester(), course.getLocation());
        if (!StringUtils.hasText(snippet)) {
            snippet = String.join(" · ", List.of(
                            metadataValue(course.getSemester()).toString(),
                            metadataValue(course.getLocation()).toString()))
                    .replaceAll("^( · )+|( · )+$", "");
        }
        return new AgentSearchItem(
                "COURSE",
                "课程",
                course.getId() == null ? null : course.getId().toString(),
                course.getId() == null ? null : course.getId().toString(),
                StringUtils.hasText(course.getTitle()) ? course.getTitle() : "未命名课程",
                relation,
                snippet(snippet),
                relation,
                Map.of("status", metadataValue(course.getStatus()), "level", metadataValue(course.getLevel())),
                Map.of(
                        "sourceType", "COURSE",
                        "sourceId", metadataValue(course.getId()),
                        "courseId", metadataValue(course.getId()),
                        "status", metadataValue(course.getStatus()),
                        "level", metadataValue(course.getLevel())));
    }

    private AgentSearchItem chapterItem(Chapter chapter, Course course, String relationLabel) {
        return new AgentSearchItem(
                "CHAPTER",
                "章节",
                chapter.getId() == null ? null : chapter.getId().toString(),
                course.getId() == null ? null : course.getId().toString(),
                StringUtils.hasText(chapter.getChapterName()) ? chapter.getChapterName() : "未命名章节",
                StringUtils.hasText(course.getTitle()) ? course.getTitle() : "",
                snippet(firstText(chapter.getDescription(), chapter.getContent())),
                relationLabel,
                Map.of("sortOrder", metadataValue(chapter.getSortOrder()), "status", metadataValue(chapter.getStatus())),
                Map.of(
                        "sourceType", "CHAPTER",
                        "sourceId", metadataValue(chapter.getId()),
                        "courseId", metadataValue(course.getId()),
                        "sortOrder", metadataValue(chapter.getSortOrder()),
                        "status", metadataValue(chapter.getStatus())));
    }

    private String courseRelationLabel(Course course, String scope, UUID userId, Integer role) {
        if (SecurityUtils.isAdmin(role)) {
            return "管理员可见";
        }
        if (Objects.equals(course.getTeacherId(), userId)) {
            return "主讲课程";
        }
        if (SCOPE_ASSISTING.equals(scope) || courseTeacherRepository.existsByCourseIdAndTeacherId(course.getId(), userId)) {
            return "协助课程";
        }
        return "学习课程";
    }

    private String sourceLabel(String sourceType) {
        return switch (sourceType) {
            case "COURSE" -> "课程";
            case "CHAPTER" -> "章节";
            case "QUESTION_BANK" -> "题库";
            case "QUESTION" -> "题目";
            case "COURSE_FILE" -> "课程文件";
            case "LIVE_PRACTICE" -> "课堂练习";
            case "PRACTICE_SESSION" -> "练习记录";
            default -> sourceType;
        };
    }

    private Map<String, Object> displayMetadata(String sourceType,
                                                String courseTitle,
                                                String relationLabel,
                                                Map<String, Object> metadata) {
        Map<String, Object> displayMetadata = new HashMap<>(metadata);
        displayMetadata.put("sourceLabel", sourceLabel(sourceType));
        displayMetadata.put("contextLabel", StringUtils.hasText(courseTitle) ? courseTitle : "");
        displayMetadata.put("relationLabel", StringUtils.hasText(relationLabel) ? relationLabel : "");
        displayMetadata.put("resourceType", sourceType);
        return displayMetadata;
    }

    private List<AgentSearchResult> trim(List<AgentSearchResult> results, int limit) {
        if (results.size() <= limit) {
            return results;
        }
        return results.subList(0, limit);
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
