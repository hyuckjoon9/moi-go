(function () {
  function saveTokens(tokenResponse) {
    localStorage.setItem("accessToken", tokenResponse.accessToken);
    localStorage.setItem("refreshToken", tokenResponse.refreshToken);
  }

  function clearTokens() {
    localStorage.removeItem("accessToken");
    localStorage.removeItem("refreshToken");
    localStorage.removeItem("memberId");
    localStorage.removeItem("memberNickname");
    localStorage.removeItem("memberProfileImageUrl");
    localStorage.removeItem("memberProfilePreview");
  }

  function getRefreshToken() {
    return localStorage.getItem("refreshToken");
  }

  function hasRefreshToken() {
    return Boolean(getRefreshToken());
  }

  function isSignedIn() {
    return Boolean(localStorage.getItem("accessToken"));
  }

  async function signup(payload) {
    return window.moiApi.request("/api/auth/signup", {
      method: "POST",
      auth: false,
      body: window.moiApi.toJsonBody(payload),
    });
  }

  async function login(payload) {
    const tokenResponse = await window.moiApi.request("/api/auth/login", {
      method: "POST",
      auth: false,
      body: window.moiApi.toJsonBody(payload),
    });
    saveTokens(tokenResponse);
    return tokenResponse;
  }

  async function reissue() {
    const refreshToken = getRefreshToken();
    if (!refreshToken) throw new Error("저장된 refresh token이 없습니다.");
    const tokenResponse = await window.moiApi.request("/api/auth/reissue", {
      method: "POST",
      auth: false,
      body: window.moiApi.toJsonBody({ refreshToken }),
    });
    saveTokens(tokenResponse);
    return tokenResponse;
  }

  async function logout() {
    const refreshToken = getRefreshToken();
    try {
      if (refreshToken) {
        await window.moiApi.request("/api/auth/logout", {
          method: "POST",
          retry: false,
          body: window.moiApi.toJsonBody({ refreshToken }),
        });
      }
    } finally {
      clearTokens();
    }
  }

  window.moiAuth = { signup, login, reissue, logout, clearTokens, hasRefreshToken, isSignedIn };
})();
