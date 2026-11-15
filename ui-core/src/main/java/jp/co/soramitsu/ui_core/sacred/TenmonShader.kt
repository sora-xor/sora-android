package jp.co.soramitsu.ui_core.sacred

/**
 * The launch scene, traced per pixel: a corridor of vermilion torii over a floor of black lacquer still as water,
 * the seals of Onmyōdō hanging lit in the gates, and at the end the red planet with 天 in a golden armillary.
 * Generated from the iOS app's Metal source (SacredTenmonShader.swift), so the two apps draw the same scene.
 * Do not edit by hand.
 */
internal const val TENMON_SHADER = """
uniform float4 view;
uniform float4 flight;
uniform float4 lens;
uniform float4 state;
uniform float4 page;

const float PI = 3.14159265;
const float TAU = 6.2831853;

// The corridor. Metres; y is up, the camera flies towards +z.
const float GATE_FIRST = 5.0;
const float GATE_STEP = 4.0;
const float GATE_LAST = 37.0;
const float POST_X = 1.9;
const float PLANET_Z = 58.0;
const float PLANET_Y = 2.3;
const float PLANET_R = 1.25;
const float RING_R = 2.58;

struct Scene {
    float time;
    float lock;      // 0: the armillary tumbles; 1: its rings lie in one plane
    float turn;      // the last seal's turn
    float3 moon;     // towards the moon
    float3 planet;   // the planet's centre
    float3 ax1;      // the armillary rings' axes
    float3 ax2;
    float3 ax3;
};

struct Hit {
    float t;
    float m;         // 0 none, 1 lacquer, 2 black, 3 gold, 4 planet, 5 floor
};

float sat(float x) { return clamp(x, 0.0, 1.0); }

float ss(float a, float b, float x) {
    float t = clamp((x - a) / (b - a), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

float hash21(float2 p) {
    float3 q = fract(float3(p.xyx) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

float noise2(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 w = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + float2(1.0, 0.0));
    float c = hash21(i + float2(0.0, 1.0));
    float d = hash21(i + float2(1.0, 1.0));
    return mix(mix(a, b, w.x), mix(c, d, w.x), w.y);
}

float fbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * noise2(p);
        p = p * 2.03 + float2(17.1, 9.2);
        a *= 0.5;
    }
    return v;
}

float2 rot(float2 p, float a) {
    float c = cos(a);
    float s = sin(a);
    return float2(p.x * c - p.y * s, p.x * s + p.y * c);
}

float3 aces(float3 x) {
    return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
}

// ---- distances ----

float sdBox(float3 p, float3 b) {
    float3 q = abs(p) - b;
    return length(max(q, float3(0.0))) + min(max(q.x, max(q.y, q.z)), 0.0);
}

float sdSegment2(float2 p, float2 a, float2 b) {
    float2 pa = p - a;
    float2 ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h);
}

// A ring of radius r and tube w round the axis n, centred on the origin.
float sdRing(float3 p, float3 n, float r, float w) {
    float h = dot(p, n);
    float l = length(p - n * h);
    return length(float2(l - r, h)) - w;
}

// One torii standing across the path at z = 0. Returns distance in x, material in y.
float2 torii(float3 p) {
    float ax = abs(p.x);
    // posts, leaning in a little as real ones do
    float lean = POST_X - 0.035 * p.y;
    float post = max(length(float2(ax - lean, p.z)) - 0.19, max(p.y - 4.05, -p.y));
    // black footing
    float foot = max(length(float2(ax - POST_X, p.z)) - 0.27, max(p.y - 0.42, -p.y));
    // the tie beam through the posts
    float nuki = sdBox(p - float3(0.0, 3.25, 0.0), float3(2.45, 0.15, 0.13)) - 0.02;
    // the top beam, turning up at its ends
    float rise = 0.045 * p.x * p.x;
    float shimaki = sdBox(float3(p.x, p.y - 3.98 - rise, p.z), float3(2.75, 0.14, 0.2)) - 0.02;
    float kasagi = sdBox(float3(p.x, p.y - 4.24 - rise, p.z), float3(2.95, 0.1, 0.27)) - 0.03;
    // the tablet between the beams
    float gaku = sdBox(p - float3(0.0, 3.62, 0.0), float3(0.2, 0.24, 0.09));
    // gilt bands where the beams meet the posts
    float band = max(length(float2(ax - lean, p.z)) - 0.205, min(abs(p.y - 2.98), abs(p.y - 0.52)) - 0.045);
    float red = min(min(post, nuki), min(shimaki, gaku));
    float black = min(foot, kasagi);
    float2 best = red < black ? float2(red, 1.0) : float2(black, 2.0);
    return band < best.x ? float2(band, 3.0) : best;
}

float2 corridor(float3 p) {
    float k = clamp(floor((p.z - GATE_FIRST) / GATE_STEP + 0.5), 0.0, (GATE_LAST - GATE_FIRST) / GATE_STEP);
    return torii(float3(p.x, p.y, p.z - (GATE_FIRST + k * GATE_STEP)));
}

float armillary(float3 p, Scene s) {
    float3 q = p - s.planet;
    float a = sdRing(q, s.ax1, RING_R, 0.09);
    float b = sdRing(q, s.ax2, RING_R * 0.9, 0.075);
    float c = sdRing(q, s.ax3, RING_R * 0.8, 0.06);
    return min(a, min(b, c));
}

float2 solid(float3 p, Scene s) {
    float2 d = corridor(p);
    float g = armillary(p, s);
    return g < d.x ? float2(g, 3.0) : d;
}

float3 normalAt(float3 p, Scene s) {
    float e = 0.004;
    float3 a = float3(e, -e, -e);
    float3 b = float3(-e, -e, e);
    float3 c = float3(-e, e, -e);
    float3 d = float3(e, e, e);
    return normalize(a * solid(p + a, s).x + b * solid(p + b, s).x + c * solid(p + c, s).x + d * solid(p + d, s).x);
}

// The nearest solid along a ray: the marched shapes, the planet and the floor.
Hit trace(float3 ro, float3 rd, Scene s, float reach) {
    Hit h;
    h.t = reach;
    h.m = 0.0;
    float t = 0.02;
    for (int i = 0; i < 64; i++) {
        float3 p = ro + rd * t;
        float2 d = solid(p, s);
        if (d.x < 0.002 * (1.0 + t * 0.4)) {
            h.t = t;
            h.m = d.y;
            break;
        }
        t += d.x * 0.92;
        if (t > reach) { break; }
    }
    // the planet
    float3 oc = ro - s.planet;
    float b = dot(oc, rd);
    float c = dot(oc, oc) - PLANET_R * PLANET_R;
    float disc = b * b - c;
    if (disc > 0.0) {
        float tp = -b - sqrt(disc);
        if (tp > 0.0 && tp < h.t) {
            h.t = tp;
            h.m = 4.0;
        }
    }
    // the floor
    if (rd.y < -0.0005) {
        float tf = -ro.y / rd.y;
        if (tf > 0.0 && tf < h.t) {
            h.t = tf;
            h.m = 5.0;
        }
    }
    return h;
}

// ---- sky ----

float3 sky(float3 rd, Scene s) {
    float up = max(rd.y, 0.0);
    float3 zenith = float3(0.004, 0.006, 0.022);
    float3 mid = float3(0.022, 0.012, 0.055);
    float3 low = float3(0.30, 0.055, 0.10);
    float3 col = mix(low, mid, ss(0.0, 0.11, up));
    col = mix(col, zenith, ss(0.06, 0.55, up));
    // the planet's light on the haze
    float3 toPlanet = normalize(float3(0.0, 0.03, 1.0));
    float toward = sat(dot(rd, toPlanet));
    col += float3(0.9, 0.2, 0.08) * pow(toward, 60.0) * 0.5 + float3(0.5, 0.08, 0.12) * pow(toward, 12.0) * 0.10;
    // a sea of clouds along the horizon
    float2 cp = float2(rd.x, rd.y * 3.2) / (abs(rd.z) + 0.25);
    float cloud = fbm(cp * 2.2 + float2(0.0, s.time * 0.004));
    float band = ss(0.30, 0.0, up) * ss(0.40, 0.74, cloud + (0.16 - up) * 1.1);
    float3 cloudCol = mix(float3(0.035, 0.016, 0.06), float3(0.80, 0.17, 0.20), sat(0.1 + pow(toward, 3.0) * 1.1 - up * 2.2));
    col = mix(col, cloudCol, band * 0.8);
    // stars, and the river of heaven
    float2 sp = rd.xy / (abs(rd.z) + 0.6);
    float river = ss(0.5, 0.9, fbm(float2(sp.x * 1.6 + sp.y * 1.1, (sp.y - sp.x * 0.7) * 5.0) + 3.7)) * ss(0.12, 0.5, up);
    float2 cell = floor(sp * 150.0);
    float star = hash21(cell);
    float twinkle = 0.75 + 0.25 * sin(s.time * 2.6 + star * 60.0);
    float2 inCell = fract(sp * 150.0) - 0.5 - (float2(hash21(cell + 7.3), hash21(cell + 2.9)) - 0.5) * 0.5;
    float lit = ss(0.9955 - river * 0.008, 1.0, star) * twinkle * ss(0.30, 0.04, length(inCell));
    col += float3(0.8, 0.85, 1.0) * lit * ss(0.03, 0.25, up) * (1.0 - band) * 1.8;
    col += float3(0.10, 0.09, 0.2) * river * 0.22 * (1.0 - band);
    // the moon, small and cold
    float m = sat(dot(rd, s.moon));
    col += float3(0.8, 0.88, 1.0) * (ss(0.99982, 0.99990, m) * 1.4 + pow(m, 600.0) * 0.25);
    return col;
}

// ---- the figures, as distances in a plane of radius 1 ----

float figRing(float2 p, float r) { return abs(length(p) - r); }

float figPolygon(float2 p, float sides, float r, float turn, float skip) {
    // edges from corner i to corner i + skip
    float d = 10.0;
    for (int i = 0; i < 6; i++) {
        float fi = float(i);
        if (fi < sides) {
            float a = turn + TAU * fi / sides;
            float b = turn + TAU * (fi + skip) / sides;
            d = min(d, sdSegment2(p, r * float2(cos(a), sin(a)), r * float2(cos(b), sin(b))));
        }
    }
    return d;
}

// 七宝: six circles round one.
float figShippo(float2 p) {
    float d = min(figRing(p, 1.0), figRing(p, 0.5));
    for (int i = 0; i < 6; i++) {
        float a = TAU * float(i) / 6.0;
        d = min(d, figRing(p - 0.5 * float2(cos(a), sin(a)), 0.5));
    }
    return d;
}

// 籠目: two triangles in a ring.
float figKagome(float2 p) {
    float d = min(figRing(p, 1.0), figRing(p, 1.07));
    d = min(d, figPolygon(p, 3.0, 1.0, PI * 0.5, 1.0));
    d = min(d, figPolygon(p, 3.0, 1.0, -PI * 0.5, 1.0));
    return d;
}

// セーマン: the five-pointed star of Abe no Seimei, in its ring.
float figSeiman(float2 p) {
    float d = min(figRing(p, 1.0), figRing(p, 1.07));
    d = min(d, figPolygon(p, 5.0, 1.0, -PI * 0.5, 2.0));
    return d;
}

// ドーマン: the nine cuts, four upright and five across.
float figDoman(float2 p) {
    float d = 10.0;
    for (int i = 0; i < 5; i++) {
        float y = -0.8 + 0.4 * float(i);
        d = min(d, sdSegment2(p, float2(-0.92, y), float2(0.92, y)));
    }
    for (int j = 0; j < 4; j++) {
        float x = -0.6 + 0.4 * float(j);
        d = min(d, sdSegment2(p, float2(x, -1.0), float2(x, 1.0)));
    }
    return d;
}

// 天円地方: the circle of heaven round the square of earth.
float figCoin(float2 p) {
    float d = min(figRing(p, 1.0), figRing(p, 0.93));
    float2 q = abs(rot(p, PI * 0.25)) - float2(0.93 * 0.7071);
    float box = abs(length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0));
    return min(d, box);
}

// 式盤: the diviner's board. Rings, twelve hours, and the eight trigrams.
float figShikiban(float2 p, float turn) {
    float r = length(p);
    float d = min(min(figRing(p, 1.0), figRing(p, 0.82)), min(figRing(p, 0.52), figRing(p, 0.46)));
    // twelve marks for the branches, on the inner plate, which turns
    float2 q = rot(p, turn);
    float a = atan(q.y, q.x);
    float seg = TAU / 12.0;
    float folded = a - seg * floor(a / seg + 0.5);
    float2 pol = float2(r, folded * r);
    d = min(d, max(abs(pol.y), max(pol.x - 0.52, 0.46 - pol.x)) );
    // sixty small marks on the rim
    float seg2 = TAU / 60.0;
    float a0 = atan(p.y, p.x);
    float f2 = a0 - seg2 * floor(a0 / seg2 + 0.5);
    d = min(d, max(abs(f2 * r), max(r - 1.0, 0.955 - r)));
    // the trigrams: three bars each, whole or broken, between the rings
    float seg8 = TAU / 8.0;
    float idx = floor(a0 / seg8 + 0.5);
    float f8 = (a0 - seg8 * idx) * r;
    float n = idx - 8.0 * floor(idx / 8.0);
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float rr = 0.60 + 0.075 * fi;
        // bit i of n: whole for 1, broken for 0
        float bit = floor(n / exp2(fi)) - 2.0 * floor(n / exp2(fi + 1.0));
        float bar = max(abs(r - rr) - 0.0, abs(f8) - 0.13);
        float gap = bit > 0.5 ? 10.0 : abs(f8) - 0.03;
        d = min(d, max(bar, -gap));
    }
    return d;
}

float figure(float kind, float2 p, float turn) {
    if (kind < 0.5) { return figShippo(p); }
    if (kind < 1.5) { return figDoman(p); }
    if (kind < 2.5) { return figSeiman(p); }
    if (kind < 3.5) { return figKagome(p); }
    if (kind < 4.5) { return figCoin(p); }
    return figShikiban(p, turn);
}

// The light of the seals hanging in the gates, gathered along a ray up to distance `reach`.
float3 seals(float3 ro, float3 rd, float reach, Scene s) {
    float3 sum = float3(0.0);
    if (rd.z < 0.001) { return sum; }
    for (int i = 0; i < 7; i++) {
        float fi = float(i);
        float z = fi < 5.5 ? GATE_FIRST + 2.0 + GATE_STEP * fi : 35.0;
        float t = (z - ro.z) / rd.z;
        if (t > 0.05 && t < reach) {
            float3 p = ro + rd * t;
            float big = fi > 5.5 ? 1.0 : 0.0;
            float radius = mix(1.28, 1.52, big);
            float2 q = (p.xy - float2(0.0, mix(1.85, 1.95, big))) / radius;
            if (dot(q, q) < 1.6) {
                float dir = fi - 2.0 * floor(fi * 0.5) < 0.5 ? 1.0 : -1.0;
                float spin = big > 0.5 ? 0.0 : dir * (0.5 - 0.5 * ss(z - 14.0, z - 1.0, ro.z)) * 1.4;
                float d = figure(big > 0.5 ? 5.0 : fi, rot(q, spin), s.turn) * radius;
                float w = 0.0035 * t + 0.004;
                float core = ss(w * 1.6, w * 0.4, d);
                float halo = exp(-d * 14.0) * 0.10 + exp(-d * 46.0) * 0.34;
                float3 gold = float3(1.0, 0.72, 0.32);
                float3 pale = float3(0.86, 0.93, 1.0);
                float3 tint = fi - 2.0 * floor(fi * 0.5) < 0.5 ? gold : mix(gold, pale, 0.7);
                tint = mix(tint, float3(1.0, 0.80, 0.42), big);
                // a seal wakes as the camera comes up to it; the last one burns from the start
                float wake = mix(ss(z - 10.5, z - 6.5, ro.z), 1.0, big);
                float fog = mix(exp(-t * 0.10) * 1.3 * wake, exp(-t * 0.035), big);
                float closeFade = ss(0.1, 1.6, t);
                sum += tint * (core * 1.5 + halo) * fog * closeFade * mix(0.9, 1.1, big);
            }
        }
    }
    return sum;
}

// ---- the planet's mark ----

float boxMask(float2 p, float2 c, float2 h, float aa) {
    float2 q = abs(p - c) - h;
    float d = max(q.x, q.y);
    return ss(aa, -aa, d);
}

float legMask(float2 p, float2 a, float2 b, float w, float aa) {
    return ss(aa, -aa, sdSegment2(p, a, b) - w);
}

// 天 on a disc of radius 1 (x right, y down), the proportions of the token's own mark.
float tenMark(float2 p, float aa) {
    float m = boxMask(p, float2(0.0, -0.418), float2(0.614, 0.082), aa);
    m = max(m, boxMask(p, float2(0.0, -0.082), float2(0.614, 0.082), aa));
    m = max(m, boxMask(p, float2(0.0, -0.25), float2(0.086, 0.25), aa));
    m = max(m, legMask(p, float2(0.0, 0.11), float2(-0.52, 0.80), 0.088, aa));
    m = max(m, legMask(p, float2(0.0, 0.11), float2(0.52, 0.80), 0.088, aa));
    // the legs stop at the disc's lower chord, as the mark's do
    return m * ss(0.86 + aa, 0.86 - aa, p.y);
}

// ---- light ----

float3 shadeSolid(float3 p, float3 n, float3 rd, float m, Scene s) {
    float3 toPlanet = s.planet - p;
    float dist = length(toPlanet);
    float3 L = toPlanet / dist;
    float3 planetLight = float3(1.0, 0.26, 0.12) * 46.0 / (dist * dist + 12.0);
    float3 moonLight = float3(0.36, 0.44, 0.62) * 0.55;
    float3 F = normalize(float3(-0.35, 0.55, -0.76));
    float3 fillLight = float3(0.95, 0.80, 0.70) * 0.72;
    float3 ambient = mix(float3(0.035, 0.012, 0.03), float3(0.03, 0.035, 0.07), 0.5 + 0.5 * n.y);
    // closeness of other things, sampled along the normal
    float ao = 1.0;
    for (int i = 1; i < 4; i++) {
        float h = 0.09 * float(i);
        ao -= (h - solid(p + n * h, s).x) * (0.5 / float(i));
    }
    ao = sat(ao);
    float3 albedo = m < 1.5 ? float3(0.70, 0.058, 0.036) : float3(0.016, 0.014, 0.018);
    float gloss = m < 1.5 ? 64.0 : 24.0;
    float3 R = reflect(rd, n);
    float fres = 0.04 + 0.96 * pow(1.0 - sat(dot(-rd, n)), 5.0);
    if (m > 2.5) {
        // gold: no colour of its own, only what it mirrors
        float3 env = sky(R, s) * 1.6 + float3(1.0, 0.3, 0.14) * pow(sat(dot(R, L)), 14.0) * 3.5
            + fillLight * pow(sat(dot(R, F)), 18.0) * 2.2;
        float3 goldTint = float3(1.0, 0.76, 0.36);
        return (env * goldTint + goldTint * (0.10 + 0.30 * sat(dot(n, F)) + 0.2 * sat(dot(n, s.moon)))) * ao;
    }
    float wrap = sat(dot(n, F) * 0.6 + 0.4);
    float3 diffuse = albedo * (ambient + planetLight * sat(dot(n, L)) + moonLight * sat(dot(n, s.moon)) + fillLight * wrap);
    float3 specCol = planetLight * pow(sat(dot(R, L)), gloss) * 0.9 + moonLight * pow(sat(dot(R, s.moon)), gloss * 2.0) * 1.2
        + fillLight * pow(sat(dot(R, F)), gloss) * 0.55;
    float3 mirror = sky(R, s) * fres * 0.6;
    // the planet's light catching the edges that face away from it
    float3 rim = float3(1.0, 0.24, 0.10) * pow(1.0 - sat(dot(-rd, n)), 3.0) * 0.35;
    return (diffuse + specCol * 0.4 + mirror + rim) * ao;
}

float3 shadePlanet(float3 p, float3 rd, Scene s, float pxAngle) {
    float3 n = (p - s.planet) / PLANET_R;
    // the mark faces down the corridor
    float2 uv = float2(n.x, -n.y);
    float aa = pxAngle * 2.0 + 0.004;
    float facing = sat(-n.z);
    float mark = tenMark(uv, aa) * ss(0.05, 0.25, facing);
    float3 red = float3(0.80, 0.045, 0.04);
    float3 base = mix(red, float3(0.96, 0.95, 0.93), mark);
    float3 key = normalize(float3(-0.55, 0.75, -0.4));
    float diffuse = 0.34 + 0.66 * sat(dot(n, key));
    float3 R = reflect(rd, n);
    float fres = 0.04 + 0.96 * pow(1.0 - sat(dot(-rd, n)), 5.0);
    float3 col = base * diffuse * 1.5;
    col += base * 0.55;                                 // it glows of itself
    col += float3(1.0, 0.95, 0.9) * pow(sat(dot(R, key)), 90.0) * 1.6;
    col += sky(R, s) * fres * 1.2;
    col += float3(1.0, 0.25, 0.1) * pow(1.0 - facing, 3.0) * 0.5;
    return col;
}

// The floor: black lacquer, still as water, with the nine cuts drawn down the path in faint gold.
float3 floorLines(float3 p, float t) {
    float w = 0.0016 * t + 0.003;
    float ax = abs(p.x);
    float along = min(abs(ax - 0.62), abs(ax - 1.38));
    float across = max(abs(p.z - floor(p.z * 0.5 + 0.5) * 2.0), ax - 1.38);
    float d = min(along, across);
    float line = ss(w * 1.5, w * 0.3, d) + exp(-d * 40.0) * 0.18;
    return float3(1.0, 0.70, 0.30) * line * 0.16 * exp(-t * 0.05);
}

float3 render(float3 ro, float3 rd, Scene s, float pxAngle) {
    float reach = 140.0;
    Hit h = trace(ro, rd, s, reach);
    float3 col;
    float fogT = h.t;
    if (h.m < 0.5) {
        col = sky(rd, s);
        fogT = 0.0;
    } else if (h.m > 4.5) {
        // the mirror floor: what it shows, dimmed by the lacquer
        float3 p = ro + rd * h.t;
        float ripple = (noise2(p.xz * 1.7 + float2(0.0, s.time * 0.12)) - 0.5) * 0.012;
        float3 n = normalize(float3(ripple, 1.0, ripple * 0.7));
        float3 R = reflect(rd, n);
        float fres = 0.05 + 0.95 * pow(1.0 - sat(dot(-rd, n)), 4.0);
        Hit r = trace(p + n * 0.01, R, s, 90.0);
        float3 seen;
        if (r.m < 0.5 || r.m > 4.5) {
            seen = sky(R, s);
        } else if (r.m > 3.5) {
            seen = shadePlanet(p + R * r.t, R, s, pxAngle * 2.0);
        } else {
            float3 q = p + R * r.t;
            seen = shadeSolid(q, normalAt(q, s), R, r.m, s);
            seen = mix(float3(0.035, 0.014, 0.05), seen, exp(-r.t * 0.02));
        }
        seen += seals(p, R, r.t, s) * 0.8;
        // the planet's glow lying on the floor
        float3 toP = s.planet - p;
        float glow = 1.0 / (1.0 + dot(toP.xz, toP.xz) * 0.012);
        col = float3(0.004, 0.003, 0.006) + seen * fres * 0.78 + float3(0.5, 0.06, 0.04) * glow * 0.05;
        col += floorLines(p, h.t);
    } else if (h.m > 3.5) {
        col = shadePlanet(ro + rd * h.t, rd, s, pxAngle);
        fogT = 0.0;
    } else {
        float3 p = ro + rd * h.t;
        col = shadeSolid(p, normalAt(p, s), rd, h.m, s);
    }
    // night air, and the seals hanging in it
    float fog = 1.0 - exp(-fogT * 0.016);
    col = mix(col, float3(0.035, 0.014, 0.05), fog * 0.9);
    col += seals(ro, rd, h.t, s);
    // the planet's bloom over everything toward it on screen
    float3 toPlanet = normalize(s.planet - ro);
    float c = sat(dot(rd, toPlanet));
    float size = PLANET_R / length(s.planet - ro);
    float ang = acos(min(c, 0.99999));
    col += float3(1.0, 0.22, 0.10) * exp(-max(ang - size, 0.0) / (size * 1.3 + 0.012)) * 0.34;
    return col;
}

half4 main(float2 fragCoord) {
    float2 frag = fragCoord;
    Scene s;
    s.time = view.z;
    s.lock = state.y;
    s.turn = state.z;
    s.moon = normalize(float3(-0.52, 0.62, 0.58));
    s.planet = float3(0.0, PLANET_Y, PLANET_Z);
    // the armillary: three rings tumbling, then lying down together in the plane the home's ring has
    float3 rest = normalize(float3(0.17, 0.95, -0.27));
    float tumble = 1.0 - s.lock;
    float a1 = s.time * 0.9 + 0.4;
    float a2 = s.time * 0.7 + 2.1;
    float a3 = s.time * 1.1 + 4.0;
    s.ax1 = normalize(mix(rest, float3(sin(a1), 0.35, cos(a1)), tumble));
    s.ax2 = normalize(mix(rest, float3(cos(a2) * 0.8, sin(a2), 0.5), tumble));
    s.ax3 = normalize(mix(rest, float3(0.3, cos(a3), sin(a3)), tumble));

    float3 ro = float3(0.0, flight.y, flight.x);
    float2 q = (frag - lens.xy) / lens.z;
    float3 rd = normalize(float3(q.x, -q.y, 1.0));
    float2 yz = rot(float2(rd.y, rd.z), flight.w);
    rd = float3(rd.x, yz.x, yz.y);
    float2 xz = rot(float2(rd.x, rd.z), flight.z);
    rd = float3(xz.x, rd.y, xz.y);
    float2 xy = rot(float2(rd.x, rd.y), lens.w);
    rd = float3(xy.x, xy.y, rd.z);

    float3 col = render(ro, rd, s, 1.0 / lens.z);

    // exposure, the lens, the film
    float2 v = (frag / view.xy - 0.5) * float2(view.x / view.y, 1.0);
    col *= 1.0 - 0.55 * dot(v, v);
    col = aces(col * 1.25);
    col = pow(col, float3(1.0 / 2.2));
    col += (hash21(frag + view.z * 61.0) - 0.5) * 0.012;

    // day breaks: everything gives way to the page, and (page.w = 0) the planet alone stays
    float3 toPlanet = float3(0.0, PLANET_Y, PLANET_Z) - ro;
    float bq = dot(toPlanet, rd);
    float onPlanet = ss(PLANET_R * 1.02, PLANET_R * 0.97, sqrt(max(dot(toPlanet, toPlanet) - bq * bq, 0.0)));
    float dawn = state.w * (1.0 - onPlanet * step(0.0, bq) * (1.0 - page.w));
    col = mix(col, page.rgb, dawn);
    // it rises out of the dark launch screen
    col = mix(float3(0.024, 0.024, 0.043), col, state.x);
    return half4(float4(col, 1.0));
}
"""
