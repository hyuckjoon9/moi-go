package com.mycom.myapp.study.service;

/** Part4가 그룹 전체 출석률 조회 권한을 판단할 때 사용하는 공개 정책이다. */
public record StudyGroupAttendanceRatePolicy(Long groupId, boolean canViewAllAttendanceRates) {}
