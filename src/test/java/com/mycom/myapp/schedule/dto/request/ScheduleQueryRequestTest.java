package com.mycom.myapp.schedule.dto.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ScheduleQueryRequestTest {

    @Test
    void parsesSupportedScopeAndPageValues() {
        ScheduleQueryRequest request = ScheduleQueryRequest.from("past", "2", "30");

        assertThat(request.scope()).isEqualTo(ScheduleScope.PAST);
        assertThat(request.page()).isEqualTo(2);
        assertThat(request.size()).isEqualTo(30);
    }

    @ParameterizedTest
    @MethodSource("invalidQueries")
    void rejectsInvalidQuery(String scope, String page, String size) {
        assertThatThrownBy(() -> ScheduleQueryRequest.from(scope, page, size))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    private static Stream<Arguments> invalidQueries() {
        return Stream.of(
                Arguments.of("all", "0", "20"),
                Arguments.of("upcoming", "-1", "20"),
                Arguments.of("past", "zero", "20"),
                Arguments.of("past", "0", "0"),
                Arguments.of("past", "0", "101"));
    }
}
