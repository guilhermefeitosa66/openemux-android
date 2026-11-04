# CI of the OpenEmux Android fork

This repository is a fork of RetroArch, and upstream's CI came along with it:
96 workflows building RetroArch for consoles, Windows, Apple and Linux desktops,
plus libretro's `.gitlab-ci.yml`. None of them built the app OpenEmux ships.
They were removed in guilhermefeitosa66/OpenEmux issue 755, and one workflow
was left: `.github/workflows/Android.yml`, named **CI Android**.

## What CI Android does

It runs on pull requests to `openemux-mobile`, on pushes to it and by hand
(`workflow_dispatch`). Three jobs:

| Job | What it checks |
|---|---|
| Workflow allowlist | `.github/workflows/` holds only the files on the allowlist (`Android.yml`). |
| Unit tests and lint | `./gradlew testDebugUnitTest lintDebug` in `pkg/android/openemux/` (JDK 17). |
| Engine compile check | `ndk-build APP_ABI=arm64-v8a` in `pkg/android/phoenix-common/`, with ccache. Skipped on pull requests that touch only `pkg/android/openemux/`; pushes and manual runs always compile. |

Lint findings that predate CI (26 errors, most of them in RetroArch's Java
glue) are recorded in `pkg/android/openemux/lint-baseline.xml`, so only new
ones fail. Fix a finding rather than growing the baseline;
`./gradlew updateLintBaseline` regenerates it when that is really needed.

The engine job only proves that the C code, the OpenEmux hooks included, still
compiles with NDK 28. The library it produces is thrown away.

The OpenEmux pipeline gates on checks from a workflow named `CI Android`, so
keep that name.

## No APK in CI, on purpose

CI builds no APK or AAB and uploads no artifacts. The beta and release APKs are
built locally by the maintainer, signed with the production key, which never
leaves his machine. A clean checkout also lacks the git-ignored bundled assets
(covers, licenses, shaders, `corelibs/`), so an APK built on a runner would
start and crash.

## Adding a workflow

Add the file to `.github/workflows/` and its name to `allowed` in the
"Only the allowed workflows exist" step of `Android.yml`, in the same pull
request.

## After a sync from upstream

Merging `upstream/master` brings upstream's workflows back:

- a workflow upstream **added** shows up as a new file: delete it before
  pushing (the allowlist job fails otherwise);
- a workflow upstream **changed** shows up as a modify/delete conflict: keep the
  deletion (`git rm <file>`);
- a change upstream made to `Android.yml` conflicts with ours: keep ours;
- `.gitlab-ci.yml` comes back the same way: delete it.
