package com.floatplay.overlay;

/** The strip locks to switching or moving from its initial direction, without a timer. */
final class BubbleGestures {
    enum Zone { CIRCLE, STRIP }
    enum Result { NONE, CLOSE, LEFT, RIGHT, DRAGGED }
    private enum Axis { UNDECIDED, HORIZONTAL, MOVING }
    private final float touchSlop, swipeDistance;
    private boolean active, dragging;
    private Zone zone;
    private Axis axis;
    private float downX, downY, maxDistance;

    BubbleGestures(float touchSlop, float swipeDistance) {
        this.touchSlop = touchSlop;
        this.swipeDistance = swipeDistance;
    }
    void down(Zone zone, float x, float y) {
        this.zone = zone;
        active = true;
        dragging = false;
        axis = Axis.UNDECIDED;
        downX = x; downY = y; maxDistance = 0;
    }
    void move(float x, float y) {
        if (!active) return;
        float dx = Math.abs(x - downX), dy = Math.abs(y - downY);
        maxDistance = Math.max(maxDistance, (float) Math.hypot(dx, dy));
        if (zone == Zone.CIRCLE && maxDistance > touchSlop) dragging = true;
        if (zone == Zone.STRIP) {
            if (axis == Axis.UNDECIDED && maxDistance > touchSlop) {
                axis = dx > dy * 1.5f ? Axis.HORIZONTAL : Axis.MOVING;
            }
            if (axis == Axis.MOVING) dragging = true;
        }
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
        return Result.NONE;
    }
    void cancel() { active = false; dragging = false; }
}
