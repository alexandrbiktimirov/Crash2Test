import { spawnSync } from "node:child_process";
import { mkdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";

const requiredSecrets = ["PUBLISH_TOKEN", "CERTIFICATE_CHAIN", "PRIVATE_KEY", "PRIVATE_KEY_PASSWORD"];

export function verifyConditions(_config, { env, cwd }) {
  const missing = requiredSecrets.filter((name) => !env[name]?.trim());
  if (missing.length) {
    throw new Error(`Missing GitHub Actions secrets: ${missing.join(", ")}. See docs/releasing.md.`);
  }

  const baseline = spawnSync("git", ["merge-base", "--is-ancestor", "refs/tags/v0.1.0", "HEAD"], { cwd, env });
  if (baseline.error || baseline.status !== 0) {
    throw new Error("Push the v0.1.0 automation baseline tag and preserve its ancestry when merging. See docs/releasing.md.");
  }
}

export function prepare(_config, context) {
  runGradle(["verifyPlugin", "signPlugin", "verifyPluginSignature"], context);
  mkdirSync(join(context.cwd, "build"), { recursive: true });
  writeFileSync(join(context.cwd, "build", "release-notes.md"), context.nextRelease.notes ?? "");
}

export function publish(_config, context) {
  runGradle(["publishPlugin"], context);
}

function runGradle(tasks, { cwd, env, nextRelease }) {
  if (!/^\d+\.\d+\.\d+$/.test(nextRelease.version)) {
    throw new Error(`Expected a stable semantic version, received: ${nextRelease.version}`);
  }
  const result = spawnSync("./gradlew", ["--no-daemon", ...tasks], {
    cwd,
    env: { ...env, PLUGIN_VERSION: nextRelease.version },
    stdio: "inherit",
  });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(`Gradle ${tasks.join(" ")} failed (exit ${result.status}).`);
}
