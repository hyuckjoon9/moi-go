package com.mycom.myapp.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
    WITHDRAWN_MEMBER(HttpStatus.FORBIDDEN, "탈퇴한 회원입니다."),
    GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "그룹을 찾을 수 없습니다."),
    GROUP_ACCESS_DENIED(HttpStatus.FORBIDDEN, "그룹에 접근할 권한이 없습니다."),
    WITHDRAWN_GROUP_MEMBER(HttpStatus.FORBIDDEN, "탈퇴한 그룹원은 그룹에 접근할 수 없습니다."),
    DUPLICATE_ATTENDANCE_ANSWER(HttpStatus.CONFLICT, "이미 참석 여부를 응답했습니다."),
    DUPLICATE_ATTENDANCE_RECORD(HttpStatus.CONFLICT, "이미 출석 체크가 등록되어 있습니다."),
    ATTENDANCE_ANSWER_NOT_FOUND(HttpStatus.NOT_FOUND, "참석 여부 응답을 찾을 수 없습니다."),
    ATTENDANCE_RECORD_NOT_FOUND(HttpStatus.NOT_FOUND, "출석 기록을 찾을 수 없습니다."),
    GROUP_ENDED(HttpStatus.CONFLICT, "종료된 그룹에서는 일정을 관리할 수 없습니다."),
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "일정을 찾을 수 없습니다."),
    SCHEDULE_MANAGEMENT_FORBIDDEN(HttpStatus.FORBIDDEN, "일정을 관리할 권한이 없습니다."),
    SCHEDULE_UPDATE_NOT_ALLOWED(HttpStatus.CONFLICT, "이미 시작된 일정은 수정할 수 없습니다."),
    SCHEDULE_DELETE_NOT_ALLOWED(HttpStatus.CONFLICT, "출석 또는 활동 이력이 있거나 이미 시작된 일정은 삭제할 수 없습니다."),
    SCHEDULE_DEADLINE_UPDATE_NOT_ALLOWED(
            HttpStatus.CONFLICT, "마감되었거나 이미 시작된 일정의 응답 마감은 변경할 수 없습니다."),
    ATTENDANCE_RESPONSE_CLOSED(HttpStatus.CONFLICT, "참석 응답 마감 시간이 지났습니다."),
    INVALID_SCHEDULE_TIME(HttpStatus.BAD_REQUEST, "일정 또는 응답 마감 시간이 올바르지 않습니다."),
    RECRUITMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "모집글을 찾을 수 없습니다."),
    DUPLICATE_APPLICATION(HttpStatus.CONFLICT, "이미 지원한 모집글입니다."),
    SELF_APPLICATION_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "본인이 작성한 모집글에는 지원할 수 없습니다."),
    RECRUITMENT_CLOSED(HttpStatus.BAD_REQUEST, "모집이 마감된 모집글입니다."),
    APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "지원 내역을 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
