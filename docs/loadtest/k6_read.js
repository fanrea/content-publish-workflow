import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://127.0.0.1:8080";

export const options = {
  vus: Number(__ENV.VUS || 20),
  duration: __ENV.DURATION || "60s",
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<800", "p(99)<1500"]
  }
};

function url(path) {
  return `${BASE_URL}${path}`;
}

export function setup() {
  // Create one draft so we can hit summary endpoint deterministically.
  const payload = JSON.stringify({
    bizNo: `K6-READ-${Date.now()}`,
    title: "k6 read test",
    summary: "k6 read test seed",
    body: "seed"
  });

  const res = http.post(url("/api/workflows/drafts"), payload, {
    headers: {
      "Content-Type": "application/json",
      "X-Workflow-Role": "EDITOR"
    }
  });
  check(res, { "setup create draft ok": (r) => r.status === 201 || r.status === 200 });
  const json = res.json();
  const draftId = json && json.data && json.data.id ? json.data.id : null;
  return { draftId };
}

export default function (data) {
  // 1) List page (summary only)
  const res1 = http.get(
    url("/api/workflows/drafts/page?pageNo=1&pageSize=20&sortBy=UPDATED_AT&sortDirection=DESC")
  );
  check(res1, { "page ok": (r) => r.status === 200 });

  // 2) Status stats (Tab/Badge)
  const res2 = http.get(url("/api/workflows/drafts/stats"));
  check(res2, { "stats ok": (r) => r.status === 200 });

  // 3) Summary card (detail page header)
  if (data && data.draftId) {
    const res3 = http.get(url(`/api/workflows/drafts/${data.draftId}/summary`));
    check(res3, { "summary ok": (r) => r.status === 200 });
  }

  sleep(0.2);
}

