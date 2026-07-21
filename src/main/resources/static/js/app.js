(function () {
  const $ = (selector) => document.querySelector(selector);
  const page = document.body.dataset.page;
  const protectedPage = document.body.dataset.protected === "true";
  const recruitmentState = { page: 0, size: 10, totalPages: 1 };
  let toastTimer;

  function formData(form) { return Object.fromEntries(new FormData(form).entries()); }
  function compact(payload) {
    return Object.fromEntries(Object.entries(payload).map(([k, v]) => [k, v === "" ? null : v]).filter(([, v]) => v !== null));
  }
  function asNumber(value) { return value === "" || value === null || value === undefined ? null : Number(value); }
  function escapeHtml(value) {
    return String(value ?? "").replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#039;");
  }
  function loginUrl() { return `/login.html?returnUrl=${encodeURIComponent(window.location.pathname)}`; }
  function safeReturnUrl(value) {
    if (!value || !value.startsWith("/") || value.startsWith("//")) return "/index.html";
    if (value === "/login.html" || value === "/signup.html") return "/index.html";
    return value;
  }
  function redirectIfAlreadySignedIn() {
    if ((page === "login" || page === "signup") && window.moiAuth.isSignedIn()) {
      const params = new URLSearchParams(window.location.search);
      window.location.replace(safeReturnUrl(params.get("returnUrl")));
      return true;
    }
    return false;
  }
  function requireLogin() {
    if (window.moiAuth.isSignedIn()) return true;
    window.location.href = loginUrl();
    return false;
  }
  function bindProtectedLinks() {
    document.querySelectorAll("[data-protected-link]").forEach((link) => {
      link.addEventListener("click", (event) => {
        if (window.moiAuth.isSignedIn()) return;
        event.preventDefault();
        window.location.href = `/login.html?returnUrl=${encodeURIComponent(link.getAttribute("href"))}`;
      });
    });
  }
  function toast(message, isError = false) {
    let element = $("#toast");
    if (!element) {
      element = document.createElement("div");
      element.id = "toast";
      element.className = "toast";
      document.body.appendChild(element);
    }
    element.textContent = message;
    element.classList.toggle("error", isError);
    element.classList.add("visible");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => element.classList.remove("visible"), 3200);
  }
  async function run(action, successMessage) {
    try {
      const result = await action();
      if (successMessage) toast(successMessage);
      refreshAuthChip();
      return result;
    } catch (error) {
      toast(error.message || "요청에 실패했습니다.", true);
      throw error;
    }
  }
  function showPanel(selector) { $(selector)?.classList.remove("hidden"); }
  function hidePanel(selector) { $(selector)?.classList.add("hidden"); }
  function setField(form, name, value) { if (form?.elements?.[name]) form.elements[name].value = value ?? ""; }
  function updateHomeForAuth() { $("#guestHeroActions")?.classList.toggle("hidden", window.moiAuth.isSignedIn()); }

  function memberInitial(member) {
    return (member?.nickname || member?.email || localStorage.getItem("memberNickname") || "M").trim().slice(0, 1).toUpperCase();
  }
  function memberImageSource(member) {
    return member?.profileImageUrl || localStorage.getItem("memberProfileImageUrl") || localStorage.getItem("memberProfilePreview") || "";
  }
  function renderAvatar(selector, member) {
    const target = $(selector);
    if (!target) return;
    const src = memberImageSource(member);
    if (src) {
      target.innerHTML = `<img src="${escapeHtml(src)}" alt="프로필 사진" />`;
      target.classList.add("has-image");
    } else {
      target.textContent = memberInitial(member);
      target.classList.remove("has-image");
    }
  }
  function cacheMemberIdentity(member) {
    if (member?.nickname) localStorage.setItem("memberNickname", member.nickname);
    if (member?.profileImageUrl) localStorage.setItem("memberProfileImageUrl", member.profileImageUrl);
  }
  function ensureHeaderAvatar() {
    let avatar = $("#headerProfileAvatar");
    if (avatar) return avatar;
    const chip = $("#authStatus");
    if (!chip) return null;
    avatar = document.createElement("span");
    avatar.id = "headerProfileAvatar";
    avatar.className = "header-avatar hidden";
    chip.before(avatar);
    return avatar;
  }
  function setHeaderAvatar(member) {
    const avatar = ensureHeaderAvatar();
    if (!avatar) return;
    const signedIn = window.moiAuth.isSignedIn();
    avatar.classList.toggle("hidden", !signedIn);
    if (!signedIn) {
      avatar.innerHTML = "";
      return;
    }
    const src = memberImageSource(member);
    avatar.innerHTML = src ? `<img src="${escapeHtml(src)}" alt="프로필 사진" />` : `<span>${escapeHtml(memberInitial(member))}</span>`;
  }
  function updateMemberImagePreview(value) {
    renderAvatar("#profileImagePreview", { profileImageUrl: value || localStorage.getItem("memberProfilePreview"), nickname: $("#profileNickname")?.textContent });
  }
  async function uploadSelectedProfileImage() {
    const input = $("#profileImageFileInput");
    const file = input?.files?.[0];
    if (!file) return null;
    const body = new FormData();
    body.append("file", file);
    const member = await window.moiApi.request("/api/members/me/profile-image", { method: "POST", body });
    if (member?.profileImageUrl) {
      localStorage.setItem("memberProfileImageUrl", member.profileImageUrl);
      localStorage.removeItem("memberProfilePreview");
      setField($("#updateMemberForm"), "profileImageUrl", member.profileImageUrl);
    }
    return member;
  }

  async function loadCurrentMemberForHeader() {
    if (!window.moiAuth.isSignedIn()) return null;
    const cachedNickname = localStorage.getItem("memberNickname");
    if (cachedNickname) return { nickname: cachedNickname, profileImageUrl: localStorage.getItem("memberProfileImageUrl") };
    try {
      const member = await window.moiApi.request("/api/members/me");
      cacheMemberIdentity(member);
      return member;
    } catch (_) { return null; }
  }
  function setHeaderGreeting(member) {
    const chip = $("#authStatus");
    if (!chip || !window.moiAuth.isSignedIn()) return;
    cacheMemberIdentity(member);
    setHeaderAvatar(member);
    const nickname = member?.nickname || localStorage.getItem("memberNickname") || "회원";
    chip.textContent = `${nickname}님 반갑습니다.`;
    chip.classList.add("signed-in");
  }
  function refreshAuthChip() {
    const chip = $("#authStatus");
    const authLink = $("#headerAuthLink");
    const signedIn = window.moiAuth.isSignedIn();
    if (chip) {
      chip.textContent = signedIn ? `${localStorage.getItem("memberNickname") || "회원"}님 반갑습니다.` : "로그인 필요";
      chip.classList.toggle("signed-in", signedIn);
      setHeaderAvatar(signedIn ? null : null);
    }
    if (authLink) {
      authLink.textContent = signedIn ? "로그아웃" : "로그인";
      authLink.href = signedIn ? "#logout" : "/login.html";
      authLink.onclick = signedIn ? async (event) => {
        event.preventDefault();
        await run(() => window.moiAuth.logout(), "로그아웃되었습니다.");
        window.location.href = "/index.html";
      } : null;
    }
  }

  function json(target, value) { const element = $(target); if (element) element.textContent = JSON.stringify(value, null, 2); }
  function renderCards(target, items, emptyText, renderer) {
    const element = $(target);
    if (!element) return;
    element.classList.remove("empty-state");
    if (!items || items.length === 0) {
      element.innerHTML = `<div class="entity-card meta">${emptyText}</div>`;
      return;
    }
    element.innerHTML = items.map(renderer).join("");
  }

  function formatDate(value) { return value ? String(value).slice(0, 10) : "-"; }
  function getPageContent(pageData) { return Array.isArray(pageData) ? pageData : (pageData?.content || []); }
  function updateRecruitmentUrl(pageNo) {
    const params = new URLSearchParams(window.location.search);
    params.delete("id");
    if (pageNo > 0) params.set("page", String(pageNo)); else params.delete("page");
    const category = $("#recruitmentCategory")?.value.trim();
    if (category) params.set("category", category); else params.delete("category");
    const query = params.toString();
    window.history.replaceState(null, "", `/recruitments.html${query ? `?${query}` : ""}`);
  }
  function buildRecruitmentQuery() {
    const params = new URLSearchParams({ page: String(recruitmentState.page), size: String(recruitmentState.size), sort: "id,desc" });
    const category = $("#recruitmentCategory")?.value.trim();
    if (category) params.set("category", category);
    return params.toString();
  }
  function showRecruitmentListMode() {
    showPanel("#recruitmentListSection");
    hidePanel("#recruitmentDetailSection");
    hidePanel("#backToRecruitmentList");
    if ($("#recruitmentPageTitle")) $("#recruitmentPageTitle").textContent = "모집글";
    if ($("#recruitmentPageDescription")) $("#recruitmentPageDescription").textContent = "최신 모집글을 확인하고 스터디 참여 흐름을 시작합니다.";
  }
  function showRecruitmentDetailMode() {
    hidePanel("#recruitmentListSection");
    showPanel("#recruitmentDetailSection");
    showPanel("#backToRecruitmentList");
    if ($("#recruitmentPageTitle")) $("#recruitmentPageTitle").textContent = "모집글 상세";
    if ($("#recruitmentPageDescription")) $("#recruitmentPageDescription").textContent = "선택한 모집글의 정보를 확인합니다.";
  }
  function renderRecruitmentPagination(pageData) {
    const target = $("#recruitmentPagination");
    if (!target) return;
    const totalPages = Math.max(1, pageData?.totalPages || 1);
    const current = pageData?.number ?? recruitmentState.page;
    const pages = Array.from({ length: totalPages }, (_, index) => index).slice(Math.max(0, current - 4), Math.min(totalPages, current + 5));
    target.innerHTML = `
      <button class="button ghost small" type="button" data-page="${Math.max(0, current - 1)}" ${current <= 0 ? "disabled" : ""}>이전</button>
      ${pages.map((pageNo) => `<button class="page-number ${pageNo === current ? "active" : ""}" type="button" data-page="${pageNo}">${pageNo + 1}</button>`).join("")}
      <button class="button ghost small" type="button" data-page="${Math.min(totalPages - 1, current + 1)}" ${current >= totalPages - 1 ? "disabled" : ""}>다음</button>`;
    target.querySelectorAll("[data-page]").forEach((button) => button.addEventListener("click", () => loadRecruitments(Number(button.dataset.page))));
  }
  async function loadRecruitments(pageNo = recruitmentState.page) {
    recruitmentState.page = Math.max(0, pageNo || 0);
    showRecruitmentListMode();
    updateRecruitmentUrl(recruitmentState.page);
    const pageData = await run(() => window.moiApi.request(`/api/recruitment-posts?${buildRecruitmentQuery()}`), null);
    const items = getPageContent(pageData);
    renderCards("#recruitmentList", items, "표시할 모집글이 없습니다.", (item) => `
      <a class="entity-card recruitment-row" href="/recruitments.html?id=${encodeURIComponent(item.id)}">
        <div class="recruitment-row-main"><span class="badge">${escapeHtml(item.status || "RECRUITING")}</span><strong>${escapeHtml(item.title)}</strong><div class="meta">${escapeHtml(item.category || "카테고리 없음")} · 모집글 #${escapeHtml(item.id)}</div></div>
        <span class="row-arrow">상세 보기</span>
      </a>`);
    renderRecruitmentPagination(pageData);
  }
  function detailItem(label, value) {
    if (value === undefined || value === null || value === "") return "";
    return `<div><dt>${escapeHtml(label)}</dt><dd>${escapeHtml(value)}</dd></div>`;
  }
  async function loadRecruitmentDetail(id, showToast = false) {
    showRecruitmentDetailMode();
    const detail = await run(() => window.moiApi.request(`/api/recruitment-posts/${id}`), showToast ? "모집글 상세를 조회했습니다." : null);
    const panel = $("#recruitmentDetailPanel");
    const view = $("#recruitmentDetailView");
    if (!panel || !view) return;
    panel.querySelector("h2").textContent = detail.title || "모집글 상세";
    panel.querySelector(".muted").textContent = `${detail.category || "카테고리 없음"} · ${detail.status || "상태 미정"}`;
    view.innerHTML = `<dl class="info-list recruitment-detail-list">
      ${detailItem("모집글 ID", detail.id)}${detailItem("카테고리", detail.category)}${detailItem("상태", detail.status)}${detailItem("모임 방식", detail.meetingType)}${detailItem("활동 지역", detail.location)}${detailItem("온라인 링크", detail.onlineLink)}${detailItem("정기 모임 요일", detail.meetingDay)}${detailItem("모집 인원", detail.capacity)}${detailItem("모집 마감일", formatDate(detail.recruitmentDeadline))}${detailItem("예상 활동 기간", detail.expectedDuration)}${detailItem("소개", detail.description)}${detailItem("목표", detail.goal)}${detailItem("진행 방식", detail.method)}${detailItem("참가 조건", detail.conditions)}
      </dl>${detail.description ? "" : `<p class="helper-text">현재 백엔드 응답 DTO가 id, title, category, status만 반환해서 상세 설명 필드는 아직 표시되지 않습니다.</p>`}`;
  }

  function openMemberEditModal() { showPanel("#memberEditBackdrop"); showPanel("#memberEditPanel"); document.body.classList.add("modal-open"); setTimeout(() => $("#updateMemberForm input[name='nickname']")?.focus(), 0); }
  function closeMemberEditModal() { hidePanel("#memberEditBackdrop"); hidePanel("#memberEditPanel"); document.body.classList.remove("modal-open"); }
  function openRecruitmentCreateModal() { showPanel("#recruitmentCreateBackdrop"); showPanel("#recruitmentCreatePanel"); document.body.classList.add("modal-open"); }
  function closeRecruitmentCreateModal() { hidePanel("#recruitmentCreateBackdrop"); hidePanel("#recruitmentCreatePanel"); document.body.classList.remove("modal-open"); }

  function bindLogin() {
    $("#loginForm")?.addEventListener("submit", async (event) => {
      event.preventDefault();
      await run(() => window.moiAuth.login(compact(formData(event.currentTarget))), "로그인되었습니다.");
      const member = await loadCurrentMemberForHeader();
      setHeaderGreeting(member);
      const params = new URLSearchParams(window.location.search);
      window.location.href = safeReturnUrl(params.get("returnUrl"));
    });
  }
  function bindSignup() {
    $("#signupForm")?.addEventListener("submit", async (event) => {
      event.preventDefault();
      await run(() => window.moiAuth.signup(compact(formData(event.currentTarget))), "회원가입이 완료되었습니다.");
      window.location.href = "/login.html";
    });
  }

  function renderMemberProfile(member) {
    renderAvatar("#profileInitial", member);
    if ($("#profileNickname")) $("#profileNickname").textContent = member.nickname || "닉네임 없음";
    if ($("#profileEmail")) $("#profileEmail").textContent = member.email || "이메일 없음";
    const details = $("#profileDetails");
    if (details) {
      details.innerHTML = `<div><span>자기소개</span><strong>${escapeHtml(member.bio || "등록된 자기소개가 없습니다.")}</strong></div><div><span>관심사</span><strong>${escapeHtml(member.interests || "등록된 관심사가 없습니다.")}</strong></div>`;
    }
  }
  function readSavedGroups() { try { return JSON.parse(localStorage.getItem("myGroups") || "[]"); } catch (_) { return []; } }
  function writeSavedGroups(groups) { localStorage.setItem("myGroups", JSON.stringify(groups)); }
  function saveMyGroup(group) {
    if (!group?.groupId) return;
    const groups = readSavedGroups().filter((item) => String(item.groupId) !== String(group.groupId));
    groups.unshift({ groupId: group.groupId, name: group.name || `그룹 ${group.groupId}`, postId: group.postId || null, savedAt: new Date().toISOString() });
    writeSavedGroups(groups.slice(0, 12));
  }
  function renderMyGroups() {
    const target = $("#myGroupList");
    if (!target) return;
    const groups = readSavedGroups();
    if (!groups.length) {
      target.innerHTML = `<div class="entity-card meta">아직 표시할 그룹이 없습니다. 그룹 홈에서 그룹을 조회하거나 아래에서 그룹 ID를 추가하세요.</div>`;
      return;
    }
    target.innerHTML = groups.map((group) => `<a class="entity-card my-group-link" href="/group.html?groupId=${encodeURIComponent(group.groupId)}"><strong>${escapeHtml(group.name || `그룹 ${group.groupId}`)}</strong><div class="meta">그룹 ID ${escapeHtml(group.groupId)}${group.postId ? ` · 모집글 ID ${escapeHtml(group.postId)}` : ""}</div></a>`).join("");
  }
  async function addMyGroupById(groupId) {
    const group = await run(() => window.moiApi.request(`/api/groups/${groupId}`), "내 그룹에 추가했습니다.");
    saveMyGroup(group);
    renderMyGroups();
  }
  function populateMemberForm(member) {
    const form = $("#updateMemberForm");
    setField(form, "nickname", member.nickname);
    setField(form, "bio", member.bio);
    setField(form, "interests", member.interests);
    setField(form, "profileImageUrl", member.profileImageUrl);
    updateMemberImagePreview(member.profileImageUrl || "");
  }
  async function loadMyProfile(showToast = false) {
    const member = await run(() => window.moiApi.request("/api/members/me"), showToast ? "내 정보를 조회했습니다." : null);
    cacheMemberIdentity(member);
    setHeaderGreeting(member);
    renderMemberProfile(member);
    populateMemberForm(member);
    return member;
  }
  function bindMypage() {
    loadMyProfile().catch(() => {});
    renderMyGroups();
    $("#openEditMemberButton")?.addEventListener("click", openMemberEditModal);
    $("#closeEditMemberButton")?.addEventListener("click", closeMemberEditModal);
    $("#memberEditBackdrop")?.addEventListener("click", closeMemberEditModal);
    document.addEventListener("keydown", (event) => { if (event.key === "Escape") closeMemberEditModal(); });
    $("#profileImageFileInput")?.addEventListener("change", (event) => {
      const file = event.currentTarget.files?.[0];
      if (!file) return;
      const reader = new FileReader();
      reader.onload = () => {
        localStorage.setItem("memberProfilePreview", String(reader.result));
        updateMemberImagePreview("");
        setHeaderAvatar({ profileImageUrl: String(reader.result) });
      };
      reader.readAsDataURL(file);
    });
    $("#addMyGroupForm")?.addEventListener("submit", async (event) => {
      event.preventDefault();
      const groupId = formData(event.currentTarget).groupId;
      if (!groupId) return;
      await addMyGroupById(groupId);
      event.currentTarget.reset();
    });
    $("#updateMemberForm")?.addEventListener("submit", async (event) => {
      event.preventDefault();
      await uploadSelectedProfileImage();
      const member = await run(() => window.moiApi.request("/api/members/me", { method: "PATCH", body: window.moiApi.toJsonBody(compact(formData(event.currentTarget))) }), "내 정보를 수정했습니다.");
      cacheMemberIdentity(member);
      setHeaderGreeting(member);
      renderMemberProfile(member);
      populateMemberForm(member);
      if ($("#profileImageFileInput")) $("#profileImageFileInput").value = "";
      closeMemberEditModal();
    });
  }

  function bindRecruitments() {
    const params = new URLSearchParams(window.location.search);
    const pageParam = Number(params.get("page") || 0);
    const id = params.get("id");
    if ($("#recruitmentCategory") && params.get("category")) $("#recruitmentCategory").value = params.get("category");
    $("#recruitmentCategory")?.addEventListener("change", () => loadRecruitments(0));
    $("#recruitmentCategory")?.addEventListener("keydown", (event) => { if (event.key === "Enter") loadRecruitments(0); });
    $("#openCreateRecruitmentButton")?.addEventListener("click", openRecruitmentCreateModal);
    $("#closeCreateRecruitmentButton")?.addEventListener("click", closeRecruitmentCreateModal);
    $("#recruitmentCreateBackdrop")?.addEventListener("click", closeRecruitmentCreateModal);
    document.addEventListener("keydown", (event) => { if (event.key === "Escape") closeRecruitmentCreateModal(); });
    $("#createRecruitmentForm")?.addEventListener("submit", async (event) => {
      event.preventDefault();
      const payload = compact(formData(event.currentTarget));
      payload.capacity = asNumber(payload.capacity);
      await run(() => window.moiApi.request("/api/recruitment-posts", { method: "POST", body: window.moiApi.toJsonBody(payload) }), "모집글을 작성했습니다.");
      closeRecruitmentCreateModal();
      event.currentTarget.reset();
      await loadRecruitments(0);
    });
    if (id) loadRecruitmentDetail(id).catch(() => {});
    else loadRecruitments(pageParam).catch(() => {});
  }

  function currentGroupId() { return $("#groupIdInput")?.value || $("#scheduleGroupId")?.value; }
  function renderGroup(group) {
    if ($("#groupTitle")) $("#groupTitle").textContent = group.name || "그룹 홈";
    if ($("#groupDescription")) $("#groupDescription").textContent = `그룹 ID ${group.groupId} · 모집글 ID ${group.postId}`;
    if ($("#groupSummary")) $("#groupSummary").innerHTML = `<article><strong>${escapeHtml(group.status)}</strong><span>그룹 상태</span></article><article><strong>${escapeHtml(group.myRole)}</strong><span>내 역할</span></article><article><strong>${group.members?.length || 0}명</strong><span>활성 그룹원</span></article><article><strong>${escapeHtml(group.createdAt || "-")}</strong><span>생성일</span></article>`;
    renderCards("#groupMemberList", group.members || [], "활성 그룹원이 없습니다.", (member) => `<article class="entity-card member-card"><span class="badge">${escapeHtml(member.role)}</span><strong>회원 #${escapeHtml(member.userId)}</strong><div class="meta">가입일 ${escapeHtml(member.joinedAt || "-")}</div></article>`);
  }
  async function loadGroupSchedules() {
    const data = await run(() => window.moiApi.request(`/api/groups/${currentGroupId()}/schedules?scope=${$("#groupScheduleScope")?.value || "upcoming"}`), "일정을 조회했습니다.");
    renderCards("#groupScheduleList", data.items || [], "표시할 일정이 없습니다.", (item) => `<article class="entity-card schedule-card"><span class="badge">일정 #${item.scheduleId}</span><strong>${escapeHtml(item.title)}</strong><div class="meta">${escapeHtml(item.scheduledAt)} · ${escapeHtml(item.location || item.onlineLink || "장소 미정")}</div></article>`);
  }
  function syncGroupScheduleIds(scheduleId) { ["#groupScheduleId", "#groupSummaryScheduleId"].forEach((selector) => { const input = $(selector); if (input && scheduleId) input.value = scheduleId; }); }
  async function loadCurrentGroup() {
    const group = await run(() => window.moiApi.request(`/api/groups/${currentGroupId()}`), "그룹 홈을 조회했습니다.");
    renderGroup(group);
    saveMyGroup(group);
    await loadGroupSchedules().catch(() => {});
  }
  function bindGroup() {
    const params = new URLSearchParams(window.location.search);
    const groupId = params.get("groupId");
    if (groupId && $("#groupIdInput")) $("#groupIdInput").value = groupId;
    $("#loadGroupButton")?.addEventListener("click", loadCurrentGroup);
    $("#groupLoadSchedulesButton")?.addEventListener("click", loadGroupSchedules);
    $("#groupCreateScheduleForm")?.addEventListener("submit", async (event) => { event.preventDefault(); const schedule = await run(() => window.moiApi.request(`/api/groups/${currentGroupId()}/schedules`, { method: "POST", body: window.moiApi.toJsonBody(compact(formData(event.currentTarget))) }), "일정을 생성했습니다."); syncGroupScheduleIds(schedule?.scheduleId); await loadGroupSchedules(); });
    $("#groupAnswerAttendanceForm")?.addEventListener("submit", async (event) => { event.preventDefault(); const payload = formData(event.currentTarget); const response = await run(() => window.moiApi.request(`/api/attendance/schedules/${payload.scheduleId}/answers`, { method: "POST", body: window.moiApi.toJsonBody({ response: payload.response }) }), "출석 응답을 저장했습니다."); json("#groupAttendanceOutput", response); });
    $("#groupCheckAttendanceForm")?.addEventListener("submit", async (event) => { event.preventDefault(); const payload = formData(event.currentTarget); const response = await run(() => window.moiApi.request(`/api/attendance/schedules/${payload.scheduleId}/records`, { method: "POST", body: window.moiApi.toJsonBody({ userId: asNumber(payload.userId), status: payload.status }) }), "출석을 체크했습니다."); json("#groupAttendanceOutput", response); });
    $("#groupLoadAttendanceSummaryButton")?.addEventListener("click", async () => { const response = await run(() => window.moiApi.request(`/api/attendance/schedules/${$("#groupSummaryScheduleId").value}/records/summary`), "출석 요약을 조회했습니다."); json("#groupAttendanceOutput", response); });
    setDefaultScheduleTime();
    if (groupId) loadCurrentGroup().catch(() => {});
  }

  async function loadSchedules() {
    const data = await run(() => window.moiApi.request(`/api/groups/${$("#scheduleGroupId").value}/schedules?scope=${$("#scheduleScope").value}`), "일정을 조회했습니다.");
    renderCards("#scheduleList", data.items || [], "표시할 일정이 없습니다.", (item) => `<article class="entity-card"><span class="badge">일정 #${item.scheduleId}</span><strong>${escapeHtml(item.title)}</strong><div class="meta">${escapeHtml(item.scheduledAt)} · ${escapeHtml(item.location || "장소 미정")}</div></article>`);
  }
  function bindSchedules() { $("#loadSchedulesButton")?.addEventListener("click", loadSchedules); setDefaultScheduleTime(); }
  function bindAttendance() {}
  function bindBackoffice() { $("#boLoadRecruitments")?.addEventListener("click", () => loadRecruitments("#boRecruitmentList")); loadRecruitments("#boRecruitmentList").catch(() => {}); }
  function setDefaultScheduleTime() {
    document.querySelectorAll('input[name="scheduledAt"], input[name="responseDeadline"]').forEach((input, index) => {
      if (input.value) return;
      const date = new Date();
      date.setDate(date.getDate() + 1);
      date.setHours(index === 0 ? 19 : 18, 0, 0, 0);
      input.value = new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
    });
  }

  document.addEventListener("DOMContentLoaded", () => {
    bindProtectedLinks();
    refreshAuthChip();
    updateHomeForAuth();
    loadCurrentMemberForHeader().then((member) => { setHeaderGreeting(member); updateHomeForAuth(); });
    if (redirectIfAlreadySignedIn()) return;
    if (protectedPage && !requireLogin()) return;
    if (page === "login") bindLogin();
    if (page === "signup") bindSignup();
    if (page === "mypage") bindMypage();
    if (page === "recruitments") bindRecruitments();
    if (page === "group") bindGroup();
    if (page === "schedules") bindSchedules();
    if (page === "attendance") bindAttendance();
    if (page === "backoffice") bindBackoffice();
  });
})();