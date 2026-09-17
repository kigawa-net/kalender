package net.kigawa.kalender.data

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class LinkedAccount(val provider: String, val providerUserName: String?)

/**
 * kalenderバックエンド(server/)を呼び出すクライアント。Keycloakのアカウント紐付け状況・
 * カレンダーAPI用アクセストークンはこのバックエンドを経由して取得する(独自DBは持たずKeycloakが情報源)。
 */
class KalenderApiClient(
    private val httpClient: HttpClient,
    private val baseUrl: String = "https://kalender-api.kigawa.net",
) {
    suspend fun fetchLinkedAccounts(accessToken: String): List<LinkedAccount> {
        val response = httpClient.get("$baseUrl/api/linked-accounts") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        if (!response.status.isSuccess()) return emptyList()
        return Json.parseToJsonElement(response.bodyAsText()).jsonArray.map { itemEl ->
            val item = itemEl.jsonObject
            LinkedAccount(
                provider = item["provider"]?.jsonPrimitive?.content ?: "",
                providerUserName = item["providerUserName"]?.jsonPrimitive?.contentOrNull(),
            )
        }
    }

    /** アカウントリンク用URLを取得する。呼び出し側はこのURLへユーザーをリダイレクトさせる */
    suspend fun fetchAccountLinkUrl(accessToken: String, provider: String, redirectUri: String): String? {
        val response = httpClient.post(
            "$baseUrl/api/linked-accounts/${provider.encodeURLParameter()}/link" +
                "?redirectUri=${redirectUri.encodeURLParameter()}"
        ) {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        if (!response.status.isSuccess()) return null
        return Json.parseToJsonElement(response.bodyAsText()).jsonObject["accountLinkUrl"]
            ?.jsonPrimitive?.contentOrNull()
    }

    /** アカウント連携を解除する */
    suspend fun unlinkAccount(accessToken: String, provider: String): Boolean {
        val response = httpClient.delete("$baseUrl/api/linked-accounts/${provider.encodeURLParameter()}") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        return response.status.isSuccess()
    }

    /** 紐付け済みIdP(Google/Microsoft)の実カレンダーAPIアクセストークンを取得する */
    suspend fun fetchCalendarToken(accessToken: String, provider: String): String? {
        val response = httpClient.get("$baseUrl/api/calendar-token/${provider.encodeURLParameter()}") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        if (response.status != HttpStatusCode.OK) return null
        return Json.parseToJsonElement(response.bodyAsText()).jsonObject["accessToken"]
            ?.jsonPrimitive?.contentOrNull()
    }
}

private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? =
    if (this is kotlinx.serialization.json.JsonNull) null else content
