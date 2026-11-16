package com.tabubble.android;

public final class BrowserRulesCheck {
    private static int checks;
    private static void expect(boolean result) {
        checks++;
        if (!result) throw new AssertionError("Check " + checks + " failed");
    }
    public static void main(String[] args) {
        expect(BrowserRules.supported("com.android.chrome"));
        expect(BrowserRules.supported("org.mozilla.firefox"));
        expect(!BrowserRules.supported("com.example.browser"));
        expect(!BrowserRules.supported(null));
        expect(BrowserRules.tabButton("com.android.chrome:id/tab_switcher_button", null));
        expect(BrowserRules.tabButton(null, "The tab counter toolbar button."));
        expect(BrowserRules.tabButton(null, "Open tabs. 4 open tabs."));
        expect(BrowserRules.tabButton("ADDRESSBAR_TABS_COUNTER", null));
        expect(BrowserRules.tabButton("TabCounterTestTags.tabCounter", null));
        expect(BrowserRules.tabButton(null, "Non-private Tabs Open: 3. Tap to switch tabs."));
        expect(BrowserRules.tabButton(null, "Private Tabs Open: 2. Tap to switch tabs."));
        expect(!BrowserRules.tabButton("TabCounterTestTags.text3", null));
        expect(!BrowserRules.tabButton(null, "Non-private Tabs Open: 3. Close all tabs."));
        expect(!BrowserRules.tabButton(null, "Close all tabs"));
        expect(!BrowserRules.tabButton(null, "Open tabs offer"));
        expect(BrowserRules.closeLabel("Close tab"));
        expect(BrowserRules.closeLabel(" CLOSE CURRENT TAB "));
        expect(!BrowserRules.closeLabel("Close all tabs"));
        expect(!BrowserRules.closeLabel("Close other tabs"));
        expect(!BrowserRules.closeLabel("Close tab example.com"));
        expect(!BrowserRules.closeLabel(null));
        expect(BrowserRules.newTabLabel("New tab"));
        expect(!BrowserRules.newTabLabel("New tab group"));
        expect(BrowserRules.addressBar("org.mozilla.firefox", "ADDRESSBAR_URL_BOX"));
        expect(BrowserRules.addressBar("com.android.chrome", "com.android.chrome:id/url_bar"));
        expect(BrowserRules.addressBar("com.android.chrome", "com.android.chrome:id/location_bar"));
        expect(!BrowserRules.addressBar("com.android.chrome", "ADDRESSBAR_URL_BOX"));
        expect(!BrowserRules.addressBar("org.mozilla.firefox", "com.android.chrome:id/url_bar"));
        expect(!BrowserRules.addressBar("com.example.app", "com.android.chrome:id/url_bar"));
        expect(!BrowserRules.addressBar("org.mozilla.firefox", null));
        expect(!BrowserRules.addressBar("com.android.chrome", "url_bar"));
        System.out.println(checks + " browser control matching checks passed.");
    }
}
