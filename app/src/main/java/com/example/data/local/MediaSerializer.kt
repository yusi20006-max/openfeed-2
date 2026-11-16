package com.example.data.local

import com.example.data.model.LinkPreview
import com.example.data.model.MediaAttachment
import com.example.data.model.MediaType
import com.example.data.model.PollData
import com.example.data.model.PollOption
import com.example.data.network.ArticleBlock
import org.json.JSONArray
import org.json.JSONObject

object MediaSerializer {

    fun encodeMedia(list: List<MediaAttachment>): String {
        val arr = JSONArray()
        list.forEach { item ->
            arr.put(JSONObject().apply {
                put("type", item.type.name)
                put("url", item.url)
                put("bridgeUrl", item.bridgeUrl)
                put("thumbnailUrl", item.thumbnailUrl ?: JSONObject.NULL)
                put("thumbnailBridgeUrl", item.thumbnailBridgeUrl ?: JSONObject.NULL)
                put("title", item.title ?: JSONObject.NULL)
                put("meta", item.meta ?: JSONObject.NULL)
            })
        }
        return arr.toString()
    }

    fun decodeMedia(json: String?): List<MediaAttachment> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val typeName = obj.optString("type", MediaType.PHOTO.name)
                    val type = MediaType.entries.firstOrNull { it.name == typeName } ?: MediaType.PHOTO
                    add(
                        MediaAttachment(
                            type = type,
                            url = obj.optString("url", ""),
                            bridgeUrl = obj.optString("bridgeUrl", ""),
                            thumbnailUrl = obj.optStringOrNull("thumbnailUrl"),
                            thumbnailBridgeUrl = obj.optStringOrNull("thumbnailBridgeUrl"),
                            title = obj.optStringOrNull("title"),
                            meta = obj.optStringOrNull("meta")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun encodeLinkPreview(preview: LinkPreview?): String? {
        if (preview == null) return null
        return JSONObject().apply {
            put("siteName", preview.siteName ?: JSONObject.NULL)
            put("title", preview.title ?: JSONObject.NULL)
            put("description", preview.description ?: JSONObject.NULL)
            put("url", preview.url)
            put("imageUrl", preview.imageUrl ?: JSONObject.NULL)
        }.toString()
    }

    fun decodeLinkPreview(json: String?): LinkPreview? {
        if (json.isNullOrBlank()) return null
        return runCatching {
            val obj = JSONObject(json)
            LinkPreview(
                siteName = obj.optStringOrNull("siteName"),
                title = obj.optStringOrNull("title"),
                description = obj.optStringOrNull("description"),
                url = obj.optString("url", ""),
                imageUrl = obj.optStringOrNull("imageUrl")
            )
        }.getOrNull()
    }

    fun encodePoll(poll: PollData?): String? {
        if (poll == null) return null
        return JSONObject().apply {
            put("question", poll.question)
            put("totalVotes", poll.totalVotes ?: JSONObject.NULL)
            put("options", JSONArray().apply {
                poll.options.forEach { opt ->
                    put(JSONObject().apply {
                        put("text", opt.text)
                        put("percentText", opt.percentText)
                        put("percentFloat", opt.percentFloat.toDouble())
                    })
                }
            })
        }.toString()
    }

    fun decodePoll(json: String?): PollData? {
        if (json.isNullOrBlank()) return null
        return runCatching {
            val obj = JSONObject(json)
            val optionsArr = obj.optJSONArray("options") ?: JSONArray()
            val options = buildList {
                for (i in 0 until optionsArr.length()) {
                    val o = optionsArr.getJSONObject(i)
                    add(
                        PollOption(
                            text = o.optString("text", ""),
                            percentText = o.optString("percentText", "0%"),
                            percentFloat = o.optDouble("percentFloat", 0.0).toFloat()
                        )
                    )
                }
            }
            PollData(
                question = obj.optString("question", ""),
                options = options,
                totalVotes = obj.optStringOrNull("totalVotes")
            )
        }.getOrNull()
    }

    fun encodeStringList(list: List<String>): String {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        return arr.toString()
    }

    fun decodeStringList(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    add(arr.getString(i))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun encodeArticleBlocks(blocks: List<ArticleBlock>): String {
        val arr = JSONArray()
        blocks.forEach { block ->
            arr.put(JSONObject().apply {
                when (block) {
                    is ArticleBlock.Heading -> {
                        put("type", "heading")
                        put("text", block.text)
                        put("level", block.level)
                    }
                    is ArticleBlock.Paragraph -> {
                        put("type", "paragraph")
                        put("text", block.text)
                    }
                    is ArticleBlock.Quote -> {
                        put("type", "quote")
                        put("text", block.text)
                    }
                    is ArticleBlock.CodeBlock -> {
                        put("type", "code")
                        put("text", block.text)
                    }
                }
            })
        }
        return arr.toString()
    }

    fun decodeArticleBlocks(json: String?): List<ArticleBlock> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val text = obj.optString("text", "")
                    if (text.isBlank()) continue
                    when (obj.optString("type", "paragraph")) {
                        "heading" -> add(ArticleBlock.Heading(text, obj.optInt("level", 2)))
                        "quote" -> add(ArticleBlock.Quote(text))
                        "code" -> add(ArticleBlock.CodeBlock(text))
                        else -> add(ArticleBlock.Paragraph(text))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val value = optString(key, "")
        return value.takeIf { it.isNotBlank() && it != "null" }
    }
}
