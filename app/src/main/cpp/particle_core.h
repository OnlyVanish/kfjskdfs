// PinBom · native particle core.
//
// Structure-of-arrays simulation for every decorative burst in the app. It has
// no JNI and no Android dependency on purpose: the same header is compiled by
// the host test (see tools/fx-host-test.cpp) and by the JNI shim.
//
// One call to step() advances every live particle; one call to read() packs the
// survivors into a flat float buffer that Compose draws without allocating.
#pragma once

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <vector>

namespace pinbom {

enum Preset : int {
    kHeart = 0,     // upward fan of small hearts (like)
    kSparkle = 1,   // omni-directional glints (save / follow)
    kConfetti = 2,  // two cannons, tumbling strips (publish / milestones)
    kEmber = 3,     // slow rising glow (ambient, auth screen)
    kDust = 4,      // drifting motes (ambient, headers)
    kRing = 5,      // expanding ring of dots (tap ripple)
};

enum Shape : int { kCircle = 0, kHeartShape = 1, kSpark = 2, kStrip = 3 };

// Floats written per particle by read(): x, y, size, rotation(deg), alpha,
// preset*8+colorIndex, shape, scaleX (the strip flip that fakes 3-D tumbling).
constexpr int kStride = 8;

class Engine {
public:
    explicit Engine(int capacity)
        : cap_(std::max(1, capacity)),
          x_(cap_), y_(cap_), vx_(cap_), vy_(cap_), age_(cap_), life_(cap_),
          size_(cap_), rot_(cap_), spin_(cap_), phase_(cap_), grav_(cap_),
          drag_(cap_), color_(cap_), shape_(cap_), kind_(cap_) {}

    int capacity() const { return cap_; }
    int alive() const { return alive_; }

    // `unit` is the pixel scale of the effect (usually min(width, height)).
    void emit(int preset, float x, float y, int count, uint32_t seed, float unit) {
        rng_ = seed ? seed : 0x9E3779B9u;
        count = std::max(0, count);
        for (int i = 0; i < count; ++i) {
            // When full, recycle the oldest slot instead of dropping the burst.
            int slot = alive_ < cap_ ? alive_++ : recycle_++ % cap_;
            spawn(slot, preset, x, y, i, count, unit);
        }
    }

    // Advances the simulation. Returns the number of live particles.
    int step(float dt, float width, float height) {
        dt = std::min(std::max(dt, 0.0f), 0.05f);  // a hitch must not teleport particles
        time_ += dt;
        int i = 0;
        while (i < alive_) {
            age_[i] += dt;
            bool dead = age_[i] >= life_[i];
            if (!dead) {
                // Cheap divergence-free-ish turbulence: two phase-shifted sines.
                float t = time_ * 1.7f + phase_[i];
                float tx = std::sin(t + y_[i] * 0.011f) * 0.6f;
                float ty = std::cos(t * 0.8f + x_[i] * 0.009f) * 0.35f;
                float k = std::exp(-drag_[i] * dt);
                vx_[i] = (vx_[i] + tx * turbulence(kind_[i]) * dt * 60.0f) * k;
                vy_[i] = (vy_[i] + (grav_[i] + ty * turbulence(kind_[i])) * dt) * k;
                x_[i] += vx_[i] * dt;
                y_[i] += vy_[i] * dt;
                rot_[i] += spin_[i] * dt;
                // Off-screen with room to spare: nothing will ever bring it back.
                if (y_[i] > height + 80.0f || x_[i] < -120.0f || x_[i] > width + 120.0f ||
                    y_[i] < -240.0f) {
                    dead = true;
                }
            }
            if (dead) {
                swapRemove(i);  // do not advance i: a new particle took this slot
            } else {
                ++i;
            }
        }
        return alive_;
    }

    // Packs live particles into `out` (room for outCapacity particles).
    int read(float* out, int outCapacity) const {
        int n = std::min(alive_, outCapacity);
        for (int i = 0; i < n; ++i) {
            float p = age_[i] / life_[i];
            float alpha = alphaCurve(kind_[i], p);
            float sz = size_[i] * sizeCurve(kind_[i], p);
            float scaleX = 1.0f;
            if (shape_[i] == kStrip) {
                float c = std::cos(rot_[i] * 0.0174533f * 1.6f + phase_[i]);
                scaleX = std::fabs(c) < 0.08f ? 0.08f : c;
            }
            float* o = out + i * kStride;
            o[0] = x_[i]; o[1] = y_[i]; o[2] = sz; o[3] = rot_[i];
            o[4] = alpha; o[5] = static_cast<float>(kind_[i] * 8 + color_[i]);
            o[6] = static_cast<float>(shape_[i]); o[7] = scaleX;
        }
        return n;
    }

    void clear() { alive_ = 0; }

private:
    float rnd() {  // xorshift32 -> [0,1)
        rng_ ^= rng_ << 13; rng_ ^= rng_ >> 17; rng_ ^= rng_ << 5;
        return static_cast<float>(rng_ & 0xFFFFFF) / 16777216.0f;
    }
    float rnd(float lo, float hi) { return lo + (hi - lo) * rnd(); }

    static float turbulence(int kind) {
        switch (kind) {
            case kConfetti: return 1.0f;
            case kEmber: return 0.9f;
            case kDust: return 0.5f;
            default: return 0.18f;
        }
    }
    static float alphaCurve(int kind, float p) {
        p = std::min(std::max(p, 0.0f), 1.0f);
        if (kind == kEmber || kind == kDust) {
            // fade in, hold, fade out: ambient particles must never pop
            float in = std::min(p / 0.2f, 1.0f), out = std::min((1.0f - p) / 0.3f, 1.0f);
            return std::min(in, out) * (kind == kDust ? 0.55f : 0.8f);
        }
        float fade = 1.0f - p;
        return std::min(1.0f, fade * 1.6f);  // opaque for the first ~40%, then fades
    }
    static float sizeCurve(int kind, float p) {
        if (kind == kSparkle) return 0.4f + 0.9f * std::sin(std::min(p, 1.0f) * 3.14159f);
        if (kind == kHeart) return 0.6f + 0.5f * std::sin(std::min(p * 2.0f, 1.0f) * 1.5708f);
        if (kind == kRing) return 1.0f - 0.5f * p;
        return 1.0f;
    }

    void spawn(int s, int preset, float x, float y, int i, int n, float unit) {
        const float u = std::max(unit, 1.0f);
        kind_[s] = preset; age_[s] = 0.0f; phase_[s] = rnd(0.0f, 6.2831853f);
        x_[s] = x; y_[s] = y; rot_[s] = rnd(0.0f, 360.0f); spin_[s] = 0.0f;
        color_[s] = static_cast<int>(rnd() * 5.0f) % 5;
        shape_[s] = kCircle; grav_[s] = 0.0f; drag_[s] = 1.5f;
        float angle, speed;
        switch (preset) {
            case kHeart:
                angle = -1.5708f + rnd(-0.9f, 0.9f); speed = u * rnd(0.55f, 1.35f);
                vx_[s] = std::cos(angle) * speed; vy_[s] = std::sin(angle) * speed;
                life_[s] = rnd(0.7f, 1.15f); size_[s] = u * rnd(0.035f, 0.06f);
                spin_[s] = rnd(-90.0f, 90.0f); shape_[s] = kHeartShape;
                grav_[s] = u * 0.9f; drag_[s] = 2.2f; rot_[s] = rnd(-25.0f, 25.0f);
                break;
            case kSparkle:
                angle = rnd(0.0f, 6.2831853f); speed = u * rnd(0.4f, 1.2f);
                vx_[s] = std::cos(angle) * speed; vy_[s] = std::sin(angle) * speed;
                life_[s] = rnd(0.5f, 0.9f); size_[s] = u * rnd(0.025f, 0.055f);
                shape_[s] = (rnd() < 0.55f) ? kSpark : kCircle; drag_[s] = 3.0f;
                spin_[s] = rnd(-120.0f, 120.0f);
                break;
            case kConfetti: {
                bool left = (i % 2) == 0;  // alternate cannons so it feels like a party
                angle = (left ? -1.05f : -2.09f) + rnd(-0.45f, 0.45f);
                speed = u * rnd(0.9f, 2.2f);
                vx_[s] = std::cos(angle) * speed; vy_[s] = std::sin(angle) * speed;
                life_[s] = rnd(1.6f, 2.8f); size_[s] = u * rnd(0.022f, 0.04f);
                spin_[s] = rnd(-420.0f, 420.0f); shape_[s] = kStrip;
                grav_[s] = u * 1.7f; drag_[s] = 1.1f;
                if (n > 1) x_[s] = x + (left ? -1.0f : 1.0f) * u * 0.02f;
                break;
            }
            case kEmber:
                vx_[s] = rnd(-0.03f, 0.03f) * u; vy_[s] = -u * rnd(0.05f, 0.16f);
                life_[s] = rnd(3.2f, 6.0f); size_[s] = u * rnd(0.006f, 0.017f);
                grav_[s] = -u * 0.02f; drag_[s] = 0.35f; shape_[s] = kCircle;
                break;
            case kDust:
                vx_[s] = rnd(-0.04f, 0.04f) * u; vy_[s] = rnd(-0.03f, 0.03f) * u;
                life_[s] = rnd(4.0f, 8.0f); size_[s] = u * rnd(0.004f, 0.011f);
                drag_[s] = 0.2f;
                break;
            case kRing:
            default:
                angle = 6.2831853f * (static_cast<float>(i) / std::max(n, 1)) + rnd(-0.05f, 0.05f);
                speed = u * rnd(0.9f, 1.1f);
                vx_[s] = std::cos(angle) * speed; vy_[s] = std::sin(angle) * speed;
                life_[s] = rnd(0.45f, 0.6f); size_[s] = u * 0.02f; drag_[s] = 4.5f;
                break;
        }
    }

    void swapRemove(int i) {
        int last = --alive_;
        if (i == last) return;
        x_[i] = x_[last]; y_[i] = y_[last]; vx_[i] = vx_[last]; vy_[i] = vy_[last];
        age_[i] = age_[last]; life_[i] = life_[last]; size_[i] = size_[last];
        rot_[i] = rot_[last]; spin_[i] = spin_[last]; phase_[i] = phase_[last];
        grav_[i] = grav_[last]; drag_[i] = drag_[last]; color_[i] = color_[last];
        shape_[i] = shape_[last]; kind_[i] = kind_[last];
    }

    int cap_;
    int alive_ = 0;
    int recycle_ = 0;
    float time_ = 0.0f;
    uint32_t rng_ = 0x9E3779B9u;
    std::vector<float> x_, y_, vx_, vy_, age_, life_, size_, rot_, spin_, phase_, grav_, drag_;
    std::vector<int> color_, shape_, kind_;
};

}  // namespace pinbom
