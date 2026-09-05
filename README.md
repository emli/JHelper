# JHelper

![Build](https://github.com/emli/JHelper/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/7541-jhelper.svg)](https://plugins.jetbrains.com/plugin/7541-jhelper)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/7541-jhelper.svg)](https://plugins.jetbrains.com/plugin/7541-jhelper)

<!-- Plugin description -->

A CLion plugin for competitive programming. It parses problems from online judges, creates a task file
from your template, manages sample tests, and generates a single submission-ready file by inlining your
library's includes.

Requires CLion 2026.2 or later and works with the Nova C++ engine, the default since CLion 2025.3.
Earlier releases required the Classic engine, which JetBrains unbundled in 2026.2 and stops updating in
December 2026.

### Features

- Browse contests and create a task per problem, without leaving the IDE
- Create task files from a customisable template
- Add, edit and delete sample test cases
- Inline your library's `#include` directives into one submission file
- Run a task against its sample tests through a CMake test runner
- Copy the generated submission to the clipboard
- Accept problems sent from the browser by the CHelper Chrome extension

There are two ways to get a problem in, and they support different judges:

| | Judges |
| --- | --- |
| **Parse contest**, in the IDE | Codeforces |
| **Chrome extension**, sent from the browser | Codeforces, AtCoder, CodeChef, Kattis, HackerRank, HackerEarth, Yandex, USACO, CS Academy, Google Code Jam |

### Note

Dead code elimination is not available. It worked by walking the C++ syntax tree to find unreferenced
declarations, and Nova analyses C++ in a separate backend process without exposing that tree to plugins.
Submissions therefore contain everything your task file includes.

<!-- Plugin description end -->

## Installation

- Using the IDE built-in plugin system:

  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "JHelper"</kbd> >
  <kbd>Install</kbd>

- Using JetBrains Marketplace:

  Go to [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/7541-jhelper) and install it by clicking the <kbd>Install to ...</kbd> button in case your IDE is running.

  You can also download the [latest release](https://plugins.jetbrains.com/plugin/7541-jhelper/versions) from JetBrains Marketplace and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

- Manually:

  Download the [latest release](https://github.com/emli/JHelper/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

## Usage

After installing, the actions are available in two places, with no setup required:

- **Main toolbar**, to the right of the run and debug buttons — one button per action
- **Tools | JHelper**

To change where they appear, use <kbd>Settings</kbd> > <kbd>Appearance & Behavior</kbd> > <kbd>Menus and Toolbars</kbd>.
To bind keyboard shortcuts, use <kbd>Settings</kbd> > <kbd>Keymap</kbd> > <kbd>Plugins</kbd> > <kbd>JHelper</kbd>.

### Getting started

1. **Configure** — set your author name, tasks directory, output file and run file. The output file is
   where submissions are generated; the run file is the CMake target used to run sample tests.
2. **Add Task** — creates a task file from your template and opens it at `solve`.
3. Write your solution, then **Process file** to generate the submission, or **Copy source** to put it
   on the clipboard.

### Parsing a contest

**Parse contest** browses Codeforces from inside the IDE: pick a contest on the left, select the
problems you want on the right, and press OK. One task is created per selected problem. Problems that
cannot be fetched are reported individually and do not stop the rest of the contest.

Alternatively, the **CHelper Chrome extension** sends the page you are looking at straight to the IDE,
which creates the task from it. This covers more judges, and needs no dialog. The plugin listens on port
4243 while a project is open, so only one JHelper or CHelper project can be open at a time.

### Templates

`task`, `submission` and `run` templates are read from your project and can be customised. See the
defaults in [src/main/resources](./src/main/resources).

## Maintainer setup

Still outstanding for releases from this repository:

- [ ] Set the [plugin signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html) secrets
- [ ] Set the [deployment token](https://plugins.jetbrains.com/docs/marketplace/plugin-upload.html) for publishing
- [ ] Configure the [CODECOV_TOKEN](https://docs.codecov.com/docs/quick-start) secret for coverage reports

---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
