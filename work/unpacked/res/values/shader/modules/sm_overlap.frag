#if !defined OVERLAP_FUNC_ID
#error not define symbol: OVERLAP_FUNC_ID

#elif OVERLAP_FUNC_ID == -1 // ILLEGAL
vec4 overlapColor(vec3 C, vec3 Cs, float As, vec3 Cd, float Ad) {
    vec4 ret;
    ret.rgb = vec3(1.0, 0.0, 1.0);
    ret.a = As;
    return ret;
}

#elif OVERLAP_FUNC_ID == 1000 // ADD
vec4 overlapColor(vec3 C, vec3 Cs, float As, vec3 Cd, float Ad) {
    vec4 ret;
    ret.rgb = Cs * As + Cd * Ad;
    ret.a = Ad;
    return ret;
}

#elif OVERLAP_FUNC_ID == 2000 // MULTIPLY
vec4 overlapColor(vec3 C, vec3 Cs, float As, vec3 Cd, float Ad) {
    vec4 res;
    res.rgb = Cs * As * Cd * Ad + (1.0 - As) * Cd * Ad;
    res.a = Ad;
    return res;
}

#else
vec4 _overlap_rgba(vec3 C, vec3 Cs, vec3 Cd, vec3 p) {
    vec4 res;
    res.rgb = C * p.x + Cs * p.y + Cd * p.z;
    res.a = p.x + p.y + p.z;

    return res;
}

#   if OVERLAP_FUNC_ID == 0 // OVER
vec4 overlapColor(vec3 C, vec3 Cs, float As, vec3 Cd, float Ad) {
    vec3 P = vec3(As * Ad, As * (1.0 - Ad), Ad * (1.0 - As));
    return clamp(_overlap_rgba(C, Cs, Cd, P), 0.0, 1.0);
}

#   elif OVERLAP_FUNC_ID == 2 // ATOP
vec4 overlapColor(vec3 C, vec3 Cs, float As, vec3 Cd, float Ad) {
    vec3 P = vec3(As * Ad, 0, Ad * (1.0 - As));
    return clamp(_overlap_rgba(C, Cs, Cd, P), 0.0, 1.0);
}

#   elif OVERLAP_FUNC_ID == 3 // DST_ATOP
vec4 overlapColor(vec3 C, vec3 Cs, float As, vec3 Cd, float Ad) {
    vec3 P = vec3(As * Ad, As * (1.0 - Ad), 0.0);
    return clamp(_overlap_rgba(C, Cs, Cd, P), 0.0, 1.0);
}

#   elif OVERLAP_FUNC_ID == 4 // OUT
vec4 overlapColor(vec3 C, vec3 Cs, float As, vec3 Cd, float Ad) {
    vec3 P = vec3(0.0, 0.0, Ad * (1.0 - As));
    return clamp(_overlap_rgba(C, Cs, Cd, P), 0.0, 1.0);
}

#   elif OVERLAP_FUNC_ID == 6 // IN
vec4 overlapColor(vec3 C, vec3 Cs, float As, vec3 Cd, float Ad) {
    vec3 P = vec3(As * Ad, 0.0, 0.0);
    return clamp(_overlap_rgba(C, Cs, Cd, P), 0.0, 1.0);
}

#   elif OVERLAP_FUNC_ID == 10 // CONJOINT
vec4 overlapColor(vec3 C, vec3 Cs, float As, vec3 Cd, float Ad) {
    vec3 P = vec3(min(As, Ad), max(As - Ad, 0.0), max(Ad - As, 0.0));
    return clamp(_overlap_rgba(C, Cs, Cd, P), 0.0, 1.0);
}

#   elif OVERLAP_FUNC_ID == 12 // DISJOINT
vec4 overlapColor(vec3 C, vec3 Cs, float As, vec3 Cd, float Ad) {
    vec3 P = vec3(max(As + Ad - 1.0, 0.0), min(As, 1.0 - Ad), min(Ad, 1.0 - As));
    return clamp(_overlap_rgba(C, Cs, Cd, P), 0.0, 1.0);
}

#   else
#   error not supported blend function
#   endif
#endif
