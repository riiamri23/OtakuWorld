package com.programmersbox.anime_sources.anime

import androidx.annotation.WorkerThread
import com.programmersbox.anime_sources.ShowApi
import com.programmersbox.anime_sources.toJsoup
import com.programmersbox.models.ChapterModel
import com.programmersbox.models.InfoModel
import com.programmersbox.models.ItemModel
import com.programmersbox.models.Storage
import io.reactivex.Single
import okhttp3.OkHttpClient
import okio.ByteString.Companion.decodeBase64
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

object WcoDubbed : WcoStream("dubbed-anime-list") {
    override val serviceName: String get() = "WCO_DUBBED"
}

object WcoSubbed : WcoStream("subbed-anime-list") {
    override val serviceName: String get() = "WCO_SUBBED"
}

object WcoCartoon : WcoStream("cartoon-list") {
    override val serviceName: String get() = "WCO_CARTOON"
}

object WcoMovies : WcoStream("movie-list") {
    override val serviceName: String get() = "WCO_MOVIES"
}

object WcoOva : WcoStream("cartoon-list") {
    override val serviceName: String get() = "WCO_OVA"
}

abstract class WcoStream(allPath: String) : ShowApi(
    baseUrl = "https://www.wcostream.com",
    allPath = allPath,
    recentPath = "last-50-recent-release"
) {

    companion object {
        var RECENT_TYPE = true
    }

    override val canScroll: Boolean get() = false

    override fun searchList(searchText: CharSequence, page: Int, list: List<ItemModel>): Single<List<ItemModel>> {
        return if (searchText.isEmpty()) super.searchList(searchText, page, list)
        else Single.create<List<ItemModel>> {
            Jsoup.connect("$baseUrl/search")
                .data("catara", searchText.toString())
                .data("konuara", "series")
                .post()
                .select("div.cerceve")
                .map {
                    ItemModel(
                        title = it.select("a").attr("title"),
                        description = "",
                        imageUrl = it.select("img").attr("abs:src"),
                        url = it.select("a").attr("abs:href"),
                        source = this
                    )
                }
                .let(it::onSuccess)
        }
            .onErrorResumeNext(super.searchList(searchText, page, list))
    }

    override fun getRecent(doc: Document): Single<List<ItemModel>> = Single.create { emitter ->
        //https://www.wcostream.com/anime/mushi-shi-english-dubbed-guide
        //https://www.wcostream.com/wonder-egg-priority-episode-7-english-dubbed
        //https://www.wcostream.com/anime/wonder-egg-priority
        doc
            //.select("ul.items")
            .select("div.menulaststyle")
            .select("li")
            .select("a")
            //.alsoPrint()
            .map {
                ItemModel(
                    title = it.text(),
                    description = "",
                    imageUrl = it.select("div.img").select("img").attr("abs:src"),
                    url = if (RECENT_TYPE)
                        it.attr("abs:href")
                    else
                        Jsoup.connect(it.attr("abs:href")).get().select("div.ildate").select("a").attr("abs:href"),
                    source = this
                )
            }
            .let(emitter::onSuccess)
    }

    override fun getList(doc: Document): Single<List<ItemModel>> = Single.create { emitter ->
        doc
            .select("div.ddmcc")
            .select("li")
            .map {
                ItemModel(
                    title = it.select("a").text(),
                    description = "",
                    imageUrl = "",
                    url = it.select("a").attr("abs:href"),
                    source = this
                )
            }
            .let(emitter::onSuccess)
    }

    override fun getItemInfo(source: ItemModel, doc: Document): Single<InfoModel> = Single.create { emitter ->
        InfoModel(
            source = this,
            url = source.url,
            title = source.title,
            description = doc.select("div.iltext").text(),
            imageUrl = doc.select("div#cat-img-desc").select("img").attr("abs:src"),
            genres = doc.select("div#cat-genre").select("div.wcobtn").eachText(),
            chapters = doc.select("div#catlist-listview").select("ul").select("li")
                .map {
                    ChapterModel(
                        name = it.select("a").text(),
                        url = it.select("a").attr("abs:href"),
                        uploaded = "",
                        sourceUrl = source.url,
                        source = this
                    )
                },
            alternativeNames = emptyList()
        )
            .let(emitter::onSuccess)
    }

    override fun getSourceByUrl(url: String): Single<ItemModel> = Single.create {
        try {
            val doc = url.toJsoup()
            ItemModel(
                title = doc.select("div#content").select("h2[title]").text(),
                description = doc.select("div.iltext").text(),
                imageUrl = doc.select("div#cat-img-desc").select("img").attr("abs:src"),
                url = url,
                source = this
            )
                .let(it::onSuccess)
        } catch (e: Exception) {
            it.onError(e)
        }
    }

    override fun getChapterInfo(chapterModel: ChapterModel): Single<List<Storage>> = Single.create { emitter ->

        val f = Jsoup.connect(chapterModel.url)
            .header("X-Requested-With", "XMLHttpRequest")
            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win 64; x64; rv:69.0) Gecko/20100101 Firefox/69.0")
            .followRedirects(true)
            .get()
            .let {
                val q = StringBuilder()

                it
                    .select("meta[itemprop=embedURL]")
                    //.alsoPrint()
                    .next()
                    .toString()
                    .let {
                        val ending = " - ([0-9]+)".toRegex().find(it)?.groups?.get(1)?.value
                        "var (.*?) = \\[(.*?)\\];".toRegex().find(it)?.groups?.get(2)?.value
                            ?.replace("\"", "")
                            ?.split(",")
                            ?.forEach {
                                it.trim()
                                    .decodeBase64()
                                    ?.utf8()
                                    //.alsoPrint()
                                    ?.replace("[^0-9]+".toRegex(), "")
                                    //.alsoPrint()
                                    ?.toIntOrNull()
                                    ?.minus(ending?.toIntOrNull() ?: 20945957)
                                    ?.toChar()
                                    //.alsoPrint()
                                    ?.let { q.append(it) }
                            }
                    }
                //.alsoPrint()

                //println(q)

                val hiddenUrl = Jsoup.parse(q.toString()).select("iframe").attr("src")
                //println("$baseUrl$hiddenUrl")

                val q2 = Jsoup.connect("$baseUrl$hiddenUrl").get()
                //println(q2)

                val q3 = q2
                    .select("div#d-player")
                    .select("p.text-center")
                    .select("a")
                    .attr("href")
                //.replace("embed-adh", "embed-adh-html5")

                val d = if (q2.toString().contains("embed-adh-html5")) q3.replace("embed-adh", "embed-adh-html5") else q3

                val q4 = Jsoup.connect(d)
                    .followRedirects(true)
                    .get()
                    .select("source").attr("abs:src")

                //val u = "get\\(\"(.*?)\"\\)".toRegex()

                //if(u.containsMatchIn(q2.toString())) {

                //println(u)

                /*val g = getJsonApi<VideoBase>("$baseUrl${u.find(q2.toString())?.groups?.get(1)?.value}") {
                    header("Referer", "$baseUrl$hiddenUrl")
                    header(
                        "User-Agent",
                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_9_3) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/35.0.1916.47 Safari/537.36"
                    )
                    header("Accept", "* / *")
                    header("X-Requested-With", "XMLHttpRequest")
                }

                val quality = if (g?.hd?.isNotEmpty() == true) g.hd else g?.enc*/

                Storage(
                    link = getApiFinalUrl(q4),//q4,//"${g?.cdn}/getvid?evid=$quality",
                    source = chapterModel.url,
                    quality = "Good",
                    sub = "Yes"
                ).apply {
                    headers["Accept"] = "video/webm,video/ogg,video/*;q=0.9,application/ogg;q=0.7,audio/*;q=0.6,*/*;q=0.5"
                    /*headers["Host"] = "${g?.cdn}/getvid?evid=$quality"
                        .split("//")
                        .lastOrNull()
                        ?.split("/")
                        ?.firstOrNull()
                        ?.split("?")
                        ?.firstOrNull()
                        .toString()*/
                    headers["User-Agent"] =
                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_9_3) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/35.0.1916.47 Safari/537.36"
                    headers["X-Requested-With"] = "XMLHttpRequest"
                    headers["Referer"] = "$baseUrl$hiddenUrl".replace("https://wcostream.com", "https://www.wcostream.com")
                    headers["Mimetype"] = "video/mp4"
                }
                /*} else {
                    Storage(
                        link = "",
                        source = chapterModel.url,
                        quality = "Good",
                        sub = "Yes"
                    )
                }*/
            }
        //.also { println("-".repeat(50)) }
        //.also { println(it) }

        /*
        Storage(
                link = link,
                source = chapterModel.url,
                quality = "Good",
                sub = "Yes"
            )
         */

        emitter.onSuccess(listOf(f))
    }

    @WorkerThread
    private fun getApiFinalUrl(url: String, builder: okhttp3.Request.Builder.() -> Unit = {}): String? {
        val request = okhttp3.Request.Builder()
            .url(url)
            .apply(builder)
            .get()
            .build()
        val response = OkHttpClient().newCall(request).execute()
        return response.request.url.toString()
    }

    data class VideoBase(val enc: String?, val server: String?, val cdn: String?, val hd: String?)

}