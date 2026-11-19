import { test } from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, mkdtempSync, readFileSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { advanceVersionCode, releaseNotes, synchronize } from './version.mjs';

test('advances Android versionCode only when Changesets changes the version', () => {
  const pkg = { version: '0.2.0', versionCode: 1001 };
  advanceVersionCode(pkg, '0.1.0');
  assert.equal(pkg.versionCode, 1002);
  advanceVersionCode(pkg, '0.2.0');
  assert.equal(pkg.versionCode, 1002);
  assert.throws(() => advanceVersionCode({ versionCode: '1001' }));
  assert.throws(() => advanceVersionCode({ version: '0.2.0', versionCode: 2100000000 }, '0.1.0'));
});

test('extracts only the requested release and preserves Unicode', () => {
  assert.equal(releaseNotes('## 0.2.0\n\n### Patch Changes\n\n- 中文\n\n## 0.1.0\nold', '0.2.0'), '### Patch Changes\n\n- 中文\n');
  assert.throws(() => releaseNotes('## 0.1.0\n', '0.2.0'));
  assert.throws(() => releaseNotes('## 0.1.0\n', '0.1.0'));
});

test('synchronizes once, detects stale metadata and rejects a mismatched tag', () => {
  const root = mkdtempSync(join(tmpdir(), 'kataghost-version-'));
  const put = (name, value) => writeFileSync(join(root, name), value);
  const get = name => readFileSync(join(root, name), 'utf8');
  try {
    put('package.json', JSON.stringify({ version: '0.2.0', versionCode: 1002 }));
    put('package-lock.json', JSON.stringify({ version: '0.1.0', packages: { '': { version: '0.1.0' } } }));
    put('CHANGELOG.md', '## 0.2.0\n\n新增功能\n');
    for (const name of ['README.md', 'README.en.md', 'CONTRIBUTING.md']) {
      put(name, '<!-- current-version:start -->\nold\n<!-- current-version:end -->\nHistorical v1.0.0\n');
    }
    assert.throws(() => synchronize(root, 'check'));
    synchronize(root, 'sync');
    assert.equal(JSON.parse(get('package.json')).versionCode, 1002);
    assert.equal(existsSync(join(root, 'version.properties')), false);
    assert.match(get('README.en.md'), /Historical v1.0.0/);
    assert.equal(existsSync(join(root, 'RELEASE_NOTES.md')), false);
    assert.match(get('README.en.md'), /\[Changelog\]\(CHANGELOG.md\)/);
    synchronize(root, 'sync');
    synchronize(root, 'check', 'v0.2.0');
    put('README.md', get('README.md').replace(/\n/g, '\r\n'));
    synchronize(root, 'check', 'v0.2.0');
    assert.equal(JSON.parse(get('package.json')).versionCode, 1002);
    assert.throws(() => synchronize(root, 'check', 'v0.1.0'));
    put('package.json', JSON.stringify({ version: '0.2.0', versionCode: 'invalid' }));
    assert.throws(() => synchronize(root, 'sync'));
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
