package com.mycom.myapp.activity.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
