package com.mycom.myapp.schedule.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;
import com.mycom.myapp.global.exception.GlobalExceptionHandler;
import com.mycom.myapp.global.security.AuthenticatedMember;
import com.mycom.myapp.member.entity.MemberRole;
import com.mycom.myapp.schedule.dto.request.ScheduleCreateRequest;
import com.mycom.myapp.schedule.dto.request.ScheduleDeadlineUpdateRequest;
import com.mycom.myapp.schedule.dto.request.ScheduleScope;
import com.mycom.myapp.schedule.dto.request.ScheduleUpdateRequest;
import com.mycom.myapp.schedule.dto.response.SchedulePageResponse;
import com.mycom.myapp.schedule.dto.response.ScheduleResponse;
import com.mycom.myapp.schedule.dto.response.ScheduleSummaryResponse;
import com.mycom.myapp.schedule.service.ScheduleService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
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
    private final String validUpdateRequestJson =
            """
            {"title":"수정 일정","scheduledAt":"2026-07-28T19:00:00","location":"수정 장소","onlineLink":null,"content":"수정 내용","materials":null}
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

    @Test
    void listsUpcomingSchedulesWithDefaultPageParameters() throws Exception {
        when(scheduleService.getSchedules(10L, 1L, ScheduleScope.UPCOMING, 0, 20))
                .thenReturn(pageResponse());

        mockMvc.perform(get("/api/groups/{groupId}/schedules", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].scheduleId").value(100))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false));
        verify(scheduleService).getSchedules(10L, 1L, ScheduleScope.UPCOMING, 0, 20);
    }

    @Test
    void listsPastSchedulesWithRequestedPageParameters() throws Exception {
        when(scheduleService.getSchedules(10L, 1L, ScheduleScope.PAST, 2, 10))
                .thenReturn(new SchedulePageResponse(List.of(), 2, 10, 0, 0, false));

        mockMvc.perform(
                        get("/api/groups/{groupId}/schedules", 10L)
                                .queryParam("scope", "past")
                                .queryParam("page", "2")
                                .queryParam("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.page").value(2));
        verify(scheduleService).getSchedules(10L, 1L, ScheduleScope.PAST, 2, 10);
    }

    @ParameterizedTest
    @MethodSource("invalidListQueries")
    void rejectsInvalidListQuery(String scope, String page, String size) throws Exception {
        mockMvc.perform(
                        get("/api/groups/{groupId}/schedules", 10L)
                                .queryParam("scope", scope)
                                .queryParam("page", page)
                                .queryParam("size", size))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_REQUEST.getMessage()));
        verifyNoMoreInteractions(scheduleService);
    }

    @Test
    void returnsFullScheduleDetail() throws Exception {
        when(scheduleService.getSchedule(10L, 1L, 100L)).thenReturn(response());

        mockMvc.perform(get("/api/groups/{groupId}/schedules/{scheduleId}", 10L, 100L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.scheduleId").value(100))
                .andExpect(jsonPath("$.data.groupId").value(10))
                .andExpect(jsonPath("$.data.content").value("3장 문제 풀이"));
        verify(scheduleService).getSchedule(10L, 1L, 100L);
    }

    @Test
    void mapsScheduleNotFoundOnDetail() throws Exception {
        when(scheduleService.getSchedule(10L, 1L, 999L))
                .thenThrow(new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND));

        mockMvc.perform(get("/api/groups/{groupId}/schedules/{scheduleId}", 10L, 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(ErrorCode.SCHEDULE_NOT_FOUND.getMessage()));
    }

    @Test
    void rejectsMissingPrincipalForScheduleList() {
        ScheduleController controller = new ScheduleController(scheduleService);

        assertThatThrownBy(() -> controller.getSchedules(10L, null, "upcoming", "0", "20"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void rejectsMissingPrincipalForScheduleDetail() {
        ScheduleController controller = new ScheduleController(scheduleService);

        assertThatThrownBy(() -> controller.getSchedule(10L, 100L, null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void updatesScheduleAndReturnsFullResponse() throws Exception {
        when(scheduleService.update(eq(10L), eq(1L), eq(100L), any(ScheduleUpdateRequest.class)))
                .thenReturn(response());

        mockMvc.perform(
                        put("/api/groups/{groupId}/schedules/{scheduleId}", 10L, 100L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validUpdateRequestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.scheduleId").value(100))
                .andExpect(jsonPath("$.data.groupId").value(10));
        verify(scheduleService).update(eq(10L), eq(1L), eq(100L), any(ScheduleUpdateRequest.class));
    }

    @Test
    void rejectsInvalidUpdateBodyBeforeServiceCall() throws Exception {
        mockMvc.perform(
                        put("/api/groups/{groupId}/schedules/{scheduleId}", 10L, 100L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"title\":\"   \",\"scheduledAt\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
        verifyNoInteractions(scheduleService);
    }

    @ParameterizedTest
    @MethodSource("updateBusinessErrors")
    void mapsUpdateBusinessErrors(ErrorCode errorCode) throws Exception {
        when(scheduleService.update(eq(10L), eq(1L), eq(100L), any(ScheduleUpdateRequest.class)))
                .thenThrow(new BusinessException(errorCode));

        mockMvc.perform(
                        put("/api/groups/{groupId}/schedules/{scheduleId}", 10L, 100L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validUpdateRequestJson))
                .andExpect(status().is(errorCode.getStatus().value()))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(errorCode.getMessage()));
    }

    @Test
    void rejectsMissingPrincipalForScheduleUpdate() {
        ScheduleController controller = new ScheduleController(scheduleService);
        ScheduleUpdateRequest request =
                new ScheduleUpdateRequest(
                        "수정 일정", LocalDateTime.of(2026, 7, 28, 19, 0), null, null, null, null);

        assertThatThrownBy(() -> controller.update(10L, 100L, null, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void updatesAndRemovesResponseDeadline() throws Exception {
        when(scheduleService.updateResponseDeadline(
                        eq(10L), eq(1L), eq(100L), any(ScheduleDeadlineUpdateRequest.class)))
                .thenReturn(response());

        mockMvc.perform(
                        patch(
                                        "/api/groups/{groupId}/schedules/{scheduleId}/response-deadline",
                                        10L,
                                        100L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"responseDeadline\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        verify(scheduleService)
                .updateResponseDeadline(
                        eq(10L), eq(1L), eq(100L), any(ScheduleDeadlineUpdateRequest.class));
    }

    @Test
    void rejectsMissingResponseDeadlineProperty() throws Exception {
        mockMvc.perform(
                        patch(
                                        "/api/groups/{groupId}/schedules/{scheduleId}/response-deadline",
                                        10L,
                                        100L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
        verifyNoInteractions(scheduleService);
    }

    @Test
    void deletesScheduleWithoutResponseBody() throws Exception {
        mockMvc.perform(delete("/api/groups/{groupId}/schedules/{scheduleId}", 10L, 100L))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(scheduleService).delete(10L, principal.id(), 100L);
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

    private static Stream<ErrorCode> updateBusinessErrors() {
        return Stream.of(
                ErrorCode.GROUP_NOT_FOUND,
                ErrorCode.GROUP_ACCESS_DENIED,
                ErrorCode.WITHDRAWN_GROUP_MEMBER,
                ErrorCode.GROUP_ENDED,
                ErrorCode.SCHEDULE_MANAGEMENT_FORBIDDEN,
                ErrorCode.SCHEDULE_NOT_FOUND,
                ErrorCode.SCHEDULE_UPDATE_NOT_ALLOWED,
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

    private static SchedulePageResponse pageResponse() {
        ScheduleSummaryResponse item =
                new ScheduleSummaryResponse(
                        100L,
                        1L,
                        "3주차 스터디",
                        LocalDateTime.of(2026, 7, 25, 19, 0),
                        null,
                        null,
                        LocalDateTime.of(2026, 7, 24, 18, 0));
        return new SchedulePageResponse(List.of(item), 0, 20, 1, 1, false);
    }

    private static Stream<Arguments> invalidListQueries() {
        return Stream.of(
                Arguments.of("all", "0", "20"),
                Arguments.of("upcoming", "-1", "20"),
                Arguments.of("past", "page", "20"),
                Arguments.of("past", "0", "0"),
                Arguments.of("past", "0", "101"));
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
