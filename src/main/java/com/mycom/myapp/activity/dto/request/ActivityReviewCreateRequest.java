package com.mycom.myapp.activity.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 활동 기록에 대한 리뷰 작성 시 사용 (activityRecordId는 경로 변수, userId는 인증된 사용자 id에서 채움) */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ActivityReviewCreateRequest {

    private String comment;
}
