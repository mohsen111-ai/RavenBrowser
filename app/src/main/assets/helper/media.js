// Raven's helper, in every page and frame: Raven asks it to mute, unmute or pause the page's audio and video, or to
// show only the video (the floating tab's "Video only"), and starts consent.js (cookie popups) when that's on. It reads
// nothing from the page and sends nothing anywhere but back to Raven.
(() => {
  let port;
  try {
    port = browser.runtime.connectNative("raven");
  } catch (e) {
    return;
  }
  const top = window === window.top;
  let muted = false;

  // Sound that plays without being on the page (new Audio(), as music sites and games often do) isn't found by
  // looking through the page, so the page's play() is wrapped to note each player as it starts (the latest few).
  const played = new Set();
  const MAX_PLAYED = 24;
  try {
    const proto = window.wrappedJSObject.HTMLMediaElement.prototype;
    const pagePlay = proto.play;
    const ownPlay = HTMLMediaElement.prototype.play;
    exportFunction(function () {
      // Noting it must never stop the page's own play() from running.
      try {
        played.delete(this);
        played.add(this);
        if (played.size > MAX_PLAYED) played.delete(played.values().next().value);
        if (muted && !this.muted) mute(this);
      } catch (e) {}
      // play() takes nothing: passing this script's own `arguments` into the page's function made the page fail
      // with "Permission denied to access property length", and nothing played (emulator run 21).
      try {
        return pagePlay.call(this);
      } catch (e) {
        // Whatever goes wrong in between, the sound must still start.
        return ownPlay.call(this);
      }
    }, proto, { defineAs: "play" });
  } catch (e) {
    // Not in this kind of page (Raven's own pages): what's on the page is still found.
  }
  const media = () => {
    const list = Array.from(document.querySelectorAll("video, audio"));
    for (const el of played) if (!list.includes(el)) list.push(el);
    return list;
  };

  // Muting: only what Raven muted is unmuted again, so a video the page itself muted stays muted.
  const mute = (el) => {
    if (!el.muted) {
      el.dataset.ravenMuted = "1";
      el.muted = true;
    }
  };
  const unmute = (el) => {
    if (el.dataset.ravenMuted) {
      delete el.dataset.ravenMuted;
      el.muted = false;
    }
  };
  // Media that starts later, or that the page unmutes itself, while Raven wants this tab quiet.
  for (const type of ["play", "volumechange", "loadeddata"]) {
    document.addEventListener(type, (e) => {
      const el = e.target;
      if (muted && el instanceof HTMLMediaElement && !el.muted) mute(el);
    }, true);
  }

  // Video only: the page's biggest video (or, in a page whose video is in a frame, that frame) fills the view.
  const STYLE = "raven-video-only";
  const css = `
    html.raven-vo, html.raven-vo body { overflow: hidden !important; background: #000 !important; }
    .raven-vo-chain { transform: none !important; filter: none !important; contain: none !important; perspective: none !important;
      will-change: auto !important; clip-path: none !important; mask: none !important; opacity: 1 !important; z-index: 2147483646 !important; }
    .raven-vo-target { position: fixed !important; inset: 0 !important; width: 100vw !important; height: 100vh !important;
      max-width: none !important; max-height: none !important; margin: 0 !important; padding: 0 !important; border: 0 !important;
      transform: none !important; object-fit: contain !important; background: #000 !important; z-index: 2147483647 !important; }`;
  const area = (el) => { const r = el.getBoundingClientRect(); return r.width * r.height; };
  const biggest = (list) => list.reduce((best, el) => (!best || area(el) > area(best) ? el : best), null);
  const leaveVideoOnly = () => {
    document.getElementById(STYLE)?.remove();
    document.documentElement.classList.remove("raven-vo");
    document.querySelectorAll(".raven-vo-chain, .raven-vo-target").forEach((el) => el.classList.remove("raven-vo-chain", "raven-vo-target"));
  };
  const videoOnly = (el) => {
    leaveVideoOnly();
    const style = document.createElement("style");
    style.id = STYLE;
    style.textContent = css;
    (document.head || document.documentElement).appendChild(style);
    document.documentElement.classList.add("raven-vo");
    el.classList.add("raven-vo-target");
    for (let p = el.parentElement; p && p !== document.documentElement; p = p.parentElement) p.classList.add("raven-vo-chain");
  };

  // Tells Raven how many players a pause or mute reached, and how many still play.
  const done = (cmd) => {
    try {
      const list = media();
      port.postMessage({ type: "done", cmd, players: list.length, playing: list.filter((el) => !el.paused).length });
    } catch (e) {}
  };

  port.onMessage.addListener((m) => {
    switch (m.cmd) {
      case "consent":
        if (m.on && typeof globalThis.ravenConsent === "function") globalThis.ravenConsent(port);
        break;
      case "mute":
        muted = !!m.on;
        media().forEach(muted ? mute : unmute);
        done(m.cmd);
        break;
      case "pause":
        media().forEach((el) => { if (!el.paused) el.pause(); });
        done(m.cmd);
        break;
      case "toggle": {
        const list = media();
        const playing = list.filter((el) => !el.paused);
        if (playing.length) playing.forEach((el) => el.pause());
        else { const v = biggest(list.filter((el) => el.tagName === "VIDEO")) || list[0]; if (v) v.play().catch(() => {}); }
        break;
      }
      case "videoOnly": {
        if (!m.on) { leaveVideoOnly(); break; }
        const video = biggest(Array.from(document.querySelectorAll("video")).filter((v) => area(v) > 0));
        if (video) {
          videoOnly(video);
          port.postMessage({ type: "videoOnly", ok: true, w: video.videoWidth || video.clientWidth, h: video.videoHeight || video.clientHeight });
        } else if (top) {
          const frame = biggest(Array.from(document.querySelectorAll("iframe")).filter((f) => area(f) >= 20000));
          if (frame) {
            videoOnly(frame);
            port.postMessage({ type: "videoOnly", ok: true, w: 16, h: 9 });
          } else {
            port.postMessage({ type: "videoOnly", ok: false });
          }
        }
        break;
      }
    }
  });
})();
