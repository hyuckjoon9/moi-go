(function () {
  const $ = (selector) => document.querySelector(selector);
  const page = document.body.dataset.page;
  const protectedPage = document.body.dataset.protected === "true";
  const recruitmentState = { page: 0, size: 10, totalPages: 1, status: "ALL", searchType: "category", items: [], pageData: null };
  const myGroupState = { groups: [], loaded: false };
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
  async function loadRecruitmentDetail(id, showToast = false) {
    showRecruitmentDetailMode();
    const detail = await run(() => window.moiApi.request(`/api/recruitment-posts/${id}`), showToast ? "모집글 상세를 조회했습니다." : null);
    const view = $("#recruitmentDetailView");
    if (!view) return;
    const owner = isOwnRecruitment(detail);
    const actionButtons = owner
      ? `<button class="button ghost" type="button" data-recruitment-action="edit">\uC218\uC815</button><button class="button danger" type="button" data-recruitment-action="delete">\uC0AD\uC81C</button><button class="button" type="button" data-recruitment-action="applications">\uC2E0\uCCAD\uC790 \uAD00\uB9AC</button>`
      : `<button class="button" type="button" data-recruitment-action="apply">\uCC38\uAC00 \uC2E0\uCCAD</button>`;
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
      <div class="detail-actions"><a class="button ghost" href="/recruitments.html">\uBAA9\uB85D\uC73C\uB85C</a>${actionButtons}</div>`;
    view.querySelectorAll("[data-recruitment-action]").forEach((button) => {
      button.addEventListener("click", () => {
        const action = button.dataset.recruitmentAction;
        if (action === "edit" || action === "delete") toast("\uBAA8\uC9D1\uAE00 \uC218\uC815/\uC0AD\uC81C API\uAC00 \uC544\uC9C1 \uD544\uC694\uD569\uB2C8\uB2E4.", true);
        if (action === "applications") toast("\uCC38\uAC00 \uC2E0\uCCAD \uC2B9\uC778/\uAC70\uC808 API\uAC00 \uC544\uC9C1 \uD544\uC694\uD569\uB2C8\uB2E4.", true);
        if (action === "apply") toast("\uCC38\uAC00 \uC2E0\uCCAD API\uAC00 \uC544\uC9C1 \uD544\uC694\uD569\uB2C8\uB2E4.", true);
      });
    });
  }
  function openMemberEditModal() { showPanel("#memberEditBackdrop"); showPanel("#memberEditPanel"); document.body.classList.add("modal-open"); setTimeout(() => $("#updateMemberForm input[name='nickname']")?.focus(), 0); }
  function closeMemberEditModal() { hidePanel("#memberEditBackdrop"); hidePanel("#memberEditPanel"); document.body.classList.remove("modal-open"); }
  async function openRecruitmentCreateModal() {
    const leaders = await fetchMyGroups().catch(() => []);
    const selectable = leaders.filter(isLeaderGroup);
    if (selectable.length === 0) {
      openNeedLeaderGroupModal();
      return;
    }
    populateRecruitmentLeaderGroups(selectable);
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
      <strong>${escapeHtml(group.name || `그룹 ${group.groupId}`)}</strong>
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
    renderGroupSection("#pendingGroupList", "#pendingGroupCount", [], "신청 중인 그룹 조회 API가 아직 필요합니다.", renderPendingApplication);
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
      const payload = formData(event.currentTarget);
      const selectedGroupId = payload.leaderGroupId;
      if (!leaderGroups().some((group) => String(group.groupId) === String(selectedGroupId))) {
        toast("운영자인 그룹을 선택해야 모집글을 작성할 수 있습니다.", true);
        return;
      }
      delete payload.leaderGroupId;
      payload.title = payload.title?.trim() || "";
      payload.category = payload.category?.trim() || "";
      payload.capacity = asNumber(payload.capacity);
      payload.recruitmentDeadline = payload.recruitmentDeadline || null;
      if (!payload.title || !payload.category || !payload.capacity || !payload.recruitmentDeadline) {
        toast("스터디 이름, 카테고리, 모집 인원, 모집 마감일은 필수입니다.", true);
        return;
      }
      const created = await run(() => window.moiApi.request("/api/recruitment-posts", { method: "POST", body: window.moiApi.toJsonBody(payload) }), "\uBAA8\uC9D1\uAE00\uC744 \uB4F1\uB85D\uD588\uC2B5\uB2C8\uB2E4.");
      markRecruitmentAsOwn(created?.id);
      closeRecruitmentCreateModal();
      event.currentTarget.reset();
      if ($("#recruitmentSearchKeyword")) $("#recruitmentSearchKeyword").value = "";
      setRecruitmentStatusFilter("ALL");
      window.history.replaceState(null, "", "/recruitments.html");
      await loadRecruitments(0);
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
  function renderGroupQuickCard(group) {
    return `<button class="entity-card my-group-link group-choice-card" type="button" data-group-id="${escapeHtml(group.groupId)}">
      <strong>${escapeHtml(group.name || `그룹 ${group.groupId}`)}</strong>
      <div class="meta">${escapeHtml(groupRoleLabel(group.role))} · ${escapeHtml(groupStatusLabel(group.status))}</div>
    </button>`;
  }
  function populateGroupSelector(groups) {
    const select = $("#myGroupSelect");
    if (select) {
      const current = select.value || new URLSearchParams(window.location.search).get("groupId") || "";
      select.innerHTML = `<option value="">그룹을 선택하세요</option>${groups.map((group) => `<option value="${escapeHtml(group.groupId)}">${escapeHtml(group.name || `그룹 ${group.groupId}`)} (${escapeHtml(groupRoleLabel(group.role))})</option>`).join("")}`;
      if (current) select.value = current;
    }
    renderCards("#myGroupQuickList", groups, "참여 중인 그룹이 없습니다. 그룹 생성은 백엔드 API가 추가되면 연결됩니다.", renderGroupQuickCard);
    document.querySelectorAll("[data-group-id]").forEach((card) => {
      card.addEventListener("click", () => {
        if ($("#myGroupSelect")) $("#myGroupSelect").value = card.dataset.groupId;
        loadCurrentGroup().catch(() => {});
      });
    });
  }
  function renderGroup(group) {
    if ($("#groupTitle")) $("#groupTitle").textContent = group.name || "그룹 홈";
    if ($("#groupDescription")) $("#groupDescription").textContent = `그룹 ID ${group.groupId} · 모집글 ID ${group.postId}`;
    if ($("#groupSummary")) $("#groupSummary").innerHTML = `<article><strong>${escapeHtml(groupStatusLabel(group.status))}</strong><span>그룹 상태</span></article><article><strong>${escapeHtml(groupRoleLabel(group.myRole))}</strong><span>내 역할</span></article><article><strong>${group.members?.length || 0}명</strong><span>활성 그룹원</span></article><article><strong>${escapeHtml(formatDate(group.createdAt) || "-")}</strong><span>생성일</span></article>`;
    renderCards("#groupMemberList", group.members || [], "활성 그룹원이 없습니다.", (member) => `<article class="entity-card member-card"><span class="badge">${escapeHtml(groupRoleLabel(member.role))}</span><strong>회원 #${escapeHtml(member.userId)}</strong><div class="meta">가입일 ${escapeHtml(formatDate(member.joinedAt) || "-")}</div></article>`);
  }
  async function loadGroupSchedules() {
    if (!currentGroupId()) { toast("그룹을 먼저 선택하세요.", true); return; }
    const data = await run(() => window.moiApi.request(`/api/groups/${currentGroupId()}/schedules?scope=${$("#groupScheduleScope")?.value || "upcoming"}`), "일정을 조회했습니다.");
    renderCards("#groupScheduleList", data.items || [], "표시할 일정이 없습니다.", (item) => `<article class="entity-card schedule-card"><span class="badge">일정 #${item.scheduleId}</span><strong>${escapeHtml(item.title)}</strong><div class="meta">${escapeHtml(item.scheduledAt)} · ${escapeHtml(item.location || item.onlineLink || "장소 미정")}</div></article>`);
  }
  function syncGroupScheduleIds(scheduleId) { ["#groupScheduleId", "#groupSummaryScheduleId"].forEach((selector) => { const input = $(selector); if (input && scheduleId) input.value = scheduleId; }); }
  async function loadCurrentGroup() {
    if (!currentGroupId()) { toast("그룹을 먼저 선택하세요.", true); return; }
    window.history.replaceState(null, "", `/group.html?groupId=${encodeURIComponent(currentGroupId())}`);
    const group = await run(() => window.moiApi.request(`/api/groups/${currentGroupId()}`), "그룹 홈을 조회했습니다.");
    renderGroup(group);
    await loadGroupSchedules().catch(() => {});
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
    $("#groupLoadSchedulesButton")?.addEventListener("click", loadGroupSchedules);
    $("#groupCreateScheduleForm")?.addEventListener("submit", async (event) => { event.preventDefault(); if (!currentGroupId()) { toast("그룹을 먼저 선택하세요.", true); return; } const schedule = await run(() => window.moiApi.request(`/api/groups/${currentGroupId()}/schedules`, { method: "POST", body: window.moiApi.toJsonBody(compact(formData(event.currentTarget))) }), "일정을 생성했습니다."); syncGroupScheduleIds(schedule?.scheduleId); await loadGroupSchedules(); });
    $("#groupAnswerAttendanceForm")?.addEventListener("submit", async (event) => { event.preventDefault(); const payload = formData(event.currentTarget); const response = await run(() => window.moiApi.request(`/api/attendance/schedules/${payload.scheduleId}/answers`, { method: "POST", body: window.moiApi.toJsonBody({ response: payload.response }) }), "출석 응답을 저장했습니다."); json("#groupAttendanceOutput", response); });
    $("#groupCheckAttendanceForm")?.addEventListener("submit", async (event) => { event.preventDefault(); const payload = formData(event.currentTarget); const response = await run(() => window.moiApi.request(`/api/attendance/schedules/${payload.scheduleId}/records`, { method: "POST", body: window.moiApi.toJsonBody({ userId: asNumber(payload.userId), status: payload.status }) }), "출석을 체크했습니다."); json("#groupAttendanceOutput", response); });
    $("#groupLoadAttendanceSummaryButton")?.addEventListener("click", async () => { const response = await run(() => window.moiApi.request(`/api/attendance/schedules/${$("#groupSummaryScheduleId").value}/records/summary`), "출석 요약을 조회했습니다."); json("#groupAttendanceOutput", response); });
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
