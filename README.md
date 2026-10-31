<!-- fork-homepage-detail:v1:start -->
# SWT: native desktop interfaces for Java

The Standard Widget Toolkit supplies Java widgets, events, layouts and graphics
backed by the host desktop platform. It powers Eclipse and can also be used by
standalone JVM applications. Start here for a native widget, event-loop, JNI or
resource-lifetime problem; use Platform UI for JFace viewers and workbench parts.

[Source map](#swt-source-map) · [Concepts](#swt-key-concepts) ·
[Quickstart](#swt-quickstart) · [Example](#swt-worked-example) ·
[Development guide](docs/DEVELOPMENT_GUIDE.md)

## SWT source map

| Area | What it owns |
| --- | --- |
| [Main SWT bundle](bundles/org.eclipse.swt/) | Shared APIs and platform-specific widget implementations |
| [Native development notes](bundles/org.eclipse.swt/Readme.md) | JNI and host-specific native build routes |
| [binaries/](binaries/) | OS/window-system/architecture-specific native fragments |
| [Snippets](examples/org.eclipse.swt.snippets/) | Small runnable API demonstrations and reproductions |
| [tests/](tests/) | Common tests and backend-specific test fragments |
| [SWT tools](bundles/org.eclipse.swt.tools/) | JNI generation and developer tooling |

The same widget name can have different GTK, Win32 and Cocoa implementations.
Find the actual host backend before explaining behavior from a source file.

## SWT key concepts

- **Display:** owns the UI event context and dispatch loop.
- **Shell / Control:** a top-level window and the base abstraction for controls.
- **Listener:** responds to a widget event; layout determines geometry separately.
- **UI-thread affinity:** widget operations normally belong on the owning display
  thread. `asyncExec` schedules UI work; it does not keep a control alive.
- **Native fragment / JNI:** matching platform libraries and the Java/native
  boundary. Java compilation alone does not prove the native library can load.
- **Owned / borrowed resource:** application-created `GC` and `Image` objects
  need cleanup; borrowed paint-event or framework-owned resources must not be
  disposed as though the application created them.

## SWT fork status

This is an experimental downstream fork of
[Eclipse SWT](https://github.com/eclipse-platform/eclipse.platform.swt).
The checkout contains SWT APIs/backends, fork-specific implementation work such
as [ItemBitSet](bundles/org.eclipse.swt/Eclipse%20SWT/common/org/eclipse/swt/internal/residency/ItemBitSet.java),
and [M3 recipe/inventory tooling](.m3/README.md). Source presence is not a claim
that every backend, default model or optimization is qualified. The
[development guide](docs/DEVELOPMENT_GUIDE.md) distinguishes native rendering,
visible-screen evidence, behavior tests and independent resource measurements.
Upstream releases and badges below describe their linked upstream projects.

## SWT quickstart

For application adoption, follow the original platform-specific JAR and Hello
World example below. For source development, the inspected
[matrix](.github/workflows/maven.yml) uses **Java 25** and the
[build workflow](.github/workflows/build.yml) selects **Maven 3.9.16**.
Install/configure Git LFS before cloning if using precompiled native binaries,
or follow the host-native build guide. You also need native dependencies and a
working display for GUI tests.

```sh
git clone https://github.com/hsoliwal/eclipse.platform.swt.git
cd eclipse.platform.swt
git rev-parse HEAD
git lfs ls-files
java -version
mvn -version
mvn clean verify
```

This is a source-checked build route, not a test receipt. LFS pointer files are
not usable native binaries, and packaging multiple fragments does not execute
tests on all of their platforms.

## SWT command and debugging cheat sheet

```sh
# Artifact-only build; does not establish test success.
mvn clean verify -DskipTests
# Native verification on a provisioned Linux x86_64 GTK host only.
mvn clean verify -Dnative=gtk.linux.x86_64
# Find the smallest event-loop reproductions.
git grep -n 'readAndDispatch' -- examples/org.eclipse.swt.snippets
```

- **Invalid thread access:** trace the caller and display ownership first.
- **Disposed widget in delayed work:** check liveness inside the dispatched
  callback and coordinate display shutdown with workers.
- **Native load failure:** check OS, CPU, resolved LFS objects and fragment match.
- **JNI changes:** use the documented generator, review its diff and build the
  matching native library. Do not hand-edit generated glue or commit local binaries.

## SWT worked example

**Open, resize and close a minimal window.** Read
[Snippet1.java](examples/org.eclipse.swt.snippets/src/org/eclipse/swt/snippets/Snippet1.java)
in a configured SWT workspace:

1. A standalone application creates its `Display` and a `Shell`.
2. `shell.open()` makes the window available for interaction.
3. `readAndDispatch()` processes events; `sleep()` waits when there are none.
4. Closing the shell ends the loop; the application disposes the display it owns.
5. Add the README's button/text example only after that lifecycle works. Keep
   listeners short and check repeated open/close behavior on the real backend.

The workbench owns its display, so a plug-in must not copy standalone teardown
blindly. This is an exploration to run, not evidence of a new native/UI test pass.
Continue with [the development guide](docs/DEVELOPMENT_GUIDE.md),
[Linux](bundles/org.eclipse.swt/Readme.Linux.md),
[Windows](bundles/org.eclipse.swt/Readme.Win32.md) or
[macOS](bundles/org.eclipse.swt/Readme.macOS.md) setup and
[CONTRIBUTING.md](CONTRIBUTING.md). Existing fork/upstream material follows.

> Source tour checked at [`9cc0683c4925`](https://github.com/hsoliwal/eclipse.platform.swt/tree/9cc0683c49255a23400ba491d86e1311e69447c0). Commands describe that revision; recheck build configuration when changing revisions. Build/UI/performance verification was not run for this README update.

---

<!-- fork-homepage-detail:v1:end -->

<div align="center">

## M³ × SWT

**Native desktop widgets are where runtime ideas meet real UI contracts.**

[![M3 research target](https://img.shields.io/badge/M%C2%B3-desktop%20research%20target-4b62b7?style=flat-square)](https://github.com/hsoliwal/M3jdk21)
[![Evidence first](https://img.shields.io/badge/acceptance-tests%20%2B%20measurements-287b57?style=flat-square)](https://github.com/hsoliwal/M3jdk21/blob/master/m3/release/BENCHMARK_AND_COMPATIBILITY_SPEC.md)

**[M3 runtime programme](https://github.com/hsoliwal/M3jdk21)** · **[Technical paper](https://github.com/hsoliwal/M3jdk21/blob/master/m3/papers/M3_SHARED_STRUCTURE_AND_REUSABLE_COMPUTATION.md)** · **[Source invitation](#come-inspect-the-code)**

</div>

> [!NOTE]
> **This is an experimental downstream fork of [the upstream SWT project](https://github.com/eclipse-platform/eclipse.platform.swt).** M3 String, regex, precompute and collection integration are **engineering directions**, not verified deployment, performance, compatibility or upstream-endorsement claims. Preserve original licensing, copyright, API behavior and native/UI contracts.

### Engineering questions for this fork

| Surface | What to investigate | Non-negotiable contract |
| --- | --- | --- |
| **Events and input** | Measure event-dispatch latency and repeated text work | UI-thread affinity and event ordering |
| **Native boundaries** | Study JNI/native transitions and canonical shared data | SWT platform behavior and resource lifecycle |
| **Widget resources** | Measure allocation, retained memory, disposal and steady-state responsiveness | No leaks, regressions or changed public APIs |

**Approach:** inventory first; copy/derive only with license and provenance; qualify reusable recipes in Synexia; port into the appropriate runtime/product owner; then test with representative desktop projects and publish CPU, allocation, retained-memory, startup and responsiveness numbers, including regressions. No Synexia runtime dependency.

### Come inspect the code

> **Java, JNI, SWT, workbenches, widgets, real code: open the repositories.** Bring a failing case, a performance counterexample, a profiler trace or a better algorithm. Challenge the design on the merits of source, tests and evidence—not slogans or a résumé.

**Explore the family:** [M3JDK21](https://github.com/hsoliwal/M3jdk21) · [SWT](https://github.com/hsoliwal/eclipse.platform.swt) · [Eclipse Platform](https://github.com/hsoliwal/eclipse.platform) · [Platform UI](https://github.com/hsoliwal/eclipse.platform.ui) · [Nebula](https://github.com/hsoliwal/nebula) · [GEF Classic](https://github.com/hsoliwal/gef-classic).

**Contribute here:** [issues](https://github.com/hsoliwal/eclipse.platform.swt/issues) · [pull requests](https://github.com/hsoliwal/eclipse.platform.swt/pulls) · [M3 compatibility and benchmark contract](https://github.com/hsoliwal/M3jdk21/blob/master/m3/release/BENCHMARK_AND_COMPATIBILITY_SPEC.md).

The remainder of this README retains the existing M3 fork notes and the **original upstream SWT documentation**. Nothing in this introduction rebrands upstream work as M3-authored code.

---

## M3 direction in this fork

M3 is based on the idea that shared immutable structure and indexed metadata can
provide a basis for reusable computation. The programme starts with M3 String,
regex, and precompute in M3JDK21, then collections and SWT/Eclipse integration.

This SWT fork is a target for exploring qualified runtime and collection improvements at the desktop toolkit boundary, with widget behavior, native integration, and responsiveness remaining central.

**Explore the idea:** [M3 technical design paper](https://github.com/hsoliwal/M3jdk21/blob/master/m3/papers/M3_SHARED_STRUCTURE_AND_REUSABLE_COMPUTATION.md) ·
[Programme goals and roadmap](https://github.com/hsoliwal/M3jdk21#project-goals-and-plan).
The paper explains the proposed architecture, cost tradeoffs, and evaluation plan.
Readers are invited to examine the work and contribute representative workloads.

This section describes the direction of the hsoliwal fork. Upstream documentation
follows below; upstream authorship, licenses, and project identity remain intact.

---

[![SWT Matrix Build](https://github.com/eclipse-platform/eclipse.platform.swt/actions/workflows/maven.yml/badge.svg)](https://github.com/eclipse-platform/eclipse.platform.swt/actions/workflows/maven.yml)
[![Publish Unit Test Results](https://github.com/eclipse-platform/eclipse.platform.swt/actions/workflows/junit.yml/badge.svg)](https://github.com/eclipse-platform/eclipse.platform.swt/actions/workflows/junit.yml)
[![License](https://img.shields.io/github/license/eclipse-platform/eclipse.platform)](https://github.com/eclipse-platform/eclipse.platform.swt/blob/master/LICENSE)

# About

SWT is a cross-platform GUI library for JVM based desktop applications.
The best known SWT-based application is [Eclipse](https://www.eclipse.org).

For more information about SWT, visit the [official SWT page](https://eclipse.dev/eclipse/swt/).

## Getting Started

SWT comes with platform-specific jar files.
Download them from https://download.eclipse.org/eclipse/downloads and add the jar file to your classpath.

### Example
![Example](example.png)
```java
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public class HelloWorld {

	public static void main(String[] args) {
		final Display display = new Display();

		final Shell shell = new Shell(display);
		shell.setText("Hello World");
		shell.setLayout(new GridLayout(2, false));

		final Label label = new Label(shell, SWT.LEFT);
		label.setText("Your &Name:");
		label.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));

		final Text text = new Text(shell, SWT.BORDER | SWT.SINGLE);
		final GridData data = new GridData(SWT.FILL, SWT.CENTER, true, false);
		data.minimumWidth = 120;
		text.setLayoutData(data);

		final Button button = new Button(shell, SWT.PUSH);
		button.setText("Say Hello");
		shell.setDefaultButton(button);
		button.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false, 2, 1));

		final Label output = new Label(shell, SWT.CENTER);
		output.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

		button.addListener(SWT.Selection, event -> {
			String name = text.getText().trim();
			if (name.length() == 0) {
				name = "world";
			}
			output.setText("Hello " + name + "!");
		});

		shell.setSize(shell.computeSize(SWT.DEFAULT, SWT.DEFAULT));
		shell.open();

		while (!shell.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}

		display.dispose();
	}
}
```
First, a `Display` is created which is something like the central place of all GUI-related code.
Then a `Shell` is created which in our example is a top-level window.
Then all child controls and listeners are created, including their layout information.
Finally, we set the window's size determines by its child controls and open the window.
The `while`-loop processes all GUI related events until the shell is disposed which happens when closing.
Before exiting, any claimed GUI resources needs to be freed.


# Contributing to SWT

Thanks for your interest in this project.

For information about contributing to Eclipse Platform in general, see the general [CONTRIBUTING](https://github.com/eclipse-platform/.github/blob/main/CONTRIBUTING.md) page.

[![Create Eclipse Development Environment for Eclipse SWT](https://download.eclipse.org/oomph/www/setups/svg/SWT.svg)](
https://www.eclipse.org/setups/installer/?url=https://raw.githubusercontent.com/eclipse-platform/eclipse.platform.swt/master/bundles/org.eclipse.swt.tools/Oomph/PlatformSWTConfiguration.setup&show=true
"Click to open Eclipse-Installer Auto Launch or drag into your running installer")

## Contributor navigation and prerequisites

- [bundles/org.eclipse.swt/](bundles/org.eclipse.swt/) owns SWT Java code and
  the platform-specific JNI sources. Its [development overview](bundles/org.eclipse.swt/Readme.md)
  explains the Java/native split and JNI regeneration
- [binaries/](binaries/) contains fragments selected by window system, OS and
  architecture. Use the fragment matching the machine on which you run SWT
- [snippets](examples/org.eclipse.swt.snippets/Readme.md) are small reproductions;
  [examples](examples/org.eclipse.swt.examples/Readme.md) are larger demonstrations
- [tests/](tests/) contains the common SWT test bundle and native-platform
  test fragments. The [root POM](pom.xml) selects the platform-specific test modules

The current [matrix workflow](.github/workflows/maven.yml) selects JDK 25;
its [build workflow](.github/workflows/build.yml) selects Maven 3.9.16.
Use those checked-in values to reproduce that lane. Older platform notes
include historical JDK versions and last-tested dates; they are not a promise
that the current reactor builds with those older JDKs.
[.mvn/maven.config](.mvn/maven.config) enables the individual-bundle profile,
which resolves the Eclipse parent through the repository configured in
[pom.xml](pom.xml).

Before rebuilding native code, read the guide for the actual host platform:

- [Linux/GTK](bundles/org.eclipse.swt/Readme.Linux.md): make, a C compiler and
  the matching GTK development files; the
  [GTK development guide](docs/gtk-dev-guide.md) has additional detail
- [Windows/Win32](bundles/org.eclipse.swt/Readme.Win32.md): Visual Studio C++
  build tools and the Windows SDK
- [macOS/Cocoa](bundles/org.eclipse.swt/Readme.macOS.md): Xcode Command Line Tools

The build workflow records additional Linux libraries and the display-session
setup used by CI. GUI tests need an appropriate display backend: its X11 lane
uses Xvfb and its Wayland lane uses a headless Mutter session. A successful build
on one OS/backend does not qualify another.

Native declarations and generated JNI C sources must stay in sync. Follow the
[JNI regeneration instructions](bundles/org.eclipse.swt/Readme.md#regenerating-the-jni-glue-code)
instead of editing generated glue by hand. Keep compiled native binaries out of
contributions, as described below.

Fork-specific work goes to
[hsoliwal/eclipse.platform.swt pull requests](https://github.com/hsoliwal/eclipse.platform.swt/pulls).
The upstream contribution links and Oomph setup above remain the route for
upstream SWT work; verify the workspace's remote before editing. Include the
OS, architecture, GTK/display backend where applicable, exact build command and
test reports in a bug report or PR. Mark unrun native/UI checks explicitly.

## Package and lifecycle documentation

The [architecture and coverage guide](docs/architecture/README.md) connects
[source-backed lifecycle contracts](docs/architecture/lifecycle-contracts.md),
[project/module ownership](docs/architecture/module-ledger.md),
[Java package and source-set coverage](docs/architecture/package-ledger.md), and
[export boundaries](docs/architecture/export-boundaries.md).
It pins the inspected source revision and keeps unreviewed semantics, generated
or native boundaries, and unrun verification visible. Inventory coverage is
reported separately from complete package/API documentation.

## Obtaining the sources

The SWT source code is stored in this Git repository.

This repository uses the [Git Large File Storage](https://git-lfs.com/) (LFS) extension to host also the precompiled SWT native binaries,
which is therefore required unless binaries are compiled locally.

When using Git from the CLI follow the Git LFS installation and setup instructions:
- https://github.com/git-lfs/git-lfs/wiki/Installation#installing

When using _EGit_ from within Eclipse follow the EGit user guide on _GIT LFS Support_:
- https://github.com/eclipse-egit/egit/wiki/User-Guide#git-lfs-support

Note:<br>
Changes to the binary artifacts must not be committed, as the binaries are compiled only in the Eclipse infrastructure.

## Building

To build all SWT artifacts and to run all tests,
execute at the CLI from the repository root:
```bash
mvn clean verify
```
To build only the artifacts, without testing, run
```bash
mvn clean verify -DskipTests
```

For instructions on building the native binaries of SWT see the section [Building native binaries](bundles/org.eclipse.swt/Readme.md#building-native-binaries).

## Developer Resources

See the following description for how to contribute a feature or a bug fix to SWT.

- https://eclipse.dev/eclipse/swt/

Information regarding source code management, builds, coding standards, and more can be found under the following link.

- <https://projects.eclipse.org/projects/eclipse.platform.swt/developer>

Also see in the SWT section of the Eclipse FAQ for more background information about SWT.

- <https://github.com/eclipse-platform/eclipse.platform/blob/master/docs/FAQ/The_Official_Eclipse_FAQs.md#standard-widget-toolkit-swt>

## Contributor License Agreement

Before your contribution can be accepted by the project, you need to create and electronically sign the Eclipse Foundation Contributor License Agreement (CLA).

- https://www.eclipse.org/legal/eca/

## Contact

Contact the project developers via GitHub:

- [GitHub Discussions](https://github.com/eclipse-platform/eclipse.platform.swt/discussions) - For questions and general discussions
- [GitHub Issues](https://github.com/eclipse-platform/eclipse.platform.swt/issues) - For bug reports and feature requests

You can register bugs and feature requests in the GitHub Issue Tracker. Remember that contributions are always welcome!

Please bear in mind that this project is almost entirely developed by volunteers. If you do not provide the implementation yourself (or pay someone to do it for you), the bug might never get fixed. If it is a serious bug, other people than you might care enough to provide a fix.

**Note:** SWT used to track ongoing development and issues in [Bugzilla](https://bugs.eclipse.org/bugs/buglist.cgi?product=Platform&component=SWT), but we now use GitHub Issues and Discussions.


# Evaluation of a Single, Cross-Platform SWT Implementation

In project [Initiative 31](https://github.com/swt-initiative31) an evaluation on the feasibility of implementing SWT in an OS-agnostic way based on a cross-platform graphics/widgets library has been conducted.
The goal was to assess if the existing three implementations could eventually be replaced by an OS-agnostic one in order to reduce maintenance efforts, enable highly customizable look and feel, and improve configurability.
A demonstrator based on the graphics library Skia was implemented and multiple prototypes for mitigating risks have been implemented, such that bringing that to a production-ready implementation is a matter of effort but not of conceptual or technical/technological risk.

The actual prototyping work has started on four technologies: Skia with Visual Class Library (VCL), Skia with custom-implemented widgets, GTK, and Swing\
All those prototypes can be found in the according GitHub organization: https://github.com/swt-initiative31 \
The work finally focused on the Skia graphics library with custom-implemented widgets: https://github.com/swt-initiative31/prototype-skija

The project concluded with the feasibility demonstration and a [report summarizing the results](https://github.com/swt-initiative31/documents/blob/main/report/overall_report.md).
Given sufficient resources, a follow-up project could convert the demonstrator into a production-ready state.

## Fork development guide

For a source map, current build prerequisites, command cheat sheet and a worked event-loop tour, see the [SWT development guide](docs/DEVELOPMENT_GUIDE.md).

## Donor notes: dancer1325/eclipse.platform.swt

Condensed notes from donor commit 95b4bef5978 ("doc(): clear notes"), carried verbatim with headings demoted; the sections above stay the canonical text.

* Eclipse SWT
  * == cross-platform GUI library -- for -- JVM based desktop applications
    * -> includes platform-specific ".jar"
  * _Example:_ [Eclipse](https://www.eclipse.org)
  * 👀ALMOST ENTIRELY developed -- by -- volunteers 👀

### Getting Started

* steps
  * download it [here](https://download.eclipse.org/eclipse/downloads/index.html)
  * add the ".jar" | YOUR classpath

* _Example:_
  * TODO: How to create a NEW Eclipse project?

### Contributing to SWT

* follow [Eclipse Platform's CONTRIBUTING](https://github.com/eclipse-platform/.github/blob/main/CONTRIBUTING.md)
* steps
  * create a Eclipse Development Environment -- [link](https://www.eclipse.org/setups/installer/?url=https://raw.githubusercontent.com/eclipse-platform/eclipse.platform.swt/master/bundles/org.eclipse.swt.tools/Oomph/PlatformSWTConfiguration.setup&show=true) --
    * [.setup used](bundles/org.eclipse.swt.tools/Oomph/PlatformSWTConfiguration.setup)

#### Developer Resources

* see [Eclipse FAQ](https://github.com/eclipse-platform/eclipse.platform/blob/master/docs/FAQ/The_Official_Eclipse_FAQs.md#standard-widget-toolkit-swt)

#### Contact

* [project's "dev" list](https://accounts.eclipse.org/mailing-list/platform-dev)

#### CURRENT Bugs

* see [Bugzilla](https://bugs.eclipse.org/bugs/buglist.cgi?product=Platform&component=SWT)

### Prototype | 1! Cross-Platform SWT Implementation

* see [here](https://github.com/swt-initiative31)
* Reasons: 🧠
  * reduce maintenance efforts,
  * enable better look and feel
  * improve configurability 🧠
* technologies suggested
  * Skia with Visual Class Library (VCL),
  * [Skia + custom-rendered widgets](https://github.com/swt-initiative31/prototype-skija),
  * [GTK](https://github.com/swt-initiative31/prototype-gtk),
  * Swing

# Using SWT with GTK on Windows (donor: swt-initiative31/prototype-gtk, branch gtk-multi-os)

> Superset note (donor-resolve 2026-10-31): the section below is the donor's README, appended verbatim after its
> first heading. In this superset the `org.eclipse.swt.gtk.win32.x86_64` fragment is carried as a source project and
> is not in the Maven reactor (`binaries/pom.xml` and the root `pom.xml` are unchanged); it is selected with the
> window system `gtk` (`Eclipse-PlatformFilter: (& (osgi.ws=gtk) (osgi.os=win32) (osgi.arch=x86_64))`, launch with
> `-ws gtk`) instead of claiming `osgi.ws=win32`, and the `org.eclipse.swt.win32.win32.x86_64` fragment keeps its
> `osgi.os=win32` filter, so the win32 port (viewport default) is never displaced. The TreeItem image tweak is
> superseded by our TreeItem, which returns images from its per-column cache (`cellImage`, viewport `virtualImages`)
> instead of reading the model surface back; the splash-shell tweak applies only when `OS.IsWin32`. The fragment's
> natives are revisions 4965r11/4967r8; a GTK-on-Windows native build at 4975r19 is needed before use.

This branch prototypes the usage of GTK for rendering SWT on Windows. It is based on work done some years ago: https://bugs.eclipse.org/bugs/show_bug.cgi?id=488431

![alt text](gtk-on-windows-example.png)

## Installing GTK on Windows

To provide GTK on Windows, you best follow the official GTK documentation: https://www.gtk.org/docs/installations/windows

The tests I have made based on this branch are using the MSYS2-based setup of GTK, which works fine.

1. Install MSYS2: https://msys2.github.io/
2. Update MSYS2:
     ``` 
     pacman -Syuu
     ``` 
3. Install GTK 3/4 via the MSYS shell:
      ```
      pacman -S mingw-w64-x86_64-gtk3
      pacman -S mingw-w64-x86_64-gtk4
      ```
4. Add the libraries installed via MSYS to your `PATH`. E.g., when using the default MSYS installation directory `C:\msys64`, the `PATH` entry should be: `C:\msys64\mingw64\bin`
5. _Optional:_ If you want to or need to compile the SWT binaries, you should istall:
      ```
      pacman -S mingw-w64-x86_64-toolchain
      ```
  
## Using GTK on Windows
For GTK on Windows, there is a new SWT fragment project in the `binaries` folder: `org.eclipse.swt.gtk.win32.x86_64`. Be aware that it is currently integrated via some tweaks, e.g., the misuse of the specified window system to ensure that GTK fragment is used on Windows even though a Win32 window system is present and to _not_ use the original Windows fragment.

Steps to use:
* Have an ordinary Eclipse SDK workspace with SWT.
* Checkout this branch and, if necessary, rebase it onto your used SWT state.
* Import the `org.eclipse.swt.gtk.win32.x86_64` fragment into your Eclipse workspace.
* In case you do not use the state of SWT as provided in the `gtk-on-windows` branch, you need to generate the binaries (see below).
* Start some product (for example the `org.eclipse.sdk.ide` product) using SWT and make sure that your launch configuration uses the GTK instead of the Win32 SWT fragment and that the Win32-specific UI fragments are not enabled.
  * The following should _not_ be enabled:
    * `org.eclipse.swt.win32.win32.x86_64`
    * `org.eclipse.compare.win32`
    * `org.eclipse.e4.ui.swt.win32`
    * `org.eclipse.ui.win32`
  * The following should be enabled instead:
    * `org.eclipse.swt.gtk.win32.x86_64`

![alt text](gtk-on-windows-application.png)

![alt text](gtk-on-windows-plugins.png)


### Building the binaries
* Start a Maven build for the SWT repository by calling `mvn clean compile` in the root folder. This will currently not succeeds but will at least generate some metadata for the binaries generation.
* Run the following command in the `binares/org.eclipse.swt.gtk.win32.x86_64` folder:
  ```
  mvn clean process-resources -Dnative=gtk.win32.x86_64 -Dws=gtk -Dos=win32 -Darch=x86_64 -DskipTests
  ``` 
  
  This should generate the binaries, i.e, in the `build-native-binaries` phase, it should execute the batch script running the C++ compiler and the copy the generated *.dll files to the project folder.

Note that this currently only builds GTK3 binaries and does not allow to use GTK4 yet. However, it's only because the latter has not been tried out and might be easy to achieve.


## Limitations and Workarounds

Some limitations of the GTK on Windows usage for SWT have been documented in the original issue: https://bugs.eclipse.org/bugs/show_bug.cgi?id=488431

There are two further tweaks currently applied in this branch:
* Images used by TreeItems are not accessed properly. They are stored in an image map of the item's Tree, but not retrieved properly. In this branch, a patch is applied that simply stored the proper image surface within each TreeItem.
* A specific shell constructor is currently not working, making the splash screen creation fail. In this branch, a patch is applied that simply creates a new shell for the splash instead of used a passed handle.

When using SWT with the Eclipse Platform, note that the Win32-specific bundles can currently not be used and have to be removed from launch configurations:
* `org.eclipse.compare.win32`
* `org.eclipse.e4.ui.swt.win32`
* `org.eclipse.ui.win32`

They may need to or can be replaced by GTK-specific bundles, but no work has been done on that yet.

## Donor notes: dvt64/oi-eclipse.platform.swt

From donor commits 420209f74c3 ("Port for illumos-based distros") and 982b4c3b8e5, line carried verbatim. The port's files: fragment binaries/org.eclipse.swt.gtk.solaris.x86_64 (outside the default binaries/pom.xml reactor, like gtk.linux.loongarch64, until illumos natives are built), binaries/legal_files/gtk.solaris, and the SunOS case of `Eclipse SWT PI/gtk/library/build.sh` with `make_solaris_x86_64.mak`.

Port for illumos-based distros (tested on OpenIndiana).

# Donor section: claesrosell/prototype-skija

Appended by the additive superset resolution (DOC recipe: keep ours, append the donor section). Source: `README.md` of
[claesrosell/prototype-skija](https://github.com/claesrosell/prototype-skija) at `f262d18f088f` (2025-02-22), the
Initiative 31 prototype-skija history before its 2025 rebase, carried in this repository's history as a merge parent.
The text describes that donor branch, not this tree: here the native widgets stay on the M3 viewport, the Initiative 31
look is a renderer and look-and-feel option ([ALL_WIDGETS_VIEWPORT_PLAN.md](docs/m3/ALL_WIDGETS_VIEWPORT_PLAN.md),
[LOOK_AND_FEEL.md](docs/m3/LOOK_AND_FEEL.md)), and the `SkijaGC` operation mapping was adapted for the M3 GPU planes.

## SWT on Custom-Rendered Widgets With Ski(j)a

This repository is a fork of the original [SWT repository](https://github.com/eclipse-platform/eclipse.platform.swt) containing prototyping work on exchanging the adaptation of native, basic widgets with custom-drawn widgets based on the rendering engine Skia and its Java bindings [Skija](https://github.com/HumbleUI/Skija/).

In the following, you find information about the current state of the prototype and how to try it out. Note that the prototype does currently not provide other/better look and feel or customizability compared to the native widgets. It is supposed to demonstrate feasibility to custom draw widgets based on an efficient rendering engine (as an enabler for improved customizability and look and feel) while seamlessly integrating the custom-drawn widgets with the other, still native widgets. For that reason, the custom-drawn widgets are currently implemented to look similar to the existing widgets they replace, but they could be easily replaced with whatever look and feel is desired.

### How to Contribute

See our [contributing information](https://github.com/claesrosell/prototype-skija/blob/f262d18f088f46aa1a909512194c9b60c187f853/CONTRIBUTING.md) to find out how to contribute.

### Getting Started

To test the prototype, an Eclipse IDE has to be set up and the SWT projects from this repository have to be imported into it to start example applications.

Note that the current development does not target all platforms (Windows, MacOS, Linux). Development usually happens on Windows (and partly MacOS), so the experience will be best there. No implementation and testing for Linux has been performed so far.

#### Setup

The prototype is currently based on the Eclipse release 2024-12.

1. Set up a Eclipse IDE for Committers 2024-12, e.g.:
   - [Eclipse IDE for Committers 2024-12](https://www.eclipse.org/downloads/packages/release/2024-12/r/eclipse-ide-eclipse-committers)
   - [Eclipse SDK Development Environment](https://github.com/eclipse-platform/eclipse.platform?tab=readme-ov-file#how-to-contribute) using product version "4.34 - 2024-12" in the installer
2. Clone this repository
3. Start the downloaded IDE and import the following projects:
   - The SWT bundle `org.eclipse.swt`
   - The OS-specific fragment `org.eclipse.swt.$WS.$OS.$ARCH` with the placeholders according to your environment, such as `org.eclipse.swt.win32.win32.x86_64` for the Windows fragment
   - _Optional:_ For testing purposes `org.eclipse.swt.examples`

The resulting workspace should look something like this:
![Workspace after setup](readme_images/workspace_after_setup.png)

#### Examples

Starting with this, you can try out the SWT implementation with whatever application you want by also importing the according plug-ins into your workspace. Two reasonable, simple starting points are (1) the SWT `ControlExample`, a demo application containing all basic SWT widgets with configurability for most of their functionality, to see how the widgets look like and behave, and (2) a simple Eclipse application to see how it looks in a complete application.

##### `ControlExample`

The `ControlExample` is part of the example project. It is placed in `org.eclipse.swt.examples.controlexample`. You can run this class as a Java application. The application has tabs for the individual widgets. The tab that opens first is for the `Button`, which is completely custom-drawn. So almost everything you see on that page is custom-drawn via code of the prototype.

At the bottom of the application, a toggle button allows to switch between using the native and the Skija-based renderer ( `GC`) at runtime (see [State](#state)). You can currently distinguish the two rendering engines by looking at the edges of buttons, which are currently not rounded when using the Skija-based GC. The following screenshot shows the rendering of that page based on Skija.

![ControlExample Button Page](readme_images/controls_button_windows_custom_skijagc.png)

##### Eclipse Application

To start a simple Eclipse application, create a `Run Configuration` via the menu `Run`, menu item `Run Configuration` and in the dialog opening up by double-clicking on the `Eclipse Application` type to create a new configuration that you can launch via the `Run` button:
![Eclipse Application Run Configuration](readme_images/eclipse_run_configuration.png)

The Eclipse application that starts will automatically use the custom-drawn widgets. To see a page with mostly custom-drawn widgets, you can, for example, open the `Create Java Project` wizard:
![Java Project Creation Wizard](readme_images/java_project_creation_windows_custom_skijagc.png)

### State

Note that this is work-in-progress prototyping work. The implementation is not (supposed to be) production ready.
Currently, there are (at least partial) custom implementations for the following basic widgets:
- Button
- Label
- Text
- Combo

For rendering, the custom-drawn widgets only rely on the functionalities of the `GC`, such a drawing text and primitives like lines.
In addition to using the existing, native `GC`, i.e., the rendering engine of the operating system, the implementation also contains a `SkijaGC`, providing (parts of) the same functionality but using Ski(j)a for rendering instead. To make these interchangeable, the common functionality has been extracted into an `IGraphicsCanvas` interface.
