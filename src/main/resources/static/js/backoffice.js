(function () {
  const LOGIN_URL = `/login.html?returnUrl=${encodeURIComponent(
    window.location.pathname + window.location.search
  )}`;

  const THEME_KEY = "moi-go-backoffice-theme";

  function showState(message, kind = "info") {
    const state = document.querySelector("#boState");
    state.textContent = message;
    state.dataset.kind = kind;
    state.hidden = false;
  }

  function applyTheme(theme) {
    document.documentElement.dataset.theme = theme;
    localStorage.setItem(THEME_KEY, theme);
    const toggle = document.querySelector("#boThemeToggle");
    if (toggle) {
      const dark = theme === "dark";
      toggle.textContent = dark ? "☀" : "◐";
      toggle.setAttribute("aria-label", dark ? "라이트 모드로 전환" : "다크 모드로 전환");
    }
  }

  function initializeTheme() {
    const saved = localStorage.getItem(THEME_KEY);
    applyTheme(saved || (window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light"));
    document.querySelector("#boThemeToggle").addEventListener("click", () => {
      applyTheme(document.documentElement.dataset.theme === "dark" ? "light" : "dark");
    });
  }

  function redirectNonAdmin() {
    document.querySelector("#boAccessModal").hidden = false;
    window.setTimeout(() => window.location.replace("/index.html"), 2000);
  }

  function setMetric(name, value) {
    const target = document.querySelector(`[data-metric="${name}"]`);
    if (target) target.textContent = String(value ?? 0);
  }

  function renderDashboard(data) {
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

    const list = document.querySelector("#boRecentActions");
    list.replaceChildren();
    if (!data.recentActions.length) {
      const empty = document.createElement("p");
      empty.className = "empty-state";
      empty.textContent = "최근 운영 조치가 없습니다.";
      list.append(empty);
      return;
    }
    data.recentActions.forEach((action) => {
      const item = document.createElement("article");
      item.className = "entity-card";
      const title = document.createElement("strong");
      title.textContent = `${action.targetLabel} · ${action.action}`;
      const meta = document.createElement("div");
      meta.className = "meta";
      meta.textContent = `${action.targetType} #${action.targetId} · 관리자 #${action.adminId}`;
      const reason = document.createElement("p");
      reason.textContent = action.reason;
      item.append(title, meta, reason);
      list.append(item);
    });
  }

  async function requireAdmin() {
    if (!window.moiAuth.isSignedIn()) {
      window.location.replace(LOGIN_URL);
      return null;
    }
    const member = await window.moiApi.request("/api/members/me");
    if (member.role !== "ADMIN" || member.status !== "ACTIVE") {
      redirectNonAdmin();
      return null;
    }
    return member;
  }

  async function initialize() {
    try {
      const admin = await requireAdmin();
      if (!admin) return;
      document.querySelector("#boAdminName").textContent = admin.nickname;
      document.querySelector("#boWorkspace").hidden = false;
      const dashboard = await window.moiApi.request("/api/admin/dashboard");
      renderDashboard(dashboard);
    } catch (error) {
      if (error.status === 401) {
        window.location.replace(LOGIN_URL);
        return;
      }
      if (error.status === 403) {
        redirectNonAdmin();
        return;
      }
      showState(error.message || "운영 현황을 불러오지 못했습니다.", "error");
    }
  }

  document.addEventListener("DOMContentLoaded", () => {
    initializeTheme();
    initialize();
  });
})();
