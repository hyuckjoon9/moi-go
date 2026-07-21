(function () {
  const JSON_TYPE = "application/json";

  function getAccessToken() {
    return localStorage.getItem("accessToken");
  }

  async function parseResponse(response) {
    if (response.status === 204) return null;
    const text = await response.text();
    return text ? JSON.parse(text) : null;
  }

  async function request(path, options = {}) {
    const retry = options.retry !== false;
    const response = await send(path, options);
    if (response.status === 401 && retry && options.auth !== false && window.moiAuth?.hasRefreshToken()) {
      try {
        await window.moiAuth.reissue();
        return request(path, { ...options, retry: false });
      } catch (error) {
        window.moiAuth?.clearTokens();
        window.location.href = `/login.html?returnUrl=${encodeURIComponent(window.location.pathname)}`;
        throw error;
      }
    }
    const payload = await parseResponse(response);
    if (!response.ok) throw new Error(payload?.message || `HTTP ${response.status}`);
    if (payload && Object.prototype.hasOwnProperty.call(payload, "success")) {
      if (!payload.success) throw new Error(payload.message || "요청에 실패했습니다.");
      return payload.data;
    }
    return payload;
  }

  async function send(path, options = {}) {
    const headers = new Headers(options.headers || {});
    const hasBody = options.body !== undefined && options.body !== null;
    const isFormData = typeof FormData !== "undefined" && options.body instanceof FormData;
    if (hasBody && !isFormData && !headers.has("Content-Type")) headers.set("Content-Type", JSON_TYPE);
    if (options.auth !== false && getAccessToken()) headers.set("Authorization", `Bearer ${getAccessToken()}`);
    const { retry, ...fetchOptions } = options;
    return fetch(path, { ...fetchOptions, headers });
  }

  function toJsonBody(value) {
    return JSON.stringify(value);
  }

  window.moiApi = { request, toJsonBody };
})();
