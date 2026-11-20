package com.floatplay.overlay;

/** The circle closes or drags the widget; the strip only swipes (left/right switch tabs, up opens the viewer). */
final class BubbleGestures {
    enum Zone { CIRCLE, STRIP }
    enum Result { NONE, CLOSE, LEFT, RIGHT, UP, DRAGGED }
    private final float touchSlop, swipeDistance;
    private boolean active, dragging;
    private Zone zone;
    private float downX, downY, maxDistance;

    BubbleGestures(float touchSlop, float swipeDistance) {
        this.touchSlop = touchSlop;
        this.swipeDistance = swipeDistance;
    }
    void down(Zone zone, float x, float y) {
        this.zone = zone;
        active = true;
        dragging = false;
        downX = x; downY = y; maxDistance = 0;
    }
    void move(float x, float y) {
        if (!active) return;
        float dx = Math.abs(x - downX), dy = Math.abs(y - downY);
        maxDistance = Math.max(maxDistance, (float) Math.hypot(dx, dy));
        if (zone == Zone.CIRCLE && maxDistance > touchSlop) dragging = true;
    }
    boolean isDragging() { return active && dragging; }
    Result up(float x, float y) {
        if (!active) return Result.NONE;
        move(x, y);
        active = false;
        if (dragging) return Result.DRAGGED;
        if (zone == Zone.CIRCLE) return Result.CLOSE;
        float dx = x - downX, dy = y - downY;
        if (Math.abs(dx) >= swipeDistance && Math.abs(dx) > Math.abs(dy) * 1.5f) {
            return dx < 0 ? Result.LEFT : Result.RIGHT;
        }
        if (-dy >= swipeDistance && -dy > Math.abs(dx) * 1.5f) return Result.UP;
        return Result.NONE;
    }
    void cancel() { active = false; dragging = false; }
}
