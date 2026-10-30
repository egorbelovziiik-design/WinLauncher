package dev.axsesclient.common.theme;

public enum ColorBlindMode {
    NONE,
    PROTANOPIA,
    DEUTERANOPIA,
    TRITANOPIA;

    public int transform(int argb) {
        if (this == NONE) {
            return argb;
        }
        int a = (argb >>> 24) & 0xFF;
        float r = (argb >>> 16) & 0xFF;
        float g = (argb >>> 8) & 0xFF;
        float b = argb & 0xFF;

        float nr;
        float ng;
        float nb;
        switch (this) {
            case PROTANOPIA:
                nr = 0.567f * r + 0.433f * g;
                ng = 0.558f * r + 0.442f * g;
                nb = 0.242f * g + 0.758f * b;
                break;
            case DEUTERANOPIA:
                nr = 0.625f * r + 0.375f * g;
                ng = 0.700f * r + 0.300f * g;
                nb = 0.300f * g + 0.700f * b;
                break;
            case TRITANOPIA:
                nr = 0.950f * r + 0.050f * g;
                ng = 0.433f * g + 0.567f * b;
                nb = 0.475f * g + 0.525f * b;
                break;
            default:
                return argb;
        }
        int ir = Math.max(0, Math.min(255, Math.round(nr)));
        int ig = Math.max(0, Math.min(255, Math.round(ng)));
        int ib = Math.max(0, Math.min(255, Math.round(nb)));
        return (a << 24) | (ir << 16) | (ig << 8) | ib;
    }
}
