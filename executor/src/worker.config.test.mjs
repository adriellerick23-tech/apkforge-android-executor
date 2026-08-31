import { describe, expect, it } from "vitest";

describe("executor security configuration", () => {
  it("requires both worker credentials before connecting to the API", () => {
    expect(process.env.APKFORGE_WORKER_TOKEN ?? "").toBe("");
    expect(process.env.APKFORGE_CALLBACK_SECRET ?? "").toBe("");
  });
});
