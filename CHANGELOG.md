<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# plugin Changelog

## [Unreleased]
### Fixed
- Parsing died with `NoClassDefFoundError: org/apache/commons/lang/StringEscapeUtils`. CHelper uses
  Commons Lang 2, which the platform used to provide and no longer does, so the plugin now ships it.
  This affects the Codeforces, Facebook, HackerEarth, USACO and CodeChef parsers.
- Parse contest created nothing on Codeforces. Codeforces changed three things the bundled CHelper
  parser matches literally — `input-file`/`output-file` divs gained a second class, sample lines are
  now wrapped one per `div` inside the `pre`, and `pre` no longer carries attributes — so every task
  silently failed to parse. The page is now adapted before CHelper sees it.
- Parse contest reports when it creates nothing instead of failing silently, and one unparseable task
  no longer abandons the rest of the contest.
- A task file is no longer registered as a run configuration before it has been written.

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