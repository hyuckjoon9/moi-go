# 출석 체크 수정 안 되는 문제 (QA)

작성일: 2026-07-24
작성: 정자비 (Part4: 출석·활동 기록) → 담당: 프론트엔드
대상 코드: `static/js/app.js` (`#groupCheckAttendanceForm` 제출 핸들러)

## 증상

모집장이 이미 출석 체크된 그룹원의 상태를 다시 저장하려고 하면(정정) `409` 오류(`이미 출석 체크가 등록되어 있습니다`)가 뜨고 저장이 안 된다.

## 원인

백엔드(`AttendanceService.updateAttendance`)는 시간 제한 없이 언제든 정정을 허용하도록 구현되어 있고 테스트도 통과했다 — 백엔드 문제가 아니다.

`app.js:1097` 모집장용 "출석 체크" 폼(`#groupCheckAttendanceForm`) 제출 핸들러가 **항상 `POST`만 호출**하는 게 문제다.

```js
$("#groupCheckAttendanceForm")?.addEventListener("submit", async (event) => {
  event.preventDefault();
  if (!canManageCurrentGroupAttendance()) return;
  const payload = formData(event.currentTarget);
  if (!payload.scheduleId || !payload.userId) { toast("일정과 그룹원을 선택하세요.", true); return; }
  await run(() => window.moiApi.request(`/api/attendance/schedules/${payload.scheduleId}/records`,
    { method: "POST", body: window.moiApi.toJsonBody({ userId: asNumber(payload.userId), status: payload.status }) }),
    "출석을 체크했습니다.");
  await loadGroupAttendanceRates().catch(() => {});
});
```

이미 체크된 사람을 다시 제출해도 `POST`(=`checkAttendance`, 신규 등록 전용)로만 요청이 가서 `409 DUPLICATE_ATTENDANCE_RECORD`가 그대로 노출된다. `PUT`(`updateAttendance`)으로 전환하는 로직이 아예 없다.

## 참고: 이미 구현된 유사 패턴

바로 위(`app.js:1069-1078`) 참석 응답(RSVP) 제출 로직은 동일한 문제를 이미 이렇게 풀어뒀다 — POST 시도 후 실패하면 PUT으로 재시도:

```js
async function submitAttendanceAnswer(scheduleId, response) {
  const body = window.moiApi.toJsonBody({ response });
  try {
    await window.moiApi.request(`/api/attendance/schedules/${scheduleId}/answers`, { method: "POST", body });
    toast("참석 여부를 저장했습니다.");
  } catch (_) {
    await run(() => window.moiApi.request(`/api/attendance/schedules/${scheduleId}/answers`, { method: "PUT", body }), "참석 여부를 수정했습니다.");
  }
}
```

## 제안

`#groupCheckAttendanceForm` 제출 핸들러에도 위와 같은 fallback(POST 실패 시 PUT 재시도) 또는 해당 스케줄의 기존 출석 기록 조회 후 존재 여부에 따라 메서드를 분기하는 로직을 추가하면 해결된다.

---

# 자신이 작성한 리뷰 삭제 기능 누락 (QA)

작성일: 2026-07-24
작성: 정자비 (Part4: 출석·활동 기록) → 담당: 프론트엔드
대상 코드: `static/js/app.js` (`renderActivityReviews`)

## 증상

일반 그룹원이 본인이 작성한 활동 회고 리뷰를 삭제할 방법이 없다. 화면에 삭제 버튼 자체가 안 보인다.

## 원인

백엔드에는 본인 리뷰 삭제용 엔드포인트(`DELETE /api/activity/records/{activityRecordId}/reviews`, `ActivityService.deleteReview`)가 이미 구현되어 있다 — 백엔드 문제가 아니다.

`app.js:958` `renderActivityReviews`에서 리뷰 삭제 버튼이 **모집장(`manager`)일 때만** 렌더링된다.

```js
function renderActivityReviews(reviews = []) {
  const manager = canManageCurrentGroupActivity();
  renderCards("#activityReviewList", reviews, "아직 회고 리뷰가 없습니다.", (review) =>
    `<article class="entity-card activity-review-card" data-review-id="${escapeHtml(review.id)}">
      <div>
        <span class="badge">${escapeHtml(groupMemberLabelForUserId(review.userId))}</span>
        <p>${escapeHtml(review.comment || "내용이 없습니다.")}</p>
        <div class="meta">작성일 ${escapeHtml(formatDate(review.createdAt) || "-")}</div>
      </div>
      ${manager ? `<button class="button ghost small" type="button" data-review-delete="true">리뷰 삭제</button>` : ""}
    </article>`);
  document.querySelectorAll("[data-review-delete]").forEach((button) => {
    button.addEventListener("click", async () => {
      const reviewId = button.closest("[data-review-id]")?.dataset.reviewId;
      if (!currentActivityRecord?.id || !reviewId) return;
      await run(() => window.moiApi.request(`/api/activity/records/${currentActivityRecord.id}/reviews/${reviewId}`, { method: "DELETE" }), "리뷰를 삭제했습니다.");
      await loadActivityReviews();
    });
  });
}
```

이 버튼이 호출하는 것도 모집장 전용 삭제(`DELETE .../reviews/{reviewId}`, `deleteReviewByManager`)뿐이다. 본인 리뷰 삭제(`DELETE .../reviews`, reviewId 없이 본인 것만 지우는 엔드포인트)를 호출하는 버튼/로직이 아예 없다.

## 제안

작성자 본인(`review.userId === 로그인한 사용자 id`)인 카드에는 별도의 "내 리뷰 삭제" 버튼을 추가하고, `DELETE /api/activity/records/{activityRecordId}/reviews`(본인 삭제, reviewId 불필요)를 호출하도록 구현이 필요하다.

---

# 회고(활동 기록) 삭제 버튼 누락 (QA)

작성일: 2026-07-24
작성: 정자비 (Part4: 출석·활동 기록) → 담당: 프론트엔드
대상 코드: `static/group.html` (`.activity-actions`), `static/js/app.js` (`setActivityRecordButtons`, `renderActivityRecord`)

## 증상

회고를 작성한 뒤에도 삭제할 방법이 없다. 화면에는 "회고 작성" / "회고 수정" / "회고 조회" 버튼만 있고 삭제 버튼 자체가 없다.

## 원인

백엔드에는 회고 삭제용 엔드포인트(`DELETE /api/activity/schedules/{scheduleId}/record`, `ActivityController.deleteRecord` → `ActivityService.deleteRecord`)가 이미 구현되어 있다 — 백엔드 문제가 아니다.

`group.html:91` 활동 패널의 버튼 그룹(`.activity-actions`)에 삭제 버튼 자체가 마크업에 없다.

```html
<div class="activity-actions">
  <button id="openActivityRecordCreateButton" class="button small hidden" type="button">회고 작성</button>
  <button id="openActivityRecordEditButton" class="button ghost small hidden" type="button">회고 수정</button>
  <button id="reloadActivityRecordButton" class="button ghost small" type="button">회고 조회</button>
</div>
```

`app.js:921` `setActivityRecordButtons`도 생성/수정 버튼의 표시 여부만 토글할 뿐 삭제 버튼을 다루는 로직이 없다.

```js
function setActivityRecordButtons(scheduleId, record) {
  const manager = canManageCurrentGroupActivity();
  $("#openActivityRecordCreateButton")?.classList.toggle("hidden", !scheduleId || !manager || !!record);
  $("#openActivityRecordEditButton")?.classList.toggle("hidden", !scheduleId || !manager || !record);
}
```

## 제안

`.activity-actions`에 `#deleteActivityRecordButton`(예: "회고 삭제") 버튼을 추가하고, `record`가 있고 관리 권한(`manager`)이 있을 때만 보이도록 `setActivityRecordButtons`에서 토글한다. 클릭 시 `DELETE /api/activity/schedules/{scheduleId}/record` 호출 후 `loadActivityRecord()`로 화면을 갱신하는 핸들러를 추가하면 해결된다.
