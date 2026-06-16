package com.dayz.sc.course.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.response.PageResponse;
import com.dayz.sc.common.security.support.SecurityUtils;
import com.dayz.sc.common.util.PageUtils;
import com.dayz.sc.common.util.UuidV7Generator;
import com.dayz.sc.course.model.dto.*;
import com.dayz.sc.course.model.entity.Question;
import com.dayz.sc.course.model.entity.QuestionAnswer;
import com.dayz.sc.course.model.entity.QuestionBank;
import com.dayz.sc.course.model.entity.QuestionOption;
import com.dayz.sc.course.model.enums.QuestionDifficulty;
import com.dayz.sc.course.model.enums.QuestionStatus;
import com.dayz.sc.course.model.enums.QuestionType;
import com.dayz.sc.course.model.vo.QuestionAnswerVO;
import com.dayz.sc.course.model.vo.QuestionBankVO;
import com.dayz.sc.course.model.vo.QuestionOptionVO;
import com.dayz.sc.course.model.vo.QuestionVO;
import com.dayz.sc.course.repository.QuestionAnswerRepository;
import com.dayz.sc.course.repository.QuestionBankRepository;
import com.dayz.sc.course.repository.QuestionOptionRepository;
import com.dayz.sc.course.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 业务服务
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@Service
@RequiredArgsConstructor
public class QuestionBankService {

    private final QuestionBankRepository questionBankRepository;
    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final QuestionAnswerRepository questionAnswerRepository;

    // ==================== QuestionBank CRUD ====================

    @Transactional(rollbackFor = Exception.class)
    public UUID createQuestionBank(CreateQuestionBankRequest request, UUID userId) {
        QuestionDifficulty.fromCode(request.difficulty());

        QuestionBank bank = new QuestionBank();
        bank.setCourseId(request.courseId());
        bank.setSysUserId(userId);
        bank.setBankName(request.bankName());
        bank.setDescription(request.description());
        bank.setBankType(request.bankType());
        bank.setTags(request.tags());
        bank.setDifficulty(request.difficulty());
        bank.setId(UuidV7Generator.generate());

        questionBankRepository.save(bank);
        return bank.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateQuestionBank(UUID bankId, UpdateQuestionBankRequest request, UUID userId, Integer role) {
        QuestionBank bank = questionBankRepository.findById(bankId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));

        if (!bank.getSysUserId().equals(userId) && !SecurityUtils.isAdmin(role)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }

        if (request.bankName() != null) {
            bank.setBankName(request.bankName());
        }
        if (request.description() != null) {
            bank.setDescription(request.description());
        }
        if (request.bankType() != null) {
            bank.setBankType(request.bankType());
        }
        if (request.tags() != null) {
            bank.setTags(request.tags());
        }
        if (request.difficulty() != null) {
            QuestionDifficulty.fromCode(request.difficulty());
            bank.setDifficulty(request.difficulty());
        }

        questionBankRepository.update(bank);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteQuestionBank(UUID bankId, UUID userId, Integer role) {
        QuestionBank bank = questionBankRepository.findById(bankId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));

        if (!bank.getSysUserId().equals(userId) && !SecurityUtils.isAdmin(role)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }

        questionBankRepository.deleteById(bankId);
    }

    public QuestionBankVO getQuestionBank(UUID bankId) {
        QuestionBank bank = questionBankRepository.findById(bankId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        long questionCount = questionRepository.countByQuestionBankId(bankId);
        return toQuestionBankVO(bank, questionCount);
    }

    public PageResponse<QuestionBankVO> listQuestionBanks(QuestionBankPageRequest request) {
        int page = PageUtils.normalizePage(request.page());
        int size = PageUtils.normalizeSize(request.size());

        Page<QuestionBank> result = questionBankRepository.findAll(page, size,
                request.courseId(), request.bankType(), request.keyword());

        List<UUID> bankIds = result.getRecords().stream().map(QuestionBank::getId).toList();
        Map<UUID, Long> countMap = questionRepository.countByQuestionBankIds(bankIds);

        List<QuestionBankVO> voList = result.getRecords().stream()
                .map(bank -> toQuestionBankVO(bank, countMap.getOrDefault(bank.getId(), 0L)))
                .toList();

        return new PageResponse<>(voList, result.getTotal(), page, size);
    }

    public List<QuestionBankVO> listQuestionBanksByCourse(UUID courseId) {
        List<QuestionBank> banks = questionBankRepository.findByCourseId(courseId);
        List<UUID> bankIds = banks.stream().map(QuestionBank::getId).toList();
        Map<UUID, Long> countMap = questionRepository.countByQuestionBankIds(bankIds);

        return banks.stream()
                .map(bank -> toQuestionBankVO(bank, countMap.getOrDefault(bank.getId(), 0L)))
                .toList();
    }

    // ==================== Question CRUD ====================

    @Transactional(rollbackFor = Exception.class)
    public UUID createQuestion(CreateQuestionRequest request, UUID userId) {
        QuestionType.fromCode(request.questionType());
        QuestionDifficulty.fromCode(request.difficulty());

        QuestionBank bank = questionBankRepository.findById(request.questionBankId())
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));

        Question question = new Question();
        question.setQuestionBankId(request.questionBankId());
        question.setCourseId(bank.getCourseId());
        question.setSysUserId(userId);
        question.setQuestionTitle(request.questionTitle());
        question.setQuestionContent(request.questionContent());
        question.setQuestionType(request.questionType());
        question.setDifficulty(request.difficulty());
        question.setScore(request.score());
        question.setEstimatedTime(request.estimatedTime());
        question.setTags(request.tags());
        question.setImageUrls(request.imageUrls());
        question.setAllowPartialCredit(request.allowPartialCredit());
        question.setViewCount(0L);
        question.setStatus(QuestionStatus.DRAFT.getCode());
        question.setId(UuidV7Generator.generate());

        questionRepository.save(question);

        // 保存选项
        if (request.options() != null && !request.options().isEmpty()) {
            List<QuestionOption> options = request.options().stream()
                    .map(opt -> {
                        QuestionOption option = new QuestionOption();
                        option.setId(UuidV7Generator.generate());
                        option.setQuestionId(question.getId());
                        option.setCourseId(bank.getCourseId());
                        option.setOptionContent(opt.optionContent());
                        option.setOptionLabel(opt.optionLabel());
                        option.setIsCorrect(opt.isCorrect());
                        option.setScore(opt.score());
                        option.setImageUrls(opt.imageUrls());
                        option.setExplanation(opt.explanation());
                        return option;
                    })
                    .toList();
            questionOptionRepository.saveBatch(options);
        }

        // 保存答案
        if (request.answers() != null && !request.answers().isEmpty()) {
            List<QuestionAnswer> answers = request.answers().stream()
                    .map(ans -> {
                        QuestionAnswer answer = new QuestionAnswer();
                        answer.setId(UuidV7Generator.generate());
                        answer.setQuestionId(question.getId());
                        answer.setCourseId(bank.getCourseId());
                        answer.setAnswerContent(ans.answerContent());
                        answer.setExplanation(ans.explanation());
                        answer.setScore(ans.score());
                        answer.setSortOrder(ans.sortOrder());
                        return answer;
                    })
                    .toList();
            questionAnswerRepository.saveBatch(answers);
        }

        return question.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateQuestion(UUID questionId, UpdateQuestionRequest request, UUID userId, Integer role) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));

        if (!question.getSysUserId().equals(userId) && !SecurityUtils.isAdmin(role)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }
        if (question.getStatus() != QuestionStatus.DRAFT.getCode()) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "只能编辑草稿题目");
        }

        updateQuestionFields(question, request);
        questionRepository.update(question);

        if (request.options() != null) {
            updateQuestionOptions(questionId, question.getCourseId(), request.options());
        }
        if (request.answers() != null) {
            updateQuestionAnswers(questionId, question.getCourseId(), request.answers());
        }
    }

    private void updateQuestionFields(Question question, UpdateQuestionRequest request) {
        if (request.questionTitle() != null) {
            question.setQuestionTitle(request.questionTitle());
        }
        if (request.questionContent() != null) {
            question.setQuestionContent(request.questionContent());
        }
        if (request.questionType() != null) {
            QuestionType.fromCode(request.questionType());
            question.setQuestionType(request.questionType());
        }
        if (request.difficulty() != null) {
            QuestionDifficulty.fromCode(request.difficulty());
            question.setDifficulty(request.difficulty());
        }
        if (request.score() != null) {
            question.setScore(request.score());
        }
        if (request.estimatedTime() != null) {
            question.setEstimatedTime(request.estimatedTime());
        }
        if (request.tags() != null) {
            question.setTags(request.tags());
        }
        if (request.imageUrls() != null) {
            question.setImageUrls(request.imageUrls());
        }
        if (request.allowPartialCredit() != null) {
            question.setAllowPartialCredit(request.allowPartialCredit());
        }
    }

    private void updateQuestionOptions(UUID questionId, UUID courseId, List<?> options) {
        questionOptionRepository.deleteByQuestionId(questionId);
        List<QuestionOption> optionEntities = options.stream()
                .map(opt -> {
                    QuestionOption option = new QuestionOption();
                    option.setId(UuidV7Generator.generate());
                    option.setQuestionId(questionId);
                    option.setCourseId(courseId);
                    option.setOptionContent(opt.optionContent());
                    option.setOptionLabel(opt.optionLabel());
                    option.setIsCorrect(opt.isCorrect());
                    option.setScore(opt.score());
                    option.setImageUrls(opt.imageUrls());
                    option.setExplanation(opt.explanation());
                    return option;
                })
                .toList();
        questionOptionRepository.saveBatch(optionEntities);
    }

    private void updateQuestionAnswers(UUID questionId, UUID courseId, List<?> answers) {
        questionAnswerRepository.deleteByQuestionId(questionId);
        List<QuestionAnswer> answerEntities = answers.stream()
                .map(ans -> {
                    QuestionAnswer answer = new QuestionAnswer();
                    answer.setId(UuidV7Generator.generate());
                    answer.setQuestionId(questionId);
                    answer.setCourseId(courseId);
                    answer.setAnswerContent(ans.answerContent());
                    answer.setExplanation(ans.explanation());
                    answer.setScore(ans.score());
                    answer.setSortOrder(ans.sortOrder());
                    return answer;
                })
                .toList();
        questionAnswerRepository.saveBatch(answerEntities);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteQuestion(UUID questionId, UUID userId, Integer role) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));

        if (!question.getSysUserId().equals(userId) && !SecurityUtils.isAdmin(role)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }

        questionOptionRepository.deleteByQuestionId(questionId);
        questionAnswerRepository.deleteByQuestionId(questionId);
        questionRepository.deleteById(questionId);
    }

    public QuestionVO getQuestion(UUID questionId, UUID userId, Integer role) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));

        // 草稿只有创建者可见
        if (question.getStatus() == QuestionStatus.DRAFT.getCode()
                && !question.getSysUserId().equals(userId)
                && !SecurityUtils.isAdmin(role)) {
            throw new BusinessException(ErrorCodes.NOT_FOUND);
        }

        List<QuestionOptionVO> options = questionOptionRepository.findByQuestionId(questionId).stream()
                .map(this::toQuestionOptionVO)
                .toList();
        List<QuestionAnswerVO> answers = questionAnswerRepository.findByQuestionId(questionId).stream()
                .map(this::toQuestionAnswerVO)
                .toList();

        return toQuestionVO(question, options, answers);
    }

    public PageResponse<QuestionVO> listQuestions(QuestionPageRequest request, UUID userId, Integer role) {
        int page = PageUtils.normalizePage(request.page());
        int size = PageUtils.normalizeSize(request.size());
        UUID visibleUserId = SecurityUtils.isAdmin(role) ? null : userId;

        Page<Question> result = questionRepository.findAll(page, size,
                request.questionBankId(), request.courseId(),
                request.questionType(), request.difficulty(), request.status(), request.keyword(),
                visibleUserId);

        List<QuestionVO> voList = result.getRecords().stream()
                .map(q -> toQuestionVO(q, null, null))
                .toList();

        return new PageResponse<>(voList, result.getTotal(), page, size);
    }

    @Transactional(rollbackFor = Exception.class)
    public void publishQuestion(UUID questionId, UUID userId, Integer role) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));

        if (!question.getSysUserId().equals(userId) && !SecurityUtils.isAdmin(role)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }
        if (question.getStatus() != QuestionStatus.DRAFT.getCode()) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "只能发布草稿题目");
        }

        question.setStatus(QuestionStatus.PUBLISHED.getCode());
        questionRepository.update(question);
    }

    @Transactional(rollbackFor = Exception.class)
    public void viewQuestion(UUID questionId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        question.setViewCount(question.getViewCount() + 1);
        questionRepository.update(question);
    }

    // ==================== VO Converters ====================

    private QuestionBankVO toQuestionBankVO(QuestionBank bank, long questionCount) {
        return new QuestionBankVO(
                bank.getId(),
                bank.getCourseId(),
                bank.getSysUserId(),
                bank.getBankName(),
                bank.getDescription(),
                bank.getBankType(),
                bank.getTags(),
                bank.getDifficulty(),
                questionCount,
                bank.getCreatedAt(),
                bank.getUpdatedAt()
        );
    }

    private QuestionVO toQuestionVO(Question question, List<QuestionOptionVO> options, List<QuestionAnswerVO> answers) {
        return new QuestionVO(
                question.getId(),
                question.getQuestionBankId(),
                question.getCourseId(),
                question.getSysUserId(),
                question.getQuestionTitle(),
                question.getQuestionContent(),
                question.getQuestionType(),
                question.getDifficulty(),
                question.getScore(),
                question.getEstimatedTime(),
                question.getTags(),
                question.getImageUrls(),
                question.getAllowPartialCredit(),
                question.getViewCount(),
                question.getStatus(),
                options,
                answers,
                question.getCreatedAt(),
                question.getUpdatedAt()
        );
    }

    private QuestionOptionVO toQuestionOptionVO(QuestionOption option) {
        return new QuestionOptionVO(
                option.getId(),
                option.getQuestionId(),
                option.getOptionContent(),
                option.getOptionLabel(),
                option.getIsCorrect(),
                option.getScore(),
                option.getImageUrls(),
                option.getExplanation()
        );
    }

    private QuestionAnswerVO toQuestionAnswerVO(QuestionAnswer answer) {
        return new QuestionAnswerVO(
                answer.getId(),
                answer.getQuestionId(),
                answer.getAnswerContent(),
                answer.getExplanation(),
                answer.getScore(),
                answer.getSortOrder()
        );
    }
}
