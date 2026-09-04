package com.glomdom.splinter.guide

import com.glomdom.splinter.Splinter
import com.glomdom.splinter.splinterKey
import io.github.pylonmc.rebar.content.guide.RebarGuide
import io.github.pylonmc.rebar.guide.button.AddonPageButton
import io.github.pylonmc.rebar.guide.pages.base.SimpleStaticGuidePage

object SplinterHelpPages {
    val HELP = SimpleStaticGuidePage(splinterKey("help"))

    init {
        RebarGuide.helpPage.addButton(AddonPageButton(Splinter, HELP))
    }
}