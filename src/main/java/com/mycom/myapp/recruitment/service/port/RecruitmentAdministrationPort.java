package com.mycom.myapp.recruitment.service.port;

import com.mycom.myapp.recruitment.entity.RecruitmentPost;
import com.mycom.myapp.recruitment.entity.RecruitmentVisibility;

public interface RecruitmentAdministrationPort {

    RecruitmentPost changeVisibility(Long recruitmentId, RecruitmentVisibility visibility);
}
