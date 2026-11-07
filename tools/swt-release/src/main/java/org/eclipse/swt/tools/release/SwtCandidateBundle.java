/*
 * Copyright (c) 2026 Synexia, Hitesh Soliwal and others.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.eclipse.swt.tools.release;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Packages source-identified Windows/macOS build candidates. This tool verifies
 * packaging integrity only: native execution, compatibility and coverage remain
 * separate release gates. Run with Java 21's source-file launcher or Maven.
 */
public final class SwtCandidateBundle {

	private static final Map<String, String> PLATFORMS = Map.of(
			"swt-windows-x86_64", "org.eclipse.swt.win32.win32.x86_64",
			"swt-windows-aarch64", "org.eclipse.swt.win32.win32.aarch64",
			"swt-macos-x86_64", "org.eclipse.swt.cocoa.macosx.x86_64",
			"swt-macos-aarch64", "org.eclipse.swt.cocoa.macosx.aarch64",
			"swt-macos-universal", "org.eclipse.swt.cocoa.macosx.universal");
	private static final Pattern SHA = Pattern.compile("[0-9a-f]{40}");
	private static final Pattern VERSION = Pattern.compile("([0-9]+\\.[0-9]+\\.[0-9]+)(?:\\.[A-Za-z0-9_-]+)?");
	private static final String SOURCE_MARKER = "swt-source-sha.txt";
	private static final byte[] LFS_HEADER = "version https://git-lfs.github.com/spec/v1".getBytes(StandardCharsets.US_ASCII);
	private static final LocalDateTime ZIP_TIME = LocalDateTime.of(1980, 1, 1, 0, 0);

	private SwtCandidateBundle() {
	}

	/** CLI: {@code <artifact-directory> <output-directory> <source-sha> <run-url>}. */
	public static void main(String[] args) throws IOException {
		if (args.length != 4) {
			throw new IllegalArgumentException("Usage: SwtCandidateBundle <artifacts> <output> <source-sha> <run-url>");
		}
		System.out.println(create(Path.of(args[0]), Path.of(args[1]), args[2], args[3]));
	}

	static Path create(Path input, Path output, String sourceSha, String runUrl) throws IOException {
		if (!SHA.matcher(sourceSha).matches()) {
			throw new IllegalArgumentException("Expected a full lowercase source commit SHA");
		}
		if (!runUrl.matches("https://github\\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/actions/runs/[0-9]+(?:/attempts/[1-9][0-9]*)?")) {
			throw new IllegalArgumentException("Expected the producing GitHub Actions run URL");
		}
		Path root = input.toAbsolutePath().normalize();
		Path destination = output.toAbsolutePath().normalize();
		if (destination.startsWith(root)) {
			throw new IllegalArgumentException("Output must be outside the input artifact directory");
		}
		requireDirectory(root);
		if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
			requireDirectory(destination);
			try (var files = Files.list(destination)) {
				if (files.findAny().isPresent()) {
					throw new IOException("Output directory is not empty: " + destination);
				}
			}
		}
		List<ArtifactFile> files = new ArrayList<>();
		String baseVersion = null;
		try (var entries = Files.list(root)) {
			for (Path entry : entries.sorted().toList()) {
				if (!PLATFORMS.containsKey(entry.getFileName().toString())) {
					throw new IOException("Unexpected platform artifact: " + entry.getFileName());
				}
			}
		}
		for (var platform : new TreeMap<>(PLATFORMS).entrySet()) {
			Path directory = root.resolve(platform.getKey());
			requireDirectory(directory);
			Path marker = directory.resolve(SOURCE_MARKER);
			requireFile(marker);
			if (!Files.readString(marker).strip().equals(sourceSha)) {
				throw new IOException("Source commit differs in " + platform.getKey());
			}
			int fragmentCount = 0;
			int hostCount = 0;
			try (var tree = Files.walk(directory)) {
				for (Path path : tree.sorted().toList()) {
					if (Files.isSymbolicLink(path)) {
						throw new IOException("Symbolic links are not candidate inputs: " + path);
					}
					if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) continue;
					requireFile(path);
					String name = root.relativize(path).toString().replace('\\', '/');
					validateName(name);
					if (path.equals(marker)) {
						files.add(new ArtifactFile(path, name, hash(path), Files.size(path), null, null));
						continue;
					}
					if (!name.endsWith(".jar")) throw new IOException("Unexpected non-JAR input: " + name);
					Bundle bundle = inspectJar(path, platform.getValue());
					if (bundle.symbolicName().equals(platform.getValue())) {
						fragmentCount++;
					}
					if (bundle.symbolicName().equals("org.eclipse.swt")) hostCount++;
					var version = VERSION.matcher(bundle.version());
					version.matches(); // inspectJar already validated the version.
					String current = version.group(1);
					if (baseVersion != null && !baseVersion.equals(current)) {
						throw new IOException("Platform/source bundle versions differ: " + baseVersion + " and " + current);
					}
					baseVersion = current;
					files.add(new ArtifactFile(path, name, hash(path), Files.size(path), bundle.symbolicName(), bundle.version()));
				}
			}
			if (fragmentCount != 1) {
				throw new IOException("Expected one runtime fragment in " + platform.getKey() + ", found " + fragmentCount);
			}
			if (!platform.getKey().equals("swt-macos-universal") && hostCount != 1) {
				throw new IOException("Expected one SWT host bundle in " + platform.getKey() + ", found " + hostCount);
			}
		}
		files.sort(Comparator.comparing(ArtifactFile::name));
		Map<String, byte[]> metadata = new TreeMap<>();
		metadata.put("candidate-manifest.json", manifest(sourceSha, runUrl, files).getBytes(StandardCharsets.UTF_8));
		metadata.put("CANDIDATE-STATUS.txt", ("SWT (M3 fork) build candidate; release qualification is pending.\n"
				+ "Included: Windows x86_64/aarch64 and macOS x86_64/aarch64/universal.\n"
				+ "Linux artifacts are absent. Native tests, coverage, API and JNI qualification are not attested here.\n"
				+ "The swt-all-platforms.zip name is a deprecated compatibility alias, not a support claim.\n")
				.getBytes(StandardCharsets.UTF_8));
		StringBuilder sums = new StringBuilder();
		for (ArtifactFile file : files) sums.append(file.sha256()).append("  ").append(file.name()).append('\n');
		for (var entry : metadata.entrySet()) {
			sums.append(hash(entry.getValue())).append("  ").append(entry.getKey()).append('\n');
		}
		metadata.put("SHA256SUMS", sums.toString().getBytes(StandardCharsets.UTF_8));
		Files.createDirectories(destination);
		Path temporary = Files.createTempFile(destination, ".swt-candidate-", ".tmp");
		String fileName = "swt-windows-macos-candidate-" + sourceSha.substring(0, 12) + ".zip";
		Path archive = destination.resolve(fileName);
		try {
			try (var zip = new ZipOutputStream(Files.newOutputStream(temporary))) {
				for (var entry : metadata.entrySet()) {
					zip.putNextEntry(zipEntry(entry.getKey()));
					zip.write(entry.getValue());
					zip.closeEntry();
				}
				for (ArtifactFile file : files) {
					zip.putNextEntry(zipEntry(file.name()));
					MessageDigest digest = digest();
					try (var stream = new DigestInputStream(Files.newInputStream(file.path()), digest)) {
						long size = stream.transferTo(zip);
						if (size != file.size() || !HexFormat.of().formatHex(digest.digest()).equals(file.sha256())) {
							throw new IOException("Input changed during packaging: " + file.name());
						}
					}
					zip.closeEntry();
				}
			}
			Files.move(temporary, archive, StandardCopyOption.ATOMIC_MOVE);
			Files.writeString(destination.resolve(fileName + ".sha256"), hash(archive) + "  " + fileName + "\n");
			Files.copy(archive, destination.resolve("swt-all-platforms.zip"));
			return archive;
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	private static Bundle inspectJar(Path path, String expectedFragment) throws IOException {
		try (var zip = new ZipFile(path.toFile())) {
			var names = new HashSet<String>();
			for (ZipEntry entry : zip.stream().toList()) {
				validateName(entry.getName());
				if (!names.add(entry.getName())) throw new IOException("Duplicate JAR entry: " + entry.getName());
				CRC32 checksum = new CRC32();
				try (var in = new CheckedInputStream(zip.getInputStream(entry), checksum)) {
					long size = in.transferTo(java.io.OutputStream.nullOutputStream());
					if (size != entry.getSize() || checksum.getValue() != entry.getCrc()) {
						throw new IOException("Corrupt JAR entry size or CRC: " + path + "!" + entry.getName());
					}
				}
			}
			ZipEntry manifestEntry = zip.getEntry("META-INF/MANIFEST.MF");
			if (manifestEntry == null) throw new IOException("Missing JAR manifest: " + path);
			Manifest manifest;
			try (InputStream in = zip.getInputStream(manifestEntry)) {
				manifest = new Manifest(in);
			}
			String symbolicName = manifest.getMainAttributes().getValue("Bundle-SymbolicName");
			String version = manifest.getMainAttributes().getValue("Bundle-Version");
			if (symbolicName == null || version == null || !VERSION.matcher(version).matches()) {
				throw new IOException("Missing or invalid bundle identity: " + path);
			}
			symbolicName = symbolicName.split(";", 2)[0].strip();
			if (!List.of(expectedFragment, expectedFragment + ".source", "org.eclipse.swt", "org.eclipse.swt.source").contains(symbolicName)) {
				throw new IOException("Unexpected bundle " + symbolicName + " in " + path);
			}
			List<? extends ZipEntry> natives = zip.stream().filter(entry -> !entry.isDirectory())
					.filter(entry -> entry.getName().endsWith(".dll") || entry.getName().endsWith(".jnilib")
							|| entry.getName().endsWith(".dylib") || entry.getName().endsWith(".so"))
					.toList();
			if (symbolicName.equals(expectedFragment) && natives.stream().noneMatch(entry -> expectedFragment.contains(".win32.")
					? entry.getName().startsWith("swt-") && entry.getName().endsWith(".dll")
					: entry.getName().startsWith("libswt-") && (entry.getName().endsWith(".jnilib") || entry.getName().endsWith(".dylib")))) {
				throw new IOException("No SWT native library in " + path);
			}
			for (ZipEntry entry : natives) {
				try (InputStream in = zip.getInputStream(entry)) {
					byte[] prefix = in.readNBytes(LFS_HEADER.length);
					if (prefix.length == 0 || java.util.Arrays.equals(prefix, LFS_HEADER)) {
						throw new IOException("Empty native library or unresolved LFS pointer: " + path + "!" + entry.getName());
					}
				}
			}
			return new Bundle(symbolicName, version);
		}
	}

	private static void validateName(String name) throws IOException {
		if (name.isEmpty() || name.startsWith("/") || name.contains("\\") || name.contains(":")
				|| name.chars().anyMatch(c -> c < 32 || c == 127)
				|| List.of(name.split("/", -1)).contains("..")) {
			throw new IOException("Unsafe archive path: " + name);
		}
	}

	private static void requireDirectory(Path path) throws IOException {
		if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Missing or unsafe directory: " + path);
	}

	private static void requireFile(Path path) throws IOException {
		if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Missing or unsafe file: " + path);
	}

	private static ZipEntry zipEntry(String name) {
		ZipEntry entry = new ZipEntry(name);
		entry.setTimeLocal(ZIP_TIME);
		return entry;
	}

	private static MessageDigest digest() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("Required SHA-256 implementation is unavailable", impossible);
		}
	}

	private static String hash(Path path) throws IOException {
		MessageDigest digest = digest();
		try (var in = new DigestInputStream(Files.newInputStream(path), digest)) {
			in.transferTo(java.io.OutputStream.nullOutputStream());
		}
		return HexFormat.of().formatHex(digest.digest());
	}

	private static String hash(byte[] bytes) {
		return HexFormat.of().formatHex(digest().digest(bytes));
	}

	private static String manifest(String sha, String runUrl, List<ArtifactFile> files) {
		StringBuilder json = new StringBuilder("{\n  \"schemaVersion\": 1,\n  \"product\": \"SWT (M3 fork)\",\n");
		json.append("  \"sourceSha\": ").append(quote(sha)).append(",\n  \"runUrl\": ").append(quote(runUrl));
		json.append(",\n  \"state\": \"candidate-unqualified\",\n  \"linuxIncluded\": false,\n");
		json.append("  \"nativeTests\": \"not-attested\",\n  \"coverage\": \"not-attested\",\n  \"files\": [\n");
		for (int i = 0; i < files.size(); i++) {
			ArtifactFile file = files.get(i);
			json.append("    {\"path\": ").append(quote(file.name())).append(", \"sha256\": ").append(quote(file.sha256()));
			json.append(", \"size\": ").append(file.size());
			if (file.symbolicName() != null) {
				json.append(", \"bundleSymbolicName\": ").append(quote(file.symbolicName()));
				json.append(", \"bundleVersion\": ").append(quote(file.version()));
			}
			json.append('}').append(i + 1 == files.size() ? "\n" : ",\n");
		}
		return json.append("  ]\n}\n").toString();
	}

	private static String quote(String text) {
		return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	private record Bundle(String symbolicName, String version) {
	}

	private record ArtifactFile(Path path, String name, String sha256, long size, String symbolicName, String version) {
	}
}
