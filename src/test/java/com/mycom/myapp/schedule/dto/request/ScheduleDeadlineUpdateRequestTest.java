package com.mycom.myapp.schedule.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ScheduleDeadlineUpdateRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void explicitNullCountsAsProvidedDeadline() {
        ScheduleDeadlineUpdateRequest request = new ScheduleDeadlineUpdateRequest();
        request.setResponseDeadline(null);

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.responseDeadline()).isNull();
    }

    @Test
    void missingPropertyFailsValidation() {
        ScheduleDeadlineUpdateRequest request = new ScheduleDeadlineUpdateRequest();

        assertThat(messagesOf(validator.validate(request)))
                .containsExactly("responseDeadline 필드는 필수입니다.");
    }

    private Set<String> messagesOf(
            Set<ConstraintViolation<ScheduleDeadlineUpdateRequest>> violations) {
        return violations.stream()
                .map(ConstraintViolation::getMessage)
                .collect(java.util.stream.Collectors.toSet());
    }
}
