package com.floatplay.overlay;

final class TabDirection {
    private TabDirection() {}
    static boolean previous(boolean swipedLeft, boolean reversed) {
        return swipedLeft ? reversed : !reversed;
    }
}
