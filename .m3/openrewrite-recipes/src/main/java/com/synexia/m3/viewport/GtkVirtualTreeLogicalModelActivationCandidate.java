package com.synexia.m3.viewport;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Exact-preimage/full-postimage candidate for activating the GTK3 logical virtual Tree model. */
public final class GtkVirtualTreeLogicalModelActivationCandidate {
    static final String TREE_PATH =
            "bundles/org.eclipse.swt/Eclipse SWT/gtk/org/eclipse/swt/widgets/Tree.java";
    static final String ITEM_PATH =
            "bundles/org.eclipse.swt/Eclipse SWT/gtk/org/eclipse/swt/widgets/TreeItem.java";
    static final String TREE_PREIMAGE =
            "926b4e3bde0f1dea2e1032ca26baa81663cc49afbdadfbe58d85781f6b3d9b4d";
    static final String ITEM_PREIMAGE =
            "ca9a7277a560fdce1bca5a4bf97748f69847b884e6a17105ea57ccc54b6b5cc0";

    /* Bound before production materialization. */
    static final String TREE_POSTIMAGE = "__TREE_POSTIMAGE__";
    static final String ITEM_POSTIMAGE = "__ITEM_POSTIMAGE__";

    private static final String RESOURCE_ROOT =
            "/com/synexia/m3/viewport/gtk-tree-logical-model-activation/after/";

    private GtkVirtualTreeLogicalModelActivationCandidate() {
    }

    static String propose(Path path, String source) {
        String normalized = path.toString().replace('\\', '/');
        if (normalized.endsWith(TREE_PATH)) {
            return replace(source, TREE_PREIMAGE, TREE_POSTIMAGE, "Tree.java.txt", "TREE");
        }
        if (normalized.endsWith(ITEM_PATH)) {
            return replace(source, ITEM_PREIMAGE, ITEM_POSTIMAGE, "TreeItem.java.txt", "ITEM");
        }
        return source;
    }

    private static String replace(
            String source, String preimage, String postimage, String resource, String owner) {
        if (source.length() > 512 * 1024) {
            throw new IllegalStateException("M3_GTK_TREE_ACTIVATION_" + owner + "_BUDGET");
        }
        String hash = sha256(source);
        if (postimage.equals(hash)) return source;
        if (!preimage.equals(hash)) {
            throw new IllegalStateException("M3_GTK_TREE_ACTIVATION_" + owner + "_PREIMAGE_DRIFT");
        }
        String after = load(resource);
        if (!postimage.equals(sha256(after))) {
            throw new IllegalStateException("M3_GTK_TREE_ACTIVATION_" + owner + "_RESOURCE_DRIFT");
        }
        return after;
    }

    static String sha256(String source) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String load(String resource) {
        try (InputStream input = GtkVirtualTreeLogicalModelActivationCandidate.class
                .getResourceAsStream(RESOURCE_ROOT + resource)) {
            if (input == null) throw new IllegalStateException("M3_GTK_TREE_ACTIVATION_RESOURCE_MISSING:" + resource);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("M3_GTK_TREE_ACTIVATION_RESOURCE_IO:" + resource, exception);
        }
    }
}
