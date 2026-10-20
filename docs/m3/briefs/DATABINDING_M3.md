# Brief: JFace data binding (and EMF binding) on M3 data structures

User, 2026-10-20: "JFACE BINDING, EMF BINDING USING M3 DATA STRUCTURES". Read C:/m3-superset/tools/VIEWPORT_AGENT_BRIEF.md (rules 1-25), OPTIMIZATION_PASS.md and PROGRAMME_PLAN.md sections 00g, 00j, 00l, 00m first.

## Goal

Data binding is the third model the user complained about ("SWT has its model, then JFace, and JFace data binding too, very wasteful"). Make it a view over the one lane model instead of a second store:

- Observables on M3 data structures. `WritableList`/`ObservableList`/`WritableSet`/`ObservableMap`/`DecoratingObservable*` and the computed observables store elements in M3 array lanes and open-addressing tables (M3jdk21 m3/collections owners, or the SWT/JFace copy package when the bundle must stay JDK-neutral) instead of ArrayList/HashSet/HashMap. No Entry objects.
- Diffs as deltas. `ListDiff`/`SetDiff`/`MapDiff` and their `*DiffEntry` objects become an M3Patch-shaped run encoding (insert/remove/replace/move runs over positions). The public `ListDiff` API stays; entry objects are created lazily only when a caller iterates `getDifferences()`.
- Bindings go straight into viewers and into the public model API (brief rule 26: M3ListModel/M3TableModel/M3TreeModel + M3Patch, designed by the enhanced-API agent a13c57aeb02e89e47; an observable list bound to a Table IS the table's model, no copy). `ObservableListContentProvider`, `ObservableListTreeContentProvider`, `ObservableSetContentProvider`, `ObservableMapLabelProvider` and `ViewerSupport` feed the JFace agent's delta viewers with the patch: no viewer refresh, no second element set. Use the JFace agent's delta API (agent ae5acc369949b2347) and agree names first.
- Realm and threading stay as stock: same realm checks, same event order, same `ChangeEvent`/`ListChangeEvent` semantics.

## EMF (forked 2026-10-20 at the user's "YES": hsoliwal/org.eclipse.emf)

After the core data-binding leaves pass, implement EMF on the same structures in the fork, on branch m3/emf-m3-20261020 with a worktree under S:/m3-scratch/worktrees/emf:
- `Notification` kinds (ADD, REMOVE, ADD_MANY, REMOVE_MANY, MOVE, SET) are turned into M3Patch runs;
- `BasicEList`/`EObjectContainmentEList` storage moves onto lanes, with no change to the EList API;
- EMF databinding (`EMFObservables`/`EMFProperties`) runs on the M3 observables above;
- EMF.Edit item providers feed the JFace delta viewers instead of refreshing.
Keep EMF's own test suites green. Write EMF_PLAN.md first, listing the exact files.

## Repos and branches

- hsoliwal/eclipse.platform.ui (bundles org.eclipse.core.databinding, .observable, .property, .beans, org.eclipse.jface.databinding). New worktree under S:/m3-scratch/worktrees/databinding, branch m3/databinding-m3-20261020 off the JFace agent's branch m3/jface-viewport-20261019. Merge that branch forward, never copy.
- M3 collection owners: hsoliwal/M3jdk21 m3/collections. Ask the collections agent (a4a54cbc5baf005fc) before adding a new owner.

## Proof

- The existing databinding test suites (tests/org.eclipse.core.tests.databinding, org.eclipse.jface.tests.databinding) pass unchanged.
- A differential: stock vs ours on random operation scripts gives the same diffs, events and values.
- Before/after at 10^4 to 10^6 elements in OPTIMIZATION_LEDGER.tsv: retained heap per element, allocations per change, and time per change. A viewer bound to a 10^6-element WritableList takes a 1-element change with no refresh (DELTA_GATE.tsv).
- 99% coverage on new code.

## Machine rules

- Never merge PRs, never force-push. Draft PRs only.
- No Co-Authored-By or "Generated with" trailers.
- Java only, JUL logging, Synexia header on new files, contributor line on modified upstream files, hooks through recipes.
- Scratch only under S:/m3-scratch/databinding. Never write to G:, D: or E:.
- Builds through C:/m3-superset/tools/build_lock.sh databinding <cmd>.
- Commit and push after every leaf.
