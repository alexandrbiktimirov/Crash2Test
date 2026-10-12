import { test } from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';

// Report file names only: never echo candidate secrets into CI logs.
const secretPattern = /\b(?:sk-(?:proj-|ant-api\d+-)?[A-Za-z0-9_-]{20,}|xai-[A-Za-z0-9_-]{20,}|gsk_[A-Za-z0-9]{20,}|AIza[A-Za-z0-9_-]{30,})\b|-----BEGIN (?:[A-Z]+ )?PRIVATE KEY-----/;

test('publishable files contain no recognizable API keys or private keys', () => {
  const files = execFileSync('git', ['ls-files', '--cached', '--others', '--exclude-standard', '-z'])
    .toString().split('\0').filter(Boolean);
  const matches = [...new Set(files)].filter(file => {
    try { return secretPattern.test(readFileSync(file, 'utf8')); }
    catch (error) { if (error.code === 'ENOENT') return false; throw error; }
  });
  assert.deepEqual(matches, [], `Possible credentials found in: ${matches.join(', ')}`);
});
