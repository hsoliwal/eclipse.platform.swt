// Changed for SWT (Apache-2.0 section 4b): Java 9 version (shared/java9/impl at tag 0.116.3); @SneakyThrows written out.
package io.github.humbleui.skija.impl;

import java.io.*;
import java.lang.module.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import org.jetbrains.annotations.*;

public final class ResourceFinder implements Closeable {

    /*
     * For multi-release jars, the APIs of the same class in different versions should be the same.
     */

    private final String _moduleName;
    private Optional<ModuleReader> _moduleReader;

    public ResourceFinder(String moduleName) {
        this._moduleName = moduleName;
    }

    public URL findResource(String path) throws IOException {
        URL url = ResourceFinder.class.getClassLoader().getResource(path);
        if (url != null) {
            return url;
        }

        if (_moduleReader != null) {
            return _moduleReader.map(reader -> _findResource(reader, path)).orElse(null);
        }

        Optional<ModuleReference> moduleReference = ModuleFinder.ofSystem().find(_moduleName);
        if (moduleReference.isPresent()) {
            _moduleReader = Optional.of(moduleReference.get().open());
            return _findResource(_moduleReader.get(), path);
        }

        String modulePath = System.getProperty("jdk.module.path");
        if (modulePath != null && !modulePath.isEmpty()) {
            Path[] paths = Arrays.stream(modulePath.split(File.pathSeparator))
                    .map(Paths::get)
                    .toArray(Path[]::new);

            moduleReference = ModuleFinder.of(paths).find(_moduleName);
            if (moduleReference.isPresent()) {
                _moduleReader = Optional.of(moduleReference.get().open());
                return _findResource(_moduleReader.get(), path);
            }
        }

        _moduleReader = Optional.empty();
        return null;
    }

    @Override
    public void close() throws IOException {
        if (_moduleReader != null && _moduleReader.isPresent()) {
            _moduleReader.get().close();
        }
    }

    private static URL _findResource(ModuleReader reader, String path) {
        try {
            URI uri = reader.find(path).orElse(null);
            return uri != null ? uri.toURL() : null;
        } catch (IOException $ex) {
            throw ResourceFinder.<RuntimeException>sneakyThrow($ex);
        }
    }

    /* rethrows a checked exception unchanged (what @SneakyThrows compiles to) */
    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException sneakyThrow(Throwable t) throws T {
        throw (T) t;
    }
}
