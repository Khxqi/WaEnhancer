package io.github.khxqi.airpodsxaplfix;

/**
 * Pure rewrite logic kept separate so it can be unit-tested without Android/Xposed.
 */
final class XaplRewriter {
    static final String BROKEN_RESPONSE = "+XAPL=iPhone,2";
    static final String FIXED_RESPONSE = "+XAPL=iPhone,0";

    private XaplRewriter() {}

    /**
     * Rewrites only the exact XAPL battery-feature response. A trailing CR/LF is
     * preserved because vendor stacks can differ in how they terminate AT data.
     * Everything else is returned byte-for-byte unchanged at the Java String level.
     */
    static String rewriteIfNeeded(String value) {
        if (value == null) return null;

        int coreEnd = value.length();
        while (coreEnd > 0) {
            char c = value.charAt(coreEnd - 1);
            if (c == '\r' || c == '\n') {
                coreEnd--;
            } else {
                break;
            }
        }

        if (coreEnd != BROKEN_RESPONSE.length()) return value;
        if (!value.regionMatches(0, BROKEN_RESPONSE, 0, BROKEN_RESPONSE.length())) return value;

        return FIXED_RESPONSE + value.substring(coreEnd);
    }
}
