// Changed for SWT (Apache-2.0 section 4b): package moved to org.eclipse.swt.internal.maya.
package org.eclipse.swt.internal.maya.shaper;

import org.jetbrains.annotations.*;
import org.eclipse.swt.internal.maya.*;
import org.eclipse.swt.internal.maya.impl.*;
import org.eclipse.swt.internal.maya.types.*;

public interface RunHandler {
    /**
     * Called when beginning a line.
     */
    void beginLine();

    /**
     * Called once for each run in a line. Can compute baselines and offsets.
     */
    void runInfo(RunInfo info);

    /**
     * Called after all {@link #runInfo} calls for a line.
     */
    void commitRunInfo();

    /**
     * Called for each run in a line after {@link #commitRunInfo}.
     * 
     * @return  an offset to add to every position
     */
    Point runOffset(RunInfo info);

    /**
     * <p>Called for each run in a line after {@link #runOffset}.</p>
     *
     * <p>WARN positions are reported from the start of the line, not run, only in Shaper.makeCoreText https://bugs.chromium.org/p/skia/issues/detail?id=10898</p>
     *
     * @param positions  put glyphs[i] at positions[i]
     * @param clusters   clusters[i] is an utf-16 offset starting run which produced glyphs[i]
     */
    void commitRun(RunInfo info, short[] glyphs, Point[] positions, int[] clusters);

    /**
     * Called when ending a line.
     */
    void commitLine();
}