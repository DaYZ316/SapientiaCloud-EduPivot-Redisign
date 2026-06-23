package com.dayz.sc.common.web.handler;

import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    @Test
    void businessExceptionHandlerShouldRespondWithJsonContentType() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var response = handler.handleBusinessException(new BusinessException(ErrorCodes.NOT_FOUND));

        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    void businessExceptionHandlerShouldReturnJsonWhenClientAcceptsEventStream() throws Exception {
        var mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/boom").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @RestController
    private static class ThrowingController {

        @GetMapping("/boom")
        void boom() {
            throw new BusinessException(ErrorCodes.NOT_FOUND);
        }
    }
}
