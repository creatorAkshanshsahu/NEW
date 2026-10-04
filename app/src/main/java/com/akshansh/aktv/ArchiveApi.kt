package com.akshansh.aktv

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

object ArchiveApi {

    suspend fun search(term: String = ""): List<Movie> = withContext(Dispatchers.IO) {
        val query = if (term.isBlank()) {
            "mediatype:movies AND (licenseurl:*creativecommons.org* OR rights:\"public domain\")"
        } else {
            "mediatype:movies AND title:\"${term.replace("\"", "")}\""
        }

        val url = "https://archive.org/advancedsearch.php?q=" +
                URLEncoder.encode(query, "UTF-8") +
                "&fl[]=identifier&fl[]=title&fl[]=year&fl[]=description&fl[]=rights&fl[]=licenseurl" +
                "&rows=100&page=1&sort[]=downloads+desc&output=json"

        val json = Http.get(url)
        val docs = JSONObject(json).getJSONObject("response").getJSONArray("docs")
        val result = mutableListOf<Movie>()

        for (i in 0 until docs.length()) {
            val d = docs.getJSONObject(i)
            val id = d.optString("identifier")
            val rights = d.optString("rights")
            val license = d.optString("licenseurl")

            // Conservative rights gate: only show records that explicitly state
            // public domain or a Creative Commons license.
            val allowed = rights.contains("public domain", true) ||
                    license.contains("creativecommons.org", true)

            if (!allowed || id.isBlank()) continue

            result += Movie(
                id = id,
                title = d.optString("title", id),
                year = d.optString("year").ifBlank { null },
                description = d.optString("description").ifBlank { null },
                rights = rights.ifBlank { null },
                licenseUrl = license.ifBlank { null },
                poster = "https://archive.org/services/img/${Uri.encode(id)}"
            )
        }
        result
    }

    suspend fun resolvePlayableUrl(movie: Movie): Movie = withContext(Dispatchers.IO) {
        val json = Http.get("https://archive.org/metadata/${Uri.encode(movie.id)}")
        val root = JSONObject(json)
        val files = root.optJSONArray("files") ?: return@withContext movie

        var best: String? = null
        var bestSize = -1L

        for (i in 0 until files.length()) {
            val f = files.optJSONObject(i) ?: continue
            val name = f.optString("name")
            val size = f.optString("size").toLongOrNull() ?: 0L

            val playable = name.endsWith(".mp4", true) &&
                    !name.contains("thumb", true) &&
                    !name.contains("_files", true)

            if (playable && size > bestSize) {
                best = "https://archive.org/download/${Uri.encode(movie.id)}/${Uri.encode(name)}"
                bestSize = size
            }
        }

        movie.videoUrl = best
        movie
    }
}
