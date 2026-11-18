// SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
package org.eclipse.swt.internal.render;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Exact compiled Java admission oracle for native region prefixes. */
public final class NativeDirtyParity {
    private NativeDirtyParity() { }
    public static void main(String[] args) throws Exception {
        try (BufferedReader input = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = input.readLine()) != null) {
                String[] parts = line.split(" ");
                int width = Integer.parseInt(parts[0]);
                int height = Integer.parseInt(parts[1]);
                int count = Integer.parseInt(parts[2]);
                int arrayLength = Integer.parseInt(parts[3]);
                if (parts.length != arrayLength + 4) throw new AssertionError("malformed parity case");
                int[] regions = new int[arrayLength];
                for (int i = 0; i < arrayLength; i++) regions[i] = Integer.parseInt(parts[4 + i]);
                try {
                    NativeDirtyRegions.packedPixelCount(width, height, regions, count);
                    System.out.println('1');
                } catch (IllegalArgumentException expected) {
                    System.out.println('0');
                }
            }
        }
    }
}
