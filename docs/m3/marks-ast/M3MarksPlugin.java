/*
 * Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 */
package com.synexia.m3.marks;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.LineMap;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

/**
 * Pass seven on the compiler's own AST (brief rule 33: no second AST). A javac plugin that, after javac has
 * attributed a compilation unit, walks its trees once and writes one row per site that matches a compiled mark
 * pattern: method invocations and constructor calls whose declaring type (or a supertype that declares the
 * method) and name match METHOD_PATTERNS.tsv, and lambdas, anonymous classes and named classes whose type is a
 * subtype of a LISTENER_TYPES.tsv type. It runs inside {@code mvn compile} or the JDK21 gate compile:
 *
 * <pre>
 * javac -processorpath m3-eclipse-enhance-marks.jar -Xplugin:"M3Marks &lt;crate-dir&gt; &lt;out.tsv&gt;" ...
 * </pre>
 *
 * The crate dir holds METHOD_PATTERNS.tsv (template, target, pattern, role, evidence) and LISTENER_TYPES.tsv
 * (template, type). Output rows: template, target, kind, path, line, matched. No source is re-parsed and nothing
 * is kept beyond the pattern tables and the open output file.
 */
public final class M3MarksPlugin implements Plugin {

	@Override
	public String getName() {
		return "M3Marks";
	}

	@Override
	public void init(JavacTask task, String... args) {
		if (args.length < 2) {
			throw new IllegalArgumentException("M3Marks <crate-dir> <out.tsv>");
		}
		Tables tables;
		PrintWriter out;
		try {
			tables = Tables.load(args[0]);
			out = new PrintWriter(Files.newBufferedWriter(Paths.get(args[1]), StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		out.println("template\ttarget\tkind\tpath\tline\tmatched");
		Trees trees = Trees.instance(task);
		Elements elements = task.getElements();
		Types types = task.getTypes();
		task.addTaskListener(new TaskListener() {
			@Override
			public void finished(TaskEvent e) {
				if (e.getKind() == TaskEvent.Kind.ANALYZE && e.getCompilationUnit() != null) {
					new Scanner(trees, elements, types, tables, out).scan(e.getCompilationUnit(), null);
					out.flush();
				}
				if (e.getKind() == TaskEvent.Kind.GENERATE && e.getCompilationUnit() == null) {
					out.close();
				}
			}
		});
		Runtime.getRuntime().addShutdownHook(new Thread(out::close));
	}

	/** The compiled pattern tables: name to (declaring type to rows), and the listener types. */
	static final class Tables {
		final Map<String, Map<String, List<String[]>>> methods = new HashMap<>();
		final Map<String, List<String[]>> constructors = new HashMap<>();
		final Map<String, List<String[]>> listeners = new HashMap<>();

		static Tables load(String dir) throws IOException {
			Tables t = new Tables();
			for (String line : Files.readAllLines(Paths.get(dir, "METHOD_PATTERNS.tsv"), StandardCharsets.UTF_8)) {
				String[] c = line.split("\t");
				if (c.length < 3 || c[0].equals("template")) {
					continue;
				}
				String[] pattern = c[2].split(" ", 2);
				if (pattern.length != 2) {
					continue;
				}
				String type = pattern[0];
				String name = pattern[1].substring(0, pattern[1].indexOf('('));
				String[] row = { c[0], c[1], c[2] };
				if (name.equals("<constructor>")) {
					t.constructors.computeIfAbsent(type, k -> new ArrayList<>()).add(row);
				} else {
					t.methods.computeIfAbsent(name, k -> new HashMap<>()).computeIfAbsent(type, k -> new ArrayList<>()).add(row);
				}
			}
			for (String line : Files.readAllLines(Paths.get(dir, "LISTENER_TYPES.tsv"), StandardCharsets.UTF_8)) {
				String[] c = line.split("\t");
				if (c.length == 2 && !c[0].equals("template")) {
					t.listeners.computeIfAbsent(c[1], k -> new ArrayList<>()).add(new String[] { c[0], "", c[1] });
				}
			}
			return t;
		}
	}

	/** One walk per compilation unit over the attributed trees. */
	static final class Scanner extends TreePathScanner<Void, Void> {
		static final int METHOD_LIMIT = 15;
		static final List<String[]> METHOD_SPLIT = List.<String[]>of(new String[] { "ML", "METHOD_SPLIT", "body > 15 lines" });
		static final List<String[]> LOOP = List.<String[]>of(new String[] { "M32", "LOOP_TO_LANE", "loop statement" });
		static final List<String[]> CONCAT = List.<String[]>of(new String[] { "M33", "M3STRING_COMPOSITE", "String concatenation" });
		private final Trees trees;
		private final Elements elements;
		private final Types types;
		private final Tables tables;
		private final PrintWriter out;
		private CompilationUnitTree unit;
		private LineMap lines;
		private SourcePositions positions;

		Scanner(Trees trees, Elements elements, Types types, Tables tables, PrintWriter out) {
			this.trees = trees;
			this.elements = elements;
			this.types = types;
			this.tables = tables;
			this.out = out;
		}

		@Override
		public Void visitCompilationUnit(CompilationUnitTree node, Void p) {
			unit = node;
			lines = node.getLineMap();
			positions = trees.getSourcePositions();
			return super.visitCompilationUnit(node, p);
		}

		@Override
		public Void visitMethodInvocation(MethodInvocationTree node, Void p) {
			Element e = trees.getElement(new TreePath(getCurrentPath(), node.getMethodSelect()));
			if (e instanceof ExecutableElement method) {
				Map<String, List<String[]>> byType = tables.methods.get(method.getSimpleName().toString());
				if (byType != null) {
					matchDeclaring(method, byType, node, "CALL");
				}
			}
			return super.visitMethodInvocation(node, p);
		}

		@Override
		public Void visitNewClass(NewClassTree node, Void p) {
			Element e = trees.getElement(getCurrentPath());
			if (e instanceof ExecutableElement ctor && ctor.getEnclosingElement() instanceof TypeElement type) {
				List<String[]> rows = tables.constructors.get(type.getQualifiedName().toString());
				if (rows != null) {
					emit(rows, node, "NEW", type.getQualifiedName().toString());
				}
				if (node.getClassBody() != null) {
					matchListener(type.asType(), node, "ANONYMOUS");
				}
			}
			return super.visitNewClass(node, p);
		}

		@Override
		public Void visitLambdaExpression(LambdaExpressionTree node, Void p) {
			TypeMirror target = trees.getTypeMirror(getCurrentPath());
			if (target != null) {
				matchListener(target, node, "LAMBDA");
			}
			return super.visitLambdaExpression(node, p);
		}

		/** Hard limit MethodLength 15: a body longer than that is a METHOD_SPLIT site (MRL method splitting). */
		@Override
		public Void visitMethod(MethodTree node, Void p) {
			if (node.getBody() != null) {
				long start = positions.getStartPosition(unit, node.getBody());
				long end = positions.getEndPosition(unit, node.getBody());
				if (start >= 0 && end >= 0) {
					long body = lines.getLineNumber(end) - lines.getLineNumber(start) - 1;
					if (body > METHOD_LIMIT) {
						emit(METHOD_SPLIT, node, "METHOD", node.getName() + " " + body);
					}
				}
			}
			return super.visitMethod(node, p);
		}

		/** Hard limit: loops are banned (lanes, streams, fused scans). */
		@Override
		public Void visitForLoop(ForLoopTree node, Void p) {
			emit(LOOP, node, "LOOP", "for");
			return super.visitForLoop(node, p);
		}

		@Override
		public Void visitEnhancedForLoop(EnhancedForLoopTree node, Void p) {
			emit(LOOP, node, "LOOP", "for-each");
			return super.visitEnhancedForLoop(node, p);
		}

		@Override
		public Void visitWhileLoop(WhileLoopTree node, Void p) {
			emit(LOOP, node, "LOOP", "while");
			return super.visitWhileLoop(node, p);
		}

		@Override
		public Void visitDoWhileLoop(DoWhileLoopTree node, Void p) {
			emit(LOOP, node, "LOOP", "do-while");
			return super.visitDoWhileLoop(node, p);
		}

		/** String concatenation: a + whose attributed type is java.lang.String (M33, the StringBuilder culprit). */
		@Override
		public Void visitBinary(BinaryTree node, Void p) {
			if (node.getKind() == Tree.Kind.PLUS && !(getCurrentPath().getParentPath().getLeaf() instanceof BinaryTree)) {
				TypeMirror t = trees.getTypeMirror(getCurrentPath());
				if (t instanceof DeclaredType d && d.asElement() instanceof TypeElement te
						&& te.getQualifiedName().contentEquals("java.lang.String")) {
					emit(CONCAT, node, "CONCAT", "String +");
				}
			}
			return super.visitBinary(node, p);
		}

		@Override
		public Void visitClass(ClassTree node, Void p) {
			Element e = trees.getElement(getCurrentPath());
			if (e instanceof TypeElement type && e.getKind() != ElementKind.ANNOTATION_TYPE) {
				matchListener(type.asType(), node, "IMPLEMENTS");
			}
			return super.visitClass(node, p);
		}

		/** The invoked method's declaring type, or any supertype that declares a method of the same name. */
		private void matchDeclaring(ExecutableElement method, Map<String, List<String[]>> byType, Tree node, String kind) {
			Element enclosing = method.getEnclosingElement();
			if (!(enclosing instanceof TypeElement declaring)) {
				return;
			}
			List<TypeElement> chain = new ArrayList<>();
			collectSupertypes(declaring, chain);
			for (TypeElement t : chain) {
				List<String[]> rows = byType.get(t.getQualifiedName().toString());
				if (rows != null) {
					emit(rows, node, kind, t.getQualifiedName() + "#" + method.getSimpleName());
					return;
				}
			}
		}

		private void collectSupertypes(TypeElement type, List<TypeElement> into) {
			if (into.contains(type)) {
				return;
			}
			into.add(type);
			for (TypeMirror s : types.directSupertypes(type.asType())) {
				if (s instanceof DeclaredType d && d.asElement() instanceof TypeElement st) {
					collectSupertypes(st, into);
				}
			}
		}

		private void matchListener(TypeMirror type, Tree node, String kind) {
			if (!(type instanceof DeclaredType d) || !(d.asElement() instanceof TypeElement te)) {
				return;
			}
			List<TypeElement> chain = new ArrayList<>();
			collectSupertypes(te, chain);
			Set<String> seen = new java.util.HashSet<>();
			for (TypeElement t : chain) {
				String fq = t.getQualifiedName().toString();
				List<String[]> rows = tables.listeners.get(fq);
				if (rows != null && seen.add(fq)) {
					emit(rows, node, kind, fq);
				}
			}
		}

		private void emit(List<String[]> rows, Tree node, String kind, String matched) {
			long pos = positions.getStartPosition(unit, node);
			long line = pos < 0 ? 0 : lines.getLineNumber(pos);
			String path = unit.getSourceFile().toUri().getPath();
			for (String[] r : rows) {
				out.println(r[0] + "\t" + r[1] + "\t" + kind + "\t" + path + "\t" + line + "\t" + matched);
			}
		}
	}
}
