package com.mycom.myapp.activity.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ActivityRecordTest {

    @Test
    void builderInitializesFieldsAndTimestamps() {
        ActivityRecord record =
                ActivityRecord.builder()
                        .scheduleId(10L)
                        .authorId(1L)
                        .topic("토픽")
                        .content("내용")
                        .assignment("과제")
                        .nextPreparation("다음 준비물")
                        .referenceLinks("참고 링크")
                        .build();

        assertThat(record.getScheduleId()).isEqualTo(10L);
        assertThat(record.getAuthorId()).isEqualTo(1L);
        assertThat(record.getTopic()).isEqualTo("토픽");
        assertThat(record.getContent()).isEqualTo("내용");
        assertThat(record.getAssignment()).isEqualTo("과제");
        assertThat(record.getNextPreparation()).isEqualTo("다음 준비물");
        assertThat(record.getReferenceLinks()).isEqualTo("참고 링크");
        assertThat(record.getCreatedAt()).isNotNull();
        assertThat(record.getUpdatedAt()).isEqualTo(record.getCreatedAt());
    }

    @Test
    void updateChangesFieldsAndUpdatedAt() throws InterruptedException {
        ActivityRecord record =
                ActivityRecord.builder()
                        .scheduleId(10L)
                        .authorId(1L)
                        .topic("토픽")
                        .content("내용")
                        .build();
        var firstUpdatedAt = record.getUpdatedAt();
        Thread.sleep(1);

        record.update("수정된 토픽", "수정된 내용", "수정된 과제", "수정된 준비물", "수정된 링크");

        assertThat(record.getTopic()).isEqualTo("수정된 토픽");
        assertThat(record.getContent()).isEqualTo("수정된 내용");
        assertThat(record.getAssignment()).isEqualTo("수정된 과제");
        assertThat(record.getNextPreparation()).isEqualTo("수정된 준비물");
        assertThat(record.getReferenceLinks()).isEqualTo("수정된 링크");
        assertThat(record.getUpdatedAt()).isAfter(firstUpdatedAt);
    }
}
