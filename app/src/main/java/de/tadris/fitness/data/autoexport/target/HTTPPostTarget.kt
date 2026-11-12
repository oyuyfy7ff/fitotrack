/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack
 *
 * FitoTrack is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     FitoTrack is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.tadris.fitness.data.autoexport.target

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.NetworkType
import de.tadris.fitness.R
import de.tadris.fitness.data.autoexport.source.ExportSource
import org.apache.commons.io.IOUtils
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class HTTPPostTarget(
    val url: String,
    val bearerToken: String? = null,
) : ExportTarget {

    override val id get() = ExportTarget.TARGET_TYPE_HTTP_POST

    override val titleRes get() = R.string.exportTargetHttpPost

    override val summary get() = url

    override val constraints
        get() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

    override fun exportFile(context: Context, file: ExportSource.ExportedFile) {
        val input = context.contentResolver.openInputStream(Uri.fromFile(file.file))
            ?: throw IOException("Source file not found")

        val client = URL(url).openConnection() as HttpURLConnection
        client.requestMethod = "POST"
        file.meta.forEach {
            client.setRequestProperty(it.key, it.value)
        }
        if (!bearerToken.isNullOrBlank()) {
            client.setRequestProperty("Authorization", "Bearer $bearerToken")
        }
        client.doOutput = true
        val output = client.outputStream
        IOUtils.copy(input, output)
        input.close()
        output.flush()
        output.close()

        try {
            val responseCode = client.responseCode
            if (responseCode >= 400) {
                throw IOException("HTTP POST failed with response code $responseCode")
            }
            client.content // Load response
        } finally {
            client.disconnect()
        }
    }

    companion object {
        private const val KEY_URL = "url"
        private const val KEY_BEARER_TOKEN = "bearerToken"

        @JvmStatic
        fun fromData(data: String): HTTPPostTarget {
            if (!data.trimStart().startsWith("{")) return HTTPPostTarget(data)
            return try {
                val json = JSONObject(data)
                HTTPPostTarget(
                    json.getString(KEY_URL),
                    json.optString(KEY_BEARER_TOKEN).ifBlank { null },
                )
            } catch (e: JSONException) {
                HTTPPostTarget(data)
            }
        }

        @JvmStatic
        fun toData(url: String, bearerToken: String?): String {
            if (bearerToken.isNullOrBlank()) return url
            return JSONObject()
                .put(KEY_URL, url)
                .put(KEY_BEARER_TOKEN, bearerToken)
                .toString()
        }
    }
}