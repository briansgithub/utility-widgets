package com.floatplay.overlay

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import kotlin.math.roundToInt

/** The HBM transition is the normal manual maximum, below sunlight-only output. */
object BrightnessCalibration {
    fun maximumSetting(): Int {
        // Android's default built-in display configuration. No hidden APIs or
        // model-specific brightness constants; unsupported layouts use the full range.
        for (root in listOf("/product", "/vendor", "/system")) {
            val file = File("$root/etc/displayconfig/display_port_0.xml")
            try {
                if (!file.isFile) continue
                file.inputStream().use { stream ->
                    val parser = Xml.newPullParser().apply { setInput(stream, null) }
                    var inHbm = false
                    while (parser.next() != XmlPullParser.END_DOCUMENT) {
                        if (parser.eventType == XmlPullParser.START_TAG) {
                            if (parser.name == "highBrightnessMode") {
                                inHbm = parser.getAttributeValue(null, "enabled") == "true"
                            } else if (inHbm && parser.name == "transitionPoint") {
                                val limit = parser.nextText().toFloatOrNull()
                                if (limit != null && limit > 0f && limit <= 1f) {
                                    // Android maps setting 1..255 to backlight 0..1.
                                    return (1 + limit * 254).roundToInt().coerceIn(2, 255)
                                }
                            }
                        } else if (parser.eventType == XmlPullParser.END_TAG && parser.name == "highBrightnessMode") {
                            inHbm = false
                        }
                    }
                }
                // Found the active port configuration without an HBM restriction.
                return 255
            } catch (_: Exception) {
                // Vendor configuration may not be readable by third-party apps.
            }
        }
        return 255
    }
}
