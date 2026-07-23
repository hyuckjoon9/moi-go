package com.mycom.myapp.member.service.port;

import com.mycom.myapp.member.entity.Member;
import com.mycom.myapp.member.entity.MemberStatus;

public interface MemberAdministrationPort {

    Member changeStatus(Long requesterId, Long memberId, MemberStatus status);
}
