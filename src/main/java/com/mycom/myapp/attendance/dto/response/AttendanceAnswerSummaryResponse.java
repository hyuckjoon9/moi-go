package com.mycom.myapp.attendance.dto.response;

import com.mycom.myapp.attendance.entity.AttendanceAnswer;
import com.mycom.myapp.attendance.entity.AttendanceResponse;
import com.mycom.myapp.study.entity.GroupMember;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 모집장이 보는 스케줄의 그룹원별 참석 여부 응답(RSVP) 현황. 그룹원 전원을 기준으로 하며, 미응답 멤버는 response를 UNDECIDED로 채운다. */
@Getter
@Builder
@AllArgsConstructor
public class AttendanceAnswerSummaryResponse {

    private Long scheduleId;
    private int totalMemberCount;
    private int attendCount;
    private int absentCount;
    private int undecidedCount;
    private List<MemberAnswer> members;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class MemberAnswer {
        private Long userId;
        private AttendanceResponse response;
        private LocalDateTime respondedAt;

        public static MemberAnswer of(Long userId, AttendanceAnswer answer) {
            return MemberAnswer.builder()
                    .userId(userId)
                    .response(answer == null ? AttendanceResponse.UNDECIDED : answer.getResponse())
                    .respondedAt(answer == null ? null : answer.getRespondedAt())
                    .build();
        }
    }

    public static AttendanceAnswerSummaryResponse of(
            Long scheduleId, List<GroupMember> groupMembers, List<AttendanceAnswer> answers) {
        Map<Long, AttendanceAnswer> answersByUserId =
                answers.stream()
                        .collect(
                                Collectors.toMap(AttendanceAnswer::getUserId, Function.identity()));

        List<MemberAnswer> members =
                groupMembers.stream()
                        .map(
                                member ->
                                        MemberAnswer.of(
                                                member.getUserId(),
                                                answersByUserId.get(member.getUserId())))
                        .toList();

        return AttendanceAnswerSummaryResponse.builder()
                .scheduleId(scheduleId)
                .totalMemberCount(members.size())
                .attendCount(countByResponse(members, AttendanceResponse.ATTEND))
                .absentCount(countByResponse(members, AttendanceResponse.ABSENT))
                .undecidedCount(countByResponse(members, AttendanceResponse.UNDECIDED))
                .members(members)
                .build();
    }

    private static int countByResponse(List<MemberAnswer> members, AttendanceResponse response) {
        return (int) members.stream().filter(member -> member.getResponse() == response).count();
    }
}
