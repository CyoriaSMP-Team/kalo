const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const code = fs.readFileSync(path.join(__dirname, "../docs-site/assets/js/release-version.js"), "utf8");

async function runScenario(replies, inDocs = false) {
  const text = { textContent: "" };
  const badge = { style: { display: "none" } };
  const docVersion = { textContent: "" };
  const fallback = { getAttribute: () => inDocs ? "../version.json" : "version.json" };
  const requests = [];
  const intervals = [];
  const context = {
    document: {
      getElementById: key => key === "version-text" && !inDocs ? text : key === "version-badge" && !inDocs ? badge : null,
      querySelectorAll: () => inDocs ? [docVersion] : [],
      querySelector: key => key === "[data-release-fallback]" ? fallback : null
    },
    fetch: async (url) => {
      requests.push(url);
      const reply = replies.shift();
      if (reply instanceof Error) throw reply;
      return { ok: reply.ok !== false, status: reply.status || 200, json: async () => reply.data };
    },
    setInterval: (fn, delay) => intervals.push({ fn, delay })
  };
  vm.runInNewContext(code, context);
  await new Promise(resolve => setImmediate(resolve));
  await new Promise(resolve => setImmediate(resolve));
  return { text, badge, docVersion, requests, intervals };
}

test("shows the latest published stable GitHub release", async () => {
  const result = await runScenario([{ data: { tag_name: "v0.3.0", draft: false, prerelease: false } }]);
  assert.equal(result.text.textContent, "v0.3.0 — Now Available");
  assert.equal(result.badge.style.display, "inline-flex");
  assert.equal(result.requests.length, 1);
  assert.match(result.requests[0], /api.github.com/);
  assert.equal(result.intervals[0].delay, 15 * 60 * 1000);
});

test("uses a clearly labelled cached fallback if GitHub is unavailable", async () => {
  const result = await runScenario([new Error("network"), { data: { name: "v0.2.0" } }], true);
  assert.equal(result.docVersion.textContent, "v0.2.0 (cached)");
  assert.equal(result.requests[1], "../version.json");
});

test("rejects unsafe or malformed API tags and uses fallback", async () => {
  const result = await runScenario([{ data: { tag_name: "<script>bad</script>" } }, { data: { name: "v0.2.0" } }]);
  assert.equal(result.text.textContent, "v0.2.0 — last known release");
});

test("fails safely if both providers are unreachable", async () => {
  const result = await runScenario([new Error("GitHub offline"), new Error("fallback offline")], true);
  assert.equal(result.docVersion.textContent, "See releases");
});
