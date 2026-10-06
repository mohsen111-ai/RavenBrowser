// Raven's cookie popups, in every page and frame while the setting is on: the site's own "Reject all" is pressed on
// the common consent popups, and a popup that only lets you accept is hidden. It looks at nothing but the popup, and
// tells Raven only what it did (for the emulator tests). Started by media.js when Raven says the setting is on.
(() => {
  // The well-known consent popups: where the popup is, and its reject button(s) in the order to try them. [open]: a
  // popup whose reject sits one step in, behind its settings button. [late]: hide it only if it's still there later
  // (its reject is pressed in its own frame).
  const KNOWN = [
    { name: "OneTrust", box: "#onetrust-banner-sdk", reject: ["#onetrust-reject-all-handler", ".ot-pc-refuse-all-handler"], open: "#onetrust-pc-btn-handler", hide: "#onetrust-consent-sdk" },
    { name: "OneTrust", box: "#onetrust-pc-sdk", reject: [".ot-pc-refuse-all-handler", "#onetrust-reject-all-handler"], hide: "#onetrust-consent-sdk" },
    { name: "Cookiebot", box: "#CybotCookiebotDialog", reject: ["#CybotCookiebotDialogBodyButtonDecline", "#CybotCookiebotDialogBodyLevelButtonLevelOptinDeclineAll"] },
    { name: "Didomi", box: "#didomi-notice, #didomi-popup", reject: ["#didomi-notice-disagree-button", ".didomi-continue-without-agreeing", "#didomi-popup .didomi-button-standard"], hide: "#didomi-host" },
    { name: "Usercentrics", box: "#usercentrics-root", shadow: true, reject: ['button[data-testid="uc-deny-all-button"]'] },
    { name: "Usercentrics", box: "#uc-banner, #uc-center-container", reject: ['button[data-testid="uc-deny-all-button"]'] },
    { name: "TrustArc", box: "#truste-consent-track", reject: ["#truste-consent-required"] },
    { name: "Funding Choices", box: ".fc-consent-root", reject: [".fc-cta-do-not-consent", ".fc-cta-reject"] },
    { name: "Complianz", box: ".cmplz-cookiebanner", reject: [".cmplz-deny"] },
    { name: "CookieYes", box: ".cky-consent-container", reject: [".cky-btn-reject"] },
    { name: "Osano", box: ".osano-cm-dialog, .osano-cm-window", reject: [".osano-cm-denyAll", ".osano-cm-deny"] },
    { name: "iubenda", box: "#iubenda-cs-banner", reject: [".iubenda-cs-reject-btn"] },
    { name: "Klaro", box: ".klaro .cookie-notice, .klaro .cookie-modal", reject: [".cm-btn-decline", ".cn-decline"] },
    { name: "Termly", box: '[data-tid="banner-wrapper"], #termly-code-snippet-support', reject: ['[data-tid="banner-decline"]'] },
    { name: "Axeptio", box: "#axeptio_overlay", reject: ["#axeptio_btn_dismiss"] },
    { name: "Cookie Control", box: "#ccc", reject: ["#ccc-reject-settings", "#ccc-notify-reject"] },
    { name: "Borlabs", box: "#BorlabsCookieBox", reject: ["[data-cookie-refuse]", "._brlbs-refuse-btn a", "._brlbs-refuse"] },
    { name: "Cookie Notice", box: "#cookie-notice", reject: ["#cn-refuse-cookie"] },
    { name: "GDPR Cookie Compliance", box: "#moove_gdpr_cookie_info_bar", reject: [".moove-gdpr-infobar-reject-btn"] },
    { name: "CookieFirst", box: ".cookiefirst-root", reject: ['[data-cookiefirst-action="reject"]'] },
    { name: "Shopify", box: "#shopify-pc__banner", reject: ["#shopify-pc__banner__btn-decline"] },
    { name: "Ketch", box: "#lanyard_root", reject: ['button[aria-label*="Reject" i]'] },
    { name: "Quantcast", box: ".qc-cmp2-container", reject: [] },
    { name: "Sourcepoint", box: '[id^="sp_message_container"]', reject: [], late: true },
    { name: "Google", box: 'form[action*="consent.google"], form[action*="consent.youtube"], ytd-consent-bump-v2-lightbox, tp-yt-paper-dialog.ytd-consent-bump-v2-lightbox', reject: [] },
  ];

  // Words that say "no" on a reject button, in the languages Raven is most likely to meet.
  const NO = new RegExp(
    "^(reject|decline|deny|refuse|disagree|object|do not (accept|agree|consent|sell)|don'?t (accept|agree)|no,? thanks|" +
    "continue without (accepting|agreeing|consent)|(use |allow |accept )?(only )?(strictly )?(necessary|essential|required)( cookies)? only|" +
    "(use |allow |accept )?only (strictly )?(necessary|essential|required)|" +
    "alle ablehnen|ablehnen|nur (notwendige|essenzielle|erforderliche|technisch)|nicht einverstanden|" +
    "tout refuser|refuser|continuer sans accepter|je refuse|rechazar|solo (las )?necesarias|rifiuta|solo necessari|" +
    "alles weigeren|weigeren|alleen (noodzakelijke|functionele)|rejeitar|recusar|odrzuć|avvisa|neka|afvis|avvis|" +
    "tümünü reddet|reddet|отклонить|odmítnout|elutasít)",
    "i",
  );
  // Words that say "yes": a popup with only these is one to hide.
  const YES = /^(accept|agree|allow|i agree|i accept|got it|ok|okay|understood|alle akzeptieren|akzeptieren|zustimmen|einverstanden|tout accepter|accepter|j'accepte|aceptar|accetta|accetto|accepteren|akkoord|aceitar|zaakceptuj|godkänn|acceptera|kabul)/i;
  // What a consent popup talks about.
  const TOPIC = /cookie|consent|gdpr|datenschutz|einwilligung|consentement|consentimiento|consenso|toestemming|privacidade|zgod|samtycke|samtykke|rgpd|dsgvo/i;
  // Where else a popup may be: anything named after cookies or consent, and dialogs.
  const LIKELY = '[id*="cookie" i], [class*="cookie" i], [id*="consent" i], [class*="consent" i], [id*="gdpr" i], [class*="gdpr" i], ' +
    '[aria-modal="true"], [role="dialog"], [role="alertdialog"], [class*="cc-window"], [class*="cc-banner"]';
  const BUTTONS = 'button, a, [role="button"], input[type="button"], input[type="submit"]';

  let port = null;
  const done = new WeakSet();
  let actions = 0;
  const tell = (action, cmp) => {
    actions++;
    try { port && port.postMessage({ type: "consent", action, cmp }); } catch (e) {}
  };

  const visible = (el) => {
    if (!el || !el.isConnected) return false;
    const r = el.getBoundingClientRect();
    if (r.width < 2 || r.height < 2) return false;
    const s = getComputedStyle(el);
    return s.visibility !== "hidden" && s.display !== "none" && s.opacity !== "0";
  };
  const label = (b) => (b.value || b.getAttribute("aria-label") || b.textContent || "").replace(/\s+/g, " ").trim();
  const press = (b) => {
    done.add(b);
    try { b.click(); } catch (e) {}
  };

  // A popup that's gone away may have left the page unable to scroll.
  const unlockScrolling = () => {
    for (const el of [document.documentElement, document.body]) {
      if (el && getComputedStyle(el).overflowY === "hidden") el.style.setProperty("overflow", "auto", "important");
    }
  };
  const hide = (el, cmp) => {
    if (done.has(el)) return;
    done.add(el);
    el.style.setProperty("display", "none", "important");
    unlockScrolling();
    tell("hidden", cmp);
  };

  // The element (or one around it) that stays put on screen: the popup itself.
  const pinned = (el) => {
    for (let p = el; p && p !== document.body && p !== document.documentElement; p = p.parentElement) {
      const pos = getComputedStyle(p).position;
      if (pos === "fixed" || pos === "sticky") return p;
    }
    return null;
  };

  // A reject button among [root]'s buttons, by its words.
  const rejectByWords = (root) => {
    for (const b of root.querySelectorAll(BUTTONS)) {
      if (done.has(b) || !visible(b)) continue;
      const t = label(b);
      if (t.length > 0 && t.length <= 45 && NO.test(t)) return b;
    }
    return null;
  };
  const hasYes = (root) => Array.from(root.querySelectorAll(BUTTONS)).some((b) => { const t = label(b); return t.length <= 30 && YES.test(t); });

  const known = () => {
    for (const k of KNOWN) {
      for (const box of document.querySelectorAll(k.box)) {
        if (done.has(box)) continue;
        const root = k.shadow ? box.shadowRoot : box;
        if (!root || (!k.shadow && !visible(box))) continue;
        let b = null;
        for (const sel of k.reject) {
          b = Array.from(root.querySelectorAll(sel)).find((x) => !done.has(x) && visible(x));
          if (b) break;
        }
        if (!b) b = rejectByWords(root);
        if (b) {
          press(b);
          done.add(box);
          tell("rejected", k.name);
          setTimeout(unlockScrolling, 800);
          return true;
        }
        if (k.open) {
          const more = root.querySelector(k.open);
          if (more && !done.has(more) && visible(more)) { press(more); return true; }
        }
        if (k.late) {
          setTimeout(() => { if (box.isConnected && visible(box)) hide(box, k.name); }, 3000);
          done.add(box);
          continue;
        }
        hide(k.hide ? (document.querySelector(k.hide) || box) : box, k.name);
        return true;
      }
    }
    return false;
  };

  // Any other popup that talks about cookies or consent: its "no" button, or else hidden if it's one that stays
  // on screen and only offers "yes".
  const others = () => {
    const seen = new Set();
    for (const el of document.querySelectorAll(LIKELY)) {
      if (done.has(el) || seen.has(el) || !visible(el)) continue;
      // The outermost such element only.
      let outer = el;
      for (let p = el.parentElement; p; p = p.parentElement) if (p.matches && p.matches(LIKELY)) outer = p;
      if (seen.has(outer) || done.has(outer)) continue;
      seen.add(outer);
      const text = (outer.innerText || "").slice(0, 4000);
      if (text.length > 4000 || !TOPIC.test(text)) continue;
      const b = rejectByWords(outer);
      if (b) {
        press(b);
        done.add(outer);
        tell("rejected", "words");
        setTimeout(unlockScrolling, 800);
        return true;
      }
      const stays = pinned(outer);
      if (stays && hasYes(outer)) { hide(stays, "words"); return true; }
    }
    return false;
  };

  // A frame that is itself a consent popup (as some are, in their own frame): its "no" button.
  const frame = () => {
    if (window === window.top || !document.body) return false;
    const text = (document.body.innerText || "").slice(0, 4000);
    if (!TOPIC.test(text)) return false;
    const b = rejectByWords(document.body);
    if (b) { press(b); tell("rejected", "frame"); return true; }
    return false;
  };

  const look = () => {
    if (actions >= 6) return;
    try { known() || others() || frame(); } catch (e) {}
  };

  // Popups come late (after the page, often after a second or two): keep an eye out for a while, then stop.
  const start = () => {
    look();
    let pending = false;
    const observer = new MutationObserver(() => {
      if (pending) return;
      pending = true;
      setTimeout(() => { pending = false; look(); }, 400);
    });
    observer.observe(document.documentElement, { childList: true, subtree: true });
    setTimeout(() => observer.disconnect(), 20000);
    for (const t of [800, 2000, 4000, 8000]) setTimeout(look, t);
  };

  globalThis.ravenConsent = (p) => {
    if (port) return;
    port = p;
    if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", start, { once: true });
    else start();
  };
})();
