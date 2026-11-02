"""复刻 FlClash 的 seededColorScheme（vivid / Variant.content），输出 Compose ColorScheme 片段。

原始实现见 FlClash lib/ui/color.dart：
- primary 不用 Material 的 tone 40，而是直接取 seed（浅色）或 seed 提亮 3 个 tone（深色）
- surface 系列保留 Material 的 tone，但 chroma 截到 3（浅色 surface/surfaceBright 截到 1.5）
- primaryContainer 的 chroma 截到 48
- 深色 surface 自定 tone：6 / 15 / 21 / 27
"""
from materialyoucolor.hct import Hct
from materialyoucolor.scheme.scheme_content import SchemeContent
from materialyoucolor.dynamiccolor.material_dynamic_colors import MaterialDynamicColors as MDC

SEED_ARGB = 0xFF007AFF                 # FlClash defaultPrimaryColor
SURFACE_CHROMA = 3.0
PAPER_CHROMA = 1.5
PC_CHROMA = 48.0
OUTLINE_VARIANT_CHROMA = 4.0

# accent（primary）规则
MIN_ACCENT_TONE, MAX_ACCENT_TONE = 35.0, 60.0
DARK_ACCENT_LIFT = 3.0
MIN_DARK_ACCENT_TONE, MAX_DARK_ACCENT_TONE = 50.0, 75.0

# 深色 surface 的自定 tone（FlClash .agents/surfaces.md: 6 / 15 / 21 / 27）
DARK_TONES = {
    "surface": 6.0,
    "surfaceDim": 6.0,
    "surfaceBright": 24.0,
    "surfaceContainerLowest": 4.0,
    "surfaceContainerLow": 10.0,
    "surfaceContainer": 15.0,
    "surfaceContainerHigh": 21.0,
    "surfaceContainerHighest": 27.0,
}
LIGHT_TONES = {
    "surface": 98.0,
    "surfaceDim": 87.0,
    "surfaceBright": 98.0,
    "surfaceContainerLowest": 100.0,
    "surfaceContainerLow": 96.0,
    "surfaceContainer": 94.0,
    "surfaceContainerHigh": 92.0,
    "surfaceContainerHighest": 90.0,
}
# 浅色里这两个角色 chroma 再降一档（纸感）
PAPER_ROLES = {"surface", "surfaceBright"}


def clamp(v, lo, hi):
    return max(lo, min(hi, v))


def build(dark: bool):
    src = Hct.from_int(SEED_ARGB)
    s = SchemeContent(src, dark, 0.0)
    out = {}

    def role(name):
        return getattr(MDC, name).get_argb(s)

    def neutral(name, max_chroma, tone=None):
        r = getattr(MDC, name)
        pal = r.palette(s)
        t = tone if tone is not None else r.get_tone(s)
        return Hct.from_hct(pal.hue, min(pal.chroma, max_chroma), t).to_int()

    # ---- primary：直接取 seed ----
    tone = (clamp(src.tone + DARK_ACCENT_LIFT, MIN_DARK_ACCENT_TONE, MAX_DARK_ACCENT_TONE)
            if dark else clamp(src.tone, MIN_ACCENT_TONE, MAX_ACCENT_TONE))
    if abs(tone - src.tone) < 1e-9:
        primary = SEED_ARGB
    else:
        primary = Hct.from_hct(src.hue, src.chroma, tone).to_int()
    on_primary = 0xFFFFFFFF if tone <= MAX_ACCENT_TONE else \
        Hct.from_hct(src.hue, src.chroma, 10.0).to_int()

    out["primary"] = primary
    out["onPrimary"] = on_primary
    out["surfaceTint"] = primary
    out["inversePrimary"] = role("inversePrimary")

    # ---- primaryContainer：chroma 截到 48，tone 30/90 ----
    pc_pal = MDC.primary.palette(s)
    pc = Hct.from_hct(pc_pal.hue, min(pc_pal.chroma, PC_CHROMA), 30.0 if dark else 90.0).to_int()
    oc = Hct.from_hct(pc_pal.hue, min(pc_pal.chroma, PC_CHROMA), 90.0 if dark else 30.0).to_int()
    out["primaryContainer"] = pc
    out["onPrimaryContainer"] = oc

    # ---- secondary / tertiary / error 用 Material 原值 ----
    for k, n in [("secondary", "secondary"), ("onSecondary", "onSecondary"),
                 ("secondaryContainer", "secondaryContainer"),
                 ("onSecondaryContainer", "onSecondaryContainer"),
                 ("tertiary", "tertiary"), ("onTertiary", "onTertiary"),
                 ("tertiaryContainer", "tertiaryContainer"),
                 ("onTertiaryContainer", "onTertiaryContainer"),
                 ("error", "error"), ("onError", "onError"),
                 ("errorContainer", "errorContainer"),
                 ("onErrorContainer", "onErrorContainer"),
                 ("inverseSurface", "inverseSurface"),
                 ("inverseOnSurface", "inverseOnSurface")]:
        out[k] = role(n)

    # ---- surface 系列：tone 用自定值，chroma 截断 ----
    for name, tone_v in (DARK_TONES if dark else LIGHT_TONES).items():
        cap = PAPER_CHROMA if (not dark and name in PAPER_ROLES) else SURFACE_CHROMA
        out[name] = neutral(name, cap, tone_v)

    out["onSurface"] = neutral("onSurface", SURFACE_CHROMA)
    out["onSurfaceVariant"] = neutral("onSurfaceVariant", SURFACE_CHROMA)
    out["surfaceVariant"] = neutral("surfaceVariant", SURFACE_CHROMA)

    # ---- outline ----
    out["outline"] = neutral("outline", SURFACE_CHROMA)
    out["outlineVariant"] = neutral("outlineVariant", OUTLINE_VARIANT_CHROMA)

    out["scrim"] = 0xFF000000
    return out, src


def kt(v):
    return "Color(0x%08X)" % (v & 0xFFFFFFFF)


for dark in (False, True):
    vals, src = build(dark)
    tag = "Dark" if dark else "Light"
    print("// ---- %s ----  (seed #007AFF: hue %.1f chroma %.1f tone %.1f)" %
          (tag, src.hue, src.chroma, src.tone))
    order = ["primary", "onPrimary", "primaryContainer", "onPrimaryContainer",
             "secondary", "onSecondary", "secondaryContainer", "onSecondaryContainer",
             "tertiary", "onTertiary", "tertiaryContainer", "onTertiaryContainer",
             "error", "onError", "errorContainer", "onErrorContainer",
             "surface", "onSurface", "surfaceVariant", "onSurfaceVariant",
             "surfaceTint", "inverseSurface", "inverseOnSurface", "inversePrimary",
             "surfaceDim", "surfaceBright", "surfaceContainerLowest",
             "surfaceContainerLow", "surfaceContainer", "surfaceContainerHigh",
             "surfaceContainerHighest", "outline", "outlineVariant", "scrim"]
    for k in order:
        print("        %s = %s," % (k, kt(vals[k])))
    print()
