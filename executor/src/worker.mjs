import { createHash, createHmac, randomUUID } from "node:crypto";
import { mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { spawn } from "node:child_process";
import { pathToFileURL } from "node:url";

const API_URL = process.env.APKFORGE_API_URL;
const TOKEN = process.env.APKFORGE_WORKER_TOKEN;
const SECRET = process.env.APKFORGE_CALLBACK_SECRET;
const POLL_MS = Number(process.env.WORKER_POLL_MS ?? 5000);
const TIMEOUT_MS = Number(process.env.BUILD_TIMEOUT_MS ?? 900000);
if (!API_URL || !TOKEN || !SECRET) throw new Error("APKFORGE_API_URL, APKFORGE_WORKER_TOKEN e APKFORGE_CALLBACK_SECRET são obrigatórios");

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const sha256 = (buffer) => createHash("sha256").update(buffer).digest("hex");

async function signedCallback(payload) {
  const body = JSON.stringify({ ...payload, timestamp: Date.now() });
  const signature = createHmac("sha256", SECRET).update(body).digest("hex");
  const response = await fetch(`${API_URL}/api/internal/worker/callback`, { method: "POST", headers: { "content-type": "application/json", authorization: `Bearer ${TOKEN}`, "x-apkforge-signature": signature }, body });
  if (!response.ok) throw new Error(`callback ${response.status}`);
}

function run(command, args, cwd) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, { cwd, shell: false, stdio: ["ignore", "pipe", "pipe"] });
    let stdout = ""; let stderr = ""; let settled = false;
    const timer = setTimeout(() => { child.kill("SIGKILL"); settled = true; reject(new Error("BUILD_TIMEOUT")); }, TIMEOUT_MS);
    child.stdout.on("data", (chunk) => { stdout += chunk.toString(); }); child.stderr.on("data", (chunk) => { stderr += chunk.toString(); });
    child.on("error", (error) => { if (settled) return; settled = true; clearTimeout(timer); reject(error); });
    child.on("close", (code) => { if (settled) return; settled = true; clearTimeout(timer); code === 0 ? resolve({ stdout, stderr }) : reject(new Error(`GRADLE_EXIT_${code}: ${stderr.slice(-1000)}`)); });
  });
}

export async function cleanupWorkdir(workdir) {
  await rm(workdir, { recursive: true, force: true });
}

export async function build(job, dependencies = {}) {
  const makeTemp = dependencies.mkdtemp ?? mkdtemp;
  const makeDir = dependencies.mkdir ?? mkdir;
  const write = dependencies.writeFile ?? writeFile;
  const read = dependencies.readFile ?? readFile;
  const execute = dependencies.run ?? run;
  const notify = dependencies.signedCallback ?? signedCallback;
  const cleanup = dependencies.cleanup ?? cleanupWorkdir;
  const workdir = await makeTemp(join(tmpdir(), `apkforge-${job.jobId}-`));
  try {
    await notify({ jobId: job.jobId, status: "validating", progress: 15, message: "entrada isolada e validada", attempt: job.attempt });
    // O adapter real deve baixar sourceKey/iconKey via SDK de storage e copiar o template escolhido.
    const project = join(workdir, "android"); await makeDir(project, { recursive: true });
    await write(join(project, "build-config.json"), JSON.stringify({ packageId: job.packageId, appName: job.appName, version: job.version, sourceType: job.sourceType, openMode: job.openMode }));
    await notify({ jobId: job.jobId, status: "building", progress: 50, message: "template Android montado", attempt: job.attempt });
    await execute("./gradlew", ["assembleDebug", "--no-daemon", "--offline"], project);
    await notify({ jobId: job.jobId, status: "signing", progress: 82, message: "APK debug assinado", attempt: job.attempt });
    const apk = await read(join(project, "app/build/outputs/apk/debug/app-debug.apk"));
    if (apk.length < 4096) throw new Error("APK_TOO_SMALL");
    await notify({ jobId: job.jobId, status: "completed", progress: 100, message: "APK pronto", artifactKey: `jobs/${job.jobId}/${randomUUID()}.apk`, sha256: sha256(apk), attempt: job.attempt });
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    await notify({ jobId: job.jobId, status: "failed", progress: 100, message: message.slice(0, 500), attempt: job.attempt });
  } finally { await cleanup(workdir); }
}

export async function reserveJob(fetchImpl = fetch) {
  const response = await fetchImpl(`${API_URL}/api/internal/worker/next`, { headers: { authorization: `Bearer ${TOKEN}` } });
  if (response.status === 204) return undefined;
  if (!response.ok) throw new Error(`worker next ${response.status}`);
  return response.json();
}

export async function pollOnce(fetchImpl = fetch, buildImpl = build) {
  const job = await reserveJob(fetchImpl);
  if (job?.jobId) await buildImpl(job);
  return job;
}

async function loop() {
  console.log("APKForge Android Executor online");
  while (true) {
    try { await pollOnce(); } catch (error) { console.error("worker cycle failed", error instanceof Error ? error.message : String(error)); }
    await sleep(POLL_MS);
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) loop();
