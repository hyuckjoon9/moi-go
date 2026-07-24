package com.mycom.myapp.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mycom.myapp.global.exception.BusinessException;
import com.mycom.myapp.global.exception.ErrorCode;
import com.mycom.myapp.member.dto.request.MemberCreateRequest;
import com.mycom.myapp.member.dto.request.MemberUpdateRequest;
import com.mycom.myapp.member.dto.response.MemberResponse;
import com.mycom.myapp.member.entity.Member;
import com.mycom.myapp.member.repository.MemberRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class MemberServiceTest {

    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final MemberService memberService =
            new MemberService(memberRepository, passwordEncoder);

    // 회원가입 성공 테스트:
    // 이메일과 닉네임 중복이 없으면 비밀번호를 암호화하고 Member를 저장한다.
    // 응답에는 저장된 회원의 기본 프로필 정보가 내려가야 한다.
    @Test
    @DisplayName("회원가입 성공 시 비밀번호를 암호화하고 회원을 저장한다")
    void createsMemberWithEncodedPassword() {
        MemberCreateRequest request =
                new MemberCreateRequest(
                        "user@moigo.test", "password123", "모이고", "자기소개", "Java,Spring", null);
        when(memberRepository.existsByEmail("user@moigo.test")).thenReturn(false);
        when(memberRepository.existsByNickname("모이고")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(memberRepository.save(any(Member.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MemberResponse response = memberService.create(request);

        assertThat(response.email()).isEqualTo("user@moigo.test");
        assertThat(response.nickname()).isEqualTo("모이고");
        verify(passwordEncoder).encode("password123");
        verify(memberRepository).save(any(Member.class));
    }

    // 회원가입 실패 테스트:
    // 이미 사용 중인 이메일이면 비밀번호 암호화나 저장을 진행하지 않고
    // 중복 이메일 예외(DUPLICATE_EMAIL)를 반환해야 한다.
    @Test
    @DisplayName("중복 이메일이면 회원가입을 거부한다")
    void rejectsDuplicateEmailOnCreate() {
        MemberCreateRequest request =
                new MemberCreateRequest("user@moigo.test", "password123", "모이고", null, null, null);
        when(memberRepository.existsByEmail("user@moigo.test")).thenReturn(true);

        assertError(ErrorCode.DUPLICATE_EMAIL, () -> memberService.create(request));
    }

    // 내 정보 수정 성공 테스트:
    // 인증된 회원이 전달한 필드만 프로필에 반영한다.
    // 닉네임을 변경하는 경우에는 기존 회원과 다른 닉네임인지 확인하고 중복 여부를 검사한다.
    @Test
    @DisplayName("내 정보 수정 시 전달된 프로필 필드만 변경한다")
    void updatesOnlyProvidedProfileFields() {
        Member member =
                Member.create(
                        "user@moigo.test", "encoded-password", "기존닉네임", "기존 소개", "Java", null);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(memberRepository.existsByNickname("새닉네임")).thenReturn(false);

        MemberResponse response =
                memberService.updateMe(
                        1L, new MemberUpdateRequest("새닉네임", "새 소개", "Spring,JPA", null));

        assertThat(response.nickname()).isEqualTo("새닉네임");
        assertThat(response.bio()).isEqualTo("새 소개");
        assertThat(response.interests()).isEqualTo("Spring,JPA");
    }

    // 내 정보 수정 실패 테스트:
    // 새 닉네임이 다른 회원에게 이미 사용 중이면 프로필을 변경하지 않고
    // 중복 닉네임 예외(DUPLICATE_NICKNAME)를 반환해야 한다.
    @Test
    @DisplayName("중복 닉네임이면 내 정보 수정을 거부한다")
    void rejectsDuplicateNicknameOnUpdate() {
        Member member =
                Member.create("user@moigo.test", "encoded-password", "기존닉네임", null, null, null);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(memberRepository.existsByNickname("중복닉네임")).thenReturn(true);

        assertError(
                ErrorCode.DUPLICATE_NICKNAME,
                () ->
                        memberService.updateMe(
                                1L, new MemberUpdateRequest("중복닉네임", null, null, null)));
    }

    // 탈퇴 회원 접근 테스트:
    // 회원 정보가 DB에 존재하더라도 상태가 WITHDRAWN이면 내 정보 조회를 허용하지 않는다.
    // 탈퇴 회원은 인증과 프로필 접근에서 차단되어야 한다.
    @Test
    @DisplayName("탈퇴 회원이면 내 정보 조회를 거부한다")
    void rejectsWithdrawnMemberWhenReadingMe() {
        Member member =
                Member.create("user@moigo.test", "encoded-password", "모이고", null, null, null);
        member.withdraw();
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        assertError(ErrorCode.WITHDRAWN_MEMBER, () -> memberService.getMe(1L));
    }

    // 프로필 이미지 URL 저장 테스트
    // 업로드 API에서 생성한 이미지 URL을 users.profile_image_url에 반영하고 MemberResponse로 반환한다.
    @Test
    @DisplayName("프로필 이미지 URL을 회원 프로필에 저장한다")
    void updatesProfileImageUrl() {
        Member member =
                Member.create("user@moigo.test", "encoded-password", "기존닉네임", null, null, null);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        MemberResponse response =
                memberService.updateProfileImage(1L, "/uploads/profile-images/profile.png");

        assertThat(response.profileImageUrl()).isEqualTo("/uploads/profile-images/profile.png");
    }

    private void assertError(
            ErrorCode errorCode, org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
