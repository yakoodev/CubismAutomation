#if !defined BLEND_FUNC_ID
#error not defined symbol: BLEND_FUNC_ID

#elif BLEND_FUNC_ID == 0 // NORMAL
vec3 blendColor(vec3 Cs, vec3 Cd) {
    return Cs;
}

#elif BLEND_FUNC_ID == 1 // ADD
vec3 blendColor(vec3 Cs, vec3 Cd) {
    return vec3(0.0);
}

#elif BLEND_FUNC_ID == 2 // MULTIPLY
vec3 blendColor(vec3 Cs, vec3 Cd) {
    return vec3(0.0);
}

#elif BLEND_FUNC_ID == 3 // ADD_R2_TSL
vec3 blendColor(vec3 Cs, vec3 Cd) {
    return min(Cs + Cd, 1.0);
}

#elif BLEND_FUNC_ID == 4 // ADD_R2
vec3 blendColor(vec3 Cs, vec3 Cd) {
    return Cs + Cd;
}

#elif BLEND_FUNC_ID == 6 // DARKEN
vec3 blendColor(vec3 Cs, vec3 Cd) {
    return min(Cs, Cd);
}

#elif BLEND_FUNC_ID == 8 // MULTIPLY_R2
vec3 blendColor(vec3 Cs, vec3 Cd) {
    return Cs * Cd;
}

#elif BLEND_FUNC_ID == 9 // COLORBURN_TSL
float _colorBurn_tsl(float Cs, float Cd){
    // 1.0                          , if Cd == 1.0
    // 0                            , if Cd < 1.0 && Cs == 0
    // 1-min(1.0,(1.0-Cd)/Cs)       , if Cd < 1.0 && Cs > 0
    return abs(Cd - 1.0) < 0.000001 ? 1.0 : (abs(Cs) < 0.000001 ? 0.0 : 1.0 - min(1.0, (1.0 - Cd) / Cs)) ;
}

vec3 blendColor(vec3 Cs, vec3 Cd) {
    return vec3(
        _colorBurn_tsl(Cs.r, Cd.r),
        _colorBurn_tsl(Cs.g, Cd.g),
        _colorBurn_tsl(Cs.b, Cd.b)
    );
}

#elif BLEND_FUNC_ID == 10 // COLORBURN
vec3 blendColor(vec3 Cs, vec3 Cd) {
    return vec3(0.0); // not supported
}

#elif BLEND_FUNC_ID == 11 // LINEARBURN_TSL
vec3 blendColor(vec3 Cs, vec3 Cd) {
    return max(vec3(0.0), Cs + Cd - 1.0);
}

#elif BLEND_FUNC_ID == 12 // LINEARBURN
vec3 blendColor(vec3 Cs, vec3 Cd) {
    return vec3(0.0); // not supported
}

#elif BLEND_FUNC_ID == 14 || BLEND_FUNC_ID == 22
float _sumRGB(vec3 rgbC){
    return rgbC.r + rgbC.g + rgbC.b;
}

#   if BLEND_FUNC_ID == 14 // DARKERCOLOR
vec3 blendColor(vec3 Cs, vec3 Cd) {
    return _sumRGB(Cs) < _sumRGB(Cd) ? Cs : Cd ;
}
#   else // LIGHTERCOLOR
vec3 blendColor(vec3 Cs, vec3 Cd) {
    return _sumRGB(Cd) < _sumRGB(Cs) ? Cs : Cd ;
}
#   endif

#elif BLEND_FUNC_ID == 16 // LIGHTEN
vec3 blendColor(vec3 Cs, vec3 Cd) {
    return max(Cs, Cd);
}

#elif BLEND_FUNC_ID == 18 // SCREEN
vec3 blendColor(vec3 Cs, vec3 Cd) {
    return Cs + Cd - Cs*Cd;
}

#elif BLEND_FUNC_ID == 19 // COLORDODGE_TSL
float _colorDodge_tsl(float Cs, float Cd){
    // 0                , if Cd <= 0
    // min(1,Cd/(1-Cs)) , if Cd >= 0 && Cs < 1
    // 1                , if Cd >= 0 && Cs == 1
    return Cd > 0.0 ? (Cs >= 1.0 ? 1.0 : min(1.0, Cd / (1.0 - Cs))) : 0.0 ;
}

vec3 blendColor(vec3 Cs, vec3 Cd) {
    return vec3(
        _colorDodge_tsl(Cs.r, Cd.r),
        _colorDodge_tsl(Cs.g, Cd.g),
        _colorDodge_tsl(Cs.b, Cd.b)
    );
}

#elif BLEND_FUNC_ID == 20 // COLORDODGE
vec3 blendColor(vec3 Cs, vec3 Cd) {
    return vec3(0.0); // not supported
}

#elif BLEND_FUNC_ID == 24 // OVERLAY
float _overlay(float Cs, float Cd) {
    // 2*Cs*Cd, if Cd < 0.5
    // 1-2*(1-Cs)*(1-Cd), otherwise

    float mul = 2.0*Cs*Cd ;
    float scr = 1.0 - 2.0*(1.0 - Cs)*(1.0 - Cd) ;
    return Cd < 0.5 ? mul : scr ;
}

vec3 blendColor(vec3 Cs, vec3 Cd) {
    return vec3(
        _overlay(Cs.r, Cd.r),
        _overlay(Cs.g, Cd.g),
        _overlay(Cs.b, Cd.b)
    );
}

#elif BLEND_FUNC_ID == 26 // SOFTLIGHT
float _softLight(float Cs, float Cd){
    // val1=Cd-(1-2*Cs)*Cd*(1-Cd)               , if Cs <= 0.5
    // val2=Cd+(2*Cs-1)*Cd*((16*Cd-12)*Cd+3)    , if Cs > 0.5 && Cd <= 0.25
    // val3=Cd+(2*Cs-1)*(sqrt(Cd)-Cd)           , if Cs > 0.5 && Cd > 0.25

    float val1 = Cd - (1.0 - 2.0 * Cs) * Cd * (1.0 - Cd) ;
    float val2 = Cd + (2.0 * Cs - 1.0) * Cd * ((16.0 * Cd - 12.0) * Cd + 3.0) ;
    float val3 = Cd + (2.0 * Cs - 1.0) * (sqrt(Cd) - Cd) ;
    return Cs <= 0.5 ? val1 : (Cd <= 0.25 ? val2 : val3) ;
}

vec3 blendColor(vec3 Cs, vec3 Cd) {
    return vec3(
        _softLight(Cs.r, Cd.r),
        _softLight(Cs.g, Cd.g),
        _softLight(Cs.b, Cd.b)
    );
}

#elif BLEND_FUNC_ID == 28 // HARDLIGHT
float _hardLight(float Cs, float Cd){
    // 2.0*Cs*Cd          , if Cs < 0.5
    // 1.0-2.0*(1.0-Cs)*(1.0-Cd), otherwise

    float mul = 2.0*Cs*Cd ;
    float scr = 1.0 - 2.0*(1.0 - Cs)*(1.0 - Cd) ;
    return Cs < 0.5 ? mul : scr ;
}

vec3 blendColor(vec3 Cs, vec3 Cd) {
    return vec3(
        _hardLight(Cs.r, Cd.r),
        _hardLight(Cs.g, Cd.g),
        _hardLight(Cs.b, Cd.b)
    );
}

#elif BLEND_FUNC_ID == 29 // VIVIDLIGHT_TSL
float _vividLight_tsl(float Cs, float Cd){
    // 1.0 - (1.0 - Cd) / (2.0 * Cs) , if Cs < 0.5
    // Cd / (2.0 * (1.0 - Cs)) , otherwise

    float val1 = abs(Cs) < 0.000001 ? 0.0 : 1.0 - (1.0 - Cd) / (2.0 * Cs) ;
    float val2 = abs(Cs - 1.0) < 0.000001 ? 1.0 : Cd / (2.0 * (1.0 - Cs)) ;
    return Cs < 0.5 ? val1 : val2 ;
}

vec3 blendColor(vec3 Cs, vec3 Cd) {
    return vec3(
        _vividLight_tsl(Cs.r, Cd.r),
        _vividLight_tsl(Cs.g, Cd.g),
        _vividLight_tsl(Cs.b, Cd.b)
    );
}

#elif BLEND_FUNC_ID == 30 // VIVIDLIGHT
vec3 blendColor(vec3 Cs, vec3 Cd) {
    return vec3(0.0); // not supported
}

#elif BLEND_FUNC_ID == 31 // LINEARLIGHT_TSL
float _linearLight_tsl(float Cs, float Cd){
    // linearburn , if Cs < 0.5
    // lineardodge , otherwise

    float burn = max(0.0, 2.0 * Cs + Cd - 1.0) ;
    float dodge = min(1.0, 2.0 * (Cs - 0.5) + Cd) ;
    return Cs < 0.5 ? burn : dodge ;
}

vec3 blendColor(vec3 Cs, vec3 Cd) {
    return vec3(
        _linearLight_tsl(Cs.r, Cd.r),
        _linearLight_tsl(Cs.g, Cd.g),
        _linearLight_tsl(Cs.b, Cd.b)
    );
}

#elif BLEND_FUNC_ID == 32 // LINEARLIGHT
vec3 blendColor(vec3 Cs, vec3 Cd) {
    return vec3(0.0); // not supported
}

#elif BLEND_FUNC_ID == 34 // PINLIGHT
float _pinLight(float Cs, float Cd){
    // darken  , if Cs < 0.5
    // lighten , otherwise

    return Cs < 0.5 ? min(2.0*Cs, Cd) : max(2.0*(Cs - 0.5), Cd) ;
}

vec3 blendColor(vec3 Cs, vec3 Cd) {
    return vec3(
        _pinLight(Cs.r, Cd.r),
        _pinLight(Cs.g, Cd.g),
        _pinLight(Cs.b, Cd.b)
    );
}

#elif BLEND_FUNC_ID == 35 // HARDMIX_TSL
vec3 blendColor(vec3 Cs, vec3 Cd) {
    return floor(Cs + Cd);
}

#elif BLEND_FUNC_ID == 36 // HARDMIX
vec3 blendColor(vec3 Cs, vec3 Cd) {
    return vec3(0.0); // not supported
}

#elif BLEND_FUNC_ID == 37 // DIFFERENCE_TSL
vec3 blendColor(vec3 Cs, vec3 Cd) {
    return abs(Cs - Cd);
}

#elif BLEND_FUNC_ID == 38 // DIFFERENCE
vec3 blendColor(vec3 Cs, vec3 Cd) {
    return vec3(0.0); // not supported
}

#elif BLEND_FUNC_ID == 40 // EXCLUSION
vec3 blendColor(vec3 Cs, vec3 Cd) {
    return Cs + Cd - 2.0f * Cs * Cd;
}

#elif BLEND_FUNC_ID == 42 // SUBTRACT
vec3 blendColor(vec3 Cs, vec3 Cd) {
    return max(vec3(0.0), Cd - Cs);
}

#elif BLEND_FUNC_ID == 44 // DIVIDE
float _divide(float Cs, float Cd){
    // 0.0              , if Cd == 0.0
    // 1.0              , if Cd > 0.0 && Cs == 0.0
    // min(1.0, Cd/Cs)  , if Cd > 0.0 && Cs > 0.0

    return abs(Cd) < 0.000001 ? 0.0 : (abs(Cs) < 0.000001 ? 1.0 : min(1.0, Cd / Cs)) ;
}

vec3 blendColor(vec3 Cs, vec3 Cd) {
    return vec3(
        _divide(Cs.r, Cd.r),
        _divide(Cs.g, Cd.g),
        _divide(Cs.b, Cd.b)
    );
}

#elif BLEND_FUNC_ID == 46 || BLEND_FUNC_ID == 48 || BLEND_FUNC_ID == 50 || BLEND_FUNC_ID == 52
// NTSC係数 http://www.brucelindbloom.com/index.html?WorkingSpaceInfo.html
// 小数点2桁までの方がphotoshopとのズレが少ない
//const float   rCoeff = 0.298839 ;
//const float   gCoeff = 0.586811 ;
//const float   bCoeff = 0.114350 ;
const float   rCoeff = 0.30 ;
const float   gCoeff = 0.59 ;
const float   bCoeff = 0.11 ;

float _getMax(vec3 rgbC){
    return max(rgbC.r, max(rgbC.g, rgbC.b)) ;
}

float _getMin(vec3 rgbC){
    return min(rgbC.r, min(rgbC.g, rgbC.b)) ;
}

// (MAX-MIN)を求める
float _getRange(vec3 rgbC){
    return max(rgbC.r, max(rgbC.g, rgbC.b)) - min(rgbC.r, min(rgbC.g, rgbC.b)) ;
}

// RGB_Color to HSY_Color 円錐モデル
// 定義ではHSLだがphotoshopに合わせてHSY色空間で評価する
// hueは使わないので計算しない
float _saturation(vec3 rgbC){
    return _getRange(rgbC) ;
}
float _luma(vec3 rgbC){
    return rCoeff * rgbC.r + gCoeff * rgbC.g + bCoeff * rgbC.b ;
}

// https://www.w3.org/TR/compositing-1/#blendingnonseparable
vec3 _clipColor(vec3 rgbC){
    float   luma = _luma(rgbC) ;
    float   maxv = _getMax(rgbC) ;
    float   minv = _getMin(rgbC) ;
    vec3    outputColor = rgbC ;

    outputColor = minv < 0.0 ? luma + (outputColor - luma) * luma / (luma - minv) : outputColor ;
    outputColor = maxv > 1.0 ? luma + (outputColor - luma) * (1.0 - luma) / (maxv - luma) : outputColor ;

    return outputColor ;
}

// https://www.w3.org/TR/compositing-1/#blendingnonseparable
vec3 _setLuma(vec3 rgbC, float luma){

    return _clipColor(rgbC + (luma - _luma(rgbC))) ;
}

// https://www.w3.org/TR/compositing-1/#blendingnonseparable
vec3 _setSaturation(vec3 rgbC, float saturation){
    float   maxv = _getMax(rgbC) ;
    float   minv = _getMin(rgbC) ;
    float   medv = rgbC.r + rgbC.g + rgbC.b - maxv - minv ;
    float   outputMax, outputMed, outputMin ;

    outputMax = minv < maxv ? saturation : 0.0 ;
    outputMed = minv < maxv ? (medv - minv) * saturation / (maxv - minv) : 0.0 ;
    outputMin = 0.0 ;

    if(rgbC.r == maxv){
        return rgbC.b < rgbC.g ? vec3(outputMax, outputMed, outputMin) : vec3(outputMax, outputMin, outputMed) ;
    }
    else if(rgbC.g == maxv){
        return rgbC.r < rgbC.b ? vec3(outputMin, outputMax, outputMed) : vec3(outputMed, outputMax, outputMin) ;
    }
    else{ // if(rgbC.b == maxv)
        return rgbC.g < rgbC.r ? vec3(outputMed, outputMin, outputMax) : vec3(outputMin, outputMed, outputMax) ;
    }
}

#   if BLEND_FUNC_ID == 46 // HSL_HUE
vec3 blendColor(vec3 Cs, vec3 Cd) {
    vec3 clampCs = clamp(Cs, 0.0, 1.0) ;
    vec3 clampCd = clamp(Cd, 0.0, 1.0) ;
    return _setLuma(_setSaturation(clampCs, _saturation(clampCd)), _luma(clampCd)) ;
}
#   elif BLEND_FUNC_ID == 48 // HSL_SATURATION
vec3 blendColor(vec3 Cs, vec3 Cd) {
    return _setLuma(_setSaturation(Cd, _saturation(Cs)), _luma(Cd)) ;
}
#   elif BLEND_FUNC_ID == 50 // HSL_COLOR
vec3 blendColor(vec3 Cs, vec3 Cd) {
    return _setLuma(Cs, _luma(Cd)) ;
}
#   else // HSL_LUMINOSITY
vec3 blendColor(vec3 Cs, vec3 Cd) {
    return _setLuma(Cd, _luma(Cs)) ;
}
#   endif

#else
#error not supported blend function
#endif
