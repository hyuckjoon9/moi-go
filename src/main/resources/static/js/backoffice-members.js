(function () {
  const state = { page: 0, size: 20, filters: {}, selectedId: null };
  const loginUrl = `/login.html?returnUrl=${encodeURIComponent(window.location.pathname + window.location.search)}`;
  const $ = (selector) => document.querySelector(selector);

  function showState(message, kind = "info") {
    const target = $("#boState");
    target.textContent = message;
    target.dataset.kind = kind;
    target.hidden = false;
  }

  function clearState() { $("#boState").hidden = true; }
  function label(value) { return ({ ACTIVE: "활성", SUSPENDED: "정지", WITHDRAWN: "탈퇴", USER: "일반 회원", ADMIN: "관리자", LEADER: "리더", MANAGER: "매니저", MEMBER: "멤버", ENDED: "종료" })[value] || value; }
  function formatDate(value) { return value ? new Date(value).toLocaleString("ko-KR") : "-"; }

  async function requireAdmin() {
    if (!window.moiAuth.isSignedIn()) { window.location.replace(loginUrl); return null; }
    const member = await window.moiApi.request("/api/members/me");
    if (member.role !== "ADMIN" || member.status !== "ACTIVE") { $("#boMembers").hidden = true; showState("관리자 권한이 필요한 화면입니다.", "forbidden"); return null; }
    $("#boAdminName").textContent = member.nickname;
    return member;
  }

  function query() {
    const params = new URLSearchParams({ page: String(state.page), size: String(state.size) });
    Object.entries(state.filters).forEach(([key, value]) => { if (value) params.set(key, value); });
    return params.toString();
  }

  function renderList(data) {
    $("#memberCount").textContent = `${data.totalElements}명`;
    const list = $("#memberList"); list.replaceChildren();
    if (!data.items.length) { list.innerHTML = '<p class="empty-state">조건에 맞는 회원이 없습니다.</p>'; return; }
    data.items.forEach((member) => {
      const card = document.createElement("button"); card.type = "button"; card.className = "entity-card recruitment-card";
      const title = document.createElement("strong"); title.textContent = member.nickname;
      const meta = document.createElement("span"); meta.className = "meta"; meta.textContent = `${member.email} · ${label(member.role)} · ${label(member.status)}`;
      const date = document.createElement("span"); date.className = "meta"; date.textContent = `가입 ${formatDate(member.createdAt)}`;
      card.append(title, meta, date); card.addEventListener("click", () => loadDetail(member.memberId)); list.append(card);
    });
    const pagination = $("#memberPagination"); pagination.replaceChildren();
    for (let page = 0; page < data.totalPages; page += 1) { const button = document.createElement("button"); button.type = "button"; button.className = `page-number${page === data.page ? " active" : ""}`; button.textContent = String(page + 1); button.addEventListener("click", () => { state.page = page; loadMembers(); }); pagination.append(button); }
  }

  async function loadMembers() {
    clearState();
    try { renderList(await window.moiApi.request(`/api/admin/members?${query()}`)); }
    catch (error) { handleError(error, "회원 목록을 불러오지 못했습니다."); }
  }

  function renderDetail(member) {
    const detail = $("#memberDetail"); detail.replaceChildren();
    const profile = document.createElement("div"); profile.className = "profile-details";
    [["이메일", member.email], ["닉네임", member.nickname], ["역할", label(member.role)], ["상태", label(member.status)], ["가입", formatDate(member.createdAt)], ["수정", formatDate(member.updatedAt)], ["소개", member.bio || "-"], ["관심사", member.interests || "-"]].forEach(([name, value]) => { const row = document.createElement("div"); const key = document.createElement("span"); key.textContent = name; const text = document.createElement("strong"); text.textContent = value; row.append(key, text); profile.append(row); });
    detail.append(profile);
    const groups = document.createElement("p"); groups.className = "helper-text"; groups.textContent = member.groups.length ? `참여 그룹: ${member.groups.map((group) => `${group.name} (${label(group.role)} · ${label(group.status)})`).join(", ")}` : "참여 그룹이 없습니다."; detail.append(groups);
    const actions = document.createElement("p"); actions.className = "helper-text"; actions.textContent = member.recentActions.length ? `최근 조치: ${member.recentActions.map((action) => `${label(action.action)} · ${action.reason}`).join(" / ")}` : "최근 운영 조치가 없습니다."; detail.append(actions);
    const badge = $("#memberStatus"); badge.textContent = label(member.status); badge.hidden = false;
    const form = $("#memberStatusForm"); form.hidden = member.role !== "USER" || member.status === "WITHDRAWN"; form.memberId.value = member.memberId; form.expectedStatus.value = member.status; form.status.value = member.status === "ACTIVE" ? "SUSPENDED" : "ACTIVE";
  }

  async function loadDetail(memberId) {
    try { const member = await window.moiApi.request(`/api/admin/members/${memberId}`); state.selectedId = memberId; renderDetail(member); }
    catch (error) { handleError(error, "회원 상세를 불러오지 못했습니다."); }
  }

  function handleError(error, fallback) {
    if (error.status === 401) { window.location.replace(loginUrl); return; }
    if (error.status === 403) { $("#boMembers").hidden = true; showState("관리자 권한이 필요한 화면입니다.", "forbidden"); return; }
    showState(error.message || fallback, "error");
  }

  function bindEvents() {
    $("#memberFilters").addEventListener("submit", (event) => { event.preventDefault(); const form = new FormData(event.currentTarget); state.filters = { keyword: String(form.get("keyword") || "").trim(), role: String(form.get("role") || ""), status: String(form.get("status") || "") }; state.page = 0; loadMembers(); });
    $("#memberStatusForm").addEventListener("submit", async (event) => { event.preventDefault(); const form = new FormData(event.currentTarget); const memberId = form.get("memberId"); try { await window.moiApi.request(`/api/admin/members/${memberId}/status`, { method: "PATCH", body: window.moiApi.toJsonBody({ expectedStatus: form.get("expectedStatus"), status: form.get("status"), reason: String(form.get("reason")).trim() }) }); event.currentTarget.reason.value = ""; await Promise.all([loadMembers(), loadDetail(memberId)]); } catch (error) { if (error.status === 409) { await loadDetail(memberId); showState("최신 상태를 불러왔습니다. 다시 확인해 주세요.", "info"); return; } handleError(error, "회원 상태를 변경하지 못했습니다."); } });
  }

  document.addEventListener("DOMContentLoaded", async () => { try { if (await requireAdmin()) { bindEvents(); loadMembers(); } } catch (error) { handleError(error, "관리자 정보를 확인하지 못했습니다."); } });
})();
