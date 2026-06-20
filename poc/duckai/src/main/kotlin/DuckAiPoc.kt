import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.graalvm.polyglot.*

suspend fun main() {
    val client = HttpClient()
    println("=== Step 1: GET /duckchat/v1/status ===")

    val statusResponse = client.get("https://duckduckgo.com/duckchat/v1/status") {
        headers {
            append("Accept", "*/*")
            append("Accept-Language", "en-US,en;q=0.9")
            append("Cache-Control", "no-store")
            append("DNT", "1")
            append("Referer", "https://duckduckgo.com/")
            append("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            append("x-vqd-accept", "1")
            append("Cookie", "5=1; dcm=3; dcs=1")
        }
    }

    val challengeB64 = statusResponse.headers["x-vqd-hash-1"]
    println("Status: ${statusResponse.status}")
    println("Challenge (first 80 chars): ${challengeB64?.take(80)}...")

    if (challengeB64 == null) {
        println("No challenge received!")
        return
    }

    val challengeJs = String(Base64.getDecoder().decode(challengeB64))
    println("\n=== Step 2: Solve JS Challenge ===")

    val resultJson = solveChallenge(challengeJs)
    println("Challenge result: $resultJson")

    val resultB64 = Base64.getEncoder().encodeToString(resultJson.toByteArray())
    println("Base64 result (first 80 chars): ${resultB64.take(80)}...")

    println("\n=== Step 3: POST /duckchat/v1/chat ===")
    val chatPayload = """{"model":"gpt-4o-mini","messages":[{"role":"user","content":"Reply with just the word monkey and nothing else."}]}"""

    val chatResponse = client.post("https://duckduckgo.com/duckchat/v1/chat") {
        headers {
            append("Accept", "text/event-stream")
            append("Accept-Encoding", "gzip, deflate, br")
            append("Accept-Language", "en-US,en;q=0.9")
            append("Content-Type", "application/json")
            append("DNT", "1")
            append("Origin", "https://duckduckgo.com")
            append("Referer", "https://duckduckgo.com/")
            append("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            append("x-vqd-4", resultB64)
            append("x-vqd-hash-1", resultB64)
            append("Cookie", "5=1; dcm=3; dcs=1")
        }
        setBody(chatPayload)
    }

    println("Chat Status: ${chatResponse.status}")
    println("Chat Headers:")
    chatResponse.headers.forEach { name, value ->
        println("  $name: $value")
    }
    println("\nChat Body:")
    println(chatResponse.bodyAsText().take(500))
}

fun solveChallenge(jsCode: String): String {
    val resultRef = arrayOfNulls<String>(1)
    val latch = CountDownLatch(1)

    val context = Context.newBuilder("js")
        .allowAllAccess(true)
        .option("js.commonjs-require", "true")
        .build()

    try {
        val polyglotBindings = context.polyglotBindings
        polyglotBindings.putMember("javaResultRef", resultRef)
        polyglotBindings.putMember("javaLatch", latch)

        val mockGlobals = """
            const navigator = {
                userAgent: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36',
                webdriver: false,
                language: 'en-US',
                platform: 'MacIntel',
                hardwareConcurrency: 8,
                deviceMemory: 8,
                maxTouchPoints: 0,
                vendor: 'Google Inc.',
                vendorSub: '',
                productSub: '20030107',
                cookieEnabled: true,
                plugins: { length: 0, item: () => null },
                mimeTypes: { length: 0, item: () => null },
                permissions: { query: () => Promise.resolve({ state: 'granted' }) },
                mediaDevices: { enumerateDevices: () => Promise.resolve([]) },
                connection: { effectiveType: '4g', downlink: 10, rtt: 50 }
            };
            const screen = {
                width: 1920,
                height: 1080,
                availWidth: 1920,
                availHeight: 1030,
                colorDepth: 24,
                pixelDepth: 24,
                orientation: { type: 'landscape-primary', angle: 0 }
            };
            const location = {
                href: 'https://duckduckgo.com/',
                hostname: 'duckduckgo.com',
                host: 'duckduckgo.com',
                origin: 'https://duckduckgo.com',
                protocol: 'https:',
                pathname: '/',
                search: '',
                hash: ''
            };
            class MockElement {
                constructor() {
                    this.style = { zIndex: 'auto', display: '', position: '', visibility: '' };
                    this._offsetWidth = 0;
                    this._offsetHeight = 0;
                    this._scrollHeight = 0;
                    this._scrollWidth = 0;
                    this.textContent = '';
                    this.childNodes = { length: 0 };
                    this.children = { length: 0 };
                    this.classList = { contains: () => false, add: () => {}, remove: () => {} };
                    this.attributes = {};
                }
                getBoundingClientRect() {
                    return { width: 0, height: 0, top: 0, left: 0, right: 0, bottom: 0, x: 0, y: 0 };
                }
                get offsetWidth() { return this._offsetWidth; }
                get offsetHeight() { return this._offsetHeight; }
                get scrollHeight() { return this._scrollHeight; }
                get scrollWidth() { return this._scrollWidth; }
                setAttribute(k, v) { this.attributes[k] = v; }
                getAttribute(k) { return this.attributes[k] || null; }
                hasAttribute(k) { return k in this.attributes; }
                appendChild(c) { return c; }
                removeChild(c) { return c; }
                remove() {}
                insertBefore(n, r) { return n; }
                addEventListener() {}
                removeEventListener() {}
                closest() { return null; }
                matches() { return false; }
                contains() { return false; }
                querySelector() { return null; }
                querySelectorAll() { return []; }
                getElementsByTagName() { return []; }
                focus() {}
                blur() {}
                scroll() {}
                scrollIntoView() {}
                cloneNode() { return new MockElement(); }
            }
            class HTMLElement {}
            class HTMLDivElement extends HTMLElement {}
            class HTMLIFrameElement extends HTMLElement {}
            class HTMLHtmlElement extends HTMLElement {}
            class HTMLBodyElement extends HTMLElement {}
            class HTMLHeadElement extends HTMLElement {}
            class HTMLSpanElement extends HTMLElement {}
            class HTMLParagraphElement extends HTMLElement {}
            class HTMLAnchorElement extends HTMLElement {}
            class HTMLImageElement extends HTMLElement {}
            class HTMLInputElement extends HTMLElement {}
            class HTMLButtonElement extends HTMLElement {}
            class HTMLFormElement extends HTMLElement {}
            class HTMLScriptElement extends HTMLElement {}
            class HTMLStyleElement extends HTMLElement {}
            class HTMLLinkElement extends HTMLElement {}
            class HTMLMetaElement extends HTMLElement {}
            class HTMLCanvasElement extends HTMLElement {}
            class HTMLVideoElement extends HTMLElement {}
            class HTMLAudioElement extends HTMLElement {}
            class HTMLSourceElement extends HTMLElement {}
            class HTMLTableElement extends HTMLElement {}
            class HTMLTableCellElement extends HTMLElement {}
            class HTMLTableRowElement extends HTMLElement {}
            class HTMLUListElement extends HTMLElement {}
            class HTMLLIElement extends HTMLElement {}
            class HTMLSelectElement extends HTMLElement {}
            class HTMLTextAreaElement extends HTMLElement {}
            class HTMLOptionElement extends HTMLElement {}
            class SVGElement extends HTMLElement {}
            class SVGAnimatedString { toString() { return ''; } }
            var tagToClass = {
                div: HTMLDivElement,
                iframe: HTMLIFrameElement,
                html: HTMLHtmlElement,
                body: HTMLBodyElement,
                head: HTMLHeadElement,
                span: HTMLSpanElement,
                p: HTMLParagraphElement,
                a: HTMLAnchorElement,
                img: HTMLImageElement,
                input: HTMLInputElement,
                button: HTMLButtonElement,
                form: HTMLFormElement,
                script: HTMLScriptElement,
                style: HTMLStyleElement,
                link: HTMLLinkElement,
                meta: HTMLMetaElement,
                canvas: HTMLCanvasElement,
                video: HTMLVideoElement,
                audio: HTMLAudioElement,
                source: HTMLSourceElement,
                table: HTMLTableElement,
                td: HTMLTableCellElement,
                tr: HTMLTableRowElement,
                ul: HTMLUListElement,
                li: HTMLLIElement,
                select: HTMLSelectElement,
                textarea: HTMLTextAreaElement,
                option: HTMLOptionElement,
                svg: SVGElement
            };
            function createHTMLElement(tag) {
                const Cls = tagToClass[tag.toLowerCase()] || HTMLElement;
                const el = new MockElement();
                Object.setPrototypeOf(el, Cls.prototype);
                return el;
            }
            const _docBody = createHTMLElement('body');
            const _docElement = createHTMLElement('html');
            const document = {
                createElement: (tag) => createHTMLElement(tag),
                createElementNS: (ns, tag) => createHTMLElement(tag),
                body: _docBody,
                documentElement: _docElement,
                hidden: false,
                visibilityState: 'visible',
                addEventListener: () => {},
                removeEventListener: () => {},
                querySelector: () => null,
                querySelectorAll: () => [],
                getElementById: () => null,
                getElementsByClassName: () => [],
                getElementsByTagName: () => [],
                createTextNode: (t) => t,
                createComment: () => '',
                createDocumentFragment: () => ({ appendChild: () => {}, children: [] }),
                head: new MockElement(),
                cookie: '',
                domain: 'duckduckgo.com',
                referrer: '',
                title: '',
                URL: 'https://duckduckgo.com/',
                readyState: 'complete',
                characterSet: 'UTF-8',
                contentType: 'text/html',
                designMode: 'off',
                dir: 'ltr',
                compatMode: 'CSS1Compat',
                scrollingElement: new MockElement(),
                activeElement: new MockElement(),
                hasFocus: () => false
            };
            const getComputedStyle = (el, pseudo) => ({
                getPropertyValue: (p) => '',
                zIndex: 'auto',
                display: 'block',
                position: 'static',
                visibility: 'visible',
                opacity: '1',
                transform: 'none',
                width: '0px',
                height: '0px'
            });
            const require = () => undefined;
                div: HTMLDivElement,
                iframe: HTMLIFrameElement,
                html: HTMLHtmlElement,
                body: HTMLBodyElement,
                head: HTMLHeadElement,
                span: HTMLSpanElement,
                p: HTMLParagraphElement,
                a: HTMLAnchorElement,
                img: HTMLImageElement,
                input: HTMLInputElement,
                button: HTMLButtonElement,
                form: HTMLFormElement,
                script: HTMLScriptElement,
                style: HTMLStyleElement,
                link: HTMLLinkElement,
                meta: HTMLMetaElement,
                canvas: HTMLCanvasElement,
                video: HTMLVideoElement,
                audio: HTMLAudioElement,
                source: HTMLSourceElement,
                table: HTMLTableElement,
                td: HTMLTableCellElement,
                tr: HTMLTableRowElement,
                ul: HTMLUListElement,
                li: HTMLLIElement,
                select: HTMLSelectElement,
                textarea: HTMLTextAreaElement,
                option: HTMLOptionElement,
                svg: SVGElement
            };
            const globalThis = this;
            const window = globalThis;
            window.self = window;
            window.top = window;
            window.parent = window;
            window.opener = null;
            window.name = '';
            window.closed = false;
            window.frameElement = null;
            window.length = 0;
            window.innerWidth = 1920;
            window.innerHeight = 1080;
            window.outerWidth = 1920;
            window.outerHeight = 1080;
            window.devicePixelRatio = 1;
            window.scrollX = 0;
            window.scrollY = 0;
            window.pageXOffset = 0;
            window.pageYOffset = 0;
            window.screenX = 0;
            window.screenY = 0;
            window.screenLeft = 0;
            window.screenTop = 0;
            window.matchMedia = () => ({ matches: false, media: '', addListener: () => {}, removeListener: () => {} });
            window.getComputedStyle = getComputedStyle;
            window.requestAnimationFrame = () => 0;
            window.cancelAnimationFrame = () => {};
            window.requestIdleCallback = () => 0;
            window.cancelIdleCallback = () => {};
            window.setTimeout = (fn, ms) => { if (typeof fn === 'function') fn(); return 0; };
            window.clearTimeout = () => {};
            window.setInterval = () => 0;
            window.clearInterval = () => {};
            window.fetch = () => Promise.resolve({ json: () => Promise.resolve({}), text: () => Promise.resolve('') });
            window.performance = { now: () => 0, timing: { navigationStart: 0 } };
            if (typeof TextEncoder === 'undefined') {
                TextEncoder = class {
                    encode(s) { return new Uint8Array(s.length); }
                };
            }
            if (typeof TextDecoder === 'undefined') {
                TextDecoder = class { decode(b) { return ''; } };
            }
            if (typeof crypto === 'undefined') {
                crypto = { subtle: { digest: () => Promise.resolve(new ArrayBuffer(32)) } };
            }
            if (typeof btoa === 'undefined') {
                btoa = (s) => '';
            }
            if (typeof atob === 'undefined') {
                atob = (s) => '';
            }
            
            document.body.appendChild = (c) => c;
            document.body.removeChild = (c) => c;
            document.body.querySelector = () => null;
            document.body.querySelectorAll = () => [];
            document.documentElement.appendChild = (c) => c;
            document.documentElement.removeChild = (c) => c;
            document.documentElement.querySelector = () => null;
            document.documentElement.querySelectorAll = () => [];
            
            window.document = document;
            globalThis.document = document;
            self = window;
            top = window;
            parent = window;
        """.trimIndent()

        context.eval("js", mockGlobals)

        context.eval("js", """
            (async function() {
                try {
                    const _result = await ($jsCode);
                    const _json = JSON.stringify(_result);
                    Polyglot.import('javaResultRef')[0] = _json;
                } catch (e) {
                    Polyglot.import('javaResultRef')[0] = 'ERROR: ' + e.message + '\\n' + e.stack;
                }
                Polyglot.import('javaLatch').countDown();
            })();
        """.trimIndent())

        val resolved = latch.await(15, TimeUnit.SECONDS)
        context.close()

        if (!resolved) {
            throw RuntimeException("Challenge timed out")
        }

        val result = resultRef[0]
            ?: throw RuntimeException("No result from challenge")

        return result
    } catch (e: Exception) {
        context.close()
        println("Challenge solve error: ${e.message}")
        throw e
    }
}
