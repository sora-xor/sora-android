package jp.co.soramitsu.ui_core.lit

/**
 * The hero scene, ray traced per pixel: a red lacquer sphere with the 天 inlaid in white enamel, the allocation ring
 * as a solid enamel band round it, both over the porcelain page they light. Every surface sees the same light rig:
 * a window (key), a sky dome and the page below as fill, the sphere's red bounce on the band, the band's colours
 * bounced and mirrored on the sphere, and the comet as a moving point light. Generated from the iOS app's Metal
 * source, so the two apps draw the same scene.
 */
internal const val LIT_HERO_SHADER = """
uniform float4 viewport;
uniform float4 center;
uniform float4 ring;
uniform float4 pose;
uniform float4 comet;
uniform float4 ringInfo;
uniform float4 segmentAngles[6];
uniform float4 segmentColors[6];
uniform float4 bounce[8];
uniform float markSide;
uniform shader mark;

float markAt(float2 uv) { return mark.eval(uv * markSide).a; }

const float PI = 3.14159265;
const float TAU = 6.2831853;

struct Scene {
    float3 K;       // towards the key light (y grows down the screen, z towards the viewer)
    float3 Kw;      // towards the middle of the window seen in reflections
    float3 Ku;      // the window's axes
    float3 Kv;
    float3 U;       // the ring plane: 0 degrees
    float3 W;       // the ring plane: 90 degrees (in front)
    float3 N;       // the ring plane's normal, up and towards the viewer
    float Rp;       // planet radius
    float R;        // band radius
    float hw;       // band half width
    float th;       // band thickness
    float aspect;
    float dark;
    float bloom;    // a soft flare of everything, once, when a read finishes
    float px;       // one pixel in points
    float3 cometPos;
    float cometPower;
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

float roundedBox(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
}

float3 aces(float3 x) {
    return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
}

float3 toSRGB(float3 c) { return pow(clamp(c, 0.0, 1.0), float3(1.0 / 2.2)); }

// What a mirror sees along direction D: the room. No geometry, just the light rig.
float3 room(float3 D, Scene s) {
    float up = -D.y;
    float3 zenith = mix(float3(0.34, 0.33, 0.33), float3(0.03, 0.03, 0.05), s.dark);
    float3 horizon = mix(float3(0.14, 0.11, 0.11), float3(0.05, 0.03, 0.05), s.dark);
    float3 ground = mix(float3(0.42, 0.32, 0.30), float3(0.05, 0.02, 0.03), s.dark);
    float3 col = up > 0.0 ? mix(horizon, zenith, ss(0.0, 0.75, up))
                          : mix(horizon, ground, ss(0.0, 0.35, -up));

    // The softbox: a broad oval that falls away from its middle, as a window behind a blind does.
    float toWindow = dot(D, s.Kw);
    if (toWindow > 0.05) {
        float2 q = float2(dot(D, s.Ku), dot(D, s.Kv)) / toWindow;
        float oval = length(q / float2(0.58, 0.26));
        float body = ss(1.0, 0.62, oval);
        float falloff = mix(0.34, 1.0, ss(0.30, -0.30, q.y));
        col += float3(1.0, 0.97, 0.94) * 19.0 * body * falloff;
    }
    return col;
}

// Soft shadow of the sphere on a point, towards direction L.
float sphereShadow(float3 P, float3 L, float Rp) {
    float along = dot(P, L);
    if (along > 0.0) return 1.0;
    float dist = length(P - along * L);
    return ss(Rp * 0.82, Rp * 1.32, dist);
}

float angleFrom(float theta, float start) {
    return fract((theta - start) / TAU) * TAU;
}

// Lacquer as seen in a reflection: no reflections of its own.
float3 planetApprox(float3 n, Scene s) {
    float key = sat((dot(n, s.K) + 0.2) / 1.2);
    float amb = 0.5 - 0.5 * n.y;
    return float3(0.72, 0.035, 0.05) * (1.5 * key + mix(0.62, 0.34, 1.0 - amb));
}

// The ring in its own plane at a world point: the segment there and its colour.
struct RingLook {
    float3 color;
    float strength;
    float lift;
    float widthScale;
    float start;
    float sweep;
    float found;
};

RingLook ringLook(float theta) {
    RingLook look;
    look.color = float3(0.0);
    look.strength = 0.0;
    look.lift = 0.0;
    look.widthScale = 1.0;
    look.start = 0.0;
    look.sweep = 0.0;
    look.found = 0.0;
    int count = int(ringInfo.y);
    for (int i = 0; i < 6; i++) {
        if (i >= count) break;
        float off = angleFrom(theta, segmentAngles[i].x);
        if (off < segmentAngles[i].y) {
            look.color = segmentColors[i].xyz;
            look.lift = segmentColors[i].w;
            look.strength = segmentAngles[i].z;
            look.widthScale = segmentAngles[i].w;
            look.start = segmentAngles[i].x;
            look.sweep = segmentAngles[i].y;
            look.found = 1.0;
            break;
        }
    }
    return look;
}

// The colour the ring casts at an angle: gaps closed, boundaries soft.
float4 ringCast(float theta) {
    float3 sum = float3(0.0);
    float strength = 0.0;
    int count = int(ringInfo.y);
    for (int k = 0; k < 3; k++) {
        float probe = theta + (float(k) - 1.0) * 0.07;
        for (int i = 0; i < 6; i++) {
            if (i >= count) break;
            float off = angleFrom(probe + 0.03, segmentAngles[i].x);
            if (off < segmentAngles[i].y + 0.06) {
                sum += segmentColors[i].xyz;
                strength += segmentAngles[i].z;
                break;
            }
        }
    }
    return float4(sum / 3.0, strength / 3.0);
}

// Enamel over a coloured base, lit by the rig, the planet's bounce and the comet.
float3 shadeBand(float3 P, float3 n, float3 base, float3 rd, float lift, Scene s) {
    float3 V = float3(0.0, 0.0, 1.0);
    float nv = sat(dot(n, V));
    float shade = sphereShadow(P, s.K, s.Rp);
    float key = sat(dot(n, s.K));
    float hemi = 0.5 - 0.5 * n.y;
    float3 amb = mix(mix(float3(0.42, 0.34, 0.32), float3(0.60, 0.56, 0.55), hemi),
                     mix(float3(0.05, 0.03, 0.04), float3(0.10, 0.10, 0.14), hemi), s.dark);
    float dist = length(P);
    float3 toPlanet = -P / max(dist, 1.0);
    float form = (0.30 + sat(dot(n, toPlanet)) * 1.8) * (s.Rp * s.Rp) / max(dist * dist, 1.0);
    float3 planetLight = float3(1.0, 0.13, 0.11) * 2.6 * form;
    float3 col = base * (float3(1.18, 1.13, 1.08) * key * shade + amb + planetLight);

    // The comet, a point light riding just above the band.
    if (s.cometPower > 0.0) {
        float3 toComet = s.cometPos - P;
        float d2 = dot(toComet, toComet);
        float nl = sat(dot(n, toComet / sqrt(max(d2, 1.0))) + 0.4);
        col += mix(base, float3(1.0), 0.45) * s.cometPower * nl * 1600.0 / (d2 + 260.0);
    }

    // The coat.
    float f = 0.045 + 0.955 * pow(1.0 - nv, 5.0);
    float3 D = reflect(-V, n);
    float3 mirror;
    // Does the reflection meet the planet?
    float b = dot(P, D);
    float c = dot(P, P) - s.Rp * s.Rp;
    float h = b * b - c;
    if (b < 0.0 && h > 0.0) {
        float t = -b - sqrt(h);
        float3 hitNormal = normalize(P + t * D);
        mirror = planetApprox(hitNormal, s);
    } else {
        mirror = room(D, s);
    }
    col = col * (1.0 - f) + mirror * f;
    col += base * (lift * 0.28 + s.bloom * 0.42);
    return col;
}

half4 main(float2 fragCoord) {
    float scale = viewport.z;
    float2 frag = fragCoord / scale;
    float2 d = frag - center.xy;
    float2 tilt = pose.zw;

    Scene s;
    s.Rp = center.z;
    s.R = ring.x;
    s.hw = ring.y * 0.5;
    s.th = ring.z;
    s.aspect = pose.x;
    s.dark = center.w;
    s.bloom = comet.w;
    s.px = 1.0 / scale;
    s.K = normalize(float3(-0.56 - 0.34 * tilt.x, -0.74 - 0.26 * tilt.y, 0.44));
    s.Kw = normalize(float3(-0.38 - 0.42 * tilt.x, -0.90 - 0.34 * tilt.y, -0.36));
    s.Ku = normalize(cross(s.Kw, float3(0.0, -1.0, 0.0)));
    s.Kv = cross(s.Ku, s.Kw);
    float roll = pose.y;
    float sinRoll = sin(roll), cosRoll = cos(roll);
    float opening = sqrt(max(1.0 - s.aspect * s.aspect, 0.0));
    s.U = float3(cosRoll, sinRoll, 0.0);
    s.W = float3(-sinRoll * s.aspect, cosRoll * s.aspect, opening);
    s.N = float3(sinRoll * opening, -cosRoll * opening, s.aspect);
    float cometAngle = comet.x;
    s.cometPower = comet.y;
    s.cometPos = (s.U * cos(cometAngle) + s.W * sin(cometAngle)) * s.R + s.N * 4.0;

    float3 V = float3(0.0, 0.0, 1.0);
    float px = s.px;

    // ---- The page: shadows and coloured bounce, as a premultiplied wash over what is behind.
    float3 Ks = normalize(float3(-0.30 - 0.20 * tilt.x, -0.42 - 0.16 * tilt.y, 0.86));
    const float floorDepth = 54.0;
    float2 shadowCentre = -Ks.xy / Ks.z * floorDepth;
    float shadowDist = length(d - shadowCentre);
    float blobA = 0.26 * exp(-pow(shadowDist / (s.Rp * 1.35), 2.0));
    float3 blobC = mix(float3(0.34, 0.10, 0.11), float3(0.0, 0.0, 0.0), s.dark);
    float pool = exp(-pow(length(d - shadowCentre * 0.45) / 150.0, 2.0));
    float poolA = mix(0.20, 0.34, s.dark) * pool;
    float3 poolC = mix(float3(0.98, 0.36, 0.34), float3(0.72, 0.06, 0.10), s.dark);

    // Accumulate "over": a wash of the planet's red, then the ring's coloured shadow, then the planet's.
    float3 floorCol = poolC * poolA;
    float floorA = poolA;

    float2 castOffset = -Ks.xy / Ks.z * 26.0;
    float2 dc = d - castOffset;
    float zCast = -(s.N.x * dc.x + s.N.y * dc.y) / s.N.z;
    float3 Q = float3(dc.x, dc.y, zCast);
    float qa = dot(Q, s.U), qb = dot(Q, s.W);
    float qr = max(length(float2(qa, qb)), 0.001);
    float qs = sqrt((qa * qa + s.aspect * s.aspect * qb * qb)) / qr;
    float castDist = abs(qr - s.R) * qs;
    float castBand = ss(s.hw * qs + 15.0, max(s.hw * qs - 3.0, 0.0), castDist);
    // Most of the page is outside the shadow: the ring's colours are looked up only inside it.
    if (castBand > 0.0) {
        float qTheta = atan(qb, qa);
        float4 castLight = ringCast(qTheta);
        if (castLight.w > 0.0) {
            float a = 0.20 * castBand * castLight.w;
            float3 tint = mix(pow(castLight.xyz, float3(1.0 / 2.2)) * 0.88, float3(0.0), s.dark * 0.4);
            floorCol = tint * a + floorCol * (1.0 - a);
            floorA = a + floorA * (1.0 - a);
        } else if (int(ringInfo.y) == 0) {
            float a = 0.07 * castBand;
            floorCol = blobC * a + floorCol * (1.0 - a);
            floorA = a + floorA * (1.0 - a);
        }
    }
    floorCol = blobC * blobA + floorCol * (1.0 - blobA);
    floorA = blobA + floorA * (1.0 - blobA);

    // A thin light round the limb: the air glowing in the sphere's light.
    float dist = length(d);
    float limbGlow = exp(-max(dist - s.Rp, 0.0) / 7.0) * step(s.Rp - 2.0, dist);
    float limbA = sat(mix(0.26, 0.42, s.dark) * (1.0 + s.bloom * 1.6)) * limbGlow;
    float3 limbC = mix(float3(1.0, 0.42, 0.40), float3(0.85, 0.10, 0.14), s.dark);
    floorCol = limbC * limbA + floorCol * (1.0 - limbA);
    floorA = limbA + floorA * (1.0 - limbA);

    // The comet's glow on the page.
    float2 cometOnScreen = s.cometPos.xy;
    float cometDist = length(d - cometOnScreen);
    float behindPlanet = 0.0;
    if (length(cometOnScreen) < s.Rp && s.cometPos.z < sqrt(max(s.Rp * s.Rp - dot(cometOnScreen, cometOnScreen), 0.0))) {
        behindPlanet = 1.0;
    }
    behindPlanet = max(behindPlanet, s.cometPos.z < 0.0
        ? ss(s.Rp + 8.0, s.Rp - 4.0, length(cometOnScreen)) : 0.0);

    // Fade the wash before the sky's bottom edge.
    float fade = ss(viewport.y, viewport.y - comet.z, frag.y);
    floorCol *= fade;
    floorA *= fade;

    float3 outCol = floorCol;
    float outA = floorA;

    // ---- The ring: solid band, ray traced as a slab between two parallel planes.
    float zTop = -(s.N.x * d.x + s.N.y * d.y) / s.N.z;
    float3 Ptop = float3(d.x, d.y, zTop);
    float a = dot(Ptop, s.U), b = dot(Ptop, s.W);
    float r = max(length(float2(a, b)), 0.001);
    float ca = a / r, sa = b / r;
    float sr = sqrt(ca * ca + s.aspect * s.aspect * sa * sa);
    float st = sqrt(sa * sa + s.aspect * s.aspect * ca * ca);
    float rpx = px / max(sr, 0.05);
    float tpx = px / max(st, 0.05);
    float outerOrbit = ringInfo.w;
    float innerOrbit = s.R - (outerOrbit - s.R);
    // Most pixels are nowhere near the band or its lines. The widest a selected segment gets, its soft edge and
    // how far the slab's underside can stand off it in the plane bound where any of it can be seen.
    float bandReach = s.hw * 1.6 + rpx + 12.0;
    bool nearBand = abs(r - s.R) < bandReach;
    bool nearOrbit = abs(r - outerOrbit) < 0.8 + rpx || abs(r - innerOrbit) < 0.65 + rpx;
    float theta = (nearBand || nearOrbit) ? atan(b, a) : 0.0;

    float3 ringCol = float3(0.0);
    float ringA = 0.0;
    float ringZ = zTop;

    float reveal = ring.w;
    float ringStart = ringInfo.x;
    float revealAngle = reveal * TAU;
    float revealOff = angleFrom(theta, ringStart);
    float revealMask = reveal >= 1.0 ? 1.0 : ss(tpx / r, -tpx / r, revealOff - revealAngle);
    float dimming = 1.0;

    // The faint orbit lines, engraved in the plane.
    float dash = ss(0.32, 0.36, fract(theta * outerOrbit / 13.0));
    float orbitOuter = ss(0.8 + rpx, 0.8 - rpx, abs(r - outerOrbit)) * dash * 0.16;
    float orbitInner = ss(0.65 + rpx, 0.65 - rpx, abs(r - innerOrbit)) * 0.09;
    float orbitA = max(orbitOuter, orbitInner) * sat(0.5 + 0.5 * sa + 0.45);
    float3 orbitC = mix(float3(0.23, 0.05, 0.08), float3(1.0), s.dark);

    int segCount = int(ringInfo.y);

    if (segCount == 0) {
        // The bare track: a porcelain groove.
        float cover = ss(s.hw + rpx, s.hw - rpx, abs(r - s.R));
        ringCol = mix(float3(0.95, 0.90, 0.88), float3(0.20, 0.18, 0.22), s.dark);
        ringCol = ringCol * (0.70 + 0.30 * ss(-1.0, 1.0, (r - s.R) / s.hw));
        ringA = cover * ringInfo.z;
        ringCol = toSRGB(ringCol);
    } else if (nearBand) {
        RingLook look = ringLook(theta);
        if (look.found > 0.5) {
            float hw = s.hw * look.widthScale;
            float off = angleFrom(theta, look.start);
            float toEnd = min(off, look.sweep - off);
            float angular = ss(-tpx / r, tpx / r, toEnd);
            float cover = ss(hw + rpx, hw - rpx, abs(r - s.R)) * angular * revealMask;
            if (cover > 0.0) {
                float3 rd = s.U * ca + s.W * sa;
                float3 td = -s.U * sa + s.W * ca;
                float e = (r - s.R) / hw;
                float bevelR = ss(0.42, 1.0, abs(e)) * (e > 0.0 ? 1.0 : -1.0);
                float bevelT = ss(2.6, 0.0, toEnd * s.R) * (off < look.sweep * 0.5 ? -1.0 : 1.0);
                float3 n = normalize(s.N + rd * bevelR * 1.15 + td * bevelT * 0.8);
                float3 lit = shadeBand(Ptop, n, look.color, rd, look.lift, s);

                // The comet's tail and head run along the band.
                if (s.cometPower > 0.0) {
                    float behind = angleFrom(cometAngle, theta);
                    float tail = exp(-behind / 0.30) * (behind < 3.0 ? 1.0 : 0.0);
                    lit += mix(look.color, float3(1.0), 0.40) * tail * 0.55 * s.cometPower * ss(1.0, 0.25, abs(e));
                }

                // Towards the far side the air takes a little contrast away.
                float far = ss(0.0, -s.R, zTop * 0.95);
                lit = mix(lit, mix(float3(0.82, 0.70, 0.68), float3(0.05, 0.04, 0.06), s.dark), far * 0.22);

                ringCol = toSRGB(aces(lit * 1.02));
            }
            ringA = cover * look.strength;
            dimming = look.strength;
        }
    }

    // The band's walls: what shows between the top plane's edge and the bottom plane's.
    float zBot = (-s.th - s.N.x * d.x - s.N.y * d.y) / s.N.z;
    float3 Pbot = float3(d.x, d.y, zBot);
    float a2 = dot(Pbot, s.U), b2 = dot(Pbot, s.W);
    float r2 = max(length(float2(a2, b2)), 0.001);
    if (segCount > 0 && abs(r2 - s.R) < bandReach) {
        float theta2 = atan(b2, a2);
        RingLook look2 = ringLook(theta2);
        if (look2.found > 0.5) {
            float hw = s.hw * look2.widthScale;
            float off2 = angleFrom(theta2, look2.start);
            float toEnd2 = min(off2, look2.sweep - off2);
            float angular2 = ss(-tpx / r2, tpx / r2, toEnd2);
            float revealOff2 = angleFrom(theta2, ringStart);
            float revealMask2 = reveal >= 1.0 ? 1.0 : ss(tpx / r2, -tpx / r2, revealOff2 - revealAngle);
            float cover2 = ss(hw + rpx, hw - rpx, abs(r2 - s.R)) * angular2 * revealMask2;
            float wallA = cover2 * (1.0 - ringA);
            if (wallA > 0.0) {
                float wallSide = r > s.R ? 1.0 : -1.0;
                float3 wallNormal = normalize((s.U * (a2 / r2) + s.W * (b2 / r2)) * wallSide);
                float3 wallLit = shadeBand(Pbot, wallNormal, look2.color * 0.82, wallNormal, 0.0, s);
                float3 wallSRGB = toSRGB(aces(wallLit));
                // "Over" with the top face in front of the wall.
                ringCol = ringCol * ringA + wallSRGB * wallA;
                float total = ringA + wallA;
                if (total > 0.0001) ringCol /= total;
                ringA = total;
            }
            dimming = ringA > 0.0001 ? mix(look2.strength, dimming, sat(cover2 < 0.5 ? 1.0 : 0.5)) : dimming;
        }
    }
    ringA *= dimming;

    // The orbit lines sit under the band in the same plane.
    if (orbitA > 0.0) {
        float3 mixed = ringCol * ringA + orbitC * orbitA * (1.0 - ringA);
        float total = ringA + orbitA * (1.0 - ringA);
        if (total > 0.0001) ringCol = mixed / total;
        ringA = total;
    }

    // ---- The planet.
    float sphereA = ss(s.Rp + px, s.Rp - px, dist);
    float3 sphereCol = float3(0.0);
    float zSphere = -1000000.0;
    if (dist < s.Rp + px * 2.0) {
        float2 onDisc = dist > s.Rp - 0.05 ? d * (s.Rp - 0.05) / dist : d;
        float z = sqrt(max(s.Rp * s.Rp - dot(onDisc, onDisc), 0.0));
        zSphere = z;
        float3 Psph = float3(onDisc, z);
        float3 n = Psph / s.Rp;

        // The ball turns a little with the phone, so the mark slides over its surface.
        float yaw = tilt.x * 0.20;
        float pitch = tilt.y * 0.15;
        float3 local = float3(n.x * cos(yaw) + n.z * sin(yaw), n.y, -n.x * sin(yaw) + n.z * cos(yaw));
        local = float3(local.x, local.y * cos(pitch) + local.z * sin(pitch), -local.y * sin(pitch) + local.z * cos(pitch));
        float inlay = 0.0;
        float seat = 0.0;
        float3 nCoat = n;
        float3 nBase = n;
        if (local.z > 0.0) {
            float2 uv = local.xy * 0.5 + 0.5;
            float step_ = 3.6 / 512.0;
            inlay = markAt(uv);
            float mx = markAt(uv + float2(step_, 0.0)) - markAt(uv - float2(step_, 0.0));
            float my = markAt(uv + float2(0.0, step_)) - markAt(uv - float2(0.0, step_));
            // Raised enamel: its edge catches the light; the clear coat above stays smooth but for a hint.
            nBase = normalize(n - float3(mx, my, 0.0) * 0.85);
            nCoat = normalize(n - float3(mx, my, 0.0) * 0.16);
            float wide = 8.0 / 512.0;
            seat = (markAt(uv + float2(wide, wide)) + markAt(uv - float2(wide, wide))
                  + markAt(uv + float2(wide, -wide)) + markAt(uv + float2(-wide, wide))) * 0.25;
        }

        float3 lacquer = float3(0.70, 0.012, 0.020);
        float3 enamel = float3(0.93, 0.92, 0.90);
        float3 albedo = mix(lacquer * (1.0 - 0.42 * seat), enamel, inlay);

        // Soft terminator: light that wraps through the lacquer picks up its colour.
        float nl = dot(nBase, s.K);
        float wrap = sat((nl + 0.30) / 1.30);
        float direct = wrap * wrap * (3.0 - 2.0 * wrap);

        // The ring shadows the ball where it stands between ball and light.
        float toPlaneK = -dot(s.N, Psph) / dot(s.N, s.K);
        float ringShade = 1.0;
        if (toPlaneK > 0.0 && segCount > 0) {
            float3 Hit = Psph + s.K * toPlaneK;
            float hr = length(float2(dot(Hit, s.U), dot(Hit, s.W)));
            ringShade = 1.0 - 0.55 * ss(s.hw + 5.0, s.hw - 2.0, abs(hr - s.R));
        }

        float hemi = 0.5 - 0.5 * n.y;
        float3 ambient = mix(mix(float3(0.66, 0.40, 0.36), float3(0.84, 0.80, 0.78), hemi),
                             mix(float3(0.14, 0.05, 0.06), float3(0.12, 0.12, 0.17), hemi), s.dark);

        // One bounce from the ring: each sample lights the ball like a small coloured area light.
        float3 ringGlow = float3(0.0);
        for (int i = 0; i < 8; i++) {
            float ang = (float(i) + 0.5) * TAU / 8.0;
            float3 probe = (s.U * cos(ang) + s.W * sin(ang)) * s.R;
            float3 L = probe - Psph;
            float d2 = dot(L, L);
            float ndl = sat(dot(n, L / sqrt(d2)) * 0.8 + 0.2);
            ringGlow += bounce[i].xyz * ndl * 5200.0 / d2;
        }

        float3 cometLight = float3(0.0);
        float cometSpec = 0.0;
        if (s.cometPower > 0.0) {
            float3 toComet = s.cometPos - Psph;
            float dc2 = dot(toComet, toComet);
            float3 Lc = toComet / sqrt(dc2);
            float visible = sat(dot(n, Lc) + 0.12);
            cometLight = float3(1.0, 0.94, 0.86) * s.cometPower * visible * 14000.0 / (dc2 + 900.0);
            float3 Hc = normalize(Lc + V);
            cometSpec = pow(sat(dot(nCoat, Hc)), 1400.0) * 14.0 * s.cometPower * step(0.0, dot(n, Lc));
        }

        float3 emissive = albedo * float3(0.06 + s.bloom * 0.55, 0.0, 0.0) * (1.0 - inlay);
        float3 diffuse = albedo * (float3(1.16, 1.11, 1.06) * direct * ringShade + ambient * mix(0.15, 0.90, inlay) + ringGlow * 0.50 + cometLight) + emissive;

        // The clear coat.
        float nv = sat(nCoat.z);
        float fresnel = 0.045 + 0.955 * pow(1.0 - nv, 5.0);
        float3 D = reflect(-V, nCoat);
        float3 mirror = room(D, s);

        // The ring in the coat: ray from the ball to the ring plane.
        float toRing = -dot(s.N, Psph) / dot(s.N, D);
        if (toRing > 0.0) {
            float3 Hit = Psph + D * toRing;
            float ha = dot(Hit, s.U), hb = dot(Hit, s.W);
            float hr = length(float2(ha, hb));
            float hTheta = atan(hb, ha);
            RingLook seen = ringLook(hTheta);
            float cover = ss(s.hw * seen.widthScale + 5.0, s.hw * seen.widthScale - 5.0, abs(hr - s.R)) * seen.found;
            if (reveal < 1.0) cover *= step(angleFrom(hTheta, ringStart), revealAngle);
            mirror = mix(mirror, seen.color * 1.05 + 0.02, cover * seen.strength);
        }

        float rimSide = sat(dot(normalize(n.xy + float2(0.0001)), normalize(float2(0.72, 0.70))));
        float rim = pow(1.0 - n.z, 3.2) * rimSide * rimSide;
        float3 rimLight = mix(float3(1.0, 0.56, 0.50), float3(0.36, 0.72, 0.86), s.dark) * rim * 0.95;
        float3 lit = diffuse * (1.0 - fresnel) + mirror * fresnel + rimLight * (1.0 - inlay * 0.5)
                   + float3(1.0, 0.95, 0.9) * cometSpec;
        sphereCol = toSRGB(aces(lit));
    }

    // ---- Depth order: back of the ring, ball, front of the ring.
    float3 glow = float3(0.0);
    float glowA = 0.0;
    if (s.cometPower > 0.0) {
        float hidden = behindPlanet;
        float core = ss(2.9, 1.5, cometDist);
        float halo = 1.0 / (1.0 + pow(cometDist / 5.5, 2.0)) * 0.72;
        glowA = sat(max(core, halo * ss(40.0, 4.0, cometDist))) * (1.0 - hidden) * s.cometPower;
        glow = mix(float3(1.0, 0.82, 0.74), float3(1.0, 1.0, 1.0), core);
    }

    if (ringZ > zSphere) {
        outCol = sphereCol * sphereA + outCol * (1.0 - sphereA);
        outA = sphereA + outA * (1.0 - sphereA);
        outCol = ringCol * ringA + outCol * (1.0 - ringA);
        outA = ringA + outA * (1.0 - ringA);
    } else {
        outCol = ringCol * ringA + outCol * (1.0 - ringA);
        outA = ringA + outA * (1.0 - ringA);
        outCol = sphereCol * sphereA + outCol * (1.0 - sphereA);
        outA = sphereA + outA * (1.0 - sphereA);
    }
    outCol = glow * glowA + outCol * (1.0 - glowA);
    outA = glowA + outA * (1.0 - glowA);

    // A grain below one code value keeps the long gradients from banding.
    float grain = (hash21(fragCoord) - 0.5) / 255.0 * 1.5;
    outCol = clamp(outCol + grain * outA, 0.0, 1.0);
    return half4(float4(outCol, outA));
}
"""
