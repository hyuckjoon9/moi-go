package com.mycom.myapp.activity.dto.response;

import com.mycom.myapp.activity.entity.ActivityRecord;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 활동 기록 생성/수정/조회 결과 */
@Getter
@Builder
@AllArgsConstructor
public class ActivityRecordResponse {

    private Long id;
    private Long scheduleId;
    private Long authorId;
    private String topic;
    private String content;
    private String assignment;
    private String nextPreparation;
    private String referenceLinks;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ActivityRecordResponse of(ActivityRecord record) {
        return ActivityRecordResponse.builder()
                .id(record.getId())
                .scheduleId(record.getScheduleId())
                .authorId(record.getAuthorId())
                .topic(record.getTopic())
                .content(record.getContent())
                .assignment(record.getAssignment())
                .nextPreparation(record.getNextPreparation())
                .referenceLinks(record.getReferenceLinks())
                .createdAt(record.getCreatedAt())
                .updatedAt(record.getUpdatedAt())
                .build();
    }
}
