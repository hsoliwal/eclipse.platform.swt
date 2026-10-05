package com.synexia.m3.viewport;

import java.util.List;
import java.util.Set;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Parser;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;

/** Sealed activation of the GTK3 logical SWT.VIRTUAL Tree model. */
public final class GtkVirtualTreeLogicalModelActivationRecipe extends Recipe {
    @Override public String getDisplayName() {
        return "Activate the GTK3 logical SWT virtual Tree model";
    }

    @Override public String getDescription() {
        return "Migrate virtual Tree presentation state to Java-owned item state and switch "
                + "GTK3 SWT.VIRTUAL Tree from GtkTreeStore prefix residency to SwtVirtualTreeModel.";
    }

    @Override public Set<String> getTags() {
        return Set.of("synexia", "m3", "swt", "gtk", "tree", "viewport", "jni", "recipe-first");
    }

    @Override public int maxCycles() {
        return 1;
    }

    @Override public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override public Tree visit(Tree tree, ExecutionContext context) {
                if (!(tree instanceof J.CompilationUnit source)) return tree;
                String candidate = GtkVirtualTreeLogicalModelActivationCandidate.propose(
                        source.getSourcePath(), source.printAll());
                if (candidate.equals(source.printAll())) return source;
                try (var parsed = JavaParser.fromJavaVersion().build().parseInputs(
                        List.of(Parser.Input.fromString(source.getSourcePath(), candidate)),
                        null, context)) {
                    List<SourceFile> files = parsed.toList();
                    if (files.size() != 1 || !(files.getFirst() instanceof J.CompilationUnit replacement)
                            || !candidate.equals(replacement.printAll())) {
                        throw new IllegalStateException(
                                "M3_GTK_TREE_ACTIVATION_JAVA_ROUNDTRIP:" + source.getSourcePath());
                    }
                    return replacement.withId(source.getId()).withMarkers(source.getMarkers())
                            .withSourcePath(source.getSourcePath());
                }
            }
        };
    }
}
