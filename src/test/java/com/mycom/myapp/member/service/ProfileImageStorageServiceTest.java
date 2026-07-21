package com.mycom.myapp.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mycom.myapp.global.exception.BusinessException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class ProfileImageStorageServiceTest {

    @TempDir Path tempDir;

    // 프로필 이미지 업로드 성공 테스트
    // 이미지 파일을 저장소에 기록하고 브라우저에서 접근할 수 있는 /uploads/profile-images/... URL을 반환한다.
    @Test
    @DisplayName("프로필 이미지 파일을 저장하고 접근 URL을 반환한다")
    void storesProfileImageAndReturnsUrl() throws Exception {
        ProfileImageStorageService storageService =
                new ProfileImageStorageService(tempDir.toString());
        MockMultipartFile image =
                new MockMultipartFile("file", "profile.png", "image/png", "image-bytes".getBytes());

        String profileImageUrl = storageService.store(image);

        assertThat(profileImageUrl).startsWith("/uploads/profile-images/").endsWith(".png");
        String storedFileName = profileImageUrl.substring(profileImageUrl.lastIndexOf('/') + 1);
        assertThat(Files.exists(tempDir.resolve(storedFileName))).isTrue();
    }

    // 프로필 이미지 업로드 검증 테스트
    // 이미지가 아닌 파일은 users.profile_image_url에 저장할 URL을 만들기 전에 거부한다.
    @Test
    @DisplayName("이미지 파일이 아니면 업로드를 거부한다")
    void rejectsNonImageFile() {
        ProfileImageStorageService storageService =
                new ProfileImageStorageService(tempDir.toString());
        MockMultipartFile textFile =
                new MockMultipartFile("file", "profile.txt", "text/plain", "not-image".getBytes());

        assertThatThrownBy(() -> storageService.store(textFile))
                .isInstanceOf(BusinessException.class);
    }
}
