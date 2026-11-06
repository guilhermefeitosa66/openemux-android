package com.openemux.mobile.data

/** Where an editor panel opens on the stage (issue 785). */
enum class PanelSide { TOP, BOTTOM, START, END }

/**
 * Panels open away from what is being edited, so the change stays in view (issue 666, section 3). Boxes are in the
 * stage's pixels, as [SkinRect].
 */
object SkinPanels {
    /**
     * Upright, a panel goes over the game screen ([TOP]) unless the selection reaches into the screen's band, then to
     * the [BOTTOM]. Sideways it opens on the side opposite the selection's centre ([START] is the left), [END] with
     * nothing selected.
     */
    fun side(landscape: Boolean, stageWidth: Float, screen: SkinRect, selection: SkinRect?): PanelSide {
        if (!landscape) {
            val over = selection != null && selection.y < screen.y + screen.height && selection.y + selection.height > screen.y
            return if (over) PanelSide.BOTTOM else PanelSide.TOP
        }
        if (selection == null) return PanelSide.END
        return if (selection.x + selection.width / 2f > stageWidth / 2f) PanelSide.START else PanelSide.END
    }
}
