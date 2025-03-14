package com.programmersbox.manga_sources.manga

import android.annotation.SuppressLint
import com.programmersbox.manga_sources.Sources
import com.programmersbox.manga_sources.utilities.*
import com.programmersbox.models.*
import com.squareup.duktape.Duktape
import io.reactivex.Single
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.text.SimpleDateFormat
import java.util.*

object MangaPark : ApiService, KoinComponent {

    override val baseUrl = "https://mangapark.net"

    override val serviceName: String get() = "MANGA_PARK"

    private val helper: NetworkHelper by inject()

    override fun searchList(searchText: CharSequence, page: Int, list: List<ItemModel>): Single<List<ItemModel>> = try {
        if (searchText.isBlank()) {
            super.searchList(searchText, page, list)
        } else {
            Single.create { emitter ->
                emitter.onSuccess(
                    cloudflare(helper, "$baseUrl/search?word=$searchText&page=$page").execute().asJsoup()
                        .browseToItemModel("div#search-list div.col")
                )
            }

        }
    } catch (e: Exception) {
        super.searchList(searchText, page, list)
    }

    override fun getList(page: Int): Single<List<ItemModel>> = Single.create { emitter ->
        cloudflare(helper, "$baseUrl/browse?sort=d007&page=$page").execute().asJsoup()
            .browseToItemModel()
            .let { emitter.onSuccess(it) }
    }

    override fun getRecent(page: Int): Single<List<ItemModel>> = Single.create { emitter ->
        cloudflare(helper, "$baseUrl/browse?sort=update&page=$page").execute().asJsoup()
            .browseToItemModel()
            .let { emitter.onSuccess(it) }
    }

    private fun Document.browseToItemModel(query: String = "div#subject-list div.col") = select(query)
        .map {
            ItemModel(
                title = it.select("a.fw-bold").text(),
                description = it.select("div.limit-html").text(),
                url = it.select("a.fw-bold").attr("abs:href"),
                imageUrl = it.select("a.position-relative img").attr("abs:src"),
                source = Sources.MANGA_PARK
            )
        }

    override fun getItemInfo(model: ItemModel): Single<InfoModel> = Single.create { emitter ->
        val doc = cloudflare(helper, model.url).execute().asJsoup()
        try {
            val infoElement = doc.select("div#mainer div.container-fluid")
            emitter.onSuccess(
                InfoModel(
                    title = model.title,
                    description = model.description,
                    url = model.url,
                    imageUrl = model.imageUrl,
                    chapters = chapterListParse(helper.cloudflareClient.newCall(chapterListRequest(model)).execute(), model.url),
                    genres = infoElement.select("div.attr-item:contains(genres) span span").map { it.text().trim() },
                    alternativeNames = emptyList(),
                    source = this
                )
            )
        } catch (e: Exception) {
            val genres = mutableListOf<String>()
            val alternateNames = mutableListOf<String>()
            doc.select(".attr > tbody > tr").forEach {
                when (it.getElementsByTag("th").first()!!.text().trim().lowercase(Locale.getDefault())) {
                    "genre(s)" -> genres.addAll(it.getElementsByTag("a").map(Element::text))
                    "alternative" -> alternateNames.addAll(it.text().split("l"))
                }
            }
            emitter.onSuccess(
                InfoModel(
                    title = model.title,
                    description = doc.select("p.summary").text(),
                    url = model.url,
                    imageUrl = model.imageUrl,
                    chapters = chapterListParse(doc, model.url),
                    genres = genres,
                    alternativeNames = alternateNames,
                    source = this
                )
            )
        }
    }

    private fun chapterListRequest(manga: ItemModel): Request {

        val url = manga.url.replace(baseUrl, "")
        val sid = url.split("/")[2]

        val jsonPayload = buildJsonObject {
            put("lang", "en")
            put("sid", sid)
        }

        val requestBody = jsonPayload.toString().toRequestBody("application/json;charset=UTF-8".toMediaType())

        val refererUrl = "$baseUrl/$url".toHttpUrlOrNull()!!.newBuilder()
            .toString()
        val newHeaders = MangaUtils.headersBuilder()
            .add("Content-Length", requestBody.contentLength().toString())
            .add("Content-Type", requestBody.contentType().toString())
            .set("Referer", refererUrl)
            .build()

        return POST(
            "$baseUrl/ajax.reader.subject.episodes.lang",
            headers = newHeaders,
            body = requestBody
        )
    }

    private fun chapterListParse(response: Response, mangaUrl: String): List<ChapterModel> {
        val resToJson = Json.parseToJsonElement(response.body!!.string()).jsonObject
        val document = Jsoup.parse(resToJson["html"]!!.jsonPrimitive.content)
        return document.select("div.episode-item").map { chapterFromElement(it) }
            .map {
                ChapterModel(
                    name = it.name,
                    url = it.url,
                    uploaded = it.originalDate,
                    sourceUrl = mangaUrl,
                    source = this
                ).apply { uploadedTime = it.dateUploaded }
            }
    }

    private class SChapter {
        var url: String = ""
        var name: String = ""
        var chapterNumber: Float = 0f
        var dateUploaded: Long? = null
        var originalDate: String = ""
    }

    private fun chapterListParse(response: Document, mangaUrl: String): List<ChapterModel> {
        fun List<SChapter>.getMissingChapters(allChapters: List<SChapter>): List<SChapter> {
            val chapterNums = this.map { it.chapterNumber }
            return allChapters.filter { it.chapterNumber !in chapterNums }.distinctBy { it.chapterNumber }
        }

        val mangaBySource = response.select("div[id^=stream]")
            .map { sourceElement ->
                var lastNum = 0F

                sourceElement.select(".volume .chapter li")
                    .reversed() // so incrementing lastNum works
                    .map { chapterElement ->
                        chapterFromElement(chapterElement, lastNum)
                            .also { lastNum = it.chapterNumber }
                    }
                    .distinctBy { it.chapterNumber } // there's even duplicate chapters within a source ( -.- )
            }

        val chapters = mangaBySource.maxByOrNull { it.count() }!!
        return (chapters + chapters.getMissingChapters(mangaBySource.flatten())).sortedByDescending { it.chapterNumber }.map {
            ChapterModel(
                name = it.name,
                url = "${baseUrl}${it.url}",
                uploaded = it.originalDate,
                sourceUrl = mangaUrl,
                source = this
            ).apply { uploadedTime = it.dateUploaded }
        }
    }

    private fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a.chapt")
        val time = element.select("div.extra > i.ps-2").text()

        return SChapter().apply {
            name = urlElement.text()
            chapterNumber = urlElement.attr("href").substringAfterLast("/").toFloat()
            if (time != "") {
                dateUploaded = parseDate(time)
            }
            url = urlElement.attr("abs:href")
        }
    }

    private fun chapterFromElement(element: Element, lastNum: Float): SChapter {
        fun Float.incremented() = this + .00001F
        fun Float?.orIncrementLastNum() = if (this == null || this < lastNum) lastNum.incremented() else this

        return SChapter().apply {
            url = element.select(".tit > a").first()!!.attr("href").replaceAfterLast("/", "")
            name = element.select(".tit > a").first()!!.text()
            // Get the chapter number or create a unique one if it's not available
            chapterNumber = Regex("""\b\d+\.?\d?\b""").findAll(name)
                .toList()
                .map { it.value.toFloatOrNull() }
                .let { nums ->
                    when {
                        nums.count() == 1 -> nums[0].orIncrementLastNum()
                        nums.count() >= 2 -> nums[1].orIncrementLastNum()
                        else -> lastNum.incremented()
                    }
                }
            dateUploaded = element.select(".time").firstOrNull()?.text()?.trim()?.let { parseDate(it) }
            originalDate = element.select(".time").firstOrNull()?.text()?.trim().toString()
        }
    }

    private val cryptoJS by lazy { helper.client.newCall(GET(cryptoJSUrl, MangaUtils.headers)).execute().body!!.string() }

    private const val cryptoJSUrl = "https://cdnjs.cloudflare.com/ajax/libs/crypto-js/4.0.0/crypto-js.min.js"

    private val dateFormat = SimpleDateFormat("MMM d, yyyy, HH:mm a", Locale.ENGLISH)
    private val dateFormatTimeOnly = SimpleDateFormat("HH:mm a", Locale.ENGLISH)

    @SuppressLint("DefaultLocale")
    private fun parseDate(date: String): Long? {
        val lcDate = date.lowercase()
        if (lcDate.endsWith("ago")) return parseRelativeDate(lcDate)

        // Handle 'yesterday' and 'today'
        var relativeDate: Calendar? = null
        if (lcDate.startsWith("yesterday")) {
            relativeDate = Calendar.getInstance()
            relativeDate.add(Calendar.DAY_OF_MONTH, -1) // yesterday
        } else if (lcDate.startsWith("today")) {
            relativeDate = Calendar.getInstance()
        }

        relativeDate?.let {
            // Since the date is not specified, it defaults to 1970!
            val time = dateFormatTimeOnly.parse(lcDate.substringAfter(' '))
            val cal = Calendar.getInstance()
            cal.time = time!!

            // Copy time to relative date
            it.set(Calendar.HOUR_OF_DAY, cal.get(Calendar.HOUR_OF_DAY))
            it.set(Calendar.MINUTE, cal.get(Calendar.MINUTE))
            return it.timeInMillis
        }

        return dateFormat.parse(lcDate)?.time
    }

    /**
     * Parses dates in this form:
     * `11 days ago`
     */
    private fun parseRelativeDate(date: String): Long? {
        val trimmedDate = date.split(" ")

        if (trimmedDate[2] != "ago") return null

        val number = when (trimmedDate[0]) {
            "a" -> 1
            else -> trimmedDate[0].toIntOrNull() ?: return null
        }
        val unit = trimmedDate[1].removeSuffix("s") // Remove 's' suffix

        val now = Calendar.getInstance()

        // Map English unit to Java unit
        val javaUnit = when (unit) {
            "year" -> Calendar.YEAR
            "month" -> Calendar.MONTH
            "week" -> Calendar.WEEK_OF_MONTH
            "day" -> Calendar.DAY_OF_MONTH
            "hour" -> Calendar.HOUR
            "minute" -> Calendar.MINUTE
            "second" -> Calendar.SECOND
            else -> return null
        }

        now.add(javaUnit, -number)

        return now.timeInMillis
    }

    override fun getSourceByUrl(url: String): Single<ItemModel> = Single.create {
        try {
            val doc = cloudflare(helper, url).execute().asJsoup()
            val infoElement = doc.select("div#mainer div.container-fluid")
            ItemModel(
                title = infoElement.select("h3.item-title").text(),
                description = infoElement.select("div.limit-height-body")
                    .select("h5.text-muted, div.limit-html")
                    .joinToString("\n\n", transform = Element::text),
                url = url,
                imageUrl = infoElement.select("div.detail-set div.attr-cover img").attr("abs:src"),
                source = this
            )
                .let(it::onSuccess)
        } catch (e: Exception) {
            it.onError(e)
        }
    }

    override fun getChapterInfo(chapterModel: ChapterModel): Single<List<Storage>> = Single.create { emitter ->

        val duktape = Duktape.create()
        val script = cloudflare(helper, chapterModel.url).execute().asJsoup().select("script").html()
        val imgCdnHost = script.substringAfter("const imgCdnHost = \"").substringBefore("\";")
        val imgPathLisRaw = script.substringAfter("const imgPathLis = ").substringBefore(";")
        val imgPathLis = Json.parseToJsonElement(imgPathLisRaw).jsonArray
        val amPass = script.substringAfter("const amPass = ").substringBefore(";")
        val amWord = script.substringAfter("const amWord = ").substringBefore(";")

        val decryptScript = cryptoJS + "CryptoJS.AES.decrypt($amWord, $amPass).toString(CryptoJS.enc.Utf8);"

        val imgWordLisRaw = duktape.evaluate(decryptScript).toString()
        val imgWordLis = Json.parseToJsonElement(imgWordLisRaw).jsonArray

        imgWordLis.mapIndexed { i, imgWordE ->
            val imgPath = imgPathLis[i].jsonPrimitive.content
            val imgWord = imgWordE.jsonPrimitive.content
            "$imgCdnHost$imgPath?$imgWord"
        }
            .map { Storage(link = it, source = chapterModel.url, quality = "Good", sub = "Yes") }
            .let { emitter.onSuccess(it) }
    }

    override val canScroll: Boolean = true
}