(function () {
  const LOGIN_URL = `/login.html?returnUrl=${encodeURIComponent(
    window.location.pathname + window.location.search
  )}`;
  const THEME_KEY = "moi-go-backoffice-theme";

  // State Management
  const appState = {
    currentView: "dashboard",
    adminMember: null,
    dashboardData: null,
    membersData: { page: 0, size: 10, filters: {}, items: [], totalElements: 0, totalPages: 0 },
    recruitmentsData: { page: 0, size: 10, filters: {}, items: [], totalElements: 0, totalPages: 0 },
    operationsFilters: {},
    auditFilters: {},
    selectedMemberId: null,
    notifications: [],
    theme: "dark"
  };

  // DOM Helpers
  const $ = (selector) => document.querySelector(selector);
  const $$ = (selector) => document.querySelectorAll(selector);

  /* ==========================================================================
     Toast Notification System
     ========================================================================== */
  function showToast(message, type = "info") {
    const container = $("#boToastContainer");
    if (!container) return;
    const toast = document.createElement("div");
    toast.className = `bo-toast ${type}`;
    const icon = type === "success" ? "✅" : type === "error" ? "⚠️" : "ℹ️";
    toast.innerHTML = `<span>${icon}</span><span>${message}</span>`;
    container.appendChild(toast);
    setTimeout(() => {
      toast.style.opacity = "0";
      toast.style.transform = "translateX(50px)";
      toast.style.transition = "all 0.3s ease";
      setTimeout(() => toast.remove(), 300);
    }, 3500);
  }

  /* ==========================================================================
     Theme System
     ========================================================================== */
  function applyTheme(theme) {
    appState.theme = theme;
    document.documentElement.setAttribute("data-theme", theme);
    localStorage.setItem(THEME_KEY, theme);
    const toggleBtn = $("#boThemeToggle");
    if (toggleBtn) {
      toggleBtn.innerHTML = theme === "dark" ? "<span>☀</span>" : "<span>◐</span>";
      toggleBtn.setAttribute("aria-label", theme === "dark" ? "라이트 모드로 전환" : "다크 모드로 전환");
    }
  }

  function initTheme() {
    const saved = localStorage.getItem(THEME_KEY);
    const preferred = saved || (window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light");
    applyTheme(preferred);
    $("#boThemeToggle")?.addEventListener("click", () => {
      applyTheme(appState.theme === "dark" ? "light" : "dark");
      showToast(`테마가 ${appState.theme === "dark" ? "다크" : "라이트"} 모드로 변경되었습니다.`, "info");
    });
    $("#boSettingsThemeBtn")?.addEventListener("click", () => {
      applyTheme(appState.theme === "dark" ? "light" : "dark");
    });
  }

  /* ==========================================================================
     SPA Navigation Router
     ========================================================================== */
  function switchView(viewName) {
    if (!["dashboard", "members", "recruitments", "operations", "audit", "settings"].includes(viewName)) {
      viewName = "dashboard";
    }
    appState.currentView = viewName;
    window.location.hash = viewName;

    $$(".bo-nav-item").forEach((el) => {
      if (el.dataset.view === viewName) el.classList.add("active");
      else el.classList.remove("active");
    });

    $$(".bo-view").forEach((el) => el.classList.remove("active"));
    $(`#view-${viewName}`)?.classList.add("active");

    // Load View Data
    if (viewName === "members") loadMembers();
    if (viewName === "recruitments") loadRecruitments();
    if (viewName === "operations") loadOperations();
    if (viewName === "audit") loadAuditLogs();
  }

  function initRouter() {
    const hash = window.location.hash.replace("#", "");
    if (hash) switchView(hash);

    window.addEventListener("hashchange", () => {
      const newHash = window.location.hash.replace("#", "");
      if (newHash && newHash !== appState.currentView) {
        switchView(newHash);
      }
    });

    $$("[data-view]").forEach((btn) => {
      btn.addEventListener("click", (e) => {
        e.preventDefault();
        switchView(btn.dataset.view);
      });
    });

    $$("[data-view-trigger]").forEach((btn) => {
      btn.addEventListener("click", () => switchView(btn.dataset.viewTrigger));
    });
  }

  /* ==========================================================================
     Auth & Access Guard
     ========================================================================== */
  function redirectNonAdmin() {
    const modal = $("#boAccessModal");
    if (modal) modal.hidden = false;
    setTimeout(() => window.location.replace("/index.html"), 2200);
  }

  async function requireAdmin() {
    if (!window.moiAuth || !window.moiAuth.isSignedIn()) {
      window.location.replace(LOGIN_URL);
      return null;
    }
    try {
      const member = await window.moiApi.request("/api/members/me");
      if (member.role !== "ADMIN" || member.status !== "ACTIVE") {
        redirectNonAdmin();
        return null;
      }
      appState.adminMember = member;
      $("#boAdminName").textContent = member.nickname;
      return member;
    } catch (e) {
      redirectNonAdmin();
      return null;
    }
  }

  /* ==========================================================================
     Dashboard & Charts (Actual Data Render)
     ========================================================================== */
  function setMetric(name, value) {
    const el = document.querySelector(`[data-metric="${name}"]`);
    if (el) el.textContent = value == null ? "-" : new Intl.NumberFormat("ko-KR").format(value);
  }

  function formatDate(value) {
    return value ? new Date(value).toLocaleString("ko-KR") : "-";
  }

  function formatAuditAction(action) {
    return ({
      MEMBER_STATUS_CHANGED: "회원 상태 변경",
      RECRUITMENT_HIDDEN: "모집글 숨김",
      RECRUITMENT_RESTORED: "모집글 복구"
    })[action] || action;
  }

  function renderSVGDonut(members) {
    const svg = $("#memberDonutChart");
    const legend = $("#memberDonutLegend");
    if (!svg || !legend) return;

    const total = (members.active || 0) + (members.suspended || 0) + (members.withdrawn || 0);
    if (total === 0) {
      svg.innerHTML = `<text x="21" y="23" text-anchor="middle" fill="var(--bo-text-muted)" font-size="4">데이터 없음</text>`;
      legend.innerHTML = `<p style="font-size: 0.8rem; color: var(--bo-text-muted);">회원 데이터가 없습니다.</p>`;
      return;
    }

    const activePct = ((members.active || 0) / total) * 100;
    const suspPct = ((members.suspended || 0) / total) * 100;
    const withPct = ((members.withdrawn || 0) / total) * 100;

    // SVG donut circles using stroke-dasharray (r=15.91549430918954 => circumference=100)
    let dashOffset = 25;
    svg.innerHTML = `
      <circle cx="21" cy="21" r="15.915" fill="transparent" stroke="rgba(255,255,255,0.05)" stroke-width="5"></circle>
      <circle cx="21" cy="21" r="15.915" fill="transparent" stroke="var(--bo-success)" stroke-width="5"
              stroke-dasharray="${activePct} ${100 - activePct}" stroke-dashoffset="${dashOffset}"></circle>
    `;
    dashOffset -= activePct;
    if (suspPct > 0) {
      svg.innerHTML += `
        <circle cx="21" cy="21" r="15.915" fill="transparent" stroke="var(--bo-warning)" stroke-width="5"
                stroke-dasharray="${suspPct} ${100 - suspPct}" stroke-dashoffset="${dashOffset}"></circle>
      `;
      dashOffset -= suspPct;
    }
    if (withPct > 0) {
      svg.innerHTML += `
        <circle cx="21" cy="21" r="15.915" fill="transparent" stroke="var(--bo-danger)" stroke-width="5"
                stroke-dasharray="${withPct} ${100 - withPct}" stroke-dashoffset="${dashOffset}"></circle>
      `;
    }
    svg.innerHTML += `<text x="21" y="22" text-anchor="middle" fill="var(--bo-text-primary)" font-weight="bold" font-size="5">${total}명</text>`;

    legend.innerHTML = `
      <div class="bo-legend-item">
        <span class="bo-legend-color" style="background: var(--bo-success);"></span>
        <span>활성 회원: <strong>${members.active || 0}</strong>명 (${activePct.toFixed(1)}%)</span>
      </div>
      <div class="bo-legend-item">
        <span class="bo-legend-color" style="background: var(--bo-warning);"></span>
        <span>정지 회원: <strong>${members.suspended || 0}</strong>명 (${suspPct.toFixed(1)}%)</span>
      </div>
      <div class="bo-legend-item">
        <span class="bo-legend-color" style="background: var(--bo-danger);"></span>
        <span>탈퇴 회원: <strong>${members.withdrawn || 0}</strong>명 (${withPct.toFixed(1)}%)</span>
      </div>
    `;
  }

  function renderBarChart(recruitments, groups) {
    const container = $("#recruitmentGroupBarChart");
    if (!container) return;

    const maxVal = Math.max(
      recruitments.recruiting || 0,
      recruitments.active || 0,
      groups.active || 0,
      1
    );

    const getWidthPct = (val) => Math.min(100, Math.max(8, ((val || 0) / maxVal) * 100));

    container.innerHTML = `
      <div>
        <div style="display: flex; justify-content: space-between; font-size: 0.8rem; margin-bottom: 0.25rem;">
          <span>모집 중 모집글 (RECRUITING)</span>
          <strong style="color: var(--bo-accent-cyan);">${recruitments.recruiting || 0}건</strong>
        </div>
        <div style="height: 8px; background: rgba(0,0,0,0.3); border-radius: 4px; overflow: hidden;">
          <div style="width: ${getWidthPct(recruitments.recruiting)}%; height: 100%; background: linear-gradient(90deg, var(--bo-primary), var(--bo-accent-cyan)); border-radius: 4px; transition: width 0.5s ease;"></div>
        </div>
      </div>

      <div>
        <div style="display: flex; justify-content: space-between; font-size: 0.8rem; margin-bottom: 0.25rem;">
          <span>진행 중 모집글 (ACTIVE)</span>
          <strong style="color: var(--bo-primary);">${recruitments.active || 0}건</strong>
        </div>
        <div style="height: 8px; background: rgba(0,0,0,0.3); border-radius: 4px; overflow: hidden;">
          <div style="width: ${getWidthPct(recruitments.active)}%; height: 100%; background: var(--bo-primary); border-radius: 4px; transition: width 0.5s ease;"></div>
        </div>
      </div>

      <div>
        <div style="display: flex; justify-content: space-between; font-size: 0.8rem; margin-bottom: 0.25rem;">
          <span>활성 스터디 그룹 (ACTIVE)</span>
          <strong style="color: var(--bo-success);">${groups.active || 0}개</strong>
        </div>
        <div style="height: 8px; background: rgba(0,0,0,0.3); border-radius: 4px; overflow: hidden;">
          <div style="width: ${getWidthPct(groups.active)}%; height: 100%; background: var(--bo-success); border-radius: 4px; transition: width 0.5s ease;"></div>
        </div>
      </div>
    `;
  }

  function renderRecentActions(actions) {
    const list = $("#boRecentActions");
    const auditTable = $("#boAuditTableBody");
    if (!list) return;

    list.replaceChildren();
    if (auditTable) auditTable.replaceChildren();

    if (!actions || !actions.length) {
      list.appendChild(
              createEmptyState(
                      "◌",
                      "아직 운영 조치가 없습니다.",
                      "회원 상태를 변경하면 조치 이력이 이곳에 기록됩니다."));
      if (auditTable) {
        renderEmptyTableRow(
                auditTable,
                5,
                "◌",
                "표시할 감사 이력이 없습니다.",
                "새 운영 조치가 발생하면 이 목록에 추가됩니다.");
      }
      return;
    }

    // Populate Dashboard Feed List & Notification Dropdown
    appState.notifications = actions;
    updateNotificationDropdown(actions);

    actions.forEach((act) => {
      const card = document.createElement("div");
      card.className = "bo-glass-panel";
      card.style.padding = "0.85rem 1.25rem";
      card.style.fontSize = "0.85rem";
      card.style.display = "flex";
      card.style.justifyContent = "space-between";
      card.style.alignItems = "center";

      card.innerHTML = `
        <div>
          <div style="font-weight: 700; color: var(--bo-text-primary); margin-bottom: 0.2rem;">
            ${act.targetLabel} <span class="bo-chip ${act.action === "SUSPENDED" ? "suspended" : "active"}">${act.action}</span>
          </div>
          <div style="color: var(--bo-text-secondary); font-size: 0.8rem;">
            사유: ${act.reason} (대상 #${act.targetId} · 관리자 #${act.adminId})
          </div>
        </div>
        <time style="color: var(--bo-text-muted); font-size: 0.75rem;">${formatDate(act.createdAt)}</time>
      `;
      list.appendChild(card);

      // Populate Audit Stream View Table
      if (auditTable) {
        const row = document.createElement("tr");
        row.innerHTML = `
          <td>${formatDate(act.createdAt)}</td>
          <td><code style="color: var(--bo-accent-purple);">#${act.adminId}</code></td>
          <td><strong>${act.targetLabel}</strong> <small style="color: var(--bo-text-muted);">(${act.targetType} #${act.targetId})</small></td>
          <td><span class="bo-chip ${act.action === "SUSPENDED" ? "suspended" : "active"}">${act.action}</span></td>
          <td>${act.reason}</td>
        `;
        auditTable.appendChild(row);
      }
    });
  }

  function updateNotificationDropdown(actions) {
    const badge = $("#boUnreadBadge");
    const count = $("#boNotifCount");
    const list = $("#boNotifList");

    if (badge) {
      badge.hidden = !actions.length;
    }
    if (count) {
      count.textContent = `${actions.length}건`;
    }
    if (list) {
      list.replaceChildren();
      if (!actions.length) {
        list.innerHTML = `<p style="font-size: 0.8rem; color: var(--bo-text-muted); text-align: center;">알림이 없습니다.</p>`;
        return;
      }
      actions.slice(0, 5).forEach((act) => {
        const item = document.createElement("div");
        item.className = "bo-notification-item";
        item.innerHTML = `
          <strong style="color: var(--bo-text-primary);">${act.targetLabel} 상태 변경</strong>
          <span style="color: var(--bo-text-secondary);">${act.action}: ${act.reason}</span>
          <time>${formatDate(act.createdAt)}</time>
        `;
        list.appendChild(item);
      });
    }
  }

  async function loadDashboard() {
    try {
      const data = await window.moiApi.request("/api/admin/dashboard");
      appState.dashboardData = data;

      // Metric Binding
      setMetric("members.total", data.members.total);
      setMetric("members.active", data.members.active);
      setMetric("members.suspended", data.members.suspended);
      setMetric("members.withdrawn", data.members.withdrawn);
      setMetric("recruitments.recruiting", data.recruitments.recruiting);
      setMetric("recruitments.closed", data.recruitments.closed);
      setMetric("recruitments.active", data.recruitments.active);
      setMetric("recruitments.ended", data.recruitments.ended);
      setMetric("groups.active", data.groups.active);
      setMetric("groups.ended", data.groups.ended);

      // Stat Section elements
      if ($("#recStatRecruiting")) $("#recStatRecruiting").textContent = `${data.recruitments.recruiting || 0}건`;
      if ($("#recStatClosed")) $("#recStatClosed").textContent = `${data.recruitments.closed || 0}건`;
      if ($("#recStatActive")) $("#recStatActive").textContent = `${data.recruitments.active || 0}건`;
      if ($("#grpStatActive")) $("#grpStatActive").textContent = `${data.groups.active || 0}개`;
      if ($("#grpStatEnded")) $("#grpStatEnded").textContent = `${data.groups.ended || 0}개`;

      // Render Donut & Bar Charts
      renderSVGDonut(data.members);
      renderBarChart(data.recruitments, data.groups);
      renderRecentActions(data.recentActions);
    } catch (e) {
      showToast("대시보드 데이터를 불러오는데 실패했습니다.", "error");
    }
  }

  /* ==========================================================================
     Member Directory Management & Actions
     ========================================================================== */
  function getQueryString() {
    const params = new URLSearchParams({
      page: String(appState.membersData.page),
      size: String(appState.membersData.size)
    });
    Object.entries(appState.membersData.filters).forEach(([key, val]) => {
      if (val) params.set(key, val);
    });
    return params.toString();
  }

  function getStatusChip(status) {
    const cls = status === "ACTIVE" ? "active" : status === "SUSPENDED" ? "suspended" : "withdrawn";
    const label = status === "ACTIVE" ? "활성" : status === "SUSPENDED" ? "정지" : "탈퇴";
    return `<span class="bo-chip ${cls}">${label}</span>`;
  }

  function getRoleChip(role) {
    if (role === "ADMIN") return `<span class="bo-chip admin">ADMIN</span>`;
    return `<span style="font-size: 0.75rem; color: var(--bo-text-muted);">USER</span>`;
  }

  function createEmptyState(icon, title, description) {
    const empty = document.createElement("div");
    empty.className = "bo-empty-state";

    const symbol = document.createElement("span");
    symbol.className = "bo-empty-state-icon";
    symbol.setAttribute("aria-hidden", "true");
    symbol.textContent = icon;

    const heading = document.createElement("strong");
    heading.textContent = title;
    const copy = document.createElement("p");
    copy.textContent = description;
    empty.append(symbol, heading, copy);
    return empty;
  }

  function renderEmptyTableRow(tbody, colspan, icon, title, description) {
    const row = document.createElement("tr");
    const cell = document.createElement("td");
    cell.colSpan = colspan;
    cell.className = "bo-empty-table-cell";
    cell.append(createEmptyState(icon, title, description));
    row.appendChild(cell);
    tbody.appendChild(row);
  }

  function openStatusModal(member) {
    const modal = $("#boStatusModal");
    const form = $("#boStatusModalForm");
    if (!modal || !form) return;

    form.memberId.value = member.memberId;
    form.expectedStatus.value = member.status;
    form.status.value = member.status === "ACTIVE" ? "SUSPENDED" : "ACTIVE";
    $("#boModalMemberNick").textContent = `${member.nickname} (${member.email})`;
    form.reason.value = "";
    modal.classList.add("show");
  }

  function renderMembersTable(data) {
    const tbody = $("#boMemberListTable");
    const pagination = $("#boMemberPagination");
    if (!tbody) return;

    tbody.replaceChildren();
    if (!data.items || !data.items.length) {
      renderEmptyTableRow(
              tbody,
              6,
              "⌕",
              "조건에 맞는 회원이 없습니다.",
              "검색어 또는 필터를 조정한 뒤 다시 확인하세요.");
      pagination.replaceChildren();
      return;
    }

    data.items.forEach((m) => {
      const tr = document.createElement("tr");
      tr.innerHTML = `
        <td><code style="color: var(--bo-text-muted);">#${m.memberId}</code></td>
        <td>
          <strong style="color: var(--bo-text-primary); display: block;">${m.nickname}</strong>
          <span style="font-size: 0.8rem; color: var(--bo-text-secondary);">${m.email}</span>
        </td>
        <td>${getRoleChip(m.role)}</td>
        <td>${getStatusChip(m.status)}</td>
        <td><span style="font-size: 0.8rem; color: var(--bo-text-muted);">${formatDate(m.createdAt)}</span></td>
        <td>
          ${
            m.role !== "ADMIN" && m.status !== "WITHDRAWN"
              ? `<button class="bo-btn ghost" style="padding: 0.35rem 0.75rem; font-size: 0.75rem;" data-action="status" data-id="${m.memberId}">상태 변경</button>`
              : `<span style="font-size: 0.75rem; color: var(--bo-text-muted);">-</span>`
          }
        </td>
      `;

      tr.addEventListener("click", (e) => {
        if (e.target.dataset.action === "status") {
          e.stopPropagation();
          openStatusModal(m);
        } else {
          loadMemberDetail(m.memberId);
        }
      });
      tbody.appendChild(tr);
    });

    // Pagination
    if (pagination) {
      pagination.replaceChildren();
      for (let p = 0; p < data.totalPages; p++) {
        const btn = document.createElement("button");
        btn.type = "button";
        btn.className = `bo-btn ${p === data.page ? "" : "ghost"}`;
        btn.style.padding = "0.35rem 0.75rem";
        btn.style.fontSize = "0.8rem";
        btn.textContent = String(p + 1);
        btn.addEventListener("click", () => {
          appState.membersData.page = p;
          loadMembers();
        });
        pagination.appendChild(btn);
      }
    }
  }

  async function loadMembers() {
    try {
      const data = await window.moiApi.request(`/api/admin/members?${getQueryString()}`);
      appState.membersData.items = data.items;
      appState.membersData.totalElements = data.totalElements;
      appState.membersData.totalPages = data.totalPages;
      renderMembersTable(data);
    } catch (e) {
      showToast("회원 목록을 불러오지 못했습니다.", "error");
    }
  }

  async function loadMemberDetail(memberId) {
    try {
      const m = await window.moiApi.request(`/api/admin/members/${memberId}`);
      appState.selectedMemberId = memberId;
      const panel = $("#boMemberDetailPanel");
      const body = $("#boMemberDetailBody");
      const chip = $("#boDetailStatusChip");

      if (!panel || !body) return;

      chip.outerHTML = `<span id="boDetailStatusChip">${getStatusChip(m.status)}</span>`;

      body.innerHTML = `
        <div style="display: flex; flex-direction: column; gap: 0.65rem;">
          <div><span style="font-size: 0.8rem; color: var(--bo-text-muted);">닉네임:</span> <strong>${m.nickname}</strong></div>
          <div><span style="font-size: 0.8rem; color: var(--bo-text-muted);">이메일:</span> <span>${m.email}</span></div>
          <div><span style="font-size: 0.8rem; color: var(--bo-text-muted);">가입일:</span> <span>${formatDate(m.createdAt)}</span></div>
          <div><span style="font-size: 0.8rem; color: var(--bo-text-muted);">자기소개:</span> <p style="margin: 0.2rem 0; font-size: 0.85rem; color: var(--bo-text-secondary);">${m.bio || "없음"}</p></div>
        </div>

        <div style="display: flex; flex-direction: column; gap: 0.65rem;">
          <div><span style="font-size: 0.8rem; color: var(--bo-text-muted);">참여 스터디 그룹:</span>
            <div style="font-size: 0.85rem; color: var(--bo-text-secondary); margin-top: 0.2rem;">
              ${m.groups.length ? m.groups.map(g => `${g.name} (${g.role})`).join(", ") : "참여 그룹 없음"}
            </div>
          </div>
          <div><span style="font-size: 0.8rem; color: var(--bo-text-muted);">최근 운영 조치 이력:</span>
            <div style="font-size: 0.85rem; color: var(--bo-text-secondary); margin-top: 0.2rem;">
              ${m.recentActions.length ? m.recentActions.map(a => `${formatAuditAction(a.action)} · ${formatDate(a.createdAt)} · ${a.reason}`).join("<br/>") : "조치 이력 없음"}
            </div>
          </div>
        </div>
      `;
      panel.hidden = false;
      panel.scrollIntoView({ behavior: "smooth" });
    } catch (e) {
      showToast("회원 상세 정보를 불러오지 못했습니다.", "error");
    }
  }

  function initMemberEvents() {
    $("#boMemberFilterForm")?.addEventListener("submit", (e) => {
      e.preventDefault();
      const fd = new FormData(e.currentTarget);
      appState.membersData.filters = {
        keyword: String(fd.get("keyword") || "").trim(),
        role: String(fd.get("role") || ""),
        status: String(fd.get("status") || "")
      };
      appState.membersData.page = 0;
      loadMembers();
    });

    // Status Modal Actions
    $("#boStatusModalClose")?.addEventListener("click", () => {
      $("#boStatusModal")?.classList.remove("show");
    });

    $("#boStatusModalForm")?.addEventListener("submit", async (e) => {
      e.preventDefault();
      const fd = new FormData(e.currentTarget);
      const memberId = fd.get("memberId");
      const reason = String(fd.get("reason") || "").trim();
      if (reason.length < 5) {
        showToast("조치 사유를 5자 이상 입력하세요.", "error");
        return;
      }

      const submitButton = e.currentTarget.querySelector('button[type="submit"]');
      try {
        if (submitButton) submitButton.disabled = true;
        await window.moiApi.request(`/api/admin/members/${memberId}/status`, {
          method: "PATCH",
          body: window.moiApi.toJsonBody({
            expectedStatus: fd.get("expectedStatus"),
            status: fd.get("status"),
            reason
          })
        });
        showToast("회원 이용 상태가 성공적으로 변경되었습니다.", "success");
        $("#boStatusModal")?.classList.remove("show");

        // Reload data concurrently
        await Promise.all([loadMembers(), loadDashboard()]);
        if (String(appState.selectedMemberId) === String(memberId)) {
          loadMemberDetail(memberId);
        }
      } catch (err) {
        if (err.status === 409) {
          await Promise.all([loadMembers(), loadMemberDetail(memberId)]);
          showToast("회원 상태가 변경되어 최신 정보를 불러왔습니다.", "info");
          return;
        }
        showToast(err.message || "회원 상태 변경에 실패했습니다.", "error");
      } finally {
        if (submitButton) submitButton.disabled = false;
      }
    });
  }

  function getRecruitmentQueryString() {
    const params = new URLSearchParams({ page: String(appState.recruitmentsData.page), size: String(appState.recruitmentsData.size) });
    Object.entries(appState.recruitmentsData.filters).forEach(([key, value]) => { if (value) params.set(key, value); });
    return params.toString();
  }

  function visibilityChip(visibility) {
    return `<span class="bo-chip ${visibility === "VISIBLE" ? "active" : "suspended"}">${visibility === "VISIBLE" ? "노출" : "숨김"}</span>`;
  }

  function renderRecruitmentsTable(data) {
    const tbody = $("#boRecruitmentListTable");
    const pagination = $("#boRecruitmentPagination");
    if (!tbody || !pagination) return;
    tbody.replaceChildren();
    if (!data.items?.length) {
      renderEmptyTableRow(tbody, 7, "⌕", "조건에 맞는 모집글이 없습니다.", "검색어 또는 필터를 조정한 뒤 다시 확인하세요.");
      pagination.replaceChildren();
      return;
    }
    data.items.forEach((item) => {
      const row = document.createElement("tr");
      row.innerHTML = `<td><code>#${item.recruitmentId}</code></td><td><strong>${item.title}</strong><br><small>${item.category || "미분류"}</small></td><td>${item.leaderNickname}</td><td>${item.status}</td><td>${visibilityChip(item.visibility)}</td><td>${formatDate(item.createdAt)}</td><td><button class="bo-btn ghost" data-action="visibility" data-id="${item.recruitmentId}">${item.visibility === "VISIBLE" ? "숨김" : "복구"}</button></td>`;
      row.addEventListener("click", (event) => {
        if (event.target.dataset.action === "visibility") {
          event.stopPropagation();
          loadRecruitmentDetail(item.recruitmentId, true);
        } else loadRecruitmentDetail(item.recruitmentId);
      });
      tbody.appendChild(row);
    });
    pagination.replaceChildren();
    for (let page = 0; page < data.totalPages; page++) {
      const button = document.createElement("button");
      button.type = "button"; button.className = `bo-btn ${page === data.page ? "" : "ghost"}`; button.textContent = String(page + 1);
      button.addEventListener("click", () => { appState.recruitmentsData.page = page; loadRecruitments(); });
      pagination.appendChild(button);
    }
  }

  async function loadRecruitments() {
    try {
      const data = await window.moiApi.request(`/api/admin/recruitments?${getRecruitmentQueryString()}`);
      Object.assign(appState.recruitmentsData, data);
      renderRecruitmentsTable(data);
    } catch (error) { showToast("모집글 목록을 불러오지 못했습니다.", "error"); }
  }

  async function loadRecruitmentDetail(recruitmentId, focusReason = false) {
    try {
      const item = await window.moiApi.request(`/api/admin/recruitments/${recruitmentId}`);
      const panel = $("#boRecruitmentDetailPanel"); const body = $("#boRecruitmentDetailBody");
      if (!panel || !body) return;
      const recentActions = item.recentActions?.length
        ? item.recentActions.map((action) => `${formatAuditAction(action.action)} · ${formatDate(action.createdAt)} · ${action.reason}`).join("<br>")
        : "조치 이력 없음";
      body.innerHTML = `<div style="display:grid;grid-template-columns:1fr 1fr;gap:1rem;"><div><p><strong>${item.title}</strong></p><p>모집장 ID: ${item.leaderId}</p><p>모집 상태: ${item.status}</p><p>노출 상태: ${visibilityChip(item.visibility)}</p><p>${item.description || "상세 내용 없음"}</p><div style="margin-top:1rem;"><span style="font-size:.8rem;color:var(--bo-text-muted);">최근 운영 조치 이력</span><div style="font-size:.85rem;color:var(--bo-text-secondary);margin-top:.35rem;">${recentActions}</div></div></div><form id="boRecruitmentVisibilityForm"><input type="hidden" name="recruitmentId" value="${item.recruitmentId}"><input type="hidden" name="expectedVisibility" value="${item.visibility}"><label class="form-label">조치 사유<textarea name="reason" class="bo-input form-control" minlength="5" maxlength="500" required></textarea></label><button class="bo-btn ${item.visibility === "VISIBLE" ? "danger" : ""}" type="submit">${item.visibility === "VISIBLE" ? "모집글 숨김" : "모집글 복구"}</button></form></div>`;
      panel.hidden = false;
      if (focusReason) body.querySelector("textarea")?.focus();
      panel.scrollIntoView({ behavior: "smooth" });
    } catch (error) { showToast("모집글 상세 정보를 불러오지 못했습니다.", "error"); }
  }

  function initRecruitmentEvents() {
    $("#boRecruitmentFilterForm")?.addEventListener("submit", (event) => {
      event.preventDefault(); const form = new FormData(event.currentTarget);
      appState.recruitmentsData.filters = { keyword: String(form.get("keyword") || "").trim(), status: String(form.get("status") || ""), visibility: String(form.get("visibility") || "") };
      appState.recruitmentsData.page = 0; loadRecruitments();
    });
    $("#boRecruitmentDetailPanel")?.addEventListener("submit", async (event) => {
      if (event.target.id !== "boRecruitmentVisibilityForm") return;
      event.preventDefault(); const form = new FormData(event.target); const reason = String(form.get("reason") || "").trim();
      if (reason.length < 5) { showToast("조치 사유를 5자 이상 입력하세요.", "error"); return; }
      const visibility = form.get("expectedVisibility") === "VISIBLE" ? "HIDDEN" : "VISIBLE";
      try {
        await window.moiApi.request(`/api/admin/recruitments/${form.get("recruitmentId")}/visibility`, { method: "PATCH", body: window.moiApi.toJsonBody({ expectedVisibility: form.get("expectedVisibility"), visibility, reason }) });
        showToast(visibility === "HIDDEN" ? "모집글을 숨겼습니다." : "모집글을 복구했습니다.", "success");
        await Promise.all([loadRecruitments(), loadDashboard()]); loadRecruitmentDetail(form.get("recruitmentId"));
      } catch (error) { showToast(error.message || "노출 상태 변경에 실패했습니다.", "error"); }
    });
  }

  function adminQuery(filters) {
    const params = new URLSearchParams({ page: "0", size: "20" });
    Object.entries(filters).forEach(([key, value]) => { if (value) params.set(key, value); });
    return params.toString();
  }

  function renderOperationRows(selector, items, template) {
    const tbody = $(selector);
    if (!tbody) return;
    tbody.replaceChildren();
    if (!items.length) { renderEmptyTableRow(tbody, 3, "◌", "표시할 데이터가 없습니다.", "검색 조건을 조정해 다시 확인하세요."); return; }
    items.forEach((item) => { const row = document.createElement("tr"); row.innerHTML = template(item); tbody.appendChild(row); });
  }

  async function loadOperations() {
    const filters = appState.operationsFilters;
    const results = await Promise.allSettled([
        window.moiApi.request(`/api/admin/groups?${adminQuery({ keyword: filters.keyword, status: filters.groupStatus })}`),
        window.moiApi.request(`/api/admin/schedules?${adminQuery(filters)}`),
        window.moiApi.request(`/api/admin/attendance-records?${adminQuery({ keyword: filters.keyword, status: filters.attendanceStatus })}`),
        window.moiApi.request(`/api/admin/activity-records?${adminQuery(filters)}`)
    ]);
    const views = [
      ["그룹", "#boGroupListTable", (g) => `<td><strong>${g.name}</strong><br><small>#${g.groupId}</small></td><td>${g.status}</td><td>${g.activeMemberCount}명</td>`],
      ["일정", "#boScheduleListTable", (s) => `<td>${s.groupName}</td><td><strong>${s.title}</strong></td><td>${formatDate(s.scheduledAt)}</td>`],
      ["출석 기록", "#boAttendanceListTable", (a) => `<td>${a.scheduleTitle}</td><td>${a.memberNickname}</td><td>${a.status}</td>`],
      ["활동 기록", "#boActivityListTable", (a) => `<td>${a.scheduleTitle}</td><td><strong>${a.topic}</strong></td><td>${formatDate(a.updatedAt)}</td>`]
    ];
    results.forEach((result, index) => {
      const [label, selector, template] = views[index];
      if (result.status === "fulfilled") {
        renderOperationRows(selector, result.value.items, template);
        return;
      }
      renderOperationRows(selector, [], template);
      showToast(`${label} 운영 조회 API를 불러오지 못했습니다: ${result.reason.message || "알 수 없는 오류"}`, "error");
      console.error(`${label} 운영 조회 API 실패`, result.reason);
    });
  }

  async function loadAuditLogs() {
    try {
      const data = await window.moiApi.request(`/api/admin/audit-logs?${adminQuery(appState.auditFilters)}`);
      const tbody = $("#boAuditTableBody");
      if (!tbody) return;
      tbody.replaceChildren();
      if (!data.items.length) { renderEmptyTableRow(tbody, 5, "◌", "표시할 운영 이력이 없습니다.", "필터를 조정하거나 새 조치 후 다시 확인하세요."); return; }
      data.items.forEach((item) => { const row = document.createElement("tr"); row.innerHTML = `<td>${formatDate(item.createdAt)}</td><td><code>#${item.adminId}</code></td><td><strong>${item.targetLabel}</strong><small> (${item.targetType} #${item.targetId})</small></td><td>${formatAuditAction(item.action)}</td><td>${item.reason}</td>`; tbody.appendChild(row); });
    } catch (error) { showToast("운영 이력을 불러오지 못했습니다.", "error"); }
  }

  function initOperationsEvents() {
    $("#boOperationsFilterForm")?.addEventListener("submit", (event) => { event.preventDefault(); const form = new FormData(event.currentTarget); appState.operationsFilters = { keyword: String(form.get("keyword") || "").trim(), groupStatus: String(form.get("groupStatus") || ""), attendanceStatus: String(form.get("attendanceStatus") || "") }; loadOperations(); });
    $("#boAuditFilterForm")?.addEventListener("submit", (event) => { event.preventDefault(); const form = new FormData(event.currentTarget); appState.auditFilters = { keyword: String(form.get("keyword") || "").trim(), action: String(form.get("action") || ""), targetType: String(form.get("targetType") || "") }; loadAuditLogs(); });
  }

  /* ==========================================================================
     Command Palette (Cmd + K)
     ========================================================================== */
  function initCommandPalette() {
    const modal = $("#boCmdModal");
    const input = $("#boCmdInput");
    const results = $("#boCmdResults");

    const commands = [
      { label: "▦ 대시보드 개요 이동", action: () => switchView("dashboard") },
      { label: "♙ 회원 디렉토리 관리 이동", action: () => switchView("members") },
      { label: "◫ 모집 & 스터디 현황 보기", action: () => switchView("recruitments") },
      { label: "▤ 그룹 운영 조회", action: () => switchView("operations") },
      { label: "🛡 보안 감사 로그 스트림", action: () => switchView("audit") },
      { label: "⚙ 시스템 설정 및 테마", action: () => switchView("settings") },
      { label: "🌓 테마 토글 (Dark / Light)", action: () => applyTheme(appState.theme === "dark" ? "light" : "dark") }
    ];

    function renderResults(query = "") {
      if (!results) return;
      results.replaceChildren();
      const q = query.toLowerCase().trim();

      const filtered = commands.filter((c) => c.label.toLowerCase().includes(q));
      filtered.forEach((cmd) => {
        const item = document.createElement("div");
        item.className = "bo-cmd-item";
        item.innerHTML = `<span>${cmd.label}</span><span class="bo-kbd">↵ Select</span>`;
        item.addEventListener("click", () => {
          cmd.action();
          modal.classList.remove("show");
        });
        results.appendChild(item);
      });
    }

    $("#boCmdTrigger")?.addEventListener("click", () => {
      modal.classList.add("show");
      input.focus();
      renderResults();
    });

    input?.addEventListener("input", (e) => renderResults(e.target.value));

    window.addEventListener("keydown", (e) => {
      if ((e.metaKey || e.ctrlKey) && e.key === "k") {
        e.preventDefault();
        modal.classList.add("show");
        input.focus();
        renderResults();
      }
      if (e.key === "Escape" && modal.classList.contains("show")) {
        modal.classList.remove("show");
      }
    });

    modal?.addEventListener("click", (e) => {
      if (e.target === modal) modal.classList.remove("show");
    });
  }

  /* ==========================================================================
     Notification Bell Dropdown
     ========================================================================== */
  function initNotifications() {
    const bell = $("#boNotifBell");
    const dropdown = $("#boNotifDropdown");

    bell?.addEventListener("click", (e) => {
      e.stopPropagation();
      dropdown?.classList.toggle("show");
    });

    document.addEventListener("click", (e) => {
      if (dropdown && !dropdown.contains(e.target) && e.target !== bell) {
        dropdown.classList.remove("show");
      }
    });
  }

  /* ==========================================================================
     Initialization
     ========================================================================== */
  document.addEventListener("DOMContentLoaded", async () => {
    initTheme();
    const admin = await requireAdmin();
    if (!admin) return;

    $("#boWorkspace").hidden = false;
    initRouter();
    initMemberEvents();
    initRecruitmentEvents();
    initOperationsEvents();
    initCommandPalette();
    initNotifications();

    // Initial Dashboard Data Load
    await loadDashboard();
  });
})();
