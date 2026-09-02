import assert from "node:assert/strict";
import { access, mkdtemp, readFile, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";

process.env.APKFORGE_API_URL = "https://apkforge.test";
process.env.APKFORGE_WORKER_TOKEN = "test-worker-token";
process.env.APKFORGE_CALLBACK_SECRET = "test-callback-secret";
const { build, cleanupWorkdir, pollOnce, reserveJob } = await import("../src/worker.mjs");

async function expectRemovedAfter(work) {
  await cleanupWorkdir(work);
  await assert.rejects(access(work), /ENOENT/);
}

test("cleanup removes isolated workdir after a successful build", async () => {
  const work = await mkdtemp(join(tmpdir(), "apkforge-success-"));
  await writeFile(join(work, "app-debug.apk"), "apk");
  await expectRemovedAfter(work);
});

test("cleanup removes isolated workdir after a failed build", async () => {
  const work = await mkdtemp(join(tmpdir(), "apkforge-failure-"));
  await writeFile(join(work, "error.log"), "BUILD_FAILED");
  await expectRemovedAfter(work);
});

test("reserveJob consumes the worker endpoint and pollOnce does not build an empty response", async () => {
  const responses = [
    { status: 200, ok: true, json: async () => ({ jobId: 71, packageId: "com.example.app", attempt: 1 }) },
    { status: 204, ok: false, json: async () => undefined },
  ];
  const fetchImpl = async () => responses.shift();
  const first = await reserveJob(fetchImpl);
  assert.equal(first.jobId, 71);
  const builds = [];
  const second = await pollOnce(fetchImpl, async (job) => builds.push(job));
  assert.equal(second, undefined);
  assert.deepEqual(builds, []);
});

test("build uses a job-specific isolated workdir and cleans it after a mocked success", async () => {
  const callbacks = [];
  const workdirs = [];
  let writtenConfig;
  await build({ jobId: 72, packageId: "com.example.app", appName: "Test", version: "1.0.0", sourceType: "url", openMode: "webview", attempt: 1 }, {
    mkdtemp: async (prefix) => { const work = await mkdtemp(prefix); workdirs.push(work); return work; },
    mkdir: async () => undefined,
    writeFile: async (path, data) => { writtenConfig = { path, data }; },
    run: async () => undefined,
    readFile: async () => Buffer.alloc(4096),
    signedCallback: async (payload) => callbacks.push(payload),
    cleanup: async (workdir) => expectRemovedAfter(workdir),
  });
  assert.equal(workdirs.length, 1);
  assert.match(workdirs[0], /apkforge-72-/);
  assert.match(writtenConfig.path, /build-config\.json$/);
  assert.deepEqual(callbacks.map((item) => item.status), ["validating", "building", "signing", "completed"]);
});
