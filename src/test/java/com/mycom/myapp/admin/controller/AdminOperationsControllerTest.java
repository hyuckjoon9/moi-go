package com.mycom.myapp.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestParam;

class AdminOperationsControllerTest {

    @Test
    void declaresExplicitRequestParameterNamesForOperationQueries() {
        assertRequestParameters("getGroups", "keyword", "status", "page", "size");
        assertRequestParameters("getSchedules", "keyword", "page", "size");
        assertRequestParameters("getAttendanceRecords", "keyword", "status", "page", "size");
        assertRequestParameters("getActivityRecords", "keyword", "page", "size");
        assertRequestParameters("getAuditLogs", "action", "targetType", "keyword", "page", "size");
    }

    private void assertRequestParameters(String methodName, String... expectedNames) {
        Method method =
                Arrays.stream(AdminOperationsController.class.getMethods())
                        .filter(candidate -> candidate.getName().equals(methodName))
                        .findFirst()
                        .orElseThrow();

        assertThat(method.getParameters())
                .extracting(parameter -> parameter.getAnnotation(RequestParam.class).name())
                .containsExactly(expectedNames);
    }
}
