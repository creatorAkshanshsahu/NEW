package com.akshansh.aktv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

object LiveTvApi {
    private const val BASE = "https://livetgtv.lovable.app/api/public/v2/channels"

    /** Runs on Dispatchers.IO – the old version ran on the main thread (lag / crash). */
    suspend fun search(query: String = ""): List<Channel> = withContext(Dispatchers.IO) {
        val url = if (query.isBlank()) BASE else "$BASE?q=${URLEncoder.encode(query, "UTF-8")}"
        parseChannels(Http.get(url))
    }

    private fun parseChannels(text: String): List<Channel> {
        val trimmed = text.trim()
        val array: JSONArray = if (trimmed.startsWith("[")) JSONArray(trimmed)
        else findArray(JSONObject(trimmed)) ?: return emptyList()

        val result = ArrayList<Channel>(array.length())
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val id = first(o, "id", "channel_id", "channelId", "uuid") ?: i.toString()
            val name = first(o, "name", "title", "channel_name", "channelName") ?: "Channel $id"
            val logo = first(o, "logo", "logo_url", "logoUrl", "image", "image_url", "poster")
            val stream = first(
                o, "stream_url", "streamUrl", "stream", "url", "play_url", "playUrl",
                "hls_url", "hlsUrl", "m3u8", "source"
            )
            val category = first(o, "category", "category_name", "categoryName", "group", "genre")
            result.add(Channel(id, name, logo, stream, category))
        }
        return result.distinctBy { it.id }
    }

    private fun findArray(o: JSONObject): JSONArray? {
        for (key in listOf("channels", "data", "results", "items")) {
            o.optJSONArray(key)?.let { return it }
        }
        o.optJSONObject("data")?.let { d ->
            for (key in listOf("channels", "results", "items")) {
                d.optJSONArray(key)?.let { return it }
            }
        }
        return null
    }

    private fun first(o: JSONObject, vararg keys: String): String? {
        for (key in keys) {
            val v = o.opt(key) ?: continue
            if (v == JSONObject.NULL) continue
            val s = when (v) {
                is JSONObject -> first(v, "url", "hls", "hls_url", "m3u8", "src", "link")
                is JSONArray -> null
                else -> v.toString().trim()
            }
            if (!s.isNullOrEmpty() && s != "null") return s
        }
        return null
    }
}
