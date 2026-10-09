import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const fixture = mkdtempSync(join(tmpdir(), "sora-production-lock-coverage-"));
const projectDir = join(fixture, "prod");
const lockPath = join(projectDir, "gradle.lockfile");
const buildPath = join(projectDir, "build.gradle");
const checker = join(root, "scripts/verify-production-release-lock-coverage.gradle");

function run(task = "verifyProductionReleaseLockCoverage") {
  const result = spawnSync(
    "./gradlew",
    [
      "-p", fixture,
      "-I", checker,
      task,
      "--offline", "--no-daemon", "--no-parallel",
    ],
    { cwd: root, encoding: "utf8", timeout: 120_000 },
  );
  assert.equal(result.error, undefined, result.error?.message);
  return { status: result.status, output: result.stdout + result.stderr };
}

try {
  mkdirSync(projectDir);
  writeFileSync(join(fixture, "settings.gradle"), "rootProject.name = 'coverage-fixture'\ninclude ':prod'\n");
  writeFileSync(buildPath, "configurations.create('productionReleaseRuntimeClasspath') { canBeResolved = true }\n");
  writeFileSync(lockPath, "empty=productionReleaseRuntimeClasspath\n");
  let result = run();
  assert.equal(result.status, 0, result.output);
  assert.match(result.output, /Production release lock coverage: 1 resolvable configurations in 1 projects/);

  // A plugin can add a configuration after the tracked lock snapshot was created.
  writeFileSync(buildPath, `
configurations.create('productionReleaseRuntimeClasspath') { canBeResolved = true }
configurations.create('hiltAnnotationProcessorProductionReleaseUnitTest') { canBeResolved = true }
`);
  result = run();
  assert.notEqual(result.status, 0, result.output);
  assert.match(result.output, /absent from gradle\.lockfile: hiltAnnotationProcessorProductionReleaseUnitTest/);

  writeFileSync(lockPath, "empty=hiltAnnotationProcessorProductionReleaseUnitTest,productionReleaseRuntimeClasspath\n");
  result = run();
  assert.equal(result.status, 0, result.output);

  writeFileSync(lockPath, "empty=hiltAnnotationProcessorProductionReleaseUnitTest,productionReleaseRuntimeClasspath,staleProductionReleaseClasspath\n");
  result = run();
  assert.notEqual(result.status, 0, result.output);
  assert.match(result.output, /no longer resolvable but present in gradle\.lockfile: staleProductionReleaseClasspath/);

  writeFileSync(lockPath, "empty=hiltAnnotationProcessorProductionReleaseUnitTest,productionReleaseRuntimeClasspath\ninvalid-record\n");
  result = run();
  assert.notEqual(result.status, 0, result.output);
  assert.match(result.output, /malformed gradle\.lockfile:2/);

  rmSync(lockPath);
  result = run();
  assert.notEqual(result.status, 0, result.output);
  assert.match(result.output, /missing gradle\.lockfile for 2 productionRelease configurations/);

  writeFileSync(buildPath, "configurations.create('testOnlyClasspath') { canBeResolved = true }\n");
  result = run();
  assert.notEqual(result.status, 0, result.output);
  assert.match(result.output, /No resolvable productionRelease configurations exist in the Gradle model/);

  // resolveReleaseDependencyLocks: lock contents must match what the configuration resolves.
  const library = join(fixture, "repo/com/example/lib/1.0");
  mkdirSync(library, { recursive: true });
  writeFileSync(join(library, "lib-1.0.pom"), `<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.example</groupId>
  <artifactId>lib</artifactId>
  <version>1.0</version>
  <packaging>pom</packaging>
</project>
`);
  writeFileSync(buildPath, `
repositories { maven { url = rootProject.file('repo').toURI() } }
dependencyLocking { lockMode = LockMode.STRICT }
configurations.create('productionReleaseRuntimeClasspath') {
    canBeResolved = true
    resolutionStrategy.activateDependencyLocking()
}
dependencies { productionReleaseRuntimeClasspath 'com.example:lib:1.0' }
`);
  writeFileSync(lockPath, "com.example:lib:1.0=productionReleaseRuntimeClasspath\nempty=\n");
  result = run("resolveReleaseDependencyLocks");
  assert.equal(result.status, 0, result.output);
  assert.match(result.output, /:prod: 1 productionRelease configurations match gradle\.lockfile/);

  // A dependency added without --write-locks.
  writeFileSync(lockPath, "empty=productionReleaseRuntimeClasspath\n");
  result = run("resolveReleaseDependencyLocks");
  assert.notEqual(result.status, 0, result.output);
  assert.match(result.output, /Resolved 'com\.example:lib:1\.0' which is not part of the dependency lock state/);

  // A dependency removed without --write-locks.
  writeFileSync(lockPath, "com.example:gone:1.0=productionReleaseRuntimeClasspath\ncom.example:lib:1.0=productionReleaseRuntimeClasspath\nempty=\n");
  result = run("resolveReleaseDependencyLocks");
  assert.notEqual(result.status, 0, result.output);
  assert.match(result.output, /Did not resolve 'com\.example:gone:1\.0' which is part of the dependency lock state/);

  console.log("Production release lock coverage mutation tests passed");
} finally {
  rmSync(fixture, { recursive: true, force: true });
}
