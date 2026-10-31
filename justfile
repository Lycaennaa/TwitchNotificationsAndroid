# Uses distill for Gradle when available; otherwise invokes Gradle directly.
# https://github.com/lycaennaa/distill


distill_available := `command -v distill >/dev/null 2>&1 && printf true || printf false`
gradle := if distill_available == "true" { "distill gradle" } else { "./gradlew" }

worktree_root := justfile_directory()
worktree_build_dir := worktree_root + "/.build"

@default:
    just --list

@setup:
    {{gradle}} help

@build:
    {{gradle}} :app:assembleDebug

@build-release:
    {{gradle}} :app:assembleRelease

@test:
    {{gradle}} :app:testDebugUnitTest

@lint:
    {{gradle}} :app:lintDebug

@check: test lint

@ci: check build-release

@run:
    {{gradle}} :app:installDebug

@clean:
    {{gradle}} clean
    rm -rf "{{worktree_build_dir}}"

@package: build-release

@dependencies:
    {{gradle}} :app:dependencies --configuration releaseRuntimeClasspath

@test-dependencies:
    {{gradle}} :app:dependencies --configuration debugUnitTestRuntimeClasspath
