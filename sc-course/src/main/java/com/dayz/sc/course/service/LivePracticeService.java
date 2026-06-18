package com.dayz.sc.course.service;

import com.dayz.sc.common.events.ai.LivePracticeAiGradingRequestedEvent;
import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.feign.client.AuthInternalClient;
import com.dayz.sc.common.feign.dto.UserBasicInfo;
import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.common.security.support.SecurityUtils;
import com.dayz.sc.common.util.UuidV7Generator;
import com.dayz.sc.course.model.dto.CreateLivePracticeQuestionRequest;
import com.dayz.sc.course.model.dto.CreateLivePracticeRequest;
import com.dayz.sc.course.model.dto.QuestionAnswerRequest;
import com.dayz.sc.course.model.dto.QuestionOptionRequest;
import com.dayz.sc.course.model.dto.SubmitLivePracticeAnswerRequest;
import com.dayz.sc.course.event.LivePracticeAiGradingEventPublisher;
import com.dayz.sc.course.model.entity.*;
import com.dayz.sc.course.model.enums.ClassParticipantRole;
import com.dayz.sc.course.model.enums.EnrollmentStatus;
import com.dayz.sc.course.model.enums.LivePracticeAiGradingStatus;
import com.dayz.sc.course.model.enums.LivePracticeSubmitStatus;
import com.dayz.sc.course.model.enums.QuestionDifficulty;
import com.dayz.sc.course.model.enums.QuestionStatus;
import com.dayz.sc.course.model.enums.QuestionType;
import com.dayz.sc.course.model.value.LivePracticeAnswerSnapshot;
import com.dayz.sc.course.model.value.LivePracticeOptionSnapshot;
import com.dayz.sc.course.model.vo.*;
import com.dayz.sc.course.repository.*;
import com.dayz.sc.course.sse.LivePracticeSseEmitter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 随堂练习业务服务
 *
 * @author DaYZ
 * @since 2026-06-18
 */
@Service
@RequiredArgsConstructor
public class LivePracticeService {

    private static final String LIVE_PRACTICE_BANK_NAME = "随堂练习";
    private static final int MAX_GROUPS_PER_CLASS_SESSION = 5;
    private static final int FLAG_ON = 1;
    private static final int FLAG_OFF = 0;
    private static final int QUESTION_TYPE_SINGLE_CHOICE = 0;
    private static final int QUESTION_TYPE_MULTI_CHOICE = 1;
    private static final int QUESTION_TYPE_TRUE_FALSE = 2;
    private static final int PARTIAL_CREDIT_SCALE = 2;

    private final LivePracticeGroupRepository livePracticeGroupRepository;
    private final LivePracticeQuestionRepository livePracticeQuestionRepository;
    private final LivePracticeSubmissionRepository livePracticeSubmissionRepository;
    private final ClassSessionRepository classSessionRepository;
    private final ClassParticipantRepository classParticipantRepository;
    private final CourseRepository courseRepository;
    private final CourseTeacherRepository courseTeacherRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final QuestionBankRepository questionBankRepository;
    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final QuestionAnswerRepository questionAnswerRepository;
    private final AuthInternalClient authInternalClient;
    private final LivePracticeAiGradingEventPublisher livePracticeAiGradingEventPublisher;
    private final LivePracticeSseEmitter livePracticeSseEmitter;

    @Transactional(rollbackFor = Exception.class)
    public UUID createLivePractice(UUID classSessionId, CreateLivePracticeRequest request, UUID userId, Integer role) {
        ClassSession session = classSessionRepository.findById(classSessionId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        requirePublished(session);
        requireCourseTeacher(session.getCourseId(), userId, role);
        validatePracticeWindow(request.availableStartAt(), request.availableEndAt());

        long groupCount = livePracticeGroupRepository.countByClassSessionId(classSessionId);
        if (groupCount >= MAX_GROUPS_PER_CLASS_SESSION) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "A class session can publish at most five live practices");
        }

        List<Question> selectedQuestions = loadSelectedQuestions(request.selectedQuestionIds(), session.getCourseId());
        Map<UUID, Integer> aiGradingFlags = new HashMap<>();
        selectedQuestions.forEach(question -> aiGradingFlags.put(question.getId(), FLAG_OFF));
        List<Question> createdQuestions = createAdHocQuestions(
                request.createdQuestions(), session.getCourseId(), userId, aiGradingFlags);
        List<Question> allQuestions = new ArrayList<>();
        allQuestions.addAll(selectedQuestions);
        allQuestions.addAll(createdQuestions);
        if (allQuestions.isEmpty()) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Live practice needs at least one question");
        }

        LivePracticeGroup group = new LivePracticeGroup();
        group.setId(UuidV7Generator.generate());
        group.setCourseId(session.getCourseId());
        group.setClassSessionId(classSessionId);
        group.setTeacherId(userId);
        group.setTitle(request.title().trim());
        group.setAvailableStartAt(request.availableStartAt());
        group.setAvailableEndAt(request.availableEndAt());
        group.setAllowLateSubmission(flag(request.allowLateSubmission()));
        group.setPublishOrder((int) groupCount + 1);
        group.setPublishedAt(Instant.now());
        livePracticeGroupRepository.save(group);

        List<LivePracticeQuestion> snapshots = new ArrayList<>();
        for (int index = 0; index < allQuestions.size(); index += 1) {
            Question question = allQuestions.get(index);
            snapshots.add(snapshotQuestion(
                    group, session, question, index + 1, aiGradingFlags.getOrDefault(question.getId(), FLAG_OFF)));
        }
        livePracticeQuestionRepository.saveBatch(snapshots);

        List<UUID> seatedStudentIds = classParticipantRepository.findBySessionId(classSessionId).stream()
                .filter(participant -> Objects.equals(participant.getRole(), ClassParticipantRole.STUDENT.getCode()))
                .map(ClassParticipant::getUserId)
                .distinct()
                .toList();
        livePracticeSseEmitter.sendToUsers(seatedStudentIds, toEventVO(group, snapshots.size()));

        return group.getId();
    }

    public List<LivePracticeGroupVO> listByClassSession(UUID classSessionId, UUID userId, Integer role) {
        ClassSession session = classSessionRepository.findById(classSessionId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        requireSessionAccess(session, userId, role);
        boolean teacherView = isCourseTeacher(session.getCourseId(), userId, role);
        return livePracticeGroupRepository.findByClassSessionId(classSessionId).stream()
                .map(group -> toGroupVO(group, teacherView, userId))
                .toList();
    }

    public LivePracticeGroupVO getLivePractice(UUID groupId, UUID userId, Integer role) {
        LivePracticeGroup group = livePracticeGroupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        requireCourseAccess(group.getCourseId(), userId, role);
        return toGroupVO(group, isCourseTeacher(group.getCourseId(), userId, role), userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public LivePracticeSubmissionVO submitAnswer(UUID groupId, UUID questionSnapshotId,
                                                 SubmitLivePracticeAnswerRequest request,
                                                 UUID userId, Integer role) {
        if (!SecurityUtils.isStudent(role)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }
        LivePracticeGroup group = livePracticeGroupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        requireActiveStudent(group.getCourseId(), userId);
        LivePracticeQuestion question = livePracticeQuestionRepository.findById(questionSnapshotId)
                .filter(q -> q.getGroupId().equals(groupId))
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        livePracticeSubmissionRepository.findByGroupIdAndQuestionSnapshotIdAndStudentId(groupId, questionSnapshotId, userId)
                .ifPresent(existing -> {
                    throw new BusinessException(ErrorCodes.BAD_REQUEST, "This question has already been submitted");
                });

        Instant now = Instant.now();
        int submitStatus = resolveSubmitStatus(group, now);
        GradeResult grade = grade(question, request);
        boolean aiGradingRequired = aiGradingRequired(question);
        if (aiGradingRequired) {
            grade = new GradeResult(null, BigDecimal.ZERO);
        }

        LivePracticeSubmission submission = new LivePracticeSubmission();
        submission.setId(UuidV7Generator.generate());
        submission.setGroupId(groupId);
        submission.setQuestionSnapshotId(questionSnapshotId);
        submission.setCourseId(group.getCourseId());
        submission.setClassSessionId(group.getClassSessionId());
        submission.setStudentId(userId);
        submission.setSelectedOptionIds(request.selectedOptionIds());
        submission.setTextAnswer(request.textAnswer());
        submission.setSubmitStatus(submitStatus);
        submission.setIsCorrect(grade.isCorrect());
        submission.setEarnedScore(grade.earnedScore());
        submission.setAiGradingStatus(aiGradingRequired
                ? LivePracticeAiGradingStatus.PENDING.name()
                : LivePracticeAiGradingStatus.NOT_REQUIRED.name());
        submission.setSubmittedAt(now);
        try {
            livePracticeSubmissionRepository.save(submission);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "This question has already been submitted");
        }
        if (aiGradingRequired) {
            publishAiGradingRequest(submission, question);
        }

        return toSubmissionVO(submission, Map.of(userId, new UserBasicInfo(userId, null, null, role)));
    }

    public List<LivePracticeWorkbookItemVO> getStudentWorkbook(UUID courseId, UUID userId, Integer role) {
        if (!SecurityUtils.isStudent(role)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }
        requireActiveStudent(courseId, userId);
        List<LivePracticeGroup> groups = livePracticeGroupRepository.findByCourseId(courseId);
        Map<UUID, LivePracticeGroup> groupMap = groups.stream()
                .collect(Collectors.toMap(LivePracticeGroup::getId, Function.identity()));
        List<LivePracticeQuestion> questions = livePracticeQuestionRepository.findByCourseId(courseId);
        Map<UUID, LivePracticeSubmission> submissions = livePracticeSubmissionRepository.findByCourseIdAndStudentId(courseId, userId)
                .stream()
                .collect(Collectors.toMap(LivePracticeSubmission::getQuestionSnapshotId, Function.identity(), (a, b) -> a));

        return questions.stream()
                .map(question -> toWorkbookItem(question, groupMap.get(question.getGroupId()), submissions.get(question.getId())))
                .filter(Objects::nonNull)
                .toList();
    }

    public List<LivePracticeGroupVO> getTeacherPractices(UUID courseId, UUID userId, Integer role) {
        requireCourseTeacher(courseId, userId, role);
        return livePracticeGroupRepository.findByCourseId(courseId).stream()
                .map(group -> toGroupVO(group, true, userId))
                .toList();
    }

    public SseEmitter subscribe(UUID userId) {
        return livePracticeSseEmitter.createEmitter(userId);
    }

    private List<Question> loadSelectedQuestions(List<UUID> questionIds, UUID courseId) {
        if (questionIds == null || questionIds.isEmpty()) {
            return List.of();
        }
        List<Question> questions = new ArrayList<>();
        for (UUID questionId : questionIds.stream().distinct().toList()) {
            Question question = questionRepository.findById(questionId)
                    .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
            if (!question.getCourseId().equals(courseId)) {
                throw new BusinessException(ErrorCodes.BAD_REQUEST, "Question does not belong to this course");
            }
            questions.add(question);
        }
        return questions;
    }

    private List<Question> createAdHocQuestions(List<CreateLivePracticeQuestionRequest> requests,
                                                UUID courseId,
                                                UUID userId,
                                                Map<UUID, Integer> aiGradingFlags) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        QuestionBank bank = ensureLivePracticeBank(courseId, userId);
        List<Question> questions = new ArrayList<>();
        for (CreateLivePracticeQuestionRequest request : requests) {
            QuestionType.fromCode(request.questionType());
            QuestionDifficulty.fromCode(request.difficulty());
            Question question = new Question();
            question.setId(UuidV7Generator.generate());
            question.setQuestionBankId(bank.getId());
            question.setCourseId(courseId);
            question.setSysUserId(userId);
            question.setQuestionTitle(request.questionTitle());
            question.setQuestionContent(request.questionContent());
            question.setQuestionType(request.questionType());
            question.setDifficulty(request.difficulty());
            question.setScore(valueOrZero(request.score()));
            question.setEstimatedTime(request.estimatedTime());
            question.setTags(request.tags());
            question.setImageUrls(request.imageUrls());
            question.setAllowPartialCredit(request.allowPartialCredit());
            question.setViewCount(0L);
            question.setStatus(QuestionStatus.PUBLISHED.getCode());
            questionRepository.save(question);
            saveQuestionChildren(question, request.options(), request.answers());
            aiGradingFlags.put(question.getId(), aiGradingFlag(request));
            questions.add(question);
        }
        return questions;
    }

    private QuestionBank ensureLivePracticeBank(UUID courseId, UUID userId) {
        return questionBankRepository.findByCourseId(courseId).stream()
                .filter(bank -> LIVE_PRACTICE_BANK_NAME.equals(bank.getBankName()))
                .findFirst()
                .orElseGet(() -> {
                    QuestionBank bank = new QuestionBank();
                    bank.setId(UuidV7Generator.generate());
                    bank.setCourseId(courseId);
                    bank.setSysUserId(userId);
                    bank.setBankName(LIVE_PRACTICE_BANK_NAME);
                    bank.setDescription("课堂快速出题自动归档题库");
                    bank.setBankType(0);
                    bank.setTags(List.of("随堂练习"));
                    bank.setDifficulty(2);
                    questionBankRepository.save(bank);
                    return bank;
                });
    }

    private void saveQuestionChildren(Question question, List<QuestionOptionRequest> options, List<QuestionAnswerRequest> answers) {
        if (options != null && !options.isEmpty()) {
            questionOptionRepository.saveBatch(options.stream()
                    .map(optionRequest -> {
                        QuestionOption option = new QuestionOption();
                        option.setId(UuidV7Generator.generate());
                        option.setQuestionId(question.getId());
                        option.setCourseId(question.getCourseId());
                        option.setOptionContent(optionRequest.optionContent());
                        option.setOptionLabel(optionRequest.optionLabel());
                        option.setIsCorrect(optionRequest.isCorrect());
                        option.setScore(optionRequest.score());
                        option.setImageUrls(optionRequest.imageUrls());
                        option.setExplanation(optionRequest.explanation());
                        return option;
                    })
                    .toList());
        }
        if (answers != null && !answers.isEmpty()) {
            questionAnswerRepository.saveBatch(answers.stream()
                    .map(answerRequest -> {
                        QuestionAnswer answer = new QuestionAnswer();
                        answer.setId(UuidV7Generator.generate());
                        answer.setQuestionId(question.getId());
                        answer.setCourseId(question.getCourseId());
                        answer.setAnswerContent(answerRequest.answerContent());
                        answer.setExplanation(answerRequest.explanation());
                        answer.setScore(answerRequest.score());
                        answer.setSortOrder(answerRequest.sortOrder());
                        return answer;
                    })
                    .toList());
        }
    }

    private LivePracticeQuestion snapshotQuestion(LivePracticeGroup group,
                                                  ClassSession session,
                                                  Question question,
                                                  int order,
                                                  int aiGradingEnabled) {
        LivePracticeQuestion snapshot = new LivePracticeQuestion();
        snapshot.setId(UuidV7Generator.generate());
        snapshot.setGroupId(group.getId());
        snapshot.setCourseId(group.getCourseId());
        snapshot.setClassSessionId(session.getId());
        snapshot.setSourceQuestionId(question.getId());
        snapshot.setQuestionOrder(order);
        snapshot.setQuestionTitle(question.getQuestionTitle());
        snapshot.setQuestionContent(question.getQuestionContent());
        snapshot.setQuestionType(question.getQuestionType());
        snapshot.setDifficulty(question.getDifficulty());
        snapshot.setScore(valueOrZero(question.getScore()));
        snapshot.setEstimatedTime(question.getEstimatedTime());
        snapshot.setTags(question.getTags());
        snapshot.setImageUrls(question.getImageUrls());
        snapshot.setAllowPartialCredit(question.getAllowPartialCredit());
        snapshot.setOptionsSnapshot(questionOptionRepository.findByQuestionId(question.getId()).stream()
                .map(option -> new LivePracticeOptionSnapshot(
                        option.getId(),
                        option.getOptionContent(),
                        option.getOptionLabel(),
                        option.getIsCorrect(),
                        option.getScore(),
                        option.getImageUrls(),
                        option.getExplanation()
                ))
                .toList());
        snapshot.setAnswersSnapshot(questionAnswerRepository.findByQuestionId(question.getId()).stream()
                .map(answer -> new LivePracticeAnswerSnapshot(
                        answer.getId(),
                        answer.getAnswerContent(),
                        answer.getExplanation(),
                        answer.getScore(),
                        answer.getSortOrder()
                ))
                .toList());
        snapshot.setAiGradingEnabled(aiGradingEnabled);
        return snapshot;
    }

    private LivePracticeGroupVO toGroupVO(LivePracticeGroup group, boolean teacherView, UUID userId) {
        List<LivePracticeQuestion> questions = livePracticeQuestionRepository.findByGroupId(group.getId());
        List<LivePracticeSubmission> submissions = livePracticeSubmissionRepository.findByGroupId(group.getId());
        Map<UUID, List<LivePracticeSubmission>> submissionsByQuestion = submissions.stream()
                .collect(Collectors.groupingBy(LivePracticeSubmission::getQuestionSnapshotId));
        List<Enrollment> students = activeStudents(group.getCourseId());
        Map<UUID, UserBasicInfo> userInfoMap = loadUserInfoMap(studentIds(students));

        List<LivePracticeQuestionVO> questionVos = questions.stream()
                .map(question -> {
                    LivePracticeSubmission mySubmission = submissionsByQuestion.getOrDefault(question.getId(), List.of()).stream()
                            .filter(submission -> submission.getStudentId().equals(userId))
                            .findFirst()
                            .orElse(null);
                    LivePracticeAnalysisVO analysis = teacherView
                            ? buildAnalysis(question, submissionsByQuestion.getOrDefault(question.getId(), List.of()), students, userInfoMap)
                            : null;
                    return toQuestionVO(question, mySubmission, analysis, userInfoMap);
                })
                .toList();

        int submittedStudents = (int) submissions.stream().map(LivePracticeSubmission::getStudentId).distinct().count();
        return new LivePracticeGroupVO(
                group.getId(),
                group.getCourseId(),
                group.getClassSessionId(),
                group.getTeacherId(),
                group.getTitle(),
                group.getAvailableStartAt(),
                group.getAvailableEndAt(),
                group.getAllowLateSubmission(),
                group.getPublishOrder(),
                group.getPublishedAt(),
                questions.size(),
                submittedStudents,
                students.size(),
                questionVos
        );
    }

    private LivePracticeWorkbookItemVO toWorkbookItem(LivePracticeQuestion question, LivePracticeGroup group,
                                                      LivePracticeSubmission submission) {
        if (group == null) {
            return null;
        }
        int status = submission != null ? submission.getSubmitStatus() : LivePracticeSubmitStatus.NOT_SUBMITTED.getCode();
        return new LivePracticeWorkbookItemVO(
                group.getId(),
                group.getTitle(),
                group.getClassSessionId(),
                group.getPublishOrder(),
                group.getAvailableStartAt(),
                group.getAvailableEndAt(),
                group.getAllowLateSubmission(),
                toQuestionVO(question, submission, null, Map.of()),
                submission != null ? toSubmissionVO(submission, Map.of()) : null,
                status,
                submitStatusText(status)
        );
    }

    private LivePracticeQuestionVO toQuestionVO(LivePracticeQuestion question, LivePracticeSubmission submission,
                                                LivePracticeAnalysisVO analysis,
                                                Map<UUID, UserBasicInfo> userInfoMap) {
        return new LivePracticeQuestionVO(
                question.getId(),
                question.getGroupId(),
                question.getSourceQuestionId(),
                question.getQuestionOrder(),
                question.getQuestionTitle(),
                question.getQuestionContent(),
                question.getQuestionType(),
                question.getDifficulty(),
                question.getScore(),
                question.getEstimatedTime(),
                question.getTags(),
                question.getImageUrls(),
                question.getAllowPartialCredit(),
                question.getOptionsSnapshot(),
                question.getAnswersSnapshot(),
                question.getAiGradingEnabled(),
                submission != null ? toSubmissionVO(submission, userInfoMap) : null,
                analysis,
                question.getCreatedAt()
        );
    }

    private LivePracticeAnalysisVO buildAnalysis(LivePracticeQuestion question, List<LivePracticeSubmission> submissions,
                                                 List<Enrollment> students, Map<UUID, UserBasicInfo> userInfoMap) {
        Set<UUID> submittedStudentIds = submissions.stream()
                .map(LivePracticeSubmission::getStudentId)
                .collect(Collectors.toSet());
        List<LivePracticeStudentVO> notSubmittedStudents = students.stream()
                .map(Enrollment::getStudentId)
                .filter(studentId -> !submittedStudentIds.contains(studentId))
                .map(studentId -> toStudentVO(studentId, userInfoMap.get(studentId)))
                .toList();
        int submittedCount = submissions.size();
        BigDecimal averageScore = submittedCount == 0
                ? BigDecimal.ZERO
                : submissions.stream()
                .map(LivePracticeSubmission::getEarnedScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(submittedCount), PARTIAL_CREDIT_SCALE, RoundingMode.HALF_UP);
        return new LivePracticeAnalysisVO(
                submittedCount,
                (int) submissions.stream().filter(s -> Objects.equals(s.getSubmitStatus(), LivePracticeSubmitStatus.LATE_SUBMITTED.getCode())).count(),
                notSubmittedStudents.size(),
                (int) submissions.stream().filter(s -> Objects.equals(s.getIsCorrect(), FLAG_ON)).count(),
                averageScore,
                optionCounts(question, submissions),
                notSubmittedStudents
        );
    }

    private Map<String, Integer> optionCounts(LivePracticeQuestion question, List<LivePracticeSubmission> submissions) {
        Map<UUID, String> optionLabels = question.getOptionsSnapshot() == null
                ? Map.of()
                : question.getOptionsSnapshot().stream()
                .collect(Collectors.toMap(LivePracticeOptionSnapshot::id, LivePracticeOptionSnapshot::optionLabel, (a, b) -> a));
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (question.getOptionsSnapshot() != null) {
            question.getOptionsSnapshot().forEach(option -> counts.put(option.optionLabel(), 0));
        }
        submissions.stream()
                .flatMap(submission -> Optional.ofNullable(submission.getSelectedOptionIds()).orElse(List.of()).stream())
                .map(optionLabels::get)
                .filter(Objects::nonNull)
                .forEach(label -> counts.merge(label, 1, Integer::sum));
        return counts;
    }

    private LivePracticeSubmissionVO toSubmissionVO(LivePracticeSubmission submission, Map<UUID, UserBasicInfo> userInfoMap) {
        UserBasicInfo userInfo = userInfoMap.get(submission.getStudentId());
        return new LivePracticeSubmissionVO(
                submission.getId(),
                submission.getGroupId(),
                submission.getQuestionSnapshotId(),
                submission.getCourseId(),
                submission.getClassSessionId(),
                submission.getStudentId(),
                userInfo != null ? userInfo.displayName() : null,
                userInfo != null ? userInfo.avatarUrl() : null,
                submission.getSelectedOptionIds(),
                submission.getTextAnswer(),
                submission.getSubmitStatus(),
                submitStatusText(submission.getSubmitStatus()),
                submission.getIsCorrect(),
                submission.getEarnedScore(),
                submission.getAiGradingStatus(),
                submission.getAiGradingFeedback(),
                submission.getAiGradingError(),
                submission.getAiGradedAt(),
                submission.getSubmittedAt()
        );
    }

    private LivePracticeStudentVO toStudentVO(UUID studentId, UserBasicInfo userInfo) {
        return new LivePracticeStudentVO(
                studentId,
                userInfo != null ? userInfo.displayName() : null,
                userInfo != null ? userInfo.avatarUrl() : null
        );
    }

    private GradeResult grade(LivePracticeQuestion question, SubmitLivePracticeAnswerRequest request) {
        int questionType = question.getQuestionType();
        if (!isObjectiveQuestionType(questionType)) {
            return new GradeResult(null, BigDecimal.ZERO);
        }

        List<LivePracticeOptionSnapshot> correctOptions = Optional.ofNullable(question.getOptionsSnapshot()).orElse(List.of())
                .stream()
                .filter(option -> Objects.equals(option.isCorrect(), FLAG_ON))
                .toList();
        Set<UUID> correctIds = correctOptions.stream().map(LivePracticeOptionSnapshot::id).collect(Collectors.toSet());
        Set<UUID> selectedIds = new HashSet<>(Optional.ofNullable(request.selectedOptionIds()).orElse(List.of()));
        boolean isCorrect = correctIds.equals(selectedIds);
        if (isCorrect) {
            return new GradeResult(FLAG_ON, valueOrZero(question.getScore()));
        }
        if (Objects.equals(question.getAllowPartialCredit(), FLAG_ON) && questionType == QUESTION_TYPE_MULTI_CHOICE && !correctIds.isEmpty()) {
            long correctSelected = selectedIds.stream().filter(correctIds::contains).count();
            BigDecimal earnedScore = valueOrZero(question.getScore())
                    .multiply(BigDecimal.valueOf(correctSelected))
                    .divide(BigDecimal.valueOf(correctIds.size()), PARTIAL_CREDIT_SCALE, RoundingMode.HALF_UP);
            return new GradeResult(FLAG_OFF, earnedScore);
        }
        return new GradeResult(FLAG_OFF, BigDecimal.ZERO);
    }

    private void publishAiGradingRequest(LivePracticeSubmission submission, LivePracticeQuestion question) {
        LivePracticeAiGradingRequestedEvent event = new LivePracticeAiGradingRequestedEvent(
                UuidV7Generator.generate(),
                submission.getId(),
                submission.getGroupId(),
                submission.getQuestionSnapshotId(),
                submission.getCourseId(),
                submission.getClassSessionId(),
                submission.getStudentId(),
                question.getQuestionTitle(),
                question.getQuestionContent(),
                question.getScore(),
                Optional.ofNullable(question.getAnswersSnapshot()).orElse(List.of()).stream()
                        .map(answer -> new LivePracticeAiGradingRequestedEvent.AnswerReference(
                                answer.answerContent(),
                                answer.explanation(),
                                answer.score(),
                                answer.sortOrder()
                        ))
                        .toList(),
                submission.getTextAnswer(),
                "LIVE_PRACTICE_AI_GRADING_REQUESTED",
                Instant.now(),
                "sc-course"
        );
        livePracticeAiGradingEventPublisher.publishRequested(event);
    }

    private boolean aiGradingRequired(LivePracticeQuestion question) {
        return Objects.equals(question.getAiGradingEnabled(), FLAG_ON)
                && !isObjectiveQuestionType(question.getQuestionType());
    }

    private int aiGradingFlag(CreateLivePracticeQuestionRequest request) {
        if (!Objects.equals(request.aiGradingEnabled(), FLAG_ON) || isObjectiveQuestionType(request.questionType())) {
            return FLAG_OFF;
        }
        return FLAG_ON;
    }

    private boolean isObjectiveQuestionType(int questionType) {
        return questionType == QUESTION_TYPE_SINGLE_CHOICE
                || questionType == QUESTION_TYPE_MULTI_CHOICE
                || questionType == QUESTION_TYPE_TRUE_FALSE;
    }

    private int resolveSubmitStatus(LivePracticeGroup group, Instant now) {
        if (now.isBefore(group.getAvailableStartAt())) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Live practice has not started");
        }
        if (!now.isAfter(group.getAvailableEndAt())) {
            return LivePracticeSubmitStatus.SUBMITTED.getCode();
        }
        if (Objects.equals(group.getAllowLateSubmission(), FLAG_ON)) {
            return LivePracticeSubmitStatus.LATE_SUBMITTED.getCode();
        }
        throw new BusinessException(ErrorCodes.BAD_REQUEST, "Live practice has ended");
    }

    private void requirePublished(ClassSession session) {
        if (session.getPublishedAt() == null) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Class session is still preparing");
        }
    }

    private void requireSessionAccess(ClassSession session, UUID userId, Integer role) {
        if (isCourseTeacher(session.getCourseId(), userId, role)) {
            return;
        }
        requireActiveStudent(session.getCourseId(), userId);
    }

    private void requireCourseAccess(UUID courseId, UUID userId, Integer role) {
        if (isCourseTeacher(courseId, userId, role)) {
            return;
        }
        requireActiveStudent(courseId, userId);
    }

    private void requireCourseTeacher(UUID courseId, UUID userId, Integer role) {
        if (!isCourseTeacher(courseId, userId, role)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }
        courseRepository.findById(courseId).orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
    }

    private boolean isCourseTeacher(UUID courseId, UUID userId, Integer role) {
        return userId != null && (SecurityUtils.isAdmin(role) || courseTeacherRepository.existsByCourseIdAndTeacherId(courseId, userId));
    }

    private void requireActiveStudent(UUID courseId, UUID userId) {
        enrollmentRepository.findByCourseIdAndStudentId(courseId, userId)
                .filter(this::activeOrCompleted)
                .orElseThrow(() -> new BusinessException(ErrorCodes.FORBIDDEN));
    }

    private boolean activeOrCompleted(Enrollment enrollment) {
        return enrollment.getStatus() == EnrollmentStatus.ACTIVE.getCode()
                || enrollment.getStatus() == EnrollmentStatus.COMPLETED.getCode();
    }

    private List<Enrollment> activeStudents(UUID courseId) {
        return enrollmentRepository.findActiveOrCompletedByCourseId(courseId);
    }

    private void validatePracticeWindow(Instant startAt, Instant endAt) {
        if (startAt == null || endAt == null || !endAt.isAfter(startAt)) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Live practice end time must be after start time");
        }
    }

    private int flag(Integer value) {
        return Objects.equals(value, FLAG_ON) ? FLAG_ON : FLAG_OFF;
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private List<UUID> studentIds(List<Enrollment> students) {
        return students.stream().map(Enrollment::getStudentId).distinct().toList();
    }

    private Map<UUID, UserBasicInfo> loadUserInfoMap(List<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        try {
            ApiResponse<@NonNull List<@NonNull UserBasicInfo>> response = authInternalClient.getUsersBasicInfo(userIds);
            if (response != null && response.code() == ErrorCodes.SUCCESS.code() && response.data() != null) {
                return response.data().stream()
                        .collect(Collectors.toMap(UserBasicInfo::id, Function.identity(), (a, b) -> a));
            }
        } catch (Exception ignored) {
            return Map.of();
        }
        return Map.of();
    }

    private LivePracticeEventVO toEventVO(LivePracticeGroup group, int totalQuestions) {
        return new LivePracticeEventVO(
                group.getId(),
                group.getCourseId(),
                group.getClassSessionId(),
                group.getTitle(),
                totalQuestions,
                group.getAvailableStartAt(),
                group.getAvailableEndAt(),
                group.getAllowLateSubmission(),
                group.getPublishedAt()
        );
    }

    private String submitStatusText(Integer status) {
        if (Objects.equals(status, LivePracticeSubmitStatus.SUBMITTED.getCode())) {
            return LivePracticeSubmitStatus.SUBMITTED.getDescription();
        }
        if (Objects.equals(status, LivePracticeSubmitStatus.LATE_SUBMITTED.getCode())) {
            return LivePracticeSubmitStatus.LATE_SUBMITTED.getDescription();
        }
        return LivePracticeSubmitStatus.NOT_SUBMITTED.getDescription();
    }

    private record GradeResult(Integer isCorrect, BigDecimal earnedScore) {
    }
}
