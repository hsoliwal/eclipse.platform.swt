// Changed for SWT (Apache-2.0 section 4b): package moved to org.eclipse.swt.internal.maya.
package org.eclipse.swt.internal.maya.shaper;

import java.util.*;
import org.jetbrains.annotations.*;
import org.eclipse.swt.internal.maya.*;

public class TrivialLanguageRunIterator implements Iterator<LanguageRun> {
    @ApiStatus.Internal public final int     _length;
    @ApiStatus.Internal public final String  _language;
    @ApiStatus.Internal public       boolean _atEnd;
    
    public TrivialLanguageRunIterator(String text, String language) {
        _length   = text.length();
        _language = language;
        _atEnd    = _length == 0;
    }

    @Override
    public LanguageRun next() {
        _atEnd = true;
        return new LanguageRun(_length, _language);
    }

    @Override
    public boolean hasNext() {
        return !_atEnd;
    }
}