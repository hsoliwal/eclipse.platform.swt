#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# The interactive differential of the drawn modes (Test_org_eclipse_swt_widgets_Table_DrawnInteraction, interaction model
# IM6 in its stage 1 form) in custom, m3 and gpu, then the final pixels of gpu against m3. Run under ui_lock.sh.
# Environment: STAMP, TESTSET; OUT (default S:/m3-scratch/render-modes); BUDGETS=true asserts the I6 budgets.
S=${OUT:-S:/m3-scratch/render-modes}; RUN=$S/interaction; mkdir -p $RUN
M2=$(cygpath -m ~/.m2/repository); SK=C:/m3-superset/i31/binaries/org.eclipse.swt.win32.win32.x86_64/lib
CS=$M2/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar
CP="$S/tests-${TESTSET:?};$S/dist/org.eclipse.swt.win32.win32.x86_64-m3-${STAMP:?}.jar;$S/dist/swt-render-m3-$STAMP.jar;$SK/skija-shared-0.116.3.jar;$SK/skija-windows-x64-0.116.3.jar;$SK/types-0.1.1.jar"
for m in ${MODES:-custom m3 gpu}; do
  bash C:/m3-superset/tools/hidden_java.sh 900 $RUN/$m.log -Xmx1g -Dswt.widget.mode=$m -Dswt.viewport.evidence=$RUN -Dswt.drawn.interaction=$RUN/interaction.tsv \
    -Dswt.drawn.budgets=${BUDGETS:-false} -jar $CS execute -cp "$CP" --select-class org.eclipse.swt.tests.junit.Test_org_eclipse_swt_widgets_Table_DrawnInteraction --disable-banner --details=tree
  echo "$m: $(sed 's/\x1b\[[0-9;]*m//g' $RUN/$m.log | grep -aE 'tests (successful|failed|aborted)' | tr -s ' ' | tr '\n' ' ')"
done
python -I -c "
import sys
from PIL import Image
a, b = (Image.open(sys.argv[1] + '/interaction-' + m + '.png').convert('RGB') for m in ('m3', 'gpu'))
same = sum(1 for p, q in zip(a.getdata(), b.getdata()) if p == q)
print('gpu against m3: %d of %d pixels equal (%s)' % (same, a.width * a.height, 'EQUAL' if same == a.width * a.height and a.size == b.size else 'DIFFERENT'))
" $RUN
cat $RUN/interaction.tsv
