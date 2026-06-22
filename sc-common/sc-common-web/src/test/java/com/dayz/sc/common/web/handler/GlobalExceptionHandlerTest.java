package com.dayz.sc.common.web.handler;

import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    @Test
    void asyncRequestNotUsableHandlerShouldNotWriteApiResponse() throws Exception {
        Method method = GlobalExceptionHandler.class.getMethod(
                "handleAsyncRequestNotUsableException",
                AsyncRequestNotUsableException.class);

        assertThat(method.getReturnType()).isEqualTo(Void.TYPE);
    }

    @Test
    void asyncRequestNotUsableHandlerShouldCompleteWithoutRethrowing() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        handler.handleAsyncRequestNotUsableException(
                new AsyncRequestNotUsableException("ServletResponse failed to flushBuffer"));
    }
}
