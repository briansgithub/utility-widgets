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
        g.down(BubbleGestures.Zone.STRIP, 100, 100);
        expect(g.up(110, 200) == BubbleGestures.Result.DRAGGED);
        g.down(BubbleGestures.Zone.STRIP, 100, 100);
        expect(g.up(150, 150) == BubbleGestures.Result.DRAGGED);
        g.down(BubbleGestures.Zone.STRIP, 100, 100);
        expect(g.up(120, 100) == BubbleGestures.Result.NONE);
        g.down(BubbleGestures.Zone.STRIP, 100, 100);
        g.move(200, 100); g.move(100, 100);
        expect(g.up(100, 100) == BubbleGestures.Result.NONE);

        g.down(BubbleGestures.Zone.STRIP, 100, 200);
        g.move(103, 180);
        expect(g.isDragging()); // Vertical movement begins during MOVE, with no hold/timer.
        expect(g.up(105, 100) == BubbleGestures.Result.DRAGGED);
        g.down(BubbleGestures.Zone.STRIP, 100, 100);
        g.move(103, 120);
        expect(g.isDragging());
        g.move(300, 125);
        expect(g.up(300, 125) == BubbleGestures.Result.DRAGGED); // Once moving, never switch on a later turn.

        g.down(BubbleGestures.Zone.STRIP, 100, 100);
        g.move(120, 103);
        expect(!g.isDragging());
        g.move(125, 300);
        expect(!g.isDragging()); // A horizontal start cannot turn into widget movement.
        expect(g.up(125, 300) == BubbleGestures.Result.NONE);
        g.down(BubbleGestures.Zone.STRIP, 100, 100);
        g.move(80, 103);
        expect(g.up(20, 120) == BubbleGestures.Result.LEFT);
        g.down(BubbleGestures.Zone.STRIP, 100, 100);
        g.move(120, 97);
        expect(g.up(180, 80) == BubbleGestures.Result.RIGHT);

        g.down(BubbleGestures.Zone.STRIP, 100, 100);
        g.move(103, 105);
        expect(!g.isDragging()); // Existing touch slop absorbs jitter.
        expect(g.up(103, 105) == BubbleGestures.Result.NONE);
        g.down(BubbleGestures.Zone.STRIP, 100, 100);
        g.move(120, 120);
        expect(g.isDragging());
        expect(g.up(150, 150) == BubbleGestures.Result.DRAGGED);

        for (int dx : new int[] {-60, 60}) {
            for (int dy : new int[] {-60, 60}) {
                g.down(BubbleGestures.Zone.STRIP, 100, 100);
                g.move(100 + dx, 100 + dy);
                expect(g.isDragging());
                expect(g.up(100 + dx, 100 + dy) == BubbleGestures.Result.DRAGGED);
            }
        }

        for (BubbleGestures.Zone zone : BubbleGestures.Zone.values()) {
            g.down(zone, 100, 100); g.cancel();
            expect(g.up(100, 100) == BubbleGestures.Result.NONE);
            expect(!g.isDragging());
        }
        System.out.println(checks + " gesture safety checks passed.");
    }
}
