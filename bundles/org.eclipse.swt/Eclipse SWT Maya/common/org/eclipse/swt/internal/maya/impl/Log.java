// Changed for SWT (Apache-2.0 section 4b): methods SWT does not reach removed (skija_reach.py, SkijaPrune); package moved to org.eclipse.swt.internal.maya.
package org.eclipse.swt.internal.maya.impl;

import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.function.*;
import org.jetbrains.annotations.*;

public class Log {
    @ApiStatus.Internal
    public static final int _level;

    static { 
        String property = System.getProperty("maya.logLevel");
        if ("ALL".equals(property))
            _level = 0;
        else if("TRACE".equals(property))
            _level = 1;
        else if("DEBUG".equals(property))
            _level = 2;
        else if("INFO".equals(property))
            _level = 3;
        else if(null == property || "WARN".equals(property))
            _level = 4;
        else if("ERROR".equals(property))
            _level = 5;
        else if("NONE".equals(property))
            _level = 6;
        else
            throw new IllegalArgumentException("Unknown log level: " + property);
    }

    public static void debug(String s) {
        _log(2, "[DEBUG]", s);
    }

    public static void warn(String s) {
        _log(4, "[WARN ]", s);
    }

    public static void trace(Supplier<String> s) {
        _log(1, "[TRACE]", s);
    }

    public static String _time() {
        return ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
    }

    public static void _log(int level, String prefix, String s) {
        if (level >= _level)
            System.out.println(_time() + " " + prefix + " " + s);
    }

    public static void _log(int level, String prefix, Supplier<String> s) {
        if (level >= _level)
            System.out.println(_time() + " " + prefix + " " + s.get());
    }
}