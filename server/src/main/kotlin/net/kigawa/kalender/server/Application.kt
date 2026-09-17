package net.kigawa.kalender.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
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
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * kigawa-net共有Keycloakの1realmを、このバックエンドが仲介する対象とする。
 * アカウント紐付け状況そのものはKeycloak自身が保持する federated-identity が正であり、
 * このサービスは独自DBを持たず常にKeycloak Admin APIへ問い合わせる(二重管理を避けるため)。
 */
private val keycloakBaseUrl = System.getenv("KEYCLOAK_BASE_URL") ?: "https://user.kigawa.net"
private val keycloakRealm = System.getenv("KEYCLOAK_REALM") ?: "kigawa-net"
private val orgServiceClientId = System.getenv("KEYCLOAK_ORG_SERVICE_CLIENT_ID") ?: "kalender-org-service"
private val orgServiceClientSecret = System.getenv("KEYCLOAK_ORG_SERVICE_CLIENT_SECRET") ?: ""

private val realmUrl get() = "$keycloakBaseUrl/realms/$keycloakRealm"

@Serializable
data class LinkedAccount(val provider: String, val providerUserName: String?)

@Serializable
data class LinkAccountResponse(val accountLinkUrl: String?)

@Serializable
data class CalendarTokenResponse(val accessToken: String)

fun main() {
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
        allowMethod(io.ktor.http.HttpMethod.Post)
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
            val provider = call.parameters["provider"]
            if (provider.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "missing provider"))
                return@post
            }
            val redirectUri = call.request.queryParameters["redirectUri"]
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

        get("/api/calendar-token/{provider}") {
            val token = call.bearerToken()
            if (token == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "invalid or missing token"))
                return@get
            }
            val provider = call.parameters["provider"]
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
    }
}

private fun io.ktor.server.application.ApplicationCall.bearerToken(): String? =
    request.header(HttpHeaders.Authorization)?.removePrefix("Bearer ")?.trim()?.takeIf { it.isNotBlank() }

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

/** Keycloak Account REST API でアカウントリンク用URLを取得する(ユーザー本人のトークンで呼ぶ) */
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

/**
 * Keycloakのbrokerトークンエンドポイントから、紐付け済みIdP(Google/Microsoft)の
 * 実アクセストークンを取得する(storeToken=trueがrealm側で設定されている前提)。
 */
private suspend fun fetchBrokeredAccessToken(client: HttpClient, userToken: String, provider: String): String? = try {
    val response = client.get("$realmUrl/broker/$provider/token") {
        header(HttpHeaders.Authorization, "Bearer $userToken")
    }
    if (response.status != HttpStatusCode.OK) {
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
    null
}
