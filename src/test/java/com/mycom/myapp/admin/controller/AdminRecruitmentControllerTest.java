package com.mycom.myapp.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

class AdminRecruitmentControllerTest {

    @Test
    void declaresRecruitmentAdministrationBasePathAndOperations() {
        Class<?> controllerClass =
                org.assertj.core.api.Assertions.catchThrowableOfType(
                                        () ->
                                                Class.forName(
                                                        "com.mycom.myapp.admin.controller.AdminRecruitmentController"),
                                        ClassNotFoundException.class)
                                == null
                        ? loadController()
                        : null;

        assertThat(controllerClass).isNotNull();
        assertThat(controllerClass.getAnnotation(RequestMapping.class).value())
                .containsExactly("/api/admin/recruitments");
        assertThat(Arrays.stream(controllerClass.getMethods()).map(method -> method.getName()))
                .contains("getRecruitments", "getRecruitment", "changeVisibility");
    }

    private Class<?> loadController() {
        try {
            return Class.forName("com.mycom.myapp.admin.controller.AdminRecruitmentController");
        } catch (ClassNotFoundException exception) {
            throw new AssertionError(exception);
        }
    }
}
