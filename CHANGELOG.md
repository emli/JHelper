<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# plugin Changelog

## [Unreleased]
### Added
- Support CLion 2026.2 and the Nova C++ engine
- Support CLion 2025.2

### Changed
- Include inlining no longer uses the C++ syntax tree. It now resolves `#include` directives from file
  text, so it works under the Nova engine, which performs its analysis in a separate backend process and
  exposes no frontend PSI to plugins ([CPP-39813](https://youtrack.jetbrains.com/issue/CPP-39813)).
- Task files are written directly through the virtual file system instead of being built with
  `PsiFileFactory` under the Classic-only "ObjectiveC" language.
- The Chrome extension listener is now a project service started by a startup activity, replacing the
  removed `ProjectComponent`.

### Removed
- Dead code elimination. It required walking the C++ syntax tree to find unreferenced declarations, and
  Nova exposes no equivalent API. The option is still shown in the configuration dialog, disabled, so the
  change is visible rather than silent.
- The dependency on the Classic C++ engine (`com.intellij.cidr.lang`), which JetBrains unbundled in
  CLion 2026.2 and stops updating in December 2026.