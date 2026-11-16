package com.floatplay.overlay;

import java.util.Locale;
import java.util.Set;

/** Exact native-control matching; never match page content or Close all tabs. */
final class BrowserRules {
    private static final Set<String> PACKAGES = Set.of("com.android.chrome", "org.mozilla.firefox");
    private static final Set<String> TAB_IDS = Set.of("tab_switcher_button", "tab_counter", "tabs_button", "counter_box",
            "mozac_browser_toolbar_tab_counter", "ADDRESSBAR_TABS_COUNTER", "TabCounterTestTags.tabCounter");
    static String normalize(CharSequence value) {
        return value == null ? "" : value.toString().strip().toLowerCase(Locale.ROOT);
    }
    static boolean supported(CharSequence pkg) { return pkg != null && PACKAGES.contains(pkg.toString()); }
    static boolean tabButton(String id, CharSequence description) {
        String leaf = id == null ? "" : id.substring(id.lastIndexOf('/') + 1);
        String label = normalize(description);
        return TAB_IDS.contains(leaf) || label.equals("the tab counter toolbar button.")
                || label.matches("(?:open|switch) tabs(?:[.,:].*)?")
                || label.matches("[0-9]+ open tabs(?:[.,:].*)?")
                || label.matches("(?:non-private|private) tabs open: [0-9]+\\. tap to switch tabs\\.");
    }
    static boolean closeLabel(CharSequence value) {
        String label = normalize(value);
        return label.equals("close tab") || label.equals("close current tab");
    }
    static boolean newTabLabel(CharSequence value) {
        return normalize(value).equals("new tab");
    }
    static boolean addressBar(CharSequence pkg, String id) {
        if (pkg == null || id == null) return false;
        if (pkg.toString().equals("org.mozilla.firefox")) {
            return id.equals("ADDRESSBAR_URL_BOX") || id.equals("org.mozilla.firefox:id/mozac_browser_toolbar_url_view");
        }
        return pkg.toString().equals("com.android.chrome")
                && (id.equals("com.android.chrome:id/url_bar") || id.equals("com.android.chrome:id/location_bar"));
    }
}
