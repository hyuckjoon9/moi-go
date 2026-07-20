package com.mycom.myapp.study.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CreateStudyGroupCommandTest {

    @Test
    void normalizesNameAndDefensivelyCopiesApprovedUsers() {
        List<Long> approvedUserIds = new ArrayList<>(List.of(20L, 30L));

        CreateStudyGroupCommand command =
                new CreateStudyGroupCommand(10L, "  알고리즘 스터디  ", 1L, approvedUserIds);
        approvedUserIds.add(40L);

        assertThat(command.postId()).isEqualTo(10L);
        assertThat(command.groupName()).isEqualTo("알고리즘 스터디");
        assertThat(command.leaderUserId()).isEqualTo(1L);
        assertThat(command.approvedUserIds()).containsExactly(20L, 30L);
    }

    @Test
    void acceptsEmptyApprovedUserList() {
        CreateStudyGroupCommand command =
                new CreateStudyGroupCommand(10L, "알고리즘 스터디", 1L, List.of());

        assertThat(command.approvedUserIds()).isEmpty();
    }

    @Test
    void rejectsMissingRequiredValues() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CreateStudyGroupCommand(null, "알고리즘 스터디", 1L, List.of()));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CreateStudyGroupCommand(10L, " ", 1L, List.of()));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CreateStudyGroupCommand(10L, "알고리즘 스터디", null, List.of()));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CreateStudyGroupCommand(10L, "알고리즘 스터디", 1L, null));
    }

    @Test
    void rejectsNullApprovedUserId() {
        List<Long> approvedUserIds = new ArrayList<>();
        approvedUserIds.add(20L);
        approvedUserIds.add(null);

        assertThatIllegalArgumentException()
                .isThrownBy(
                        () -> new CreateStudyGroupCommand(10L, "알고리즘 스터디", 1L, approvedUserIds));
    }
}
