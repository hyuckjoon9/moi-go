package com.mycom.myapp.member.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class MemberTest {

    @Test
    void createInitializesActiveUser() {
        Member member =
                Member.create(
                        "user@moigo.test", "encoded-password", "모이고", "소개", "Java,Spring", null);

        assertThat(member.getEmail()).isEqualTo("user@moigo.test");
        assertThat(member.getNickname()).isEqualTo("모이고");
        assertThat(member.getRole()).isEqualTo(MemberRole.USER);
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    void createRejectsRequiredFields() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Member.create(null, "password", "닉네임", null, null, null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Member.create("user@moigo.test", " ", "닉네임", null, null, null));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () -> Member.create("user@moigo.test", "password", " ", null, null, null));
    }

    @Test
    void updateProfileChangesEditableFields() {
        Member member =
                Member.create("user@moigo.test", "encoded-password", "모이고", null, null, null);

        member.updateProfile("새닉네임", "새 소개", "JPA", "https://example.com/profile.png");

        assertThat(member.getNickname()).isEqualTo("새닉네임");
        assertThat(member.getBio()).isEqualTo("새 소개");
        assertThat(member.getInterests()).isEqualTo("JPA");
        assertThat(member.getProfileImageUrl()).isEqualTo("https://example.com/profile.png");
    }
}
