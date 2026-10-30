package net.kigawa.kalender.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Statement
import java.util.concurrent.ConcurrentHashMap

/**
 * kigawa-net共有Keycloakの1realmを、このバックエンドが仲介する対象とする。
 * アカウント紐付け状況そのものはKeycloak自身が保持する federated-identity が正であり、
 * このサービスは独自DBを持たず常にKeycloak Admin APIへ問い合わせる(二重管理を避けるため)。
 */
private val keycloakBaseUrl = System.getenv("KEYCLOAK_BASE_URL") ?: "https://user.kigawa.net"
private val keycloakRealm = System.getenv("KEYCLOAK_REALM") ?: "kigawa-net"
private val orgServiceClientId = System.getenv("KEYCLOAK_ORG_SERVICE_CLIENT_ID") ?: "kalender-org-service"
private val orgServiceClientSecret = System.getenv("KEYCLOAK_ORG_SERVICE_CLIENT_SECRET") ?: ""

// OAuth2 クライアント設定 (Google/Microsoft)
private val googleClientId = System.getenv("GOOGLE_CLIENT_ID") ?: ""
private val googleClientSecret = System.getenv("GOOGLE_CLIENT_SECRET") ?: ""
private val microsoftClientId = System.getenv("MICROSOFT_CLIENT_ID") ?: ""
private val microsoftClientSecret = System.getenv("MICROSOFT_CLIENT_SECRET") ?: ""
private val calendarRedirectUri = System.getenv("CALENDAR_REDIRECT_URI") ?: "https://kalender-api.kigawa.net/api/calendar-accounts/callback"

private val realmUrl get() = "$keycloakBaseUrl/realms/$keycloakRealm"

// H2インメモリデータベース接続
private val dbConnection: java.sql.Connection by lazy {
    val url = "jdbc:h2:mem:kalender;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
    DriverManager.getConnection(url, "sa", "").also { conn ->
        // テーブル作成
        conn.createStatement().use { stmt ->
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS calendar_accounts (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    user_id VARCHAR(255) NOT NULL,
                    provider VARCHAR(50) NOT NULL,
                    provider_user_id VARCHAR(255) NOT NULL,
                    email VARCHAR(255) NOT NULL,
                    display_name VARCHAR(255),
                    refresh_token TEXT,
                    scopes TEXT NOT NULL,
                    created_at BIGINT NOT NULL,
                    updated_at BIGINT NOT NULL
                )
            """.trimIndent())
        }
    }
}

// トークン更新キャッシュ (userId -> refreshToken)
private val refreshTokenCache = ConcurrentHashMap<String, String>()

@Serializable
data class LinkedAccount(val provider: String, val providerUserName: String?)

@Serializable
data class LinkAccountResponse(val accountLinkUrl: String?)

@Serializable
data class CalendarTokenResponse(val accessToken: String)

fun main() {
    // データベース初期化
    dbConnection // force initialization
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, module = Application::module).start(wait = true)
}

private val allowedWebOrigin = System.getenv("KALENDER_WEB_ORIGIN") ?: "https://kalender-62z.pages.dev"

fun Application.module() {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(CORS) {
        allowHost(allowedWebOrigin.removePrefix("https://").removePrefix("http://"), schemes = listOf("https"))
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowMethod(io.ktor.http.HttpMethod.Get)
        allowMethod(io.ktor.http.HttpMethod.Post)
        allowMethod(io.ktor.http.HttpMethod.Delete)
    }

    val httpClient = HttpClient(CIO) {
        install(ClientContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    routing {
        get("/health") {
            call.respondText("OK")
        }

        get("/api/linked-accounts") {
            val token = call.bearerToken()
            if (token == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "invalid or missing token"))
                return@get
            }
            val userId = fetchUserId(httpClient, token)
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "invalid or missing token"))
                return@get
            }
            val serviceToken = fetchServiceAccountToken(httpClient)
            if (serviceToken == null) {
                call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to "failed to obtain service account token"))
                return@get
            }
            call.respond(fetchLinkedAccounts(httpClient, serviceToken, userId))
        }

        post("/api/linked-accounts/{provider}/link") {
            val token = call.bearerToken()
            if (token == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "invalid or missing token"))
                return@post
            }
            val provider = call.parameters["provider"] as String?
            if (provider.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "missing provider"))
                return@post
            }
            val redirectUri = call.request.queryParameters["redirectUri"] as String?
            if (redirectUri.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "missing redirectUri"))
                return@post
            }
            val response = fetchAccountLinkUrl(httpClient, token, provider, redirectUri)
            if (response == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "invalid or missing token"))
                return@post
            }
            call.respond(response)
        }

        // calendar-accounts API パス: Google/MicrosoftのOAuth2承認URLを直接生成
        post("/api/calendar-accounts/{provider}/link") {
            val token = call.bearerToken()
            if (token == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "invalid or missing token"))
                return@post
            }
            val provider = call.parameters["provider"] as String?
            if (provider.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "missing provider"))
                return@post
            }
            val redirectUri = call.request.queryParameters["redirectUri"] as String?
            if (redirectUri.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "missing redirectUri"))
                return@post
            }
            val oauthUrl = generateOAuth2Url(provider, redirectUri)
            if (oauthUrl == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "unsupported provider: $provider"))
                return@post
            }
            call.respond(mapOf("accountLinkUrl" to oauthUrl))
        }

        delete("/api/linked-accounts/{provider}") {
            val token = call.bearerToken()
            if (token == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "invalid or missing token"))
                return@delete
            }
            val provider = call.parameters["provider"] as String?
            if (provider.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "missing provider"))
                return@delete
            }
            val success = unlinkAccount(httpClient, token, provider)
            if (!success) {
                call.respond(HttpStatusCode.BadGateway, mapOf("error" to "failed to unlink account"))
                return@delete
            }
            call.respond(HttpStatusCode.NoContent)
        }

        get("/api/calendar-token/{provider}") {
            val token = call.bearerToken()
            if (token == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "invalid or missing token"))
                return@get
            }
            val provider = call.parameters["provider"] as String?
            if (provider.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "missing provider"))
                return@get
            }
            val accessToken = fetchBrokeredAccessToken(httpClient, token, provider)
            if (accessToken == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "no linked account token for provider"))
                return@get
            }
            call.respond(CalendarTokenResponse(accessToken))
        }

        // カレンダー連携アカウント一覧取得
        get("/api/calendar-accounts") {
            val token = call.bearerToken()
            if (token == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "invalid or missing token"))
                return@get
            }
            val userId = fetchUserId(httpClient, token)
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "invalid or missing token"))
                return@get
            }
            val accounts = getCalendarAccounts(userId)
            call.respond(accounts)
        }

        // カレンダー連携アカウント解除
        delete("/api/calendar-accounts/{accountId}") {
            val token = call.bearerToken()
            if (token == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "invalid or missing token"))
                return@delete
            }
            val userId = fetchUserId(httpClient, token)
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "invalid or missing token"))
                return@delete
            }
            val accountId = call.parameters["accountId"] as String?
            if (accountId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "missing accountId"))
                return@delete
            }
            val success = deleteCalendarAccount(userId, accountId.toLong())
            if (!success) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "account not found"))
                return@delete
            }
            call.respond(HttpStatusCode.NoContent)
        }

        // カレンダーAPIトークン取得 (accountId指定)
        get("/api/calendar-accounts/{accountId}/token") {
            val token = call.bearerToken()
            if (token == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "invalid or missing token"))
                return@get
            }
            val userId = fetchUserId(httpClient, token)
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "invalid or missing token"))
                return@get
            }
            val accountId = call.parameters["accountId"] as String?
            if (accountId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "missing accountId"))
                return@get
            }
            val account = getCalendarAccountById(userId, accountId.toLong())
            if (account == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "account not found"))
                return@get
            }
            val providerToken = account["refreshToken"] as? String
            if (providerToken.isNullOrBlank()) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "no refresh token"))
                return@get
            }
            val provider = account["provider"] as? String ?: ""
            val accessToken = refreshProviderToken(httpClient, provider, providerToken)
            if (accessToken == null) {
                call.respond(HttpStatusCode.BadGateway, mapOf("error" to "failed to refresh token"))
                return@get
            }
            call.respond(CalendarTokenResponse(accessToken))
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.bearerToken(): String? =
    (request.header(HttpHeaders.Authorization)?.removePrefix("Bearer ")?.trim()?.takeIf { it.isNotBlank() }) as String?

/** ユーザー本人のアクセストークンをuserinfoエンドポイントで検証し、Keycloakユーザーid(sub)を返す */
private suspend fun fetchUserId(client: HttpClient, token: String): String? = try {
    val response: HttpResponse = client.get("$realmUrl/protocol/openid-connect/userinfo") {
        header(HttpHeaders.Authorization, "Bearer $token")
    }
    if (response.status != HttpStatusCode.OK) {
        null
    } else {
        Json.parseToJsonElement(response.bodyAsText()).jsonObject["sub"]?.jsonPrimitive?.content
    }
} catch (e: Exception) {
    null
}

/** バックエンド用サービスアカウント(kalender-org-service)のアクセストークンをClient Credentials Grantで取得する */
private suspend fun fetchServiceAccountToken(client: HttpClient): String? = try {
    val response = client.submitForm(
        url = "$realmUrl/protocol/openid-connect/token",
        formParameters = Parameters.build {
            append("grant_type", "client_credentials")
            append("client_id", orgServiceClientId)
            append("client_secret", orgServiceClientSecret)
        },
    )
    if (response.status != HttpStatusCode.OK) {
        null
    } else {
        Json.parseToJsonElement(response.bodyAsText()).jsonObject["access_token"]?.jsonPrimitive?.content
    }
} catch (e: Exception) {
    null
}

/** Keycloak Admin API の federated-identity 一覧を取得する(このサービスは独自DBを持たずKeycloakを情報源とする) */
private suspend fun fetchLinkedAccounts(client: HttpClient, serviceToken: String, userId: String): List<LinkedAccount> = try {
    val response = client.get("$keycloakBaseUrl/admin/realms/$keycloakRealm/users/$userId/federated-identity") {
        header(HttpHeaders.Authorization, "Bearer $serviceToken")
    }
    if (response.status != HttpStatusCode.OK) {
        emptyList()
    } else {
        Json.parseToJsonElement(response.bodyAsText()).jsonArray.map { itemEl ->
            val item = itemEl.jsonObject
            LinkedAccount(
                provider = item["identityProvider"]?.jsonPrimitive?.content ?: "",
                providerUserName = item["userName"]?.jsonPrimitive?.content,
            )
        }
    }
} catch (e: Exception) {
    emptyList()
}

/** Google/MicrosoftのOAuth2承認URLを生成する */
private fun generateOAuth2Url(provider: String, redirectUri: String): String? {
    return when (provider.lowercase()) {
        "google" -> {
            if (googleClientId.isBlank()) return null
            val state = java.util.UUID.randomUUID().toString()
            val encodedRedirect = java.net.URLEncoder.encode(redirectUri, "UTF-8")
            val encodedScope = java.net.URLEncoder.encode("https://www.googleapis.com/auth/calendar.readonly offline_access", "UTF-8")
            val encodedState = java.net.URLEncoder.encode("google:$state", "UTF-8")
            "https://accounts.google.com/o/oauth2/v2/auth" +
                "?response_type=code" +
                "&client_id=${java.net.URLEncoder.encode(googleClientId, "UTF-8")}" +
                "&redirect_uri=$encodedRedirect" +
                "&scope=$encodedScope" +
                "&state=$encodedState" +
                "&access_type=offline" +
                "&prompt=consent"
        }
        "microsoft" -> {
            if (microsoftClientId.isBlank()) return null
            val state = java.util.UUID.randomUUID().toString()
            val encodedRedirect = java.net.URLEncoder.encode(redirectUri, "UTF-8")
            val encodedScope = java.net.URLEncoder.encode("https://outlook.office.com/Calendars.Read offline_access", "UTF-8")
            val encodedState = java.net.URLEncoder.encode("microsoft:$state", "UTF-8")
            "https://login.microsoftonline.com/common/oauth2/v2.0/authorize" +
                "?response_type=code" +
                "&client_id=${java.net.URLEncoder.encode(microsoftClientId, "UTF-8")}" +
                "&redirect_uri=$encodedRedirect" +
                "&scope=$encodedScope" +
                "&state=$encodedState"
        }
        else -> null
    }
}

/** Keycloak Account REST API でアカウントリンク用URLを取得する(ユーザー本人のトークンで呼ぶ) */
@Deprecated("Use generateOAuth2Url instead")
private suspend fun fetchAccountLinkUrl(
    client: HttpClient,
    userToken: String,
    provider: String,
    redirectUri: String,
): LinkAccountResponse? = try {
    val response = client.get("$realmUrl/account/linked-accounts/$provider") {
        header(HttpHeaders.Authorization, "Bearer $userToken")
        url {
            parameters.append("redirectUri", redirectUri)
        }
    }
    if (response.status != HttpStatusCode.OK) {
        null
    } else {
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        LinkAccountResponse(accountLinkUrl = body["accountLinkUrl"]?.jsonPrimitive?.content)
    }
} catch (e: Exception) {
    null
}

/** Keycloak Account REST API でアカウント連携を解除する(ユーザー本人のトークンで呼ぶ) */
private suspend fun unlinkAccount(client: HttpClient, userToken: String, provider: String): Boolean = try {
    val response = client.delete("$realmUrl/account/linked-accounts/$provider") {
        header(HttpHeaders.Authorization, "Bearer $userToken")
    }
    response.status == HttpStatusCode.OK || response.status == HttpStatusCode.NoContent
} catch (e: Exception) {
    false
}

/**
 * Keycloakのbrokerトークンエンドポイントから、紐付け済みIdP(Google/Microsoft)の
 * 実アクセストークンを取得する(storeToken=trueがrealm側で設定されている前提)。
 */
private suspend fun fetchBrokeredAccessToken(client: HttpClient, userToken: String, provider: String): String? {
    return try {
        val response = client.get("$realmUrl/broker/$provider/token") {
            header(HttpHeaders.Authorization, "Bearer $userToken")
        }
        if (response.status != HttpStatusCode.OK) {
            val errorBody = response.bodyAsText()
            System.err.println("[fetchBrokeredAccessToken] provider=$provider status=${response.status.value} body=$errorBody")
            null
        } else {
            val text = response.bodyAsText()
            // Keycloakはブローカー先のトークンレスポンスをそのまま返すため、
            // JSON(Google等)またはフォームエンコード文字列(access_token=...&...)の両対応にする
            runCatching { Json.parseToJsonElement(text).jsonObject["access_token"]?.jsonPrimitive?.content }
                .getOrNull()
                ?: text.split("&")
                    .mapNotNull { pair -> pair.split("=", limit = 2).takeIf { it.size == 2 } }
                    .firstOrNull { it[0] == "access_token" }
                    ?.get(1)
        }
    } catch (e: Exception) {
        System.err.println("[fetchBrokeredAccessToken] provider=$provider error=${e.message}")
        null
    }
}


private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? =
    if (this is kotlinx.serialization.json.JsonNull) null else content

// ============================================================================
// カレンダー連携アカウントのデータベース操作
// ============================================================================

/**
 * ユーザーのカレンダー連携アカウント一覧を取得する
 */
private fun getCalendarAccounts(userId: String): List<Map<String, Any?>> {
    val sql = "SELECT * FROM calendar_accounts WHERE user_id = ?"
    val accounts = mutableListOf<Map<String, Any?>>()
    dbConnection.prepareStatement(sql).use { pstmt ->
        pstmt.setString(1, userId)
        pstmt.executeQuery().use { rs ->
            while (rs.next()) {
                val scopesStr = rs.getString("scopes")
                accounts.add(mapOf<String, Any?>(
                    "id" to rs.getLong("id"),
                    "userId" to rs.getString("user_id"),
                    "provider" to rs.getString("provider"),
                    "providerUserId" to rs.getString("provider_user_id"),
                    "email" to rs.getString("email"),
                    "displayName" to rs.getString("display_name"),
                    "refreshToken" to rs.getString("refresh_token"),
                    "scopes" to (scopesStr?.split(",") ?: emptyList()),
                    "createdAt" to rs.getLong("created_at"),
                    "updatedAt" to rs.getLong("updated_at"),
                ))
            }
        }
    }
    return accounts
}

/**
 * 指定されたaccountIdのアカウントを取得する
 */
private fun getCalendarAccountById(userId: String, accountId: Long): Map<String, Any?>? {
    val sql = "SELECT * FROM calendar_accounts WHERE user_id = ? AND id = ?"
    dbConnection.prepareStatement(sql).use { pstmt ->
        pstmt.setString(1, userId)
        pstmt.setLong(2, accountId)
        pstmt.executeQuery().use { rs ->
            if (rs.next()) {
                val scopesStr = rs.getString("scopes")
                return mapOf<String, Any?>(
                    "id" to rs.getLong("id"),
                    "userId" to rs.getString("user_id"),
                    "provider" to rs.getString("provider"),
                    "providerUserId" to rs.getString("provider_user_id"),
                    "email" to rs.getString("email"),
                    "displayName" to rs.getString("display_name"),
                    "refreshToken" to rs.getString("refresh_token"),
                    "scopes" to (scopesStr?.split(",") ?: emptyList()),
                    "createdAt" to rs.getLong("created_at"),
                    "updatedAt" to rs.getLong("updated_at"),
                )
            }
        }
    }
    return null
}

/**
 * カレンダー連携アカウントを削除する
 */
private fun deleteCalendarAccount(userId: String, accountId: Long): Boolean {
    val sql = "DELETE FROM calendar_accounts WHERE user_id = ? AND id = ?"
    dbConnection.prepareStatement(sql).use { pstmt ->
        pstmt.setString(1, userId)
        pstmt.setLong(2, accountId)
        return pstmt.executeUpdate() > 0
    }
}

/**
 * カレンダー連携アカウントを保存する
 */
private fun saveCalendarAccount(
    userId: String,
    provider: String,
    providerUserId: String,
    email: String,
    displayName: String?,
    refreshToken: String?,
    scopes: List<String>,
): Long {
    val now = System.currentTimeMillis()
    val insertSql = """
        INSERT INTO calendar_accounts (user_id, provider, provider_user_id, email, display_name, refresh_token, scopes, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    """.trimIndent()
    val selectSql = "SELECT MAX(id) FROM calendar_accounts WHERE user_id = ?"
    
    dbConnection.prepareStatement(insertSql).use { pstmt ->
        pstmt.setString(1, userId)
        pstmt.setString(2, provider)
        pstmt.setString(3, providerUserId)
        pstmt.setString(4, email)
        pstmt.setString(5, displayName)
        pstmt.setString(6, refreshToken)
        pstmt.setString(7, scopes.joinToString(","))
        pstmt.setLong(8, now)
        pstmt.setLong(9, now)
        pstmt.executeUpdate()
    }
    
    // INSERT後に最新のIDを取得
    dbConnection.prepareStatement(selectSql).use { pstmt ->
        pstmt.setString(1, userId)
        pstmt.executeQuery().use { rs ->
            if (rs.next()) {
                return rs.getLong(1)
            }
        }
    }
    throw RuntimeException("Failed to save calendar account")
}

/**
 * プロバイダのアクセストークンをrefreshTokenで更新する
 */
private suspend fun refreshProviderToken(client: HttpClient, provider: String, refreshToken: String): String? {
    return try {
        when (provider.lowercase()) {
            "google" -> {
                val response = client.submitForm(
                    url = "https://oauth2.googleapis.com/token",
                    formParameters = Parameters.build {
                        append("client_id", System.getenv("GOOGLE_CLIENT_ID") ?: "")
                        append("client_secret", System.getenv("GOOGLE_CLIENT_SECRET") ?: "")
                        append("grant_type", "refresh_token")
                        append("refresh_token", refreshToken)
                    },
                )
                if (response.status != HttpStatusCode.OK) {
                    null
                } else {
                    Json.parseToJsonElement(response.bodyAsText()).jsonObject["access_token"]?.jsonPrimitive?.content
                }
            }
            "microsoft" -> {
                val response = client.submitForm(
                    url = "https://login.microsoftonline.com/common/oauth2/v2.0/token",
                    formParameters = Parameters.build {
                        append("client_id", System.getenv("MICROSOFT_CLIENT_ID") ?: "")
                        append("client_secret", System.getenv("MICROSOFT_CLIENT_SECRET") ?: "")
                        append("grant_type", "refresh_token")
                        append("refresh_token", refreshToken)
                        append("scope", "https://outlook.office.com/Calendars.Read offline_access")
                    },
                )
                if (response.status != HttpStatusCode.OK) {
                    null
                } else {
                    Json.parseToJsonElement(response.bodyAsText()).jsonObject["access_token"]?.jsonPrimitive?.content
                }
            }
            else -> null
        }
    } catch (e: Exception) {
        System.err.println("[refreshProviderToken] provider=$provider error=${e.message}")
        null
    }
}
