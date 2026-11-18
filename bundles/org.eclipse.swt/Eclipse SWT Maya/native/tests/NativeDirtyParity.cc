// SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
#include "MayaCompositionDirty.h"
#include <climits>
#include <iostream>
#include <sstream>
#include <string>
#include <vector>

int main() {
    std::string line;
    while (std::getline(std::cin, line)) {
        std::istringstream in(line);
        int width = 0, height = 0, count = 0, arrayLength = 0;
        if (!(in >> width >> height >> count >> arrayLength) || arrayLength < 0) return 2;
        std::vector<int> regions(static_cast<std::size_t>(arrayLength));
        for (int &r : regions) if (!(in >> r)) return 3;
        int extra;
        if (in >> extra) return 4;
        const bool ok = swt_maya::validPackedDirtyFrame(width, height, INT_MAX,
                regions.data(), count, arrayLength);
        std::cout << (ok ? '1' : '0') << '\n';
    }
}
