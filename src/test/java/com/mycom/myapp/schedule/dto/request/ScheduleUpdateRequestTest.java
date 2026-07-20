package com.mycom.myapp.schedule.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ScheduleUpdateRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final LocalDateTime scheduledAt = LocalDateTime.of(2026, 7, 28, 19, 0);

    @Test
    void normalizesRequiredAndOptionalStrings() {
        ScheduleUpdateRequest request =
                new ScheduleUpdateRequest(
                        "  수정 일정  ", scheduledAt, "   ", " Discord  ", "  내용  ", "");

        assertThat(request.title()).isEqualTo("수정 일정");
        assertThat(request.location()).isNull();
        assertThat(request.onlineLink()).isEqualTo("Discord");
        assertThat(request.content()).isEqualTo("내용");
        assertThat(request.materials()).isNull();
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsBlankTitleAndMissingScheduledAt() {
        ScheduleUpdateRequest request =
                new ScheduleUpdateRequest("   ", null, null, null, null, null);

        assertThat(propertiesOf(validator.validate(request)))
                .containsExactlyInAnyOrder("title", "scheduledAt");
    }

    @Test
    void rejectsValuesBeyondDocumentedLengths() {
        ScheduleUpdateRequest request =
                new ScheduleUpdateRequest(
                        "t".repeat(101),
                        scheduledAt,
                        "l".repeat(256),
                        "o".repeat(501),
                        "c".repeat(5001),
                        "m".repeat(5001));

        assertThat(propertiesOf(validator.validate(request)))
                .containsExactlyInAnyOrder(
                        "title", "location", "onlineLink", "content", "materials");
    }

    private Set<String> propertiesOf(Set<ConstraintViolation<ScheduleUpdateRequest>> violations) {
        return violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
