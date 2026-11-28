package io.kalo.content.gui.radial;

import java.util.List;
import java.util.Objects;

/** A platform-neutral radial menu geometry model. Input is a normalized cursor vector. */
public record RadialMenuLayout(List<Segment> segments, double deadZone) {
    public RadialMenuLayout {
        segments = List.copyOf(Objects.requireNonNull(segments, "segments"));
        if (segments.size() < 2 || segments.size() > 12)
            throw new IllegalArgumentException("radial menus require 2-12 segments");
        if (!Double.isFinite(deadZone) || deadZone < 0 || deadZone >= 1)
            throw new IllegalArgumentException("deadZone must be within [0,1)");
        if (segments.stream().map(Segment::id).distinct().count() != segments.size())
            throw new IllegalArgumentException("segment identifiers must be unique");
    }

    /** Clockwise order, first segment centered at twelve o'clock. -1 means no selection. */
    public int selectedIndex(double x, double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) return -1;
        if (Math.hypot(x, y) < deadZone) return -1;
        double angle = Math.atan2(x, -y);
        if (angle < 0) angle += Math.PI * 2;
        double sector = Math.PI * 2 / segments.size();
        return (int) Math.floor((angle + sector / 2) / sector) % segments.size();
    }

    public record Segment(String id, String label, String action) {
        public Segment {
            if (id == null || !id.matches("[a-z0-9_-]{1,64}"))
                throw new IllegalArgumentException("invalid segment id");
            if (label == null || label.isBlank()) throw new IllegalArgumentException("label required");
            if (action == null || action.isBlank()) throw new IllegalArgumentException("action required");
        }
    }
}
