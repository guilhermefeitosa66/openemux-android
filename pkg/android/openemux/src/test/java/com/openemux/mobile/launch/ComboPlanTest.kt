package com.openemux.mobile.launch

import com.openemux.mobile.data.Console
import com.openemux.mobile.data.SkinElement
import com.openemux.mobile.data.SkinElementType
import com.openemux.mobile.data.SkinLayout
import com.openemux.mobile.data.SkinRect
import com.openemux.mobile.data.SkinSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A combo button in game (issue 842): one overlay desc pressing all its buttons, as the console maps them. */
class ComboPlanTest {
    private val layout = SkinLayout(SkinSize(1000, 2000), listOf(SkinRect(0f, 100f, 1000f, 700f)))

    private fun combo(bind: String) = SkinElement("combo-1", SkinElementType.BUTTON, bind, SkinRect(400f, 1500f, 100f, 100f))

    private fun planned(console: Console, bind: String, coreId: String? = null) =
        planPage(console, layout.copy(elements = listOf(combo(bind))), 1000, 2000, hardcore = false, coreId = coreId).elements.single() as ElementPlan.Button

    @Test fun aComboPressesEachOfItsButtons() {
        assertEquals("y|b", planned(Console.SFC, "y|b").bind)
        assertEquals("l|r|a", planned(Console.SFC, "l|r|a").bind)
    }

    @Test fun eachButtonIsWhereTheConsolePutsIt() {
        // The N64's A is the RetroPad's B, its C buttons the right stick.
        assertEquals("b|r_y_minus", planned(Console.N64, "a|cUp").bind)
        // The Mega Drive's six-button pad: A on Y, C on A.
        assertEquals("y|a", OpenEmuxBinds.combo(Console.MD, "a|c"))
    }

    @Test fun aButtonNothingMapsIsLeftOutAndNoneLeftPressesNothing() {
        assertEquals("a", OpenEmuxBinds.combo(Console.SFC, "a|warp"))
        assertNull(OpenEmuxBinds.combo(Console.SFC, "warp|zap"))
        assertNull(planned(Console.SFC, "warp|zap").bind)
        // Two names for one RetroPad button press it once.
        assertEquals("l", OpenEmuxBinds.combo(Console.SFC, "l|l1"))
    }
}
