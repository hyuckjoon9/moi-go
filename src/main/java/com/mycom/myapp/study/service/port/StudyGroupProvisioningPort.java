package com.mycom.myapp.study.service.port;

import com.mycom.myapp.study.service.AddStudyGroupMemberCommand;
import com.mycom.myapp.study.service.CreateStudyGroupCommand;

public interface StudyGroupProvisioningPort {

    Long createGroup(CreateStudyGroupCommand command);

    Long addMember(AddStudyGroupMemberCommand command);

    Long endGroup(Long postId);
}
