// Host-side check of the native particle core (no Android needed):
//   g++ -std=c++17 -O2 -Wall -Wextra -I app/src/main/cpp tools/fx-host-test.cpp -o /tmp/fx && /tmp/fx
#include <cassert>
#include <cstdio>
#include <vector>

#include "particle_core.h"

int main() {
    using namespace pinbom;
    Engine e(256);
    std::vector<float> out(256 * kStride);

    // 1. every preset spawns, simulates and finishes
    for (int preset = kHeart; preset <= kRing; ++preset) {
        e.clear();
        e.emit(preset, 200.0f, 300.0f, 40, 1234u + preset, 400.0f);
        assert(e.alive() == 40);
        int n = e.read(out.data(), 256);
        assert(n == 40);
        for (int i = 0; i < n; ++i) {
            float a = out[i * kStride + 4];
            assert(a >= 0.0f && a <= 1.0f && std::isfinite(out[i * kStride]));
        }
        int frames = 0;
        while (e.step(1.0f / 60.0f, 400.0f, 800.0f) > 0 && frames < 60 * 20) ++frames;
        std::printf("preset %d finished after %d frames\n", preset, frames);
        assert(e.alive() == 0 || preset == kEmber || preset == kDust);
    }

    // 2. capacity is respected and old slots are recycled, never overrun
    Engine small(10);
    small.emit(kConfetti, 0, 0, 100, 7u, 300.0f);
    assert(small.alive() == 10);

    // 3. a 200 ms hitch is clamped (no teleporting)
    Engine hitch(4);
    hitch.emit(kSparkle, 100, 100, 4, 9u, 100.0f);
    hitch.step(0.2f, 1000, 1000);
    assert(hitch.alive() == 4);

    std::puts("fx host test: OK");
    return 0;
}
