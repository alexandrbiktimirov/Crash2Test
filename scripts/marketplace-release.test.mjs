import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdtempSync, realpathSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { basename, dirname, join } from "node:path";
import test from "node:test";
import { analyzeCommits } from "@semantic-release/commit-analyzer";
import { generateNotes } from "@semantic-release/release-notes-generator";
import config from "../release.config.mjs";
import { prepare, verifyConditions } from "./marketplace-release.mjs";

const logger = { log() {} };
const analyzerOptions = config.plugins[0][1];
const secrets = Object.fromEntries(
  ["PUBLISH_TOKEN", "CERTIFICATE_CHAIN", "PRIVATE_KEY", "PRIVATE_KEY_PASSWORD"].map((name) => [name, "test-value"]),
);

test("commit conventions determine releases and skip maintenance-only changes", async () => {
  for (const [message, expected] of [
    ["fix: handle connection failures", "patch"],
    ["feat: add model settings", "minor"],
    ["feat!: change configuration format", "major"],
    ["refactor: simplify the editor", null],
    ["docs: explain local setup", null],
    ["chore: update build tooling", null],
  ]) {
    assert.equal(await analyzeCommits(analyzerOptions, { commits: [{ message }], logger }), expected, message);
  }
});

test("a release includes all changes since the previous tag and uses the largest bump", async () => {
  const commits = ["fix: handle empty input", "feat: support another language", "refactor: simplify rendering"]
    .map((message) => ({ message }));
  assert.equal(await analyzeCommits(analyzerOptions, { commits, logger }), "minor");
});

test("release notes render features, fixes, and links with the configured preset", async () => {
  const notes = await generateNotes(config.plugins[1][1], {
    cwd: process.cwd(),
    logger,
    options: { repositoryUrl: config.repositoryUrl },
    lastRelease: { version: "0.1.0", gitTag: "v0.1.0" },
    nextRelease: { version: "0.2.0", gitTag: "v0.2.0" },
    commits: [
      { hash: "a".repeat(40), message: "Merge pull request #18 from alexandrbiktimirov/dev\n\nfeat: automate Marketplace releases" },
      { hash: "b".repeat(40), message: "feat: automate Marketplace releases" },
      { hash: "c".repeat(40), message: "fix: handle connection failures (#42)" },
    ],
  });

  assert.match(notes, /automate Marketplace releases/);
  assert.match(notes, /handle connection failures/);
  const repository = config.repositoryUrl.replace(/\.git$/, "");
  assert.ok(notes.includes(`${repository}/compare/v0.1.0...v0.2.0`));
  assert.ok(notes.includes(`${repository}/commit/${"b".repeat(40)}`));
  assert.ok(notes.includes(`${repository}/issues/42`));
});

test("missing credentials fail without exposing secret values", () => {
  assert.throws(
    () => verifyConditions({}, { env: { PUBLISH_TOKEN: "never-print-this" } }),
    (error) => error.message.includes("PRIVATE_KEY") && !error.message.includes("never-print-this"),
  );
});

test("release baseline must exist and belong to the current history", (t) => {
  const temporaryRoot = realpathSync(tmpdir());
  const cwd = mkdtempSync(join(temporaryRoot, "crash2test-release-"));
  t.after(() => {
    assert.equal(dirname(realpathSync(cwd)), temporaryRoot);
    assert.ok(basename(cwd).startsWith("crash2test-release-"));
    rmSync(cwd, { recursive: true, force: true });
  });
  const git = (...args) => {
    const result = spawnSync("git", args, { cwd, encoding: "utf8" });
    assert.equal(result.status, 0, result.stderr);
  };
  const commit = (message = "test") => git("-c", "user.name=Release Test", "-c", "user.email=test@example.com", "commit", "--allow-empty", "-m", message);
  git("init");
  commit();
  const context = { cwd, env: { ...process.env, ...secrets } };
  assert.throws(() => verifyConditions({}, context), /v0\.1\.0/);
  git("tag", "v0.1.0");
  assert.doesNotThrow(() => verifyConditions({}, context));
  git("checkout", "--orphan", "unrelated-history");
  commit("unrelated root");
  assert.throws(() => verifyConditions({}, context), /v0\.1\.0/);
});

test("invalid release versions are rejected before running Gradle", () => {
  assert.throws(() => prepare({}, { nextRelease: { version: "1.2.3; unexpected-command" } }), /stable semantic version/);
});
