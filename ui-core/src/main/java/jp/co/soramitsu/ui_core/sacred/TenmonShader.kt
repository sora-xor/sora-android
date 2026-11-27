package jp.co.soramitsu.ui_core.sacred

/**
 * The launch scene, drawn per pixel: the SORA mark, a disc of cut red glass with 天 let into it in white, on a
 * stack of seven rings of mother of pearl that spread out from under it where it lands.
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

// Heights are in units of the mark's radius at rest, as the rings' radii are.
const float STEP = 0.10;
const float MARK_THICK = 0.07;
const float PULSE_PERIOD = 1.9;

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
    for (int k = 0; k < 3; k++) {
        v += a * noise2(p);
        p = p * 2.03 + float2(17.1, 9.2);
        a *= 0.5;
    }
    return v;
}

// Runs a twentieth past its end and comes back.
float backOut(float x) {
    float y = x - 1.0;
    return 1.0 + 2.2 * y * y * y + 1.2 * y * y;
}

// ---- the rings ----
// Seven, counted from the innermost (0). Each is a disc lying on the next larger one.

float ringRadius(float i) {
    return 1.52 + 0.60 * i + 0.07 * i * i;
}

// How far ring i has spread, 0...1 and a little over. The outermost leaves first and arrives last,
// as the first ripple of a drop does.
float ringSpread(float i, float bloom) {
    float start = 0.045 * (6.0 - i);
    float end = 0.74 + 0.04 * i;
    return backOut(sat((bloom - start) / (end - start)));
}

// The pulse that runs out from the mark while the wallet gets ready. `index` is 0 for the mark and
// i + 1 for ring i.
float pulseAt(float index, float clock) {
    float head = fract(clock / PULSE_PERIOD) * 13.0 - 2.5;
    float x = head - index;
    return exp(-x * x * 0.9) * ss(0.0, 0.2, clock);
}

float ringHeight(float i, float spread, float clock, float stand) {
    float level = (7.0 - i) * STEP;
    return (level * ss(0.0, 0.55, spread) + 0.6 * STEP * pulseAt(i + 1.0, clock)) * stand;
}

// How dark the shadow of a disc is here: 1 well inside it, 0 well clear. The disc stands `rise` above
// the surface the shadow falls on.
float discShadow(float2 p, float2 centre, float radius, float rise, float2 fall, float unit) {
    float soft = (0.02 + 1.15 * rise) * unit;
    return ss(soft, -soft, length(p - centre - fall * rise * unit) - radius);
}

// Mother of pearl. The shell is laid down in films whose depth wanders across it (`flow`, two fields
// each -1...1), and the depth of the film under a point sets which colours it sends back. Looking or
// lighting it from another side (`glance`) is the same as another depth, so a tilt runs the colours
// across the piece; each ring is cut from another part of the shell (`which`) and answers in its own.
// `fibre` is the fine silk across the grain, 0...1.
float3 pearl(float2 flow, float fibre, float2 glance, float which, float2 dirn, float wave) {
    float turnBy = 0.9 * which + 0.4;
    float grain = flow.x * cos(turnBy) + flow.y * sin(turnBy);
    float2 axis = float2(cos(1.7 * which + 0.6), sin(1.7 * which + 0.6));
    float slide = dot(glance, axis) + 0.3 * dot(glance, dirn);
    float film = 0.5 * grain + 0.23 * which + 1.1 * slide + 0.25 * wave;
    float3 hue = 0.5 + 0.5 * cos(TAU * (film + float3(0.0, 0.36, 0.58)));
    float mean = (hue.x + hue.y + hue.z) / 3.0;
    float band = 0.5 + 0.5 * sin(TAU * (0.75 * grain + 0.31 * which) + 5.0 * slide + 2.0 * wave);
    float lustre = band * (0.55 + 0.9 * fibre * band);
    float3 body = float3(0.945, 0.942, 0.938) * (0.984 + 0.052 * lustre);
    return body + (hue - mean) * (0.016 + 0.058 * lustre);
}

// The colour of a ring's face: a polished disc of mother of pearl, very slightly domed, with a rounded
// rim that catches the lamp on one side and loses it on the other.
float3 ringFace(float2 p, float2 centre, float radius, float3 light, float3 base, float unit, float stand) {
    float2 v = p - centre;
    float dist = length(v);
    float2 dirn = v / max(dist, 0.001);
    float rim = ss(0.06 * unit, 0.0, radius - dist);
    float dome = 0.11 * dist / max(radius, 1.0);
    float3 n = normalize(float3(dirn * (dome + 0.9 * rim * rim) * stand, 1.0));
    float lit = dot(n, light) - light.z;
    float3 h = normalize(light + float3(0.0, 0.0, 1.0));
    float glint = pow(sat(dot(n, h)), 60.0) * rim * stand;
    return base * (1.0 + 0.38 * lit) + 0.34 * glint;
}

// ---- the mark ----

float boxMask(float2 p, float2 c, float2 h, float aa) {
    float2 q = abs(p - c) - h;
    return ss(aa, -aa, max(q.x, q.y));
}

float legMask(float2 p, float2 through, float2 along, float aa) {
    float2 v = p - through;
    float across = abs(v.x * along.y - v.y * along.x) - 0.0857;
    float before = -(v.x * along.x + v.y * along.y) - 0.13;
    return ss(aa, -aa, max(across, before));
}

// 天 on a disc of radius 1 (x right, y down), measured off the logo: two bars, the stem between
// them, and two legs that run out to the rim, where the disc cuts them.
float tenMark(float2 p, float aa) {
    float m = boxMask(p, float2(0.0045, -0.418), float2(0.6135, 0.082), aa);
    m = max(m, boxMask(p, float2(0.0045, -0.082), float2(0.6135, 0.082), aa));
    m = max(m, boxMask(p, float2(0.0045, -0.25), float2(0.0865, 0.25), aa));
    m = max(m, legMask(p, float2(-0.036, 0.086), float2(-0.5547, 0.8321), aa));
    m = max(m, legMask(p, float2(0.05, 0.086), float2(0.5547, 0.8321), aa));
    return m * ss(1.0 + aa, 1.0 - aa, length(p));
}

half4 main(float2 fragCoord) {
    float2 p = fragCoord;
    float unit = max(lens.w, 1.0);
    float2 mid = view.xy * 0.5;
    float bloom = state.x;
    float clock = state.y;
    float lift = state.z;
    float reveal = state.w;
    float aa = 0.75;

    // What is left of the rings' height, and how far they have run out, as they give way to the page.
    float stand = 1.0 - ss(0.0, 0.8, reveal);
    float widen = 1.0 + 0.5 * reveal * reveal;
    float settle = ss(0.45, 1.0, reveal);

    float3 light = normalize(float3(-0.44 + flight.z, -0.58 + flight.w, 0.70));
    float2 fall = -light.xy / light.z;
    float2 lean = flight.xy;
    float3 pageCol = page.xyz;
    float toPage = ss(0.25, 0.95, reveal);
    float3 shade = float3(0.70, 0.675, 0.66);

    // The mark: where it is, how big, how far above the stack.
    float2 markAt = lens.xy;
    float markR = max(lens.z, 1.0);
    float seen = 1.0 - ss(0.5, 1.0, lift);
    float top = ringHeight(0.0, ringSpread(0.0, bloom), clock, stand);
    float markRise = MARK_THICK * (1.0 - settle) + 2.4 * lift;

    // The shell's grain here, the same under every ring, and how far the eye and the lamp are off
    // the straight view.
    float2 at = (p - mid) / unit;
    float warp = fbm(at * 0.6 + float2(3.1, 1.7));
    float2 flow = float2(fbm(float2(at.x * 0.4, at.y * 1.3) + 2.0 * warp),
                         fbm(float2(at.x * 1.3 + 7.0, at.y * 0.4 - 3.0) + 2.0 * warp)) * 2.8 - 1.4;
    float2 glance = float2(flight.z, flight.w) * 2.0 + lean * 0.8;

    // The page under everything, then each ring from the outermost in.
    float3 col = pageCol;
    float3 face = pageCol;
    for (int n = 0; n < 8; n++) {
        // n = 0 is the page itself; ring 7 - n lies on it, and so on inwards.
        float i = 7.0 - float(n);
        float cover = 1.0;
        float here = 0.0;
        float2 centre = mid;
        float radius = 0.0;
        float spread = 1.0;
        if (n > 0) {
            spread = ringSpread(i, bloom);
            here = ringHeight(i, spread, clock, stand);
            centre = mid + lean * (here - top) * unit;
            radius = ringRadius(i) * spread * widen * unit;
            cover = ss(aa, -aa, length(p - centre) - radius) * step(0.002, spread);
        }
        if (cover > 0.0) {
            if (n > 0) {
                float2 off = p - centre;
                float2 dirn = off / max(length(off), 0.001);
                float along = 0.7 * i + 0.5;
                float2 grainAt = float2(at.x * cos(along) + at.y * sin(along), at.y * cos(along) - at.x * sin(along));
                float fibre = noise2(float2(grainAt.x * 0.9, grainAt.y * 16.0) + 5.0 * i + 1.5 * flow);
                float3 shell = pearl(flow, fibre, glance, i, dirn, pulseAt(i + 1.0, clock));
                face = ringFace(p, centre, radius, light, mix(shell, pageCol, toPage), unit, stand * ss(0.0, 0.3, spread));
            }
            // The ring lying on this one throws its shadow here, and so does the mark.
            float dark = 0.0;
            float bounce = 0.0;
            float contact = 0.0;
            if (n < 7) {
                float j = i - 1.0;
                float spreadJ = ringSpread(j, bloom);
                float above = ringHeight(j, spreadJ, clock, stand);
                float2 centreJ = mid + lean * (above - top) * unit;
                float radiusJ = ringRadius(j) * spreadJ * widen * unit;
                float rise = max(above - here, 0.0);
                float grown = step(0.002, spreadJ);
                dark = discShadow(p, centreJ, radiusJ, rise, fall, unit) * sat(rise / STEP) * grown;
                bounce = discShadow(p, centreJ, radiusJ, rise, -fall, unit) * sat(rise / STEP) * grown;
                contact = exp(-max(length(p - centreJ) - radiusJ, 0.0) / (0.07 * unit)) * sat(rise / STEP) * grown;
            }
            float markDark = discShadow(p, markAt, markR, max(top - here, 0.0) + markRise, fall, unit)
                * seen * (1.0 - settle) / (1.0 + 2.2 * lift);
            float3 surface = face;
            surface += 0.05 * bounce * (1.0 - dark);
            surface *= mix(float3(1.0, 1.0, 1.0), shade, 0.78 * dark);
            surface *= 1.0 - 0.05 * contact;
            surface *= mix(float3(1.0, 1.0, 1.0), float3(0.80, 0.63, 0.61), 0.62 * markDark);
            col = mix(col, surface, cover);
        }
    }

    // Washi's grain, and enough noise that the soft shadows do not band.
    float grain = noise2(p / view.w * 1.9) - 0.5;
    col += (0.010 * grain) * (1.0 - settle);
    col += (hash21(p + fract(view.z) * 61.0) - 0.5) / 255.0;

    // The mark: a disc of cut red glass with 天 let into it in white.
    float2 q = (p - markAt) / markR;
    float rad = length(q);
    float edge = aa / markR + 0.32 * lift;
    float inside = ss(edge, -edge, rad - 1.0) * seen;
    if (inside > 0.0) {
        float3 red = float3(0.890, 0.137, 0.173);
        float2 dirn = q / max(rad, 0.001);
        float cut = ss(0.895, 0.985, rad);
        float3 n = normalize(float3(dirn * (0.05 * rad + 1.0 * cut), 1.0));
        float lit = dot(n, light) - light.z;
        float3 h = normalize(light + float3(0.0, 0.0, 1.0));
        float2 toLight = normalize(light.xy);
        float across = dot(q, toLight);
        float3 glass = red * (1.0 + 0.55 * lit);
        // Light crosses the face: brighter towards the lamp, and pooled warm inside the far rim.
        glass += float3(0.075, 0.03, 0.03) * across * (1.0 - cut);
        glass += float3(0.16, 0.05, 0.03) * ss(0.45, 0.9, rad) * sat(-dot(dirn, toLight)) * (1.0 - cut);
        // The cut's upper edge takes the lamp as a hard line; the bevel takes it broadly.
        float rimLine = ss(0.03, 0.0, abs(rad - 0.90)) * sat(dot(dirn, toLight));
        glass += float3(1.0, 0.86, 0.84) * (0.30 * rimLine + 0.55 * pow(sat(dot(n, h)), 28.0) * cut);
        float soft = aa / markR + 0.2 * lift;
        float mark = tenMark(q, soft);
        float under2 = tenMark(q - fall * 0.016, soft * 2.5 + 0.012);
        glass *= 1.0 - 0.16 * under2 * (1.0 - mark);
        float3 ink = float3(1.0, 1.0, 1.0) * (0.985 + 0.02 * across);
        float3 lively = mix(glass, ink, mark);
        float3 plain = mix(red, float3(1.0, 1.0, 1.0), tenMark(q, aa / markR));
        col = mix(col, mix(lively, plain, settle), inside * (1.0 - page.w * reveal));
    }
    return half4(float4(col, 1.0));
}
"""
