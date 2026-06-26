package com.dayz.sc.common.error;

import org.springframework.http.HttpStatus;

/**
 * 系统统一错误码
 *
 * @author DaYZ
 * @since 2026-05-07
 */
public enum ErrorCodes implements ErrorCode {
    /**
     * 请求处理成功
     */
    SUCCESS(0, "success", HttpStatus.OK),
    /**
     * 请求参数不合法或格式错误
     */
    BAD_REQUEST(40000, "请求参数错误", HttpStatus.BAD_REQUEST),
    /**
     * 请求未完成认证或认证凭证无效
     */
    UNAUTHORIZED(40100, "未认证", HttpStatus.UNAUTHORIZED),
    /**
     * 当前用户无权访问目标资源
     */
    FORBIDDEN(40300, "无访问权限", HttpStatus.FORBIDDEN),
    /**
     * 用户基础信息未完善
     */
    PROFILE_INCOMPLETE(40310, "用户基础信息未完善", HttpStatus.FORBIDDEN),
    /**
     * 请求访问的资源不存在
     */
    NOT_FOUND(40400, "资源不存在", HttpStatus.NOT_FOUND),
    /**
     * 系统内部异常
     */
    SYSTEM_ERROR(50000, "系统异常", HttpStatus.INTERNAL_SERVER_ERROR),
    /**
     * 上游服务不可用（Feign 熔断降级）
     */
    SERVICE_UNAVAILABLE(50300, "服务暂不可用", HttpStatus.SERVICE_UNAVAILABLE),
    /**
     * Google 登录流程失败
     */
    GOOGLE_LOGIN_FAILED(40101, "Google 登录失败", HttpStatus.UNAUTHORIZED),
    /**
     * GitHub 登录流程失败
     */
    GITHUB_LOGIN_FAILED(40102, "GitHub 登录失败", HttpStatus.UNAUTHORIZED),
    /**
     * 账号密码登录失败
     */
    PASSWORD_LOGIN_FAILED(40103, "邮箱或密码错误", HttpStatus.UNAUTHORIZED),
    /**
     * 邮箱已注册
     */
    EMAIL_ALREADY_EXISTS(40104, "邮箱已注册", HttpStatus.CONFLICT),
    /**
     * 通知不存在
     */
    NOTIFICATION_NOT_FOUND(40401, "通知不存在", HttpStatus.NOT_FOUND),
    /**
     * 无权发送通知
     */
    NOTIFICATION_SEND_FORBIDDEN(40301, "无权发送通知", HttpStatus.FORBIDDEN),
    /**
     * 只能撤回自己发出的通知
     */
    NOTIFICATION_RECALL_FORBIDDEN(40302, "只能撤回自己发出的通知", HttpStatus.FORBIDDEN),
    /**
     * 课程不存在
     */
    COURSE_NOT_FOUND(40402, "课程不存在", HttpStatus.NOT_FOUND),
    /**
     * 章节不存在
     */
    CHAPTER_NOT_FOUND(40403, "章节不存在", HttpStatus.NOT_FOUND),
    /**
     * 论坛不存在
     */
    FORUM_NOT_FOUND(40404, "论坛不存在", HttpStatus.NOT_FOUND),
    /**
     * 帖子不存在
     */
    POST_NOT_FOUND(40405, "帖子不存在", HttpStatus.NOT_FOUND),
    /**
     * 回复不存在
     */
    REPLY_NOT_FOUND(40406, "回复不存在", HttpStatus.NOT_FOUND),
    /**
     * 题库不存在
     */
    QUESTION_BANK_NOT_FOUND(40407, "题库不存在", HttpStatus.NOT_FOUND),
    /**
     * 题目不存在
     */
    QUESTION_NOT_FOUND(40408, "题目不存在", HttpStatus.NOT_FOUND),
    /**
     * 存储对象不存在
     */
    STORAGE_OBJECT_NOT_FOUND(40409, "存储对象不存在", HttpStatus.NOT_FOUND),
    /**
     * 文件上传失败
     */
    STORAGE_UPLOAD_FAILED(40010, "文件上传失败", HttpStatus.BAD_REQUEST),
    /**
     * 无权访问该存储资源
     */
    STORAGE_UNAUTHORIZED(40303, "无权访问该存储资源", HttpStatus.FORBIDDEN),
    /**
     * 已选过该课程
     */
    ENROLLMENT_ALREADY_EXISTS(40011, "已选过该课程", HttpStatus.BAD_REQUEST),
    /**
     * 课程人数已满
     */
    ENROLLMENT_COURSE_FULL(40012, "课程人数已满", HttpStatus.BAD_REQUEST),
    /**
     * 邀请状态无效
     */
    INVITATION_NOT_PENDING(40013, "邀请状态无效", HttpStatus.BAD_REQUEST),
    /**
     * 已邀请过该用户
     */
    INVITATION_ALREADY_INVITED(40014, "已邀请过该用户", HttpStatus.BAD_REQUEST),
    /**
     * AI 会话不存在
     */
    AI_CONVERSATION_NOT_FOUND(40410, "会话不存在", HttpStatus.NOT_FOUND),
    /**
     * AI 知识库文档不存在
     */
    AI_KNOWLEDGE_DOC_NOT_FOUND(40411, "知识库文档不存在", HttpStatus.NOT_FOUND),
    /**
     * AI 文档处理失败
     */
    AI_DOCUMENT_PROCESS_FAILED(50001, "文档处理失败", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCodes(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public int code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
