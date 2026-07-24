package com.mycom.myapp.study.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mycom.myapp.global.exception.ErrorCode;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class StudyGroupHomeContractTest {

    @Test
    void exposesTheGroupHomeServiceMethodAndAgreedErrorCodes() {
        assertThat(
                        Arrays.stream(StudyGroupService.class.getDeclaredMethods())
                                .map(method -> method.getName())
                                .toList())
                .contains("getHome");
        assertThat(ErrorCode.valueOf("GROUP_NOT_FOUND").getMessage()).isEqualTo("그룹을 찾을 수 없습니다.");
        assertThat(ErrorCode.valueOf("GROUP_ACCESS_DENIED").getMessage())
                .isEqualTo("그룹에 접근할 권한이 없습니다.");
        assertThat(ErrorCode.valueOf("WITHDRAWN_GROUP_MEMBER").getMessage())
                .isEqualTo("탈퇴한 그룹원은 그룹에 접근할 수 없습니다.");
    }
}
