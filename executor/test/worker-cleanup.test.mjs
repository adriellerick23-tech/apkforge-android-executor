import assert from "node:assert/strict";
import { access, mkdtemp, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";

process.env.APKFORGE_API_URL = "https://apkforge.test";
process.env.APKFORGE_WORKER_TOKEN = "test-worker-token";
process.env.APKFORGE_CALLBACK_SECRET = "test-callback-secret";
const { cleanupWorkdir } = await import("../src/worker.mjs");

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
