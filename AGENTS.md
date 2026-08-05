# Repository maintenance rules

For every user-visible feature, behavior change, or release pushed to this repository:

1. Update `versionCode` and `versionName` in `app/build.gradle.kts` according to semantic versioning.
2. Add a new entry at the top of the `README.md` section named `更新日志`.
3. Every entry must include the release date (`YYYY-MM-DD`), version number, and a concise list of changes.
4. The README changelog and version metadata must be included in the same commit as the related code changes.
5. Run `./gradlew testDebugUnitTest assembleDebug` before pushing and record the result in the pull request or handoff.
6. Publish installable versions with `.github/workflows/android-release.yml`; use the intentionally public signing material in `signing/` and never commit APK build outputs.
