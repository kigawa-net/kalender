@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package net.kigawa.kalender.data.auth

import kotlin.js.Promise

/**
 * Google Identity Services (GIS) の token client (実装上は暗黙的にアクセストークンを
 * 直接返すポップアップフロー) を使ってアクセストークンを取得する。
 * GoogleのOAuthトークンエンドポイントはSPA(クライアントシークレット無し)からの
 * 認可コード交換を正式にはサポートしていないため、PKCEではなくこの公式JS SDKを使う。
 */
@JsFun(
    """
    (clientId, scope) => {
        return new Promise((resolve, reject) => {
            function afterLoad() {
                try {
                    const client = globalThis.google.accounts.oauth2.initTokenClient({
                        client_id: clientId,
                        scope: scope,
                        callback: (resp) => {
                            if (resp.error) { reject(new Error(String(resp.error))); return; }
                            resolve(resp.access_token);
                        },
                        error_callback: (err) => {
                            reject(new Error(err && err.type ? String(err.type) : 'google_auth_error'));
                        },
                    });
                    client.requestAccessToken();
                } catch (e) { reject(e); }
            }
            if (globalThis.google && globalThis.google.accounts && globalThis.google.accounts.oauth2) {
                afterLoad();
            } else {
                const script = document.createElement('script');
                script.src = 'https://accounts.google.com/gsi/client';
                script.async = true;
                script.onload = afterLoad;
                script.onerror = () => reject(new Error('gsi_load_failed'));
                document.head.appendChild(script);
            }
        });
    }
    """
)
external fun jsRequestGoogleAccessToken(clientId: String, scope: String): Promise<JsString>

/** ブラウザでランダムなPKCE code_verifierを生成する (RFC7636準拠, base64url文字集合) */
@JsFun(
    """
    () => {
        const bytes = crypto.getRandomValues(new Uint8Array(32));
        let binary = '';
        for (let i = 0; i < bytes.byteLength; i++) binary += String.fromCharCode(bytes[i]);
        return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+${'$'}/, '');
    }
    """
)
external fun jsRandomPkceVerifier(): JsString

/** PKCE code_challenge (S256) を計算する */
@JsFun(
    """
    (verifier) => {
        const enc = new TextEncoder();
        const data = enc.encode(verifier);
        return crypto.subtle.digest('SHA-256', data).then((digest) => {
            const bytes = new Uint8Array(digest);
            let binary = '';
            for (let i = 0; i < bytes.byteLength; i++) binary += String.fromCharCode(bytes[i]);
            return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+${'$'}/, '');
        });
    }
    """
)
external fun jsPkceChallengeS256(verifier: String): Promise<JsString>

@JsFun("() => globalThis.location.origin + globalThis.location.pathname")
external fun jsRedirectUri(): JsString

@JsFun("(url) => { globalThis.location.href = url; }")
external fun jsNavigateTo(url: String)

@JsFun("() => globalThis.location.search")
external fun jsLocationSearch(): JsString

@JsFun("() => { const u = new URL(globalThis.location.href); u.search = ''; globalThis.history.replaceState({}, '', u.toString()); }")
external fun jsClearQueryParams()

@JsFun("(key) => globalThis.sessionStorage.getItem(key)")
external fun jsSessionStorageGet(key: String): JsString?

@JsFun("(key, value) => { globalThis.sessionStorage.setItem(key, value); }")
external fun jsSessionStorageSet(key: String, value: String)

@JsFun("(key) => { globalThis.sessionStorage.removeItem(key); }")
external fun jsSessionStorageRemove(key: String)

@JsFun(
    """
    (name) => {
        const p = new URLSearchParams(globalThis.location.search);
        const v = p.get(name);
        return v === null ? undefined : v;
    }
    """
)
external fun jsGetQueryParam(name: String): JsString?
