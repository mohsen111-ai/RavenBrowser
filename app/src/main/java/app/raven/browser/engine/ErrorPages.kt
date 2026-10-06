package app.raven.browser.engine

import android.net.Uri
import android.util.Base64
import org.mozilla.geckoview.WebRequestError

/** Space-themed error pages, built as self-contained data: pages (no network, no scripts). */
object ErrorPages {
    const val ALLOW_HTTP = "raven://allow-http"

    /** Start of every error page's address, so it isn't shown in the address bar or history. */
    const val PREFIX = "data:text/html;charset=utf-8;base64,"

    fun dataUri(uri: String?, error: WebRequestError): String {
        val url = uri.orEmpty()
        val host = Uri.parse(url).host?.removePrefix("www.") ?: url
        val page = when (error.code) {
            WebRequestError.ERROR_HTTPS_ONLY -> https(url, host)
            WebRequestError.ERROR_UNKNOWN_HOST, WebRequestError.ERROR_OFFLINE -> page(
                "Can't reach $host",
                "Your phone seems to be offline, or this address doesn't exist. Check Wi-Fi or mobile data and try again.",
                url, "Server not found", broken = true,
            )
            WebRequestError.ERROR_NET_TIMEOUT -> page("$host is taking too long", "The site didn't answer in time. It may be busy or down.", url, "Connection timed out", broken = true)
            WebRequestError.ERROR_CONNECTION_REFUSED, WebRequestError.ERROR_NET_RESET, WebRequestError.ERROR_NET_INTERRUPT ->
                page("Couldn't connect to $host", "The connection was refused or interrupted. Try again in a moment.", url, "Connection failed", broken = true)
            WebRequestError.ERROR_SECURITY_BAD_CERT, WebRequestError.ERROR_SECURITY_SSL, WebRequestError.ERROR_BAD_HSTS_CERT ->
                page("This connection isn't private", "$host has a security certificate problem, so Raven stopped loading it. Someone could be trying to read or change what you send.", null, "Certificate error", warn = true)
            WebRequestError.ERROR_MALFORMED_URI, WebRequestError.ERROR_UNKNOWN_PROTOCOL ->
                page("This address doesn't look right", "Check the address for typos.", null, "Invalid address")
            WebRequestError.ERROR_REDIRECT_LOOP -> page("$host keeps redirecting", "The page is sending you in circles. Clearing cookies for this site sometimes helps.", url, "Redirect loop")
            WebRequestError.ERROR_SAFEBROWSING_MALWARE_URI, WebRequestError.ERROR_SAFEBROWSING_PHISHING_URI,
            WebRequestError.ERROR_SAFEBROWSING_UNWANTED_URI, WebRequestError.ERROR_SAFEBROWSING_HARMFUL_URI ->
                page("This site may be harmful", "$host has been reported as deceptive or dangerous.", null, "Blocked for your safety", warn = true)
            WebRequestError.ERROR_CONTENT_CRASHED -> page("This page crashed", "Something went wrong while showing it.", url, "Page crashed")
            else -> page("Couldn't open this page", "Something went wrong while loading $host.", url, "Error ${error.code}")
        }
        return PREFIX + Base64.encodeToString(page.toByteArray(), Base64.NO_WRAP)
    }

    private fun https(url: String, host: String): String {
        val http = url.replaceFirst("https://", "http://")
        val allow = ALLOW_HTTP + "?u=" + Uri.encode(http)
        return shell(
            "$host has no secure version",
            planet = """<div class="planet amber"></div><div class="ring amber open"></div>""",
            body = """
                <h1>${esc(host)} has no secure version</h1>
                <p>HTTPS-only mode stopped this page. On an unsecure connection, people on the same network could see or change what you read and type here.</p>
                <a class="btn primary" href="javascript:history.back()">Go back</a>
                <a class="btn amber" href="${esc(allow)}">Open the unsecure site anyway</a>
                <p class="small">Raven will go back to secure-only as soon as the page has loaded.</p>
            """,
        )
    }

    private fun page(title: String, text: String, retry: String?, detail: String, broken: Boolean = false, warn: Boolean = false): String {
        val planet = when {
            warn -> """<div class="planet amber"></div><div class="ring amber open"></div>"""
            broken -> """<div class="planet dim"></div><div class="ring dashed"></div><div class="sat"></div>"""
            else -> """<div class="planet dim"></div><div class="ring"></div>"""
        }
        val buttons = buildString {
            if (retry != null) append("""<a class="btn primary" href="${esc(retry)}">Try again</a>""")
            append("""<div class="detail"><span>Details</span>${esc(detail)}</div>""")
        }
        return shell(title, planet, "<h1>${esc(title)}</h1><p>${esc(text)}</p>$buttons")
    }

    private fun shell(title: String, planet: String, body: String) = """<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><meta name="color-scheme" content="dark"><title>${esc(title)}</title>
<style>
html,body{margin:0;background:#070A12;color:#E9ECF3;font:15px/1.6 system-ui,sans-serif}
body{min-height:100vh;display:flex;flex-direction:column;align-items:center;padding:12vh 28px 40px;box-sizing:border-box;text-align:center}
.scene{position:relative;width:220px;height:110px;margin-bottom:34px}
.planet{position:absolute;left:74px;top:19px;width:72px;height:72px;border-radius:50%;background:radial-gradient(circle at 42% 40%,#DCE0E8,#B8BFCC 70%,#8E97A8);box-shadow:0 0 60px rgba(199,204,216,.22)}
.planet.amber{background:radial-gradient(circle at 42% 40%,#F4E3B5,#E6C77E 70%,#B49250);box-shadow:0 0 60px rgba(230,199,126,.3)}
.planet.dim{background:#070A12;box-shadow:0 0 0 1.5px rgba(233,236,243,.85),0 0 46px rgba(154,140,255,.28)}
.ring{display:none}
.sat{position:absolute;right:56px;top:28px;width:6px;height:6px;border-radius:50%;background:#fff;box-shadow:0 0 14px 3px rgba(233,236,243,.5)}
h1{font:800 24px/1.25 system-ui,sans-serif;letter-spacing:-.3px;margin:0 0 10px}p{color:#9098AC;margin:0 0 24px;max-width:420px}
.btn{display:block;width:100%;max-width:360px;box-sizing:border-box;padding:15px;border-radius:999px;text-decoration:none;font-weight:700;margin:0 auto 12px}
.primary{background:#E9ECF3;color:#070A12}.amber{border:1px solid rgba(230,199,126,.5);color:#F0DCA8;font-weight:600}
.detail{max-width:360px;margin:6px auto 0;padding:12px 18px;border-radius:22px;background:#0D111C;border:1px solid rgba(199,204,216,.12);text-align:left;font-size:13px}
.detail span{display:block;color:#9098AC;font-size:12px}.small{font-size:12px;margin-top:8px}
</style></head><body><div class="scene">$planet</div>$body</body></html>"""

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
