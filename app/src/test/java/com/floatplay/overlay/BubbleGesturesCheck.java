package com.floatplay.overlay;

public final class BubbleGesturesCheck {
    private static int checks;
    private static void expect(boolean result) {
        checks++;
        if (!result) throw new AssertionError("Gesture check " + checks + " failed");
    }
    private static BubbleGestures fresh() { return new BubbleGestures(8, 40); }
    public static void main(String[] args) {
        BubbleGestures g = fresh();
        g.down(BubbleGestures.Zone.CIRCLE, 100, 100);
        expect(g.up(102, 100) == BubbleGestures.Result.CLOSE); // Immediate, with no confirmation timer.
        expect(g.up(102, 100) == BubbleGestures.Result.NONE);
        g.down(BubbleGestures.Zone.CIRCLE, 100, 100);
        expect(g.up(100, 100) == BubbleGestures.Result.CLOSE); // A second independent tap is still immediate.

        g.down(BubbleGestures.Zone.CIRCLE, 100, 100);
        g.move(20, 105);
        expect(g.isDragging());
        expect(g.up(20, 105) == BubbleGestures.Result.DRAGGED); // Circle left swipe moves; never switches.
        g.down(BubbleGestures.Zone.CIRCLE, 100, 100);
        expect(g.up(180, 95) == BubbleGestures.Result.DRAGGED);
        g.down(BubbleGestures.Zone.CIRCLE, 100, 100);
        expect(g.up(100, 200) == BubbleGestures.Result.DRAGGED);
        g.down(BubbleGestures.Zone.CIRCLE, 100, 100);
        g.move(200, 100); g.move(100, 100);
        expect(g.up(100, 100) == BubbleGestures.Result.DRAGGED); // Out and back must not close.

        g.down(BubbleGestures.Zone.STRIP, 100, 100);
        g.move(20, 105);
        expect(!g.isDragging());
        expect(g.up(20, 105) == BubbleGestures.Result.LEFT);
        expect(g.up(20, 105) == BubbleGestures.Result.NONE);
        g.down(BubbleGestures.Zone.STRIP, 100, 100);
        expect(g.up(180, 95) == BubbleGestures.Result.RIGHT);
        g.down(BubbleGestures.Zone.STRIP, 100, 100);
        expect(g.up(100, 100) == BubbleGestures.Result.NONE); // Rectangle taps never close.
        // The rectangle never moves the widget: vertical/diagonal drags are not DRAGGED.
        g.down(BubbleGestures.Zone.STRIP, 100, 100);
        expect(g.up(110, 200) == BubbleGestures.Result.NONE); // Swipe down: nothing.
        g.down(BubbleGestures.Zone.STRIP, 100, 100);
        g.move(110, 140);
        expect(!g.isDragging());
        expect(g.up(150, 150) == BubbleGestures.Result.NONE); // Diagonal down-right: nothing.
        g.down(BubbleGestures.Zone.STRIP, 100, 100);
        expect(g.up(120, 100) == BubbleGestures.Result.NONE);
        g.down(BubbleGestures.Zone.STRIP, 100, 100);
        g.move(200, 100); g.move(100, 100);
        expect(g.up(100, 100) == BubbleGestures.Result.NONE);

        // Swipe up opens the viewer.
        g.down(BubbleGestures.Zone.STRIP, 100, 200);
        g.move(103, 180);
        expect(!g.isDragging());
        expect(g.up(105, 100) == BubbleGestures.Result.UP);
        g.down(BubbleGestures.Zone.STRIP, 100, 200);
        expect(g.up(100, 160) == BubbleGestures.Result.UP); // Exactly the minimum distance.
        g.down(BubbleGestures.Zone.STRIP, 100, 200);
        expect(g.up(100, 170) == BubbleGestures.Result.NONE); // Too short.
        g.down(BubbleGestures.Zone.STRIP, 100, 200);
        expect(g.up(160, 150) == BubbleGestures.Result.NONE); // Diagonal up, not vertical-dominant.
        g.down(BubbleGestures.Zone.STRIP, 100, 200);
        expect(g.up(130, 100) == BubbleGestures.Result.UP); // Mostly vertical with slight drift.
        g.down(BubbleGestures.Zone.STRIP, 100, 200);
        expect(g.up(20, 190) == BubbleGestures.Result.LEFT); // Horizontal swipes keep switching tabs.
        g.down(BubbleGestures.Zone.STRIP, 100, 200);
        expect(g.up(180, 160) == BubbleGestures.Result.RIGHT);
        g.down(BubbleGestures.Zone.CIRCLE, 100, 200);
        expect(g.up(100, 100) == BubbleGestures.Result.DRAGGED); // Circle up-swipe moves, never opens the viewer.

        for (BubbleGestures.Zone zone : BubbleGestures.Zone.values()) {
            g.down(zone, 100, 100); g.cancel();
            expect(g.up(100, 100) == BubbleGestures.Result.NONE);
            expect(!g.isDragging());
        }
        System.out.println(checks + " gesture safety checks passed.");
    }
}
