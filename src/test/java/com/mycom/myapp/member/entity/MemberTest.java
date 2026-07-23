package com.mycom.myapp.member.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemberTest {

    // Member 생성 성공 테스트:
    // 회원가입에서 전달된 이메일, 암호화된 비밀번호, 닉네임과 프로필 값을 사용해 회원을 생성한다.
    // 새 회원은 기본 시스템 권한 USER와 상태 ACTIVE로 시작해야 한다.
    @Test
    @DisplayName("Member 생성 시 기본 권한 USER와 상태 ACTIVE를 설정한다")
    void createInitializesActiveUser() {
        Member member =
                Member.create(
                        "user@moigo.test", "encoded-password", "모이고", "소개", "Java,Spring", null);

        assertThat(member.getEmail()).isEqualTo("user@moigo.test");
        assertThat(member.getNickname()).isEqualTo("모이고");
        assertThat(member.getRole()).isEqualTo(MemberRole.USER);
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    // Member 필수값 검증 테스트:
    // 이메일, 비밀번호, 닉네임은 회원 계정 생성에 반드시 필요한 값이다.
    // null 또는 공백 값이 들어오면 잘못된 회원 엔티티가 생성되지 않도록 예외를 발생시킨다.
    @Test
    @DisplayName("Member 생성 시 필수값이 없으면 예외를 발생시킨다")
    void createRejectsRequiredFields() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Member.create(null, "password", "닉네임", null, null, null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Member.create("user@moigo.test", " ", "닉네임", null, null, null));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () -> Member.create("user@moigo.test", "password", " ", null, null, null));
    }

    // Member 프로필 수정 테스트:
    // 내 정보 수정 API에서 변경 가능한 닉네임, 자기소개, 관심사, 프로필 이미지 값을 엔티티에 반영한다.
    // 서비스 계층의 중복 검증 이후 실제 상태 변경은 Member 엔티티가 담당한다.
    @Test
    @DisplayName("Member 프로필 수정 시 변경 가능한 필드를 갱신한다")
    void updateProfileChangesEditableFields() {
        Member member =
                Member.create("user@moigo.test", "encoded-password", "모이고", null, null, null);

        member.updateProfile("새닉네임", "새 소개", "JPA", "https://example.com/profile.png");

        assertThat(member.getNickname()).isEqualTo("새닉네임");
        assertThat(member.getBio()).isEqualTo("새 소개");
        assertThat(member.getInterests()).isEqualTo("JPA");
        assertThat(member.getProfileImageUrl()).isEqualTo("https://example.com/profile.png");
    }

    @Test
    @DisplayName("Member 상태를 ACTIVE와 SUSPENDED 사이에서 변경한다")
    void changeStatusUpdatesMemberStatus() {
        Member member =
                Member.create("user@moigo.test", "encoded-password", "모이고", null, null, null);

        member.changeStatus(MemberStatus.SUSPENDED);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.SUSPENDED);
    }
}
