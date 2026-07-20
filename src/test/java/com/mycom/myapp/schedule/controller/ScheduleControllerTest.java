package com.mycom.myapp.schedule.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;
import com.mycom.myapp.global.exception.GlobalExceptionHandler;
import com.mycom.myapp.global.security.AuthenticatedMember;
import com.mycom.myapp.member.entity.MemberRole;
import com.mycom.myapp.schedule.dto.request.ScheduleCreateRequest;
import com.mycom.myapp.schedule.dto.response.ScheduleResponse;
import com.mycom.myapp.schedule.service.ScheduleService;
import java.time.LocalDateTime;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class ScheduleControllerTest {

    private final ScheduleService scheduleService = mock(ScheduleService.class);
    private final AuthenticatedMember principal =
            new AuthenticatedMember(1L, "leader@example.com", MemberRole.USER);
    private final ScheduleCreateRequest validRequest =
            new ScheduleCreateRequest(
                    "3주차 스터디",
                    LocalDateTime.of(2026, 7, 25, 19, 0),
                    null,
                    null,
                    "3장 문제 풀이",
                    "교재와 노트북",
                    LocalDateTime.of(2026, 7, 24, 18, 0));
    private final String validRequestJson =
            """
            {"title":"3주차 스터디","scheduledAt":"2026-07-25T19:00:00","location":null,"onlineLink":null,"content":"3장 문제 풀이","materials":"교재와 노트북","responseDeadline":"2026-07-24T18:00:00"}
            """;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc =
                MockMvcBuilders.standaloneSetup(new ScheduleController(scheduleService))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .setCustomArgumentResolvers(new PrincipalResolver())
                        .setValidator(validator)
                        .build();
    }

    @Test
    void createsScheduleAndReturnsFullResponse() throws Exception {
        when(scheduleService.create(eq(10L), eq(1L), any(ScheduleCreateRequest.class)))
                .thenReturn(response());

        mockMvc.perform(
                        post("/api/groups/{groupId}/schedules", 10L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validRequestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.scheduleId").value(100))
                .andExpect(jsonPath("$.data.groupId").value(10));
        verify(scheduleService).create(eq(10L), eq(1L), any(ScheduleCreateRequest.class));
    }

    @Test
    void rejectsBlankTitleAndMissingScheduledAt() throws Exception {
        mockMvc.perform(
                        post("/api/groups/{groupId}/schedules", 10L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"title\":\"   \",\"scheduledAt\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
        verifyNoInteractions(scheduleService);
    }

    @ParameterizedTest
    @MethodSource("businessErrors")
    void mapsBusinessErrors(ErrorCode errorCode) throws Exception {
        when(scheduleService.create(eq(10L), eq(1L), any(ScheduleCreateRequest.class)))
                .thenThrow(new BusinessException(errorCode));
        mockMvc.perform(
                        post("/api/groups/{groupId}/schedules", 10L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validRequestJson))
                .andExpect(status().is(errorCode.getStatus().value()))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(errorCode.getMessage()));
    }

    @Test
    void rejectsMissingPrincipal() {
        ScheduleController controller = new ScheduleController(scheduleService);
        assertThatThrownBy(() -> controller.create(10L, null, validRequest))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    private static Stream<ErrorCode> businessErrors() {
        return Stream.of(
                ErrorCode.GROUP_NOT_FOUND,
                ErrorCode.GROUP_ACCESS_DENIED,
                ErrorCode.WITHDRAWN_GROUP_MEMBER,
                ErrorCode.GROUP_ENDED,
                ErrorCode.SCHEDULE_MANAGEMENT_FORBIDDEN,
                ErrorCode.INVALID_SCHEDULE_TIME);
    }

    private static ScheduleResponse response() {
        return new ScheduleResponse(
                100L,
                10L,
                1L,
                "3주차 스터디",
                LocalDateTime.of(2026, 7, 25, 19, 0),
                null,
                null,
                "3장 문제 풀이",
                "교재와 노트북",
                LocalDateTime.of(2026, 7, 24, 18, 0),
                LocalDateTime.of(2026, 7, 20, 12, 0),
                LocalDateTime.of(2026, 7, 20, 12, 0));
    }

    private class PrincipalResolver implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
        }

        @Override
        public Object resolveArgument(
                MethodParameter parameter,
                ModelAndViewContainer mavContainer,
                NativeWebRequest webRequest,
                WebDataBinderFactory binderFactory) {
            return principal;
        }
    }
}
