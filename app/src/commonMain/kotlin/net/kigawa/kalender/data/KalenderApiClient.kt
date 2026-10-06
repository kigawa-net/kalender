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

/**
 * アカウントIDを表す値クラス（型安全性のため）
 */
@kotlinx.serialization.Serializable
@JvmInline
value class AccountId(val value: String)

/**
 * プロバイダIDを表す値クラス（型安全性のため）
 */
@kotlinx.serialization.Serializable
@JvmInline
value class ProviderId(val value: String)

@JvmName("fetchCalendarTokenByAccountId")
suspend fun fetchCalendarToken(accessToken: String, accountId: AccountId): String? {
    error("Platform-specific implementation required")
}

@JvmName("fetchCalendarTokenByProvider")
suspend fun fetchCalendarToken(accessToken: String, provider: ProviderId): String? {
    error("Platform-specific implementation required")
}
/**
 * 外部カレンダーアカウントの連携情報
 */
@kotlinx.serialization.Serializable
data class LinkedCalendarAccount(
    val id: String,
    val userId: String,
    val provider: String,
    val providerUserId: String,
    val email: String,
    val displayName: String?,
    val refreshToken: String?,
    val scopes: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * 互換性のための旧モデル（段階的移行用）
 */
@kotlinx.serialization.Serializable
data class LinkedAccount(
    val provider: String,
    val providerUserName: String?,
)

/**
 * kalenderバックエンド(server/)を呼び出すクライアント。
 * Keycloakのアカウント紐付け状況・カレンダーAPI用アクセストークンはこのバックエンドを経由して取得する。
 */
class KalenderApiClient(
    private val httpClient: HttpClient,
    private val baseUrl: String = "https://kalender-api.kigawa.net",
) {
    /**
     * 連携済み外部カレンダーアカウント一覧を取得
     */
    suspend fun fetchLinkedCalendarAccounts(accessToken: String): List<LinkedCalendarAccount> {
        val response = httpClient.get("$baseUrl/api/calendar-accounts") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        if (!response.status.isSuccess()) return emptyList()
        return Json.parseToJsonElement(response.bodyAsText()).jsonArray.map { itemEl ->
            val item = itemEl.jsonObject
            LinkedCalendarAccount(
                id = item["id"]?.jsonPrimitive?.contentOrNull() ?: "",
                userId = item["userId"]?.jsonPrimitive?.contentOrNull() ?: "",
                provider = item["provider"]?.jsonPrimitive?.contentOrNull() ?: "",
                providerUserId = item["providerUserId"]?.jsonPrimitive?.contentOrNull() ?: "",
                email = item["email"]?.jsonPrimitive?.contentOrNull() ?: "",
                displayName = item["displayName"]?.jsonPrimitive?.contentOrNull(),
                refreshToken = item["refreshToken"]?.jsonPrimitive?.contentOrNull(),
                scopes = item["scopes"]?.jsonArray?.map { it.jsonPrimitive?.contentOrNull() ?: "" } ?: emptyList(),
                createdAt = item["createdAt"]?.jsonPrimitive?.contentOrNull()?.toLongOrNull() ?: 0L,
                updatedAt = item["updatedAt"]?.jsonPrimitive?.contentOrNull()?.toLongOrNull() ?: 0L,
            )
        }
    }

    /**
     * 互換性のための旧API（段階的移行用）
     */
    @Deprecated("Use fetchLinkedCalendarAccounts instead", replaceWith = ReplaceWith("fetchLinkedCalendarAccounts(accessToken)"))
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
            "$baseUrl/api/calendar-accounts/${provider.encodeURLParameter()}/link" +
                "?redirectUri=${redirectUri.encodeURLParameter()}"
        ) {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        if (!response.status.isSuccess()) return null
        return Json.parseToJsonElement(response.bodyAsText()).jsonObject["accountLinkUrl"]
            ?.jsonPrimitive?.contentOrNull()
    }

    /** アカウント連携を解除する */
    suspend fun unlinkCalendarAccount(accessToken: String, accountId: AccountId): Boolean {
        val response = httpClient.delete("$baseUrl/api/calendar-accounts/${accountId.value.encodeURLParameter()}") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        return response.status.isSuccess()
    }

    /**
     * 互換性のための旧API（段階的移行用）
     */
    @Deprecated("Use unlinkCalendarAccount instead", replaceWith = ReplaceWith("unlinkCalendarAccount(accessToken, accountId)"))
    suspend fun unlinkAccount(accessToken: String, provider: ProviderId): Boolean {
        val response = httpClient.delete("$baseUrl/api/linked-accounts/${provider.value.encodeURLParameter()}") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        return response.status.isSuccess()
    }

    /** 紐付け済みIdP(Google/Microsoft)の実カレンダーAPIアクセストークンを取得する (accountId指定) */
    suspend fun fetchCalendarToken(accessToken: String, accountId: AccountId): String? {
        val response = httpClient.get("$baseUrl/api/calendar-accounts/${accountId.value.encodeURLParameter()}/token") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        if (response.status != HttpStatusCode.OK) return null
        return Json.parseToJsonElement(response.bodyAsText()).jsonObject["accessToken"]
            ?.jsonPrimitive?.contentOrNull()
    }

    /**
     * 互換性のための旧API（段階的移行用）
     */
    @Deprecated("Use fetchCalendarToken with AccountId instead", replaceWith = ReplaceWith("fetchCalendarToken(accessToken, AccountId(accountId))"))
    suspend fun fetchCalendarToken(accessToken: String, provider: ProviderId): String? {
        val response = httpClient.get("$baseUrl/api/calendar-token/${provider.value.encodeURLParameter()}") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        if (response.status != HttpStatusCode.OK) return null
        return Json.parseToJsonElement(response.bodyAsText()).jsonObject["accessToken"]
            ?.jsonPrimitive?.contentOrNull()
    }
}

private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? =
    if (this is kotlinx.serialization.json.JsonNull) null else content