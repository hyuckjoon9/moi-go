(function () {
  const $ = (selector) => document.querySelector(selector);
  const page = document.body.dataset.page;
  const protectedPage = document.body.dataset.protected === "true";
  const recruitmentState = { page: 0, size: 10, totalPages: 1, status: "ALL", searchType: "category", items: [], pageData: null };
  const myGroupState = { groups: [], loaded: false };
  let currentRecruitmentDetail = null;
  let currentGroupDetail = null;
  let currentGroupMembers = [];
  let currentGroupSchedules = [];
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
  function displayImageSource(src) {
    if (!src || src.startsWith("data:") || src.includes("?")) return src;
    return src.startsWith("/uploads/") ? `${src}?v=${Date.now()}` : src;
  }
  function renderAvatar(selector, member) {
    const target = $(selector);
    if (!target) return;
    const src = memberImageSource(member);
    if (src) {
      target.innerHTML = `<img src="${escapeHtml(displayImageSource(src))}" alt="프로필 사진" />`;
      target.classList.add("has-image");
    } else {
      target.textContent = memberInitial(member);
      target.classList.remove("has-image");
    }
  }
  function cacheMemberIdentity(member) {
    if (member?.id) localStorage.setItem("memberId", String(member.id));
    if (member?.nickname) localStorage.setItem("memberNickname", member.nickname);
    if (member?.profileImageUrl) localStorage.setItem("memberProfileImageUrl", member.profileImageUrl);
  }
  function currentMemberId() {
    const value = localStorage.getItem("memberId");
    return value ? Number(value) : null;
  }
  function ownRecruitmentIds() {
    try { return JSON.parse(localStorage.getItem("ownRecruitmentIds") || "[]"); }
    catch (_) { return []; }
  }
  function markRecruitmentAsOwn(id) {
    if (!id) return;
    const ids = new Set(ownRecruitmentIds().map(String));
    ids.add(String(id));
    localStorage.setItem("ownRecruitmentIds", JSON.stringify([...ids]));
  }
  function isOwnRecruitment(detail) {
    if (!detail) return false;
    if (detail.isOwner === true || detail.owner === true) return true;
    const memberId = currentMemberId();
    const leaderId = detail.leaderId || detail.leader?.id || detail.leader?.memberId;
    if (memberId && leaderId && Number(leaderId) === memberId) return true;
    return ownRecruitmentIds().map(String).includes(String(detail.id));
  }
  function setRecruitmentStatusFilter(status) {
    recruitmentState.status = status;
    document.querySelectorAll("[data-recruitment-status]").forEach((button) => {
      button.classList.toggle("active", button.dataset.recruitmentStatus === recruitmentState.status);
    });
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
    avatar.innerHTML = src ? `<img src="${escapeHtml(displayImageSource(src))}" alt="프로필 사진" />` : `<span>${escapeHtml(memberInitial(member))}</span>`;
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
    const cachedMemberId = localStorage.getItem("memberId");
    if (cachedNickname && cachedMemberId) return { id: Number(cachedMemberId), nickname: cachedNickname, profileImageUrl: localStorage.getItem("memberProfileImageUrl") };
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
  function statusLabel(status) {
    const value = String(status || "RECRUITING").toUpperCase();
    if (["RECRUITING", "ACTIVE"].includes(value)) return "모집 중";
    if (["CLOSED", "ENDED"].includes(value)) return "모집 종료";
    return "상태 미정";
  }
  function statusGroup(status) {
    const value = String(status || "RECRUITING").toUpperCase();
    return ["RECRUITING", "ACTIVE"].includes(value) ? "OPEN" : "CLOSED";
  }
  function meetingTypeLabel(value) {
    const labels = { ONLINE: "온라인", OFFLINE: "오프라인", HYBRID: "온·오프라인 병행" };
    return labels[String(value || "").toUpperCase()] || value || "미정";
  }
  function updateRecruitmentUrl(pageNo) {
    const params = new URLSearchParams(window.location.search);
    params.delete("id");
    if (pageNo > 0) params.set("page", String(pageNo)); else params.delete("page");
    const searchType = $("#recruitmentSearchType")?.value || "category";
    const keyword = $("#recruitmentSearchKeyword")?.value.trim() || "";
    if (keyword) params.set("q", keyword); else params.delete("q");
    if (searchType !== "category") params.set("searchType", searchType); else params.delete("searchType");
    if (recruitmentState.status !== "ALL") params.set("status", recruitmentState.status); else params.delete("status");
    const query = params.toString();
    window.history.replaceState(null, "", `/recruitments.html${query ? `?${query}` : ""}`);
  }
  function buildRecruitmentQuery() {
    const params = new URLSearchParams({ page: String(recruitmentState.page), size: String(recruitmentState.size), sort: "id,desc" });
    const searchType = $("#recruitmentSearchType")?.value || "category";
    const keyword = $("#recruitmentSearchKeyword")?.value.trim() || "";
    if (searchType === "category" && keyword) params.set("category", keyword);
    return params.toString();
  }
  function showRecruitmentListMode() {
    showPanel("#recruitmentListSection");
    hidePanel("#recruitmentDetailSection");
    hidePanel("#backToRecruitmentList");
    if ($("#recruitmentPageTitle")) $("#recruitmentPageTitle").textContent = "모집글";
    if ($("#recruitmentPageDescription")) $("#recruitmentPageDescription").textContent = "스터디 모집글을 확인하고 참여할 그룹을 찾아보세요.";
  }
  function showRecruitmentDetailMode() {
    hidePanel("#recruitmentListSection");
    showPanel("#recruitmentDetailSection");
    showPanel("#backToRecruitmentList");
    if ($("#recruitmentPageTitle")) $("#recruitmentPageTitle").textContent = "모집글 상세";
    if ($("#recruitmentPageDescription")) $("#recruitmentPageDescription").textContent = "스터디 소개와 운영 방식을 확인합니다.";
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
  function filterRecruitmentsBySearch(items) {
    const type = $("#recruitmentSearchType")?.value || "category";
    const keyword = ($("#recruitmentSearchKeyword")?.value || "").trim().toLowerCase();
    if (!keyword) return items;
    return items.filter((item) => {
      if (type === "category") return String(item.category || "").toLowerCase().includes(keyword);
      if (type === "title") return String(item.title || "").toLowerCase().includes(keyword);
      if (type === "content") return [item.description, item.goal, item.method, item.conditions].some((value) => String(value || "").toLowerCase().includes(keyword));
      if (type === "writer") return String(item.leaderId || item.leader?.id || "").toLowerCase().includes(keyword);
      return true;
    });
  }
  function renderRecruitmentsFromState() {
    const statusItems = recruitmentState.status === "ALL" ? recruitmentState.items : recruitmentState.items.filter((item) => statusGroup(item.status) === recruitmentState.status);
    const visibleItems = filterRecruitmentsBySearch(statusItems);
    renderCards("#recruitmentList", visibleItems, "표시할 모집글이 없습니다.", renderRecruitmentRow);
    renderRecruitmentPagination(recruitmentState.pageData);
  }
  function renderRecruitmentRow(item) {
    const label = statusLabel(item.status);
    const group = statusGroup(item.status).toLowerCase();
    return `
      <a class="entity-card recruitment-row" href="/recruitments.html?id=${encodeURIComponent(item.id)}">
        <div class="recruitment-row-main">
          <div class="recruitment-row-meta"><span class="badge status-${group}">${escapeHtml(label)}</span><span>${escapeHtml(item.category || "카테고리 없음")}</span><span>글 번호 ${escapeHtml(item.id)}</span></div>
          <strong>${escapeHtml(item.title)}</strong>
          <p>${escapeHtml(item.description || item.goal || "상세 내용을 확인해보세요.")}</p>
        </div>
        <span class="row-arrow">상세 보기</span>
      </a>`;
  }
  async function loadRecruitments(pageNo = recruitmentState.page) {
    recruitmentState.page = Math.max(0, pageNo || 0);
    showRecruitmentListMode();
    updateRecruitmentUrl(recruitmentState.page);
    const pageData = await run(() => window.moiApi.request(`/api/recruitment-posts?${buildRecruitmentQuery()}`), null);
    recruitmentState.pageData = pageData;
    recruitmentState.items = getPageContent(pageData);
    renderRecruitmentsFromState();
  }
  function detailItem(label, value) {
    if (value === undefined || value === null || value === "") return "";
    return `<div><dt>${escapeHtml(label)}</dt><dd>${escapeHtml(value)}</dd></div>`;
  }
  function detailTextSection(label, value) {
    if (!value) return "";
    return `<section class="detail-text-section"><h3>${escapeHtml(label)}</h3><p>${escapeHtml(value)}</p></section>`;
  }
  function recruitmentPayloadFromForm(form) {
    const payload = compact(formData(form));
    delete payload.leaderGroupId;
    payload.title = payload.title?.trim() || "";
    payload.category = payload.category?.trim() || "";
    payload.meetingType = payload.meetingType || "ONLINE";
    payload.capacity = asNumber(payload.capacity);
    payload.recruitmentDeadline = payload.recruitmentDeadline || null;
    return payload;
  }
  function validateRecruitmentPayload(payload) {
    if (!payload.title || !payload.category || !payload.meetingType || !payload.capacity || !payload.recruitmentDeadline) {
      toast("스터디 이름, 카테고리, 모임 방식, 모집 인원, 모집 마감일은 필수입니다.", true);
      return false;
    }
    return true;
  }
  function populateRecruitmentForm(detail = {}) {
    const form = $("#createRecruitmentForm");
    if (!form) return;
    ["title", "category", "description", "goal", "method", "meetingType", "location", "onlineLink", "meetingDay", "capacity", "recruitmentDeadline", "expectedDuration", "conditions"].forEach((name) => setField(form, name, detail[name]));
  }
  function setRecruitmentFormMode(mode, detail = null) {
    const isEdit = mode === "edit";
    if ($("#recruitmentCreateTitle")) $("#recruitmentCreateTitle").textContent = isEdit ? "모집글 수정" : "모집글 작성";
    const eyebrow = $("#recruitmentCreatePanel .eyebrow");
    if (eyebrow) eyebrow.textContent = isEdit ? "Edit Study" : "New Study";
    const form = $("#createRecruitmentForm");
    const submit = form?.querySelector("button[type='submit']");
    if (submit) submit.textContent = isEdit ? "수정하기" : "등록하기";
    if (form) form.dataset.mode = mode;
    if (isEdit) populateRecruitmentForm(detail || {});
    else form?.reset();
  }
  function openRecruitmentEditModal(detail) {
    setRecruitmentFormMode("edit", detail);
    showPanel("#recruitmentCreateBackdrop");
    showPanel("#recruitmentCreatePanel");
    document.body.classList.add("modal-open");
  }
  function applicationStatusLabel(status) {
    const value = String(status || "").toUpperCase();
    if (value === "PENDING") return "승인 대기";
    if (value === "APPROVED") return "승인";
    if (value === "REJECTED") return "거절";
    if (value === "CANCELLED") return "취소";
    return "상태 미정";
  }
  function ensureApplicationPanel() {
    let panel = $("#recruitmentApplicationPanel");
    if (panel) return panel;
    panel = document.createElement("section");
    panel.id = "recruitmentApplicationPanel";
    panel.className = "application-panel";
    $("#recruitmentDetailView")?.appendChild(panel);
    return panel;
  }
  async function loadRecruitmentApplications(postId) {
    const panel = ensureApplicationPanel();
    panel.innerHTML = `<h3>신청자 관리</h3><p class="meta">신청자 목록을 불러오는 중입니다.</p>`;
    const applications = await run(() => window.moiApi.request(`/api/recruitment-posts/${postId}/applications`), null);
    if (!applications || applications.length === 0) {
      panel.innerHTML = `<h3>신청자 관리</h3><div class="entity-card meta">아직 참가 신청이 없습니다.</div>`;
      return;
    }
    panel.innerHTML = `
      <h3>신청자 관리</h3>
      <div class="application-list">
        ${applications.map((application) => `
          <article class="entity-card application-card" data-application-id="${escapeHtml(application.id)}">
            <div>
              <span class="badge">${escapeHtml(applicationStatusLabel(application.status))}</span>
              <strong>${escapeHtml(application.applicantNickname || `회원 #${application.applicantId}`)}</strong>
              <p>${escapeHtml(application.motivation || "지원 동기가 없습니다.")}</p>
              <div class="meta">${escapeHtml(application.experience || "경험 미입력")} · ${escapeHtml(application.availableTime || "가능 시간 미입력")} · ${escapeHtml(application.desiredRole || "희망 역할 미입력")}</div>
            </div>
            ${String(application.status || "").toUpperCase() === "PENDING" ? `<div class="application-actions"><button class="button small" type="button" data-application-action="approve">승인</button><button class="button ghost small" type="button" data-application-action="reject">거절</button></div>` : ""}
          </article>`).join("")}
      </div>`;
    panel.querySelectorAll("[data-application-action]").forEach((button) => {
      button.addEventListener("click", async () => {
        const applicationId = button.closest("[data-application-id]")?.dataset.applicationId;
        const action = button.dataset.applicationAction;
        await run(() => window.moiApi.request(`/api/recruitment-posts/${postId}/applications/${applicationId}/${action}`, { method: "PATCH" }), action === "approve" ? "신청을 승인했습니다." : "신청을 거절했습니다.");
        await loadRecruitmentApplications(postId);
        await fetchMyGroups(true).catch(() => []);
      });
    });
  }
  function openJoinApplicationModal(postId) {
    $("#joinApplicationBackdrop")?.remove();
    $("#joinApplicationPanel")?.remove();
    document.body.insertAdjacentHTML("beforeend", `
      <div id="joinApplicationBackdrop" class="modal-backdrop"></div>
      <article id="joinApplicationPanel" class="panel edit-panel modal-panel application-modal" role="dialog" aria-modal="true" aria-labelledby="joinApplicationTitle">
        <div class="panel-heading"><div><p class="eyebrow">Apply</p><h2 id="joinApplicationTitle">참가 신청</h2></div><button id="closeJoinApplicationButton" class="button ghost small" type="button">닫기</button></div>
        <form id="joinApplicationForm" class="form-stack compact">
          <label>지원 동기 <em class="field-tag required">필수</em><textarea name="motivation" required placeholder="스터디에 참여하고 싶은 이유를 적어주세요."></textarea></label>
          <label>관련 경험 <em class="field-tag optional">선택</em><textarea name="experience" placeholder="관련 경험이 있으면 적어주세요."></textarea></label>
          <label>참여 가능한 시간 <em class="field-tag optional">선택</em><input name="availableTime" placeholder="예: 평일 저녁, 주말 오전" /></label>
          <label>희망 역할 <em class="field-tag optional">선택</em><input name="desiredRole" placeholder="예: 발표, 기록, 자료 정리" /></label>
          <button class="button" type="submit">신청하기</button>
        </form>
      </article>`);
    const close = () => { $("#joinApplicationBackdrop")?.remove(); $("#joinApplicationPanel")?.remove(); document.body.classList.remove("modal-open"); };
    document.body.classList.add("modal-open");
    $("#joinApplicationBackdrop")?.addEventListener("click", close);
    $("#closeJoinApplicationButton")?.addEventListener("click", close);
    $("#joinApplicationForm")?.addEventListener("submit", async (event) => {
      event.preventDefault();
      const payload = compact(formData(event.currentTarget));
      if (!payload.motivation) { toast("지원 동기는 필수입니다.", true); return; }
      await run(() => window.moiApi.request(`/api/recruitment-posts/${postId}/applications`, { method: "POST", body: window.moiApi.toJsonBody(payload) }), "참가 신청을 보냈습니다.");
      close();
      await loadRecruitmentDetail(postId);
    });
  }
  async function loadRecruitmentDetail(id, showToast = false) {
    showRecruitmentDetailMode();
    const detail = await run(() => window.moiApi.request(`/api/recruitment-posts/${id}`), showToast ? "모집글 상세를 조회했습니다." : null);
    currentRecruitmentDetail = detail;
    if (!currentMemberId()) await loadCurrentMemberForHeader().catch(() => null);
    const view = $("#recruitmentDetailView");
    if (!view) return;
    const owner = isOwnRecruitment(detail);
    const actionButtons = owner
      ? `<button class="button ghost" type="button" data-recruitment-action="edit">수정</button><button class="button danger" type="button" data-recruitment-action="delete">삭제</button><button class="button ghost" type="button" data-recruitment-action="close">모집 종료</button><button class="button ghost" type="button" data-recruitment-action="end">활동 종료</button><button class="button" type="button" data-recruitment-action="applications">신청자 관리</button>`
      : `<button class="button" type="button" data-recruitment-action="apply">참가 신청</button>`;
    view.innerHTML = `
      <header class="board-detail-header">
        <div class="recruitment-row-meta"><span class="badge status-${statusGroup(detail.status).toLowerCase()}">${escapeHtml(statusLabel(detail.status))}</span><span>${escapeHtml(detail.category || "카테고리 없음")}</span><span>글 번호 ${escapeHtml(detail.id)}</span></div>
        <h2>${escapeHtml(detail.title || "모집글 상세")}</h2>
      </header>
      <dl class="info-list recruitment-detail-list board-meta-list">
        ${detailItem("모임 방식", meetingTypeLabel(detail.meetingType))}${detailItem("활동 지역", detail.location)}${detailItem("온라인 링크", detail.onlineLink)}${detailItem("정기 모임 요일", detail.meetingDay)}${detailItem("모집 인원", detail.capacity ? `${detail.capacity}명` : "")}${detailItem("모집 마감일", formatDate(detail.recruitmentDeadline))}${detailItem("예상 활동 기간", detail.expectedDuration)}
      </dl>
      <div class="board-body">
        ${detailTextSection("소개", detail.description)}${detailTextSection("목표", detail.goal)}${detailTextSection("진행 방식", detail.method)}${detailTextSection("참가 조건", detail.conditions)}
      </div>
      <div class="detail-actions"><a class="button ghost" href="/recruitments.html">목록으로</a>${actionButtons}</div>`;
    view.querySelectorAll("[data-recruitment-action]").forEach((button) => {
      button.addEventListener("click", async () => {
        const action = button.dataset.recruitmentAction;
        if (action === "edit") openRecruitmentEditModal(detail);
        if (action === "delete" && confirm("모집글을 삭제할까요?")) {
          await run(() => window.moiApi.request(`/api/recruitment-posts/${detail.id}`, { method: "DELETE" }), "모집글을 삭제했습니다.");
          window.location.href = "/recruitments.html";
        }
        if (action === "close") { await run(() => window.moiApi.request(`/api/recruitment-posts/${detail.id}/close`, { method: "PATCH" }), "모집을 종료했습니다."); await loadRecruitmentDetail(detail.id); }
        if (action === "end") { await run(() => window.moiApi.request(`/api/recruitment-posts/${detail.id}/end`, { method: "PATCH" }), "활동을 종료했습니다."); await loadRecruitmentDetail(detail.id); }
        if (action === "applications") await loadRecruitmentApplications(detail.id);
        if (action === "apply") openJoinApplicationModal(detail.id);
      });
    });
  }
  function openMemberEditModal() { showPanel("#memberEditBackdrop"); showPanel("#memberEditPanel"); document.body.classList.add("modal-open"); setTimeout(() => $("#updateMemberForm input[name='nickname']")?.focus(), 0); }
  function closeMemberEditModal() { hidePanel("#memberEditBackdrop"); hidePanel("#memberEditPanel"); document.body.classList.remove("modal-open"); }
  async function openRecruitmentCreateModal() {
    setRecruitmentFormMode("create");
    showPanel("#recruitmentCreateBackdrop");
    showPanel("#recruitmentCreatePanel");
    document.body.classList.add("modal-open");
  }
  function closeRecruitmentCreateModal() { hidePanel("#recruitmentCreateBackdrop"); hidePanel("#recruitmentCreatePanel"); document.body.classList.remove("modal-open"); }
  function openNeedLeaderGroupModal() { showPanel("#needLeaderGroupBackdrop"); showPanel("#needLeaderGroupPanel"); document.body.classList.add("modal-open"); }
  function closeNeedLeaderGroupModal() { hidePanel("#needLeaderGroupBackdrop"); hidePanel("#needLeaderGroupPanel"); document.body.classList.remove("modal-open"); }

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
  function renderGroupSection(targetSelector, countSelector, items, emptyText, renderer) {
    const target = $(targetSelector);
    const count = $(countSelector);
    if (count) count.textContent = String(items?.length || 0);
    if (!target) return;
    renderCards(targetSelector, items || [], emptyText, renderer);
  }
  function groupRoleLabel(role) {
    const value = String(role || "").toUpperCase();
    if (value === "LEADER") return "운영자";
    if (value === "MANAGER") return "매니저";
    if (value === "MEMBER") return "팀원";
    return "참여 중";
  }
  function groupStatusLabel(status) {
    const value = String(status || "").toUpperCase();
    if (value === "ACTIVE") return "진행 중";
    if (value === "ENDED") return "종료";
    return "상태 미정";
  }
  function isLeaderGroup(group) {
    return String(group?.role || "").toUpperCase() === "LEADER";
  }
  function leaderGroups() {
    return myGroupState.groups.filter(isLeaderGroup);
  }
  async function fetchMyGroups(force = false) {
    if (myGroupState.loaded && !force) return myGroupState.groups;
    const groups = await run(() => window.moiApi.request("/api/groups/me"), null);
    myGroupState.groups = Array.isArray(groups) ? groups : [];
    myGroupState.loaded = true;
    return myGroupState.groups;
  }
  function renderMyGroupLink(group) {
    const joinedText = group.joinedAt ? ` · 가입일 ${formatDate(group.joinedAt)}` : "";
    return `<a class="entity-card my-group-link" href="/group.html?groupId=${encodeURIComponent(group.groupId)}">
      <strong>${escapeHtml(groupDisplayName(group))}</strong>
      <div class="meta">${escapeHtml(groupRoleLabel(group.role))} · ${escapeHtml(groupStatusLabel(group.status))}${escapeHtml(joinedText)}</div>
    </a>`;
  }
  function renderPendingApplication(application) {
    return `<a class="entity-card my-group-link pending-group-link" href="/recruitments.html?id=${encodeURIComponent(application.postId)}">
      <strong>${escapeHtml(application.postTitle || `모집글 ${application.postId}`)}</strong>
      <div class="meta">${escapeHtml(application.category || "카테고리 없음")} · 승인 대기</div>
    </a>`;
  }
  async function loadMyGroups(force = false) {
    const list = await fetchMyGroups(force);
    const operatingGroups = list.filter((group) => ["LEADER", "MANAGER"].includes(String(group.role || "").toUpperCase()));
    const memberGroups = list.filter((group) => String(group.role || "").toUpperCase() === "MEMBER");

    renderGroupSection("#operatingGroupList", "#operatingGroupCount", operatingGroups, "운영 중인 그룹이 없습니다.", renderMyGroupLink);
    const pendingApplications = await run(() => window.moiApi.request("/api/join-applications/me?status=PENDING"), null).catch(() => []);
    renderGroupSection("#pendingGroupList", "#pendingGroupCount", pendingApplications, "신청 중인 그룹이 없습니다.", renderPendingApplication);
    renderGroupSection("#memberGroupList", "#memberGroupCount", memberGroups, "팀원으로 활동 중인 그룹이 없습니다.", renderMyGroupLink);
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
    loadMyGroups().catch(() => {});
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
    $("#updateMemberForm")?.addEventListener("submit", async (event) => {
      event.preventDefault();
      const form = event.currentTarget;
      const uploadedMember = await uploadSelectedProfileImage();
      const member = await run(() => window.moiApi.request("/api/members/me", { method: "PATCH", body: window.moiApi.toJsonBody(compact(formData(form))) }), "내 정보를 수정했습니다.");
      if (!member.profileImageUrl && uploadedMember?.profileImageUrl) member.profileImageUrl = uploadedMember.profileImageUrl;
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
    const statusParam = params.get("status");
    if (["ALL", "OPEN", "CLOSED"].includes(statusParam)) recruitmentState.status = statusParam;
    document.querySelectorAll("[data-recruitment-status]").forEach((button) => {
      button.classList.toggle("active", button.dataset.recruitmentStatus === recruitmentState.status);
      button.addEventListener("click", () => {
        recruitmentState.status = button.dataset.recruitmentStatus;
        document.querySelectorAll("[data-recruitment-status]").forEach((item) => item.classList.toggle("active", item === button));
        loadRecruitments(0).catch(() => {});
      });
    });
    if ($("#recruitmentSearchType")) $("#recruitmentSearchType").value = params.get("searchType") || "category";
    if ($("#recruitmentSearchKeyword")) $("#recruitmentSearchKeyword").value = params.get("q") || params.get("category") || "";
    $("#recruitmentSearchButton")?.addEventListener("click", () => loadRecruitments(0));
    $("#recruitmentSearchKeyword")?.addEventListener("input", renderRecruitmentsFromState);
    $("#recruitmentSearchKeyword")?.addEventListener("keydown", (event) => { if (event.key === "Enter") loadRecruitments(0); });
    $("#recruitmentSearchType")?.addEventListener("change", renderRecruitmentsFromState);
    $("#openCreateRecruitmentButton")?.addEventListener("click", openRecruitmentCreateModal);
    $("#closeNeedLeaderGroupButton")?.addEventListener("click", closeNeedLeaderGroupModal);
    $("#cancelNeedLeaderGroupButton")?.addEventListener("click", closeNeedLeaderGroupModal);
    $("#needLeaderGroupBackdrop")?.addEventListener("click", closeNeedLeaderGroupModal);
    $("#goCreateGroupButton")?.addEventListener("click", () => { window.location.href = "/group.html#create-group"; });
    $("#closeCreateRecruitmentButton")?.addEventListener("click", closeRecruitmentCreateModal);
    $("#recruitmentCreateBackdrop")?.addEventListener("click", closeRecruitmentCreateModal);
    document.addEventListener("keydown", (event) => { if (event.key === "Escape") closeRecruitmentCreateModal(); });
    $("#createRecruitmentForm")?.addEventListener("submit", async (event) => {
      event.preventDefault();
      const form = event.currentTarget;
      const payload = recruitmentPayloadFromForm(form);
      if (!validateRecruitmentPayload(payload)) return;
      const isEdit = form.dataset.mode === "edit";
      const targetId = currentRecruitmentDetail?.id;
      const saved = await run(
        () => window.moiApi.request(isEdit ? `/api/recruitment-posts/${targetId}` : "/api/recruitment-posts", { method: isEdit ? "PATCH" : "POST", body: window.moiApi.toJsonBody(payload) }),
        isEdit ? "모집글을 수정했습니다." : "모집글을 등록했습니다."
      );
      markRecruitmentAsOwn(saved?.id || targetId);
      closeRecruitmentCreateModal();
      form.reset();
      if (isEdit) await loadRecruitmentDetail(targetId, true);
      else {
        if ($("#recruitmentSearchKeyword")) $("#recruitmentSearchKeyword").value = "";
        setRecruitmentStatusFilter("ALL");
        window.history.replaceState(null, "", "/recruitments.html");
        await loadRecruitments(0);
      }
    });
    if (id) loadRecruitmentDetail(id).catch(() => {});
    else loadRecruitments(pageParam).catch(() => {});
  }
  function populateRecruitmentLeaderGroups(groups = leaderGroups()) {
    const select = $("#recruitmentLeaderGroupId");
    if (!select) return;
    select.innerHTML = groups.map((group) => `<option value="${escapeHtml(group.groupId)}">${escapeHtml(group.name || `그룹 ${group.groupId}`)}</option>`).join("");
  }
  function currentGroupId() { return $("#myGroupSelect")?.value || $("#groupIdInput")?.value || $("#scheduleGroupId")?.value; }
  function groupDisplayName(group) { return group?.name || "이름 없는 그룹"; }
  function groupMemberDisplayName(member, index = 0) {
    const ownId = currentMemberId();
    if (ownId && String(member.userId) === String(ownId)) return "나";
    if (member.nickname || member.memberNickname || member.name) return member.nickname || member.memberNickname || member.name;
    const role = String(member.role || "").toUpperCase();
    if (role === "LEADER") return "그룹장";
    if (role === "MANAGER") return "매니저";
    return `그룹원 ${index + 1}`;
  }
  function groupMemberAvatarHtml(member, index = 0) {
    const nickname = groupMemberDisplayName(member, index);
    if (member?.profileImageUrl) {
      return `<span class="member-avatar has-image"><img src="${escapeHtml(displayImageSource(member.profileImageUrl))}" alt="${escapeHtml(nickname)} 프로필" /></span>`;
    }
    return `<span class="member-avatar">${escapeHtml(memberInitial({ nickname }))}</span>`;
  }
  function groupMemberLabelForUserId(userId) {
    const index = currentGroupMembers.findIndex((member) => String(member.userId) === String(userId));
    if (index >= 0) return groupMemberDisplayName(currentGroupMembers[index], index);
    return "그룹원";
  }
  function renderGroupQuickCard(group) {
    return `<button class="entity-card my-group-link group-choice-card" type="button" data-group-id="${escapeHtml(group.groupId)}">
      <strong>${escapeHtml(groupDisplayName(group))}</strong>
      <div class="meta">${escapeHtml(groupRoleLabel(group.role))} · ${escapeHtml(groupStatusLabel(group.status))}</div>
    </button>`;
  }
  function populateGroupSelector(groups) {
    const select = $("#myGroupSelect");
    if (select) {
      const current = select.value || new URLSearchParams(window.location.search).get("groupId") || "";
      select.innerHTML = `<option value="">그룹을 선택하세요</option>${groups.map((group) => `<option value="${escapeHtml(group.groupId)}">${escapeHtml(groupDisplayName(group))} (${escapeHtml(groupRoleLabel(group.role))})</option>`).join("")}`;
      if (current) select.value = current;
    }
    renderCards("#myGroupQuickList", groups, "참여 중인 그룹이 없습니다. 모집글을 작성하면 그룹이 자동으로 생성됩니다.", renderGroupQuickCard);
    document.querySelectorAll("[data-group-id]").forEach((card) => {
      card.addEventListener("click", () => {
        if ($("#myGroupSelect")) $("#myGroupSelect").value = card.dataset.groupId;
        loadCurrentGroup().catch(() => {});
      });
    });
  }
  function scheduleDisplayName(schedule) {
    if (!schedule) return "일정";
    const title = schedule.title || "제목 없는 일정";
    const when = schedule.scheduledAt ? ` · ${formatDate(schedule.scheduledAt) || schedule.scheduledAt}` : "";
    return `${title}${when}`;
  }
  function selectedGroupSchedule(scheduleId) {
    return currentGroupSchedules.find((schedule) => String(schedule.scheduleId) === String(scheduleId));
  }
  function setGroupMembersExpanded(expanded) {
    const list = $("#groupMemberList");
    const trigger = $("#toggleGroupMembersButton");
    if (!list || !trigger) return;
    list.classList.toggle("hidden", !expanded);
    trigger.setAttribute("aria-expanded", String(expanded));
    trigger.querySelector(".metric-toggle-icon")?.classList.toggle("expanded", expanded);
  }
  function toggleScheduleCreateForm(show) {
    const form = $("#groupCreateScheduleForm");
    if (!form) return;
    form.classList.toggle("hidden", !show);
    if (show) form.querySelector('input[name="title"]')?.focus();
  }
  function populateGroupMemberSelect() {
    const select = $("#groupAttendanceMemberSelect");
    if (!select) return;
    select.innerHTML = `<option value="">그룹원을 선택하세요</option>${currentGroupMembers.map((member, index) => `<option value="${escapeHtml(member.userId)}">${escapeHtml(groupMemberDisplayName(member, index))} (${escapeHtml(groupRoleLabel(member.role))})</option>`).join("")}`;
  }
  function populateGroupSchedulePickers() {
    document.querySelectorAll(".group-schedule-picker").forEach((select) => {
      const current = select.value;
      select.innerHTML = `<option value="">일정을 선택하세요</option>${currentGroupSchedules.map((schedule) => `<option value="${escapeHtml(schedule.scheduleId)}">${escapeHtml(scheduleDisplayName(schedule))}</option>`).join("")}`;
      if (current && currentGroupSchedules.some((schedule) => String(schedule.scheduleId) === String(current))) select.value = current;
    });
  }
  function selectGroupSchedule(scheduleId) {
    if (!scheduleId) return;
    syncGroupScheduleIds(scheduleId);
    const schedule = selectedGroupSchedule(scheduleId);
    const detailPanel = $("#groupScheduleDetailPanel");
    if (detailPanel) detailPanel.classList.remove("hidden");
    if ($("#selectedScheduleTitle")) $("#selectedScheduleTitle").textContent = scheduleDisplayName(schedule);
    loadGroupAttendanceRates().catch(() => {});
  }
  function renderAttendanceRate(rate) {
    const percentage = Number(rate.attendanceRate || 0).toFixed(1);
    return `<article class="entity-card attendance-rate-card"><span class="badge">${escapeHtml(groupMemberLabelForUserId(rate.userId))}</span><strong>${percentage}%</strong><div class="meta">출석 ${escapeHtml(rate.presentCount || 0)} · 지각 ${escapeHtml(rate.lateCount || 0)} · 결석 ${escapeHtml(rate.absentCount || 0)} · 인정 ${escapeHtml(rate.excusedCount || 0)}</div></article>`;
  }
  async function loadGroupAttendanceRates() {
    if (!currentGroupId()) return;
    const rates = await run(() => window.moiApi.request(`/api/attendance/groups/${currentGroupId()}/rates`), null);
    renderCards("#groupAttendanceRateList", rates || [], "표시할 출석률이 없습니다.", renderAttendanceRate);
  }
  function renderGroup(group) {
    currentGroupDetail = group;
    currentGroupMembers = group.members || [];
    if ($("#groupTitle")) $("#groupTitle").textContent = group.name || "그룹 홈";
    if ($("#groupDescription")) $("#groupDescription").textContent = `${groupStatusLabel(group.status)} 그룹 · 일정과 출석을 한 화면에서 관리합니다.`;
    if ($("#groupSummary")) $("#groupSummary").innerHTML = `<article><strong>${escapeHtml(groupStatusLabel(group.status))}</strong><span>그룹 상태</span></article><article><strong>${escapeHtml(groupRoleLabel(group.myRole))}</strong><span>내 역할</span></article><article id="toggleGroupMembersButton" class="metric-member-toggle" role="button" tabindex="0" aria-expanded="false"><strong>${currentGroupMembers.length}명</strong><span class="metric-member-label">활성 그룹원 <span class="metric-toggle-icon" aria-hidden="true">▾</span></span><div id="groupMemberList" class="card-list metric-member-list hidden"></div></article><article><strong>${escapeHtml(formatDate(group.createdAt) || "-")}</strong><span>생성일</span></article>`;
    renderCards("#groupMemberList", currentGroupMembers, "활성 그룹원이 없습니다.", (member, index) => `<article class="entity-card member-card member-profile-card">${groupMemberAvatarHtml(member, index)}<div class="member-profile-main"><span class="badge">${escapeHtml(groupRoleLabel(member.role))}</span><strong>${escapeHtml(groupMemberDisplayName(member, index))}</strong><div class="meta">가입일 ${escapeHtml(formatDate(member.joinedAt) || "-")}</div></div></article>`);
    setGroupMembersExpanded(false);
    const memberToggle = $("#toggleGroupMembersButton");
    memberToggle?.addEventListener("click", (event) => {
      if (event.target.closest(".member-profile-card")) return;
      setGroupMembersExpanded($("#groupMemberList")?.classList.contains("hidden"));
    });
    memberToggle?.addEventListener("keydown", (event) => {
      if (event.key === "Enter" || event.key === " ") {
        event.preventDefault();
        setGroupMembersExpanded($("#groupMemberList")?.classList.contains("hidden"));
      }
    });
    populateGroupMemberSelect();
    renderGroupOperations(group);
  }
  function renderGroupApplications(applications, postId) {
    const target = $("#groupApplicationList");
    if (!target) return;
    if (!applications || applications.length === 0) {
      target.innerHTML = `<div class="entity-card meta">아직 참가 신청이 없습니다.</div>`;
      return;
    }
    target.innerHTML = applications.map((application) => `<article class="entity-card application-card" data-application-id="${escapeHtml(application.id)}"><div><span class="badge">${escapeHtml(applicationStatusLabel(application.status))}</span><strong>${escapeHtml(application.applicantNickname || "신청자")}</strong><p>${escapeHtml(application.motivation || "지원 동기가 없습니다.")}</p><div class="meta">${escapeHtml(application.experience || "경험 미입력")} · ${escapeHtml(application.availableTime || "가능 시간 미입력")} · ${escapeHtml(application.desiredRole || "희망 역할 미입력")}</div></div>${String(application.status || "").toUpperCase() === "PENDING" ? `<div class="application-actions"><button class="button small" type="button" data-group-application-action="approve">승인</button><button class="button ghost small" type="button" data-group-application-action="reject">거절</button></div>` : ""}</article>`).join("");
    target.querySelectorAll("[data-group-application-action]").forEach((button) => {
      button.addEventListener("click", async () => {
        const applicationId = button.closest("[data-application-id]")?.dataset.applicationId;
        const action = button.dataset.groupApplicationAction;
        await run(() => window.moiApi.request(`/api/recruitment-posts/${postId}/applications/${applicationId}/${action}`, { method: "PATCH" }), action === "approve" ? "신청을 승인했습니다." : "신청을 거절했습니다.");
        await loadGroupApplications(postId);
        await loadCurrentGroup().catch(() => {});
      });
    });
  }
  async function loadGroupApplications(postId = currentGroupDetail?.postId) {
    if (!postId) { toast("연결된 모집글이 없습니다.", true); return; }
    const target = $("#groupApplicationList");
    if (target) target.innerHTML = `<div class="entity-card meta">신청자 목록을 불러오는 중입니다.</div>`;
    const applications = await run(() => window.moiApi.request(`/api/recruitment-posts/${postId}/applications`), null);
    renderGroupApplications(applications, postId);
  }
  function renderGroupOperations(group) {
    const panel = $("#groupOperationPanel");
    if (!panel) return;
    const isLeader = String(group.myRole || "").toUpperCase() === "LEADER";
    if (!isLeader || !group.postId) {
      panel.classList.add("hidden");
      panel.innerHTML = "";
      return;
    }
    panel.classList.remove("hidden");
    panel.innerHTML = `<div class="panel-heading"><div><p class="eyebrow">Recruitment</p><h2>모집 관리</h2></div><div class="page-actions"><a class="button ghost small" href="/recruitments.html?id=${encodeURIComponent(group.postId)}">모집글 보기</a><button id="groupLoadApplicationsButton" class="button small" type="button">신청자 보기</button><button id="groupCloseRecruitmentButton" class="button ghost small" type="button">모집 종료</button></div></div><div id="groupApplicationList" class="application-list"></div>`;
    $("#groupLoadApplicationsButton")?.addEventListener("click", () => loadGroupApplications(group.postId));
    $("#groupCloseRecruitmentButton")?.addEventListener("click", async () => {
      await run(() => window.moiApi.request(`/api/recruitment-posts/${group.postId}/close`, { method: "PATCH" }), "모집을 종료했습니다.");
      await loadCurrentGroup().catch(() => {});
    });
  }
  async function loadGroupSchedules() {
    if (!currentGroupId()) { toast("그룹을 먼저 선택하세요.", true); return; }
    const data = await run(() => window.moiApi.request(`/api/groups/${currentGroupId()}/schedules?scope=${$("#groupScheduleScope")?.value || "upcoming"}`), "일정을 조회했습니다.");
    currentGroupSchedules = [...(data.items || [])].sort((a, b) => new Date(b.scheduledAt || 0) - new Date(a.scheduledAt || 0));
    populateGroupSchedulePickers();
    renderCards("#groupScheduleList", currentGroupSchedules, "표시할 일정이 없습니다.", (item) => `<article class="entity-card schedule-card selectable-card" data-schedule-card="true" data-schedule-id="${escapeHtml(item.scheduleId)}"><span class="badge">일정</span><strong>${escapeHtml(item.title || "제목 없는 일정")}</strong><div class="meta">${escapeHtml(formatDate(item.scheduledAt) || item.scheduledAt || "일시 미정")} · ${escapeHtml(item.location || item.onlineLink || "장소 미정")}</div><p>${escapeHtml(item.content || "상세 내용이 없습니다.")}</p></article>`);
    document.querySelectorAll("[data-schedule-card]").forEach((card) => card.addEventListener("click", () => selectGroupSchedule(card.dataset.scheduleId)));
  }
  function syncGroupScheduleIds(scheduleId) {
    document.querySelectorAll(".group-schedule-picker, #groupScheduleSelect").forEach((select) => { if (select && scheduleId) select.value = scheduleId; });
  }
  async function loadCurrentGroup() {
    if (!currentGroupId()) { toast("그룹을 먼저 선택하세요.", true); return; }
    window.history.replaceState(null, "", `/group.html?groupId=${encodeURIComponent(currentGroupId())}`);
    const group = await run(() => window.moiApi.request(`/api/groups/${currentGroupId()}`), "그룹 홈을 조회했습니다.");
    renderGroup(group);
    $(".group-schedule-workspace-panel")?.classList.remove("hidden");
    await loadGroupSchedules().catch(() => {});
    await loadGroupAttendanceRates().catch(() => {});
  }
  function openCreateGroupModal() { showPanel("#groupCreateBackdrop"); showPanel("#groupCreatePanel"); document.body.classList.add("modal-open"); }
  function closeCreateGroupModal() { hidePanel("#groupCreateBackdrop"); hidePanel("#groupCreatePanel"); document.body.classList.remove("modal-open"); }
  async function bindGroup() {
    const params = new URLSearchParams(window.location.search);
    const groups = await fetchMyGroups().catch(() => []);
    populateGroupSelector(groups);
    const groupId = params.get("groupId");
    if (groupId && $("#myGroupSelect")) $("#myGroupSelect").value = groupId;
    $("#refreshMyGroupsButton")?.addEventListener("click", async () => populateGroupSelector(await fetchMyGroups(true)));
    $("#loadGroupButton")?.addEventListener("click", loadCurrentGroup);
    $("#openCreateGroupButton")?.addEventListener("click", openCreateGroupModal);
    $("#closeCreateGroupButton")?.addEventListener("click", closeCreateGroupModal);
    $("#groupCreateBackdrop")?.addEventListener("click", closeCreateGroupModal);
    $("#createGroupForm")?.addEventListener("submit", async (event) => { event.preventDefault(); toast("그룹 생성 API가 아직 필요합니다. Part3에서 POST /api/groups 또는 Part2 승인 후 그룹 생성 연결이 필요합니다.", true); });
    $("#openGroupScheduleCreateButton")?.addEventListener("click", () => toggleScheduleCreateForm(true));
    $("#cancelGroupScheduleCreateButton")?.addEventListener("click", () => toggleScheduleCreateForm(false));
    $("#groupLoadSchedulesButton")?.addEventListener("click", loadGroupSchedules);
    $("#groupCreateScheduleForm")?.addEventListener("submit", async (event) => { event.preventDefault(); if (!currentGroupId()) { toast("그룹을 먼저 선택하세요.", true); return; } const form = event.currentTarget; const schedule = await run(() => window.moiApi.request(`/api/groups/${currentGroupId()}/schedules`, { method: "POST", body: window.moiApi.toJsonBody(compact(formData(form))) }), "일정을 생성했습니다."); const createdId = schedule?.scheduleId; form.reset(); setDefaultScheduleTime(); toggleScheduleCreateForm(false); await loadGroupSchedules(); if (createdId) selectGroupSchedule(createdId); });
    $("#groupAnswerAttendanceForm")?.addEventListener("submit", async (event) => { event.preventDefault(); const payload = formData(event.currentTarget); if (!payload.scheduleId) { toast("일정을 먼저 선택하세요.", true); return; } await run(() => window.moiApi.request(`/api/attendance/schedules/${payload.scheduleId}/answers`, { method: "POST", body: window.moiApi.toJsonBody({ response: payload.response }) }), "출석 응답을 저장했습니다."); await loadGroupAttendanceRates().catch(() => {}); });
    $("#groupCheckAttendanceForm")?.addEventListener("submit", async (event) => { event.preventDefault(); const payload = formData(event.currentTarget); if (!payload.scheduleId || !payload.userId) { toast("일정과 그룹원을 선택하세요.", true); return; } await run(() => window.moiApi.request(`/api/attendance/schedules/${payload.scheduleId}/records`, { method: "POST", body: window.moiApi.toJsonBody({ userId: asNumber(payload.userId), status: payload.status }) }), "출석을 체크했습니다."); await loadGroupAttendanceRates().catch(() => {}); });
    $("#groupLoadAttendanceSummaryButton")?.addEventListener("click", async () => { const scheduleId = $("#groupSummaryScheduleSelect")?.value; if (!scheduleId) { toast("일정을 먼저 선택하세요.", true); return; } await run(() => window.moiApi.request(`/api/attendance/schedules/${scheduleId}/records/summary`), "출석 요약을 조회했습니다."); await loadGroupAttendanceRates().catch(() => {}); });
    setDefaultScheduleTime();
    if (window.location.hash === "#create-group") openCreateGroupModal();
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
