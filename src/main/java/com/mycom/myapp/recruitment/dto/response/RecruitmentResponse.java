package com.mycom.myapp.recruitment.dto.response;

import com.mycom.myapp.recruitment.entity.RecruitmentPost;

public record RecruitmentResponse(Long id, String title, String category, String status) {
    public static RecruitmentResponse from(RecruitmentPost post) {
        return new RecruitmentResponse(
                post.getId(), post.getTitle(), post.getCategory(), post.getStatus().name());
    }
}
