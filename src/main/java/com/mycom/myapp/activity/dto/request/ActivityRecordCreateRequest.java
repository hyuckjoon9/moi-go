package com.mycom.myapp.activity.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 활동 기록 생성/수정 시 사용 (scheduleId는 경로 변수, authorId는 인증된 사용자 id에서 채움) */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ActivityRecordCreateRequest {

    private String topic;

    private String content;

    private String assignment;

    private String nextPreparation;

    private String referenceLinks;
}
