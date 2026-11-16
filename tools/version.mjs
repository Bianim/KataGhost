import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';
import { spawnSync } from 'node:child_process';

export function advanceVersionCode(pkg, previousVersion) {
  if (!Number.isSafeInteger(pkg.versionCode) || pkg.versionCode < 1 || pkg.versionCode > 2100000000) {
    throw new Error('Invalid Android versionCode');
  }
  if (pkg.version !== previousVersion) {
    if (pkg.versionCode === 2100000000) throw new Error('Android versionCode limit reached');
    pkg.versionCode += 1;
  }
}

export function releaseNotes(changelog, version) {
  const heading = `## ${version}`;
  const lines = changelog.split(/\r?\n/);
  const start = lines.indexOf(heading);
  if (start < 0) throw new Error(`Missing changelog entry: ${version}`);
  const next = lines.findIndex((line, i) => i > start && line.startsWith('## '));
  const notes = lines.slice(start + 1, next < 0 ? undefined : next).join('\n').trim();
  if (!notes) throw new Error('Release notes must not be empty');
  return `# KataGhost ${version}\n\n${notes}\n`;
}

export function synchronize(root, mode, tag) {
  const read = name => readFileSync(resolve(root, name), 'utf8').replace(/\r\n/g, '\n');
  const pkg = JSON.parse(read('package.json'));
  if (!/^\d+\.\d+\.\d+(-[0-9A-Za-z]+([.-][0-9A-Za-z]+)*)?$/.test(pkg.version)) {
    throw new Error('Invalid release version');
  }
  if (tag && tag !== `v${pkg.version}`) throw new Error('Tag does not match package.json');
  advanceVersionCode(pkg, pkg.version);
  const versionCode = pkg.versionCode;
  releaseNotes(read('CHANGELOG.md'), pkg.version);
  const lock = JSON.parse(read('package-lock.json'));
  lock.version = pkg.version;
  lock.packages[''].version = pkg.version;
  const outputs = new Map([
    ['package-lock.json', `${JSON.stringify(lock, null, 2)}\n`],
  ]);
  for (const name of ['README.md', 'README.zh-CN.md', 'CONTRIBUTING.md']) {
    const source = read(name);
    const pattern = /<!-- current-version:start -->[\s\S]*?<!-- current-version:end -->/g;
    if ([...source.matchAll(pattern)].length !== 1) throw new Error(`Missing version block: ${name}`);
    outputs.set(name, source.replace(pattern,
      `<!-- current-version:start -->\nKataGhost: **v${pkg.version}** · Android versionCode: **${versionCode}** · [Changelog](CHANGELOG.md)\n<!-- current-version:end -->`));
  }
  for (const [name, content] of outputs) {
    if (mode === 'sync') writeFileSync(resolve(root, name), content);
    else if (read(name) !== content) throw new Error(`Outdated ${name}; run npm run version:sync`);
  }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const mode = process.argv[2];
  const root = fileURLToPath(new URL('../', import.meta.url));
  if (mode === 'version') {
    const packagePath = resolve(root, 'package.json');
    const previous = JSON.parse(readFileSync(packagePath, 'utf8'));
    advanceVersionCode(previous, previous.version);
    const result = spawnSync(process.execPath,
      [resolve(root, 'node_modules/@changesets/cli/bin.js'), 'version'],
      { cwd: root, stdio: 'inherit' });
    if (result.error) throw result.error;
    if (result.status !== 0) process.exit(result.status ?? 1);
    const pkg = JSON.parse(readFileSync(packagePath, 'utf8'));
    advanceVersionCode(pkg, previous.version);
    writeFileSync(packagePath, `${JSON.stringify(pkg, null, 2)}\n`);
    synchronize(root, 'sync');
  } else if (mode === 'notes') {
    const pkg = JSON.parse(readFileSync(resolve(root, 'package.json'), 'utf8'));
    process.stdout.write(releaseNotes(readFileSync(resolve(root, 'CHANGELOG.md'), 'utf8'), pkg.version));
  } else {
    if (!['sync', 'check'].includes(mode)) throw new Error('Use version, sync, check [tag], or notes');
    synchronize(root, mode, process.argv[3]);
  }
}
