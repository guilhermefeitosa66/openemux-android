package com.openemux.mobile.launch

/**
 * Where a touch can start a D-pad or an analog stick: well past what is drawn, because a player looking at the game
 * lands the thumb beside the control. The engine gives a touch to every desc whose hitbox holds it, so a zone has to
 * stop short of whatever else takes touches. It is only read when a touch starts.
 */
internal object TouchZones {

    /** The least a zone goes past what is drawn, as a share of the screen's short side: a small control's is still thumb-sized. */
    const val SCREEN_SHARE = 0.12f

    /**
     * The start zone of a control that shows [drawn] and is touched over [floor] already. Each side goes past [drawn]
     * by the larger of its half-size and [SCREEN_SHARE] of the [screenW] x [screenH] screen's short side, then is
     * pulled back [gap] short of the [neighbours]. The other movement controls ([peers]) share the space between
     * them at the midpoint, so the result does not depend on their order. Never smaller than [floor].
     */
    fun startZone(
        drawn: SkinBox, floor: SkinBox, neighbours: List<SkinBox>, peers: List<SkinBox>, gap: Float,
        screenW: Float, screenH: Float,
    ): SkinBox {
        val least = minOf(screenW, screenH) * SCREEN_SHARE
        val mx = maxOf(drawn.w / 2f, least)
        val my = maxOf(drawn.h / 2f, least)
        var left = minOf(floor.x, drawn.x - mx)
        var top = minOf(floor.y, drawn.y - my)
        var right = maxOf(floor.right, drawn.right + mx)
        var bottom = maxOf(floor.bottom, drawn.bottom + my)
        for (o in neighbours + peers.map { halfway(it, floor) }) {
            if (o.right + gap <= left || o.x - gap >= right || o.bottom + gap <= top || o.y - gap >= bottom) continue
            // What each side loses to clear it; a side the neighbour does not lie beyond cannot clear it.
            val none = Float.POSITIVE_INFINITY
            val cuts = floatArrayOf(
                if (o.right <= floor.x) minOf(floor.x, o.right + gap) - left else none,
                if (o.bottom <= floor.y) minOf(floor.y, o.bottom + gap) - top else none,
                if (o.x >= floor.right) right - maxOf(floor.right, o.x - gap) else none,
                if (o.y >= floor.bottom) bottom - maxOf(floor.bottom, o.y - gap) else none,
            )
            val cut = cuts.min()
            when {
                // Over the control's own area (a package's extended edges): nothing clears it, keep what it had.
                cut == none -> {
                    left = floor.x
                    top = floor.y
                    right = floor.right
                    bottom = floor.bottom
                }
                cuts[0] == cut -> left += cut
                cuts[1] == cut -> top += cut
                cuts[2] == cut -> right -= cut
                else -> bottom -= cut
            }
        }
        return SkinBox(left, top, right - left, bottom - top)
    }

    /** [peer] grown towards [own] up to the middle of the space between them, on each side that faces it. */
    private fun halfway(peer: SkinBox, own: SkinBox): SkinBox {
        val left = if (peer.x >= own.right) (peer.x + own.right) / 2f else peer.x
        val top = if (peer.y >= own.bottom) (peer.y + own.bottom) / 2f else peer.y
        val right = if (peer.right <= own.x) (peer.right + own.x) / 2f else peer.right
        val bottom = if (peer.bottom <= own.y) (peer.bottom + own.y) / 2f else peer.bottom
        return SkinBox(left, top, right - left, bottom - top)
    }

    /**
     * [zone] as the engine's reach: multiples of the half-size of [range], the desc's rect, from its centre, in the
     * order left, top, right, bottom. A range with no area reaches 1 on every side, which is the engine's default.
     */
    fun reach(range: SkinBox, zone: SkinBox): FloatArray {
        if (range.w <= 0f || range.h <= 0f) return floatArrayOf(1f, 1f, 1f, 1f)
        val cx = range.x + range.w / 2f
        val cy = range.y + range.h / 2f
        val hw = range.w / 2f
        val hh = range.h / 2f
        return floatArrayOf((cx - zone.x) / hw, (cy - zone.y) / hh, (zone.right - cx) / hw, (zone.bottom - cy) / hh)
    }
}
