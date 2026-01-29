package com.lagradost

import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.StreamTape
import com.lagradost.cloudstream3.extractors.VidHidePro

class TapeWithAdBlock : StreamTape() {
    override var name = "StreamTape"
    override var mainUrl = "https://tapewithadblock.org"
}

class Mmsi01 : Filesim() {
    override var name = "StreamHide"
    override var mainUrl = "https://mmsi01.com"
}

class Mmvh01 : VidHidePro() {
    override var name = "VidHide"
    override var mainUrl = "https://mmvh01.com"
}
