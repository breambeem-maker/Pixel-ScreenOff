package com.example.screenoff

import android.content.Intent
import android.service.quicksettings.TileService

class ScreenOffTileService : TileService() {
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, BlackScreenActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivityAndCollapse(intent)
    }
}
