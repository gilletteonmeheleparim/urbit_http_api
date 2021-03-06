package urbit.http.api

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import okio.IOException
import java.util.*
import java.util.concurrent.TimeUnit


class Urbit(val code: String, val url: String) {
    var ship: String? = null
    val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.SECONDS).build()
    val sseclient = EventSources.createFactory(client)
    var cookie: String? = null
    var uid: Long? = null
    var lastEventId: Int = 0
    var channelUrl: String = "$url/~/channel/$uid"


    fun getEventId(): Int {
        lastEventId = lastEventId + 1
        return lastEventId
    }

    fun connect() {
        val formBody = FormBody.Builder()
            .add("password", code)
            .build()
        val request = Request.Builder()
            .url("$url/~/login")
            .post(formBody)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
            ship = response.header("set-cookie")
            ship = ship?.split("-", "=")?.get(1)

            cookie = response.header("set-cookie")
            cookie = cookie?.split(";")?.get(0)

            uid = Calendar.getInstance().timeInMillis
            channelUrl = "$url/~/channel/$uid"
        }
    }


    fun poke(ship: String, app: String, mark: String, j: String) {
        val putBody = """[{"id":${getEventId()},"action":"poke","ship":"$ship","app":"$app","mark":"$mark","json":$j}]"""
        val request = Request.Builder()
            .url(channelUrl)
            .header("Cookie", cookie!!)
            .put(putBody.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
        }
    }

    fun subscribe(ship: String, app: String, path: String) {
        val putBody = """[{"id":${getEventId()},"action":"subscribe","ship":"$ship","app":"$app","path":"$path"}]"""
        val request = Request.Builder()
            .url(channelUrl)
            .header("Cookie", cookie!!)
            .put(putBody.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")

        }
    }

    fun unsubscribe(subscription: Int) {
        val putBody = """[{"id":${getEventId()},"action":"unsubscribe","subscription":$subscription}]"""
        val request = Request.Builder()
            .url(channelUrl)
            .header("Cookie", cookie!!)
            .put(putBody.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
            println("unsubscribed $subscription")

        }
    }

    fun delete() {
        val putBody = """[{"id":${getEventId()},"action":"delete"}]"""
        val request = Request.Builder()
            .url(channelUrl)
            .header("Cookie", cookie!!)
            .put(putBody.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
            println("deleted channel")

        }
        //sseInit().cancel()
        //client.dispatcher.executorService.shutdown()
    }

    fun ack(eventId: String) {
        val body = """[{"id":${getEventId()},"action":"ack","event-id":${eventId.toInt()}}]"""
        val request = Request.Builder()
            .url(channelUrl)
            .header("Cookie", cookie!!)
            .put(body.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
        }
    }

    fun scry(app: String, path: String, mark: String): String {
        val request = Request.Builder()
                .url("$url/~/scry/$app$path.$mark")
                .header("Cookie", cookie!!)
                .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
            return response.body!!.string()
        }
    }

    fun spider(inputMark: String, outputMark: String, threadName: String, j: String) {
        val request = Request.Builder()
                .url("$url/spider/$inputMark/$threadName/$outputMark.json")
                .header("Cookie", cookie!!)
                .post(j.toRequestBody("application/json".toMediaType()))
                .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) throw IOException("Unexpected code $response")
                }
            }
        })
    }

    fun sseInit(): EventSource {
        val request = Request.Builder()
            .url(channelUrl)
            .header("Cookie", cookie!!)
            .header("Connection", "keep-alive")
            .build()
        return sseclient.newEventSource(request = request, listener = Sselistener())
    }

    inner class Sselistener : EventSourceListener() {
        override fun onOpen(eventSource: EventSource, response: Response) {
        }

        override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
            t?.printStackTrace()

        }

        override fun onClosed(eventSource: EventSource) {

        }

        override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
            ack(eventId = id!!)
            println(data)
        }

    }

}