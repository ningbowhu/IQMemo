package com.example.iqmemo.speech

import com.example.iqmemo.BuildConfig

/** Public adapter only. Values come from ignored local.properties or the environment.
 * Default public APKs use placeholders. Any values compiled into an APK are extractable.
 */
object XfyunConfig {
    const val APP_ID = BuildConfig.XFYUN_APP_ID
    const val API_KEY = BuildConfig.XFYUN_API_KEY
    const val API_SECRET = BuildConfig.XFYUN_API_SECRET

    fun isConfigured(): Boolean =
        APP_ID.isNotBlank() && APP_ID != "your_app_id" &&
            API_KEY.isNotBlank() && API_KEY != "your_api_key" &&
            API_SECRET.isNotBlank() && API_SECRET != "your_api_secret"
}
