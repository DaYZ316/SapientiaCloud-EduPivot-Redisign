package com.dayz.sc.gateway.handler;

import com.dayz.sc.common.error.ErrorCodes;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayExceptionHandlerTest {

    private final GatewayExceptionHandler handler = new GatewayExceptionHandler();

    @Test
    void shouldReturnServiceUnavailableWhenGatewayLoadBalancerHasNoInstance() {
        var response = handler.handleServiceUnavailable(
                new HttpServerErrorException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Unable to find instance for sc-course"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ErrorCodes.SERVICE_UNAVAILABLE.code());
    }

    @Test
    void shouldRethrowOtherServiceUnavailableErrors() {
        var exception = new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE, "downstream overloaded");

        assertThatThrownBy(() -> handler.handleServiceUnavailable(exception))
                .isSameAs(exception);
    }
}
