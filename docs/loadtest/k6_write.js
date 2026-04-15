import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://127.0.0.1:8080";

export const options = {
  vus: Number(__ENV.VUS || 5),
  duration: __ENV.DURATION || "30s",
  thresholds: {
    http_req_failed: ["rate<0.02"],
    http_req_duration: ["p(95)<1500", "p(99)<2500"]
  }
};

function url(path) {
  return `${BASE_URL}${path}`;
}

function post(path, payload, role) {
  return http.post(url(path), JSON.stringify(payload), {
    headers: {
      "Content-Type": "application/json",
      "X-Workflow-Role": role
    }
  });
}

function put(path, payload, role) {
  return http.put(url(path), JSON.stringify(payload), {
    headers: {
      "Content-Type": "application/json",
      "X-Workflow-Role": role
    }
  });
}

export default function () {
  // 1) create draft (EDITOR)
  const bizNo = `K6-WRITE-${__VU}-${Date.now()}`;
  const createRes = post(
    "/api/workflows/drafts",
    { bizNo, title: `title ${bizNo}`, summary: "k6 write test", body: "body" },
    "EDITOR"
  );
  check(createRes, { "create ok": (r) => r.status === 201 || r.status === 200 });
  const draftId = createRes.json()?.data?.id;
  if (!draftId) {
    sleep(0.2);
    return;
  }

  // 2) update draft (EDITOR)
  const updateRes = put(
    `/api/workflows/drafts/${draftId}`,
    { title: `title updated ${bizNo}`, summary: "k6 write updated", body: "body updated" },
    "EDITOR"
  );
  check(updateRes, { "update ok": (r) => r.status === 200 });

  // 3) submit review (EDITOR)
  const submitRes = post(`/api/workflows/drafts/${draftId}/submit-review`, {}, "EDITOR");
  check(submitRes, { "submit review ok": (r) => r.status === 200 });

  // 4) approve (REVIEWER)
  const reviewRes = post(
    `/api/workflows/drafts/${draftId}/review`,
    { decision: "APPROVE", comment: "k6 auto approve" },
    "REVIEWER"
  );
  check(reviewRes, { "approve ok": (r) => r.status === 200 });

  // 5) publish (OPERATOR)
  const publishRes = post(
    `/api/workflows/drafts/${draftId}/publish`,
    { remark: "k6 publish" },
    "OPERATOR"
  );
  check(publishRes, { "publish accepted": (r) => r.status === 200 });

  sleep(0.3);
}

