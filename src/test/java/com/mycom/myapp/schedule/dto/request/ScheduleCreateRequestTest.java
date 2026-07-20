package com.mycom.myapp.schedule.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ScheduleCreateRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final LocalDateTime scheduledAt = LocalDateTime.of(2026, 7, 25, 19, 0);

    @Test
    void normalizesRequiredAndOptionalStrings() {
        ScheduleCreateRequest request =
                new ScheduleCreateRequest(
                        "  3주차 스터디  ", scheduledAt, "   ", " Zoom 123 ", "  내용  ", "", null);

        assertThat(request.title()).isEqualTo("3주차 스터디");
        assertThat(request.location()).isNull();
        assertThat(request.onlineLink()).isEqualTo("Zoom 123");
        assertThat(request.content()).isEqualTo("내용");
        assertThat(request.materials()).isNull();
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsBlankTitleAndMissingScheduledAt() {
        ScheduleCreateRequest request =
                new ScheduleCreateRequest("   ", null, null, null, null, null, null);

        assertThat(propertiesOf(validator.validate(request)))
                .containsExactlyInAnyOrder("title", "scheduledAt");
    }

    @Test
    void rejectsValuesBeyondDocumentedLengths() {
        ScheduleCreateRequest request =
                new ScheduleCreateRequest(
                        "t".repeat(101),
                        scheduledAt,
                        "l".repeat(256),
                        "o".repeat(501),
                        "c".repeat(5001),
                        "m".repeat(5001),
                        null);

        assertThat(propertiesOf(validator.validate(request)))
                .containsExactlyInAnyOrder(
                        "title", "location", "onlineLink", "content", "materials");
    }

    private Set<String> propertiesOf(Set<ConstraintViolation<ScheduleCreateRequest>> violations) {
        return violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }
}
