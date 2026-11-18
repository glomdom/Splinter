package com.glomdom.splinter.guide

import com.glomdom.splinter.Splinter
import com.glomdom.splinter.splinterKey
import io.github.pylonmc.rebar.content.guide.RebarGuide
import io.github.pylonmc.rebar.guide.button.AddonPageButton
import io.github.pylonmc.rebar.guide.pages.base.SimpleStaticGuidePage

object SplinterPages {
    /** Main item page; registry entries add themselves to it. */
    val SPLINTER = SimpleStaticGuidePage(splinterKey("splinter"))

    /** Entry under the Rebar guide's help section. */
    val HELP = SimpleStaticGuidePage(splinterKey("help"))

    /** Hooks the pages into the Rebar guide. Call after items are registered so [SPLINTER] is populated. */
    fun registerAll() {
        RebarGuide.rootPage.addPage(Splinter.material, SPLINTER)
        RebarGuide.helpPage.addButton(AddonPageButton(Splinter, HELP))
    }
}
