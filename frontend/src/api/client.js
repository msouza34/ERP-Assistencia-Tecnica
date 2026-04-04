const RAW_API_URL = (import.meta.env.VITE_API_URL || "").trim();

function normalizeApiBase(value) {
  if (!value || value === "/") {
    return "";
  }

  return value.endsWith("/") ? value.slice(0, -1) : value;
}

const API_URL = normalizeApiBase(RAW_API_URL);

function buildApiUrl(path) {
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  return `${API_URL}${normalizedPath}`;
}

async function parseResponse(response, responseType) {
  if (!response.ok) {
    let message = `Erro HTTP ${response.status}`;

    try {
      const contentType = response.headers.get("content-type") || "";
      if (contentType.includes("application/json")) {
        const payload = await response.json();
        if (payload && typeof payload.message === "string") {
          message = payload.message;
        }
      } else {
        const text = await response.text();
        if (text) {
          message = text;
        }
      }
    } catch {
      // Keep default message.
    }

    throw new Error(message);
  }

  if (responseType === "blob") {
    return response.blob();
  }

  if (responseType === "text") {
    return response.text();
  }

  if (response.status === 204) {
    return null;
  }

  const contentType = response.headers.get("content-type") || "";
  if (contentType.includes("application/json")) {
    return response.json();
  }

  return response.text();
}

export async function apiRequest(
  path,
  { method = "GET", body, formData, token, tenantId, responseType = "json" } = {}
) {
  const headers = {
    "X-Tenant-Id": tenantId || "public"
  };

  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  let payload;
  if (formData) {
    payload = formData;
  } else if (body !== undefined) {
    headers["Content-Type"] = "application/json";
    payload = JSON.stringify(body);
  }

  const response = await fetch(buildApiUrl(path), {
    method,
    headers,
    body: payload
  });

  return parseResponse(response, responseType);
}

export async function login({ username, password, tenantId }) {
  return apiRequest("/api/v1/auth/login", {
    method: "POST",
    body: { username, password, tenantId },
    tenantId
  });
}

export function openBlobInNewTab(blob) {
  const url = URL.createObjectURL(blob);
  window.open(url, "_blank", "noopener,noreferrer");
  setTimeout(() => URL.revokeObjectURL(url), 10000);
}

export function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  setTimeout(() => URL.revokeObjectURL(url), 10000);
}

export function getApiUrl() {
  return API_URL;
}
