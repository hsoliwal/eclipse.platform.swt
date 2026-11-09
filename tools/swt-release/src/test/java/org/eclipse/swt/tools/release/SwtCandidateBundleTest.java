/*
 * Copyright (c) 2026 Synexia, Hitesh Soliwal and others.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.eclipse.swt.tools.release;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Packaging fixtures are deliberately not loadable SWT natives. */
class SwtCandidateBundleTest {

	private static final String SHA = "0123456789abcdef0123456789abcdef01234567";
	private static final String RUN = "https://github.com/hsoliwal/eclipse.platform.swt/actions/runs/12345";
	private static final String VERSION = "3.138.0.v20261107";
	private static final Map<String, String> PLATFORMS = Map.of(
			"swt-windows-x86_64", "org.eclipse.swt.win32.win32.x86_64",
			"swt-windows-aarch64", "org.eclipse.swt.win32.win32.aarch64",
			"swt-macos-x86_64", "org.eclipse.swt.cocoa.macosx.x86_64",
			"swt-macos-aarch64", "org.eclipse.swt.cocoa.macosx.aarch64",
			"swt-macos-universal", "org.eclipse.swt.cocoa.macosx.universal");

	@TempDir Path temporary;
	private Path artifacts;
	private Path output;
	private Path fragment;
	private String fragmentName;

	@BeforeEach
	void candidates() throws IOException {
		artifacts = temporary.resolve("artifacts");
		output = temporary.resolve("output");
		for (var platform : PLATFORMS.entrySet()) {
			Path directory = Files.createDirectories(artifacts.resolve(platform.getKey()));
			Files.writeString(directory.resolve("swt-source-sha.txt"), SHA + "\n");
			writeJar(directory.resolve("fragment.jar"), platform.getValue(), VERSION,
					Map.of(nativeName(platform.getValue()), new byte[] {1, 2, 3}));
			if (!platform.getKey().endsWith("universal")) {
				writeJar(directory.resolve("host.jar"), "org.eclipse.swt", VERSION, Map.of());
			}
		}
		fragment = artifacts.resolve("swt-windows-x86_64/fragment.jar");
		fragmentName = PLATFORMS.get("swt-windows-x86_64");
	}

	@Test
	void bundleIdentifiesIncludedPlatformsAndHashesEveryArchivedInput() throws Exception {
		Path nested = Files.createDirectories(artifacts.resolve("swt-windows-x86_64/bundles/target"));
		writeJar(nested.resolve("org.eclipse.swt-sources.jar"), "org.eclipse.swt.source", VERSION, Map.of());
		writeJar(nested.resolve("fragment-sources.jar"), fragmentName + ".source", VERSION, Map.of());
		Path archive = SwtCandidateBundle.create(artifacts, output, SHA, RUN);
		assertEquals("swt-windows-macos-candidate-0123456789ab.zip", archive.getFileName().toString());
		assertArrayEquals(Files.readAllBytes(archive), Files.readAllBytes(output.resolve("swt-all-platforms.zip")));
		assertEquals(hash(Files.readAllBytes(archive)) + "  " + archive.getFileName() + "\n",
				Files.readString(output.resolve(archive.getFileName() + ".sha256")));
		try (var zip = new ZipFile(archive.toFile())) {
			String manifest = text(zip, "candidate-manifest.json");
			assertTrue(manifest.contains("\"sourceSha\": \"" + SHA + "\""));
			assertTrue(manifest.contains("\"state\": \"candidate-unqualified\""));
			assertTrue(manifest.contains("\"linuxIncluded\": false"));
			assertTrue(manifest.contains("\"coverage\": \"not-attested\""));
			assertTrue(manifest.contains("\"nativeTests\": \"not-attested\""));
			assertTrue(text(zip, "CANDIDATE-STATUS.txt").contains("Linux artifacts are absent"));
			for (String line : text(zip, "SHA256SUMS").lines().toList()) {
				String[] columns = line.split("  ", 2);
				assertEquals(columns[0], hash(zip.getInputStream(zip.getEntry(columns[1])).readAllBytes()), columns[1]);
			}
		}
	}

	@Test
	void repeatBuildsAreByteIdenticalAndCliUsesTheSameChecks() throws Exception {
		Path first = SwtCandidateBundle.create(artifacts, output, SHA, RUN);
		Path secondOutput = temporary.resolve("second");
		SwtCandidateBundle.main(new String[] {artifacts.toString(), secondOutput.toString(), SHA, RUN});
		assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(secondOutput.resolve(first.getFileName())));
		assertThrows(IllegalArgumentException.class, () -> SwtCandidateBundle.main(new String[0]));
	}

	@ParameterizedTest
	@ValueSource(strings = {"1234567", "0123456789ABCDEF0123456789ABCDEF01234567", "../bad", ""})
	void rejectsUnpinnedSource(String source) {
		assertThrows(IllegalArgumentException.class, () -> SwtCandidateBundle.create(artifacts, output, source, RUN));
		assertFalse(Files.exists(output));
	}

	@Test
	void rejectsInvalidRunAndNestedOrOccupiedOutput() throws IOException {
		assertThrows(IllegalArgumentException.class, () -> SwtCandidateBundle.create(artifacts, output, SHA, "https://example.com"));
		assertThrows(IllegalArgumentException.class, () -> SwtCandidateBundle.create(artifacts, artifacts.resolve("output"), SHA, RUN));
		Files.createDirectory(output);
		Files.writeString(output.resolve("keep.txt"), "existing output");
		assertThrows(IOException.class, () -> SwtCandidateBundle.create(artifacts, output, SHA, RUN));
		assertEquals("existing output", Files.readString(output.resolve("keep.txt")));
	}

	@Test
	void acceptsAnEmptyOutputDirectory() throws IOException {
		Files.createDirectory(output);
		assertTrue(Files.exists(SwtCandidateBundle.create(artifacts, output, SHA, RUN)));
	}

	@Test
	void identifiesTheExactRunAttempt() throws IOException {
		Path archive = SwtCandidateBundle.create(artifacts, output, SHA, RUN + "/attempts/2");
		try (var zip = new ZipFile(archive.toFile())) {
			assertTrue(text(zip, "candidate-manifest.json").contains(RUN + "/attempts/2"));
		}
	}

	@Test
	void rejectsMissingInputOrPlatform() throws IOException {
		assertThrows(IOException.class, () -> SwtCandidateBundle.create(temporary.resolve("missing"), output, SHA, RUN));
		Files.move(artifacts.resolve("swt-macos-universal"), temporary.resolve("missing-platform"));
		assertThrows(IOException.class, () -> SwtCandidateBundle.create(artifacts, output, SHA, RUN));
		assertFalse(Files.exists(output));
	}

	@Test
	void refusesUnexpectedLinuxOrOtherArtifacts() throws IOException {
		Files.createDirectory(artifacts.resolve("swt-linux-x86_64"));
		assertThrows(IOException.class, () -> SwtCandidateBundle.create(artifacts, output, SHA, RUN));
	}

	@Test
	void refusesMissingOrMixedSourceMarkers() throws IOException {
		Path marker = artifacts.resolve("swt-windows-x86_64/swt-source-sha.txt");
		Files.writeString(marker, "f".repeat(40));
		assertThrows(IOException.class, () -> SwtCandidateBundle.create(artifacts, output, SHA, RUN));
		Files.delete(marker);
		assertThrows(IOException.class, () -> SwtCandidateBundle.create(artifacts, output, SHA, RUN));
	}

	@Test
	void refusesMissingOrDuplicateRuntimeFragment() throws IOException {
		Path duplicate = fragment.resolveSibling("duplicate.jar");
		Files.copy(fragment, duplicate);
		assertThrows(IOException.class, () -> SwtCandidateBundle.create(artifacts, output, SHA, RUN));
		Files.delete(duplicate);
		Files.delete(fragment);
		assertThrows(IOException.class, () -> SwtCandidateBundle.create(artifacts, output, SHA, RUN));
	}

	@Test
	void refusesMissingOrDuplicateHostBundle() throws IOException {
		Path host = fragment.resolveSibling("host.jar");
		Path duplicate = host.resolveSibling("duplicate-host.jar");
		Files.copy(host, duplicate);
		assertThrows(IOException.class, () -> SwtCandidateBundle.create(artifacts, output, SHA, RUN));
		Files.delete(duplicate);
		Files.delete(host);
		assertThrows(IOException.class, () -> SwtCandidateBundle.create(artifacts, output, SHA, RUN));
	}

	@Test
	void refusesUnrelatedBundleAndMixedVersions() throws IOException {
		writeJar(fragment, "org.unrelated.bundle", VERSION, Map.of());
		assertThrows(IOException.class, () -> SwtCandidateBundle.create(artifacts, output, SHA, RUN));
		writeJar(fragment, fragmentName, "3.139.0.v20261107", Map.of(nativeName(fragmentName), new byte[] {1}));
		assertThrows(IOException.class, () -> SwtCandidateBundle.create(artifacts, output, SHA, RUN));
	}

	@Test
	void acceptsDifferentBuildQualifiersForTheSameBundleVersion() throws IOException {
		writeJar(fragment, fragmentName, "3.138.0.v20261106", Map.of(nativeName(fragmentName), new byte[] {1}));
		assertTrue(Files.exists(SwtCandidateBundle.create(artifacts, output, SHA, RUN)));
	}

	@Test
	void refusesSourcesFromADifferentSwtVersion() throws IOException {
		Path sources = fragment.resolveSibling("sources.jar");
		writeJar(sources, "org.eclipse.swt.source", "9.999.0", Map.of());
		assertThrows(IOException.class, () -> SwtCandidateBundle.create(artifacts, output, SHA, RUN));
		assertFalse(Files.exists(output));
	}

	@ParameterizedTest
	@ValueSource(strings = {"WebView2Loader.dll", "support.jnilib", "support.dylib", "support.so"})
	void refusesLfsPointersAndEmptyFilesInEveryShippedNative(String support) throws IOException {
		for (byte[] bytes : List.of(new byte[0],
				"version https://git-lfs.github.com/spec/v1\noid sha256:abc\n".getBytes(StandardCharsets.UTF_8))) {
			writeJar(fragment, fragmentName, VERSION, Map.of(nativeName(fragmentName), new byte[] {1}, support, bytes));
			assertThrows(IOException.class, () -> SwtCandidateBundle.create(artifacts, output, SHA, RUN));
			assertFalse(Files.exists(output));
		}
	}

	@Test
	void rejectsMissingNativeEmptyNativeAndLfsPointer() throws IOException {
		for (Map<String, byte[]> entries : List.of(Map.<String, byte[]>of(),
				Map.of(nativeName(fragmentName), new byte[0]),
				Map.of(nativeName(fragmentName), "version https://git-lfs.github.com/spec/v1\noid sha256:abc\n".getBytes(StandardCharsets.UTF_8)))) {
			writeJar(fragment, fragmentName, VERSION, entries);
			assertThrows(IOException.class, () -> SwtCandidateBundle.create(artifacts, output, SHA, RUN));
		}
	}

	@Test
	void acceptsCocoaDylibAndUnfoldsBundleSymbolicName() throws IOException {
		String name = PLATFORMS.get("swt-macos-aarch64");
		Path jar = artifacts.resolve("swt-macos-aarch64/fragment.jar");
		String folded = name.substring(0, 20) + "\r\n " + name.substring(20) + "; singleton:=true";
		writeJar(jar, folded, VERSION, Map.of("libswt-cocoa.dylib", new byte[] {1}));
		assertTrue(Files.exists(SwtCandidateBundle.create(artifacts, output, SHA, RUN)));
	}

	@ParameterizedTest
	@ValueSource(strings = {"../outside", "/absolute", "C:/absolute", "bad\\name", "bad\nname", "bad\u007fname"})
	void rejectsUnsafeJarEntries(String name) throws IOException {
		writeJar(fragment, fragmentName, VERSION, Map.of(nativeName(fragmentName), new byte[] {1}, name, new byte[] {1}));
		assertThrows(IOException.class, () -> SwtCandidateBundle.create(artifacts, output, SHA, RUN));
		assertFalse(Files.exists(output));
	}

	@Test
	void refusesInvalidJarMissingManifestAndMissingBundleMetadata() throws IOException {
		Files.writeString(fragment, "not a ZIP file");
		assertThrows(IOException.class, () -> SwtCandidateBundle.create(artifacts, output, SHA, RUN));
		writeZip(fragment, Map.of("native.dll", new byte[] {1}));
		assertThrows(IOException.class, () -> SwtCandidateBundle.create(artifacts, output, SHA, RUN));
		writeJar(fragment, null, VERSION, Map.of());
		assertThrows(IOException.class, () -> SwtCandidateBundle.create(artifacts, output, SHA, RUN));
		writeJar(fragment, fragmentName, null, Map.of());
		assertThrows(IOException.class, () -> SwtCandidateBundle.create(artifacts, output, SHA, RUN));
		writeJar(fragment, fragmentName, "not-a-version", Map.of());
		assertThrows(IOException.class, () -> SwtCandidateBundle.create(artifacts, output, SHA, RUN));
	}

	@Test
	void refusesDuplicateJarEntries() throws IOException {
		writeJar(fragment, fragmentName, VERSION, Map.of(nativeName(fragmentName), new byte[] {1},
				"uniqueA.bin", new byte[] {2}, "uniqueB.bin", new byte[] {3}));
		// ZIP writers reject duplicates, so change equal-length local/central names.
		byte[] duplicate = new String(Files.readAllBytes(fragment), StandardCharsets.ISO_8859_1)
				.replace("uniqueB.bin", "uniqueA.bin").getBytes(StandardCharsets.ISO_8859_1);
		Files.write(fragment, duplicate);
		assertThrows(IOException.class, () -> SwtCandidateBundle.create(artifacts, output, SHA, RUN));
		assertFalse(Files.exists(output));
	}

	@Test
	void refusesCorruptedClassPayloadEvenWhenTheManifestAndNativesAreReadable() throws IOException {
		byte[] payload = "unique-class-payload-for-crc-check".getBytes(StandardCharsets.UTF_8);
		var entries = Map.of("META-INF/MANIFEST.MF", ("Manifest-Version: 1.0\r\nBundle-SymbolicName: "
				+ fragmentName + "\r\nBundle-Version: " + VERSION + "\r\n\r\n").getBytes(StandardCharsets.UTF_8),
				nativeName(fragmentName), new byte[] {1}, "org/eclipse/swt/Corrupted.class", payload);
		try (var zip = new ZipOutputStream(Files.newOutputStream(fragment))) {
			for (var entry : entries.entrySet()) {
				CRC32 checksum = new CRC32();
				checksum.update(entry.getValue());
				ZipEntry item = new ZipEntry(entry.getKey());
				item.setMethod(ZipEntry.STORED);
				item.setSize(entry.getValue().length);
				item.setCrc(checksum.getValue());
				zip.putNextEntry(item);
				zip.write(entry.getValue());
				zip.closeEntry();
			}
		}
		byte[] bytes = Files.readAllBytes(fragment);
		int offset = new String(bytes, StandardCharsets.ISO_8859_1).indexOf(new String(payload, StandardCharsets.ISO_8859_1));
		assertTrue(offset >= 0);
		bytes[offset] ^= 1; // Retain directory/local CRC fields, corrupt only the payload.
		Files.write(fragment, bytes);
		assertThrows(IOException.class, () -> SwtCandidateBundle.create(artifacts, output, SHA, RUN));
		assertFalse(Files.exists(output));
	}

	@Test
	void refusesUnexpectedFilesAndSymbolicLinks() throws IOException {
		Path unrelated = fragment.resolveSibling("notes.txt");
		Files.writeString(unrelated, "not an artifact");
		assertThrows(IOException.class, () -> SwtCandidateBundle.create(artifacts, output, SHA, RUN));
		Files.delete(unrelated);
		Path link = fragment.resolveSibling("linked.jar");
		Files.createSymbolicLink(link, fragment);
		assertThrows(IOException.class, () -> SwtCandidateBundle.create(artifacts, output, SHA, RUN));
	}

	private static String nativeName(String bundle) {
		return bundle.contains("win32") ? "swt-win32.dll" : "libswt-cocoa.jnilib";
	}

	private static void writeJar(Path file, String name, String version, Map<String, byte[]> extra) throws IOException {
		String manifest = "Manifest-Version: 1.0\r\n";
		if (name != null) manifest += "Bundle-SymbolicName: " + name + "\r\n";
		if (version != null) manifest += "Bundle-Version: " + version + "\r\n";
		var entries = new TreeMap<>(extra);
		entries.put("META-INF/MANIFEST.MF", (manifest + "\r\n").getBytes(StandardCharsets.UTF_8));
		writeZip(file, entries);
	}

	private static void writeZip(Path path, Map<String, byte[]> entries) throws IOException {
		try (var zip = new ZipOutputStream(Files.newOutputStream(path))) {
			for (var entry : entries.entrySet()) {
				ZipEntry item = new ZipEntry(entry.getKey());
				item.setTime(0);
				zip.putNextEntry(item);
				zip.write(entry.getValue());
				zip.closeEntry();
			}
		}
	}

	private static String text(ZipFile zip, String name) throws IOException {
		try (var in = zip.getInputStream(zip.getEntry(name))) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static String hash(byte[] bytes) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
	}
}
