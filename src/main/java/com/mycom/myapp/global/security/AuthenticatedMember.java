package com.mycom.myapp.global.security;

import com.mycom.myapp.member.entity.MemberRole;

public record AuthenticatedMember(Long id, String email, MemberRole role) {}
