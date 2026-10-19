// Leftovers B, phones: the tour as a gallery of the app's screens.
// One spring holds the position (in screens, so 2.5 is halfway between the third and fourth). The finger moves it 1:1,
// a flick carries it to the next screen, and the tabs, dots and neighbours all follow the same number, so nothing jumps.
(() => {
  "use strict";
  const { Spring, loop, project, rubber, tracker, clamp, buzz, reduceMotion } = window.LO;
  const $ = (s, el = document) => el.querySelector(s);
  const gallery = $("#gallery"), view = $("#g-view"), tabsEl = $("#g-tabs"), ind = $("#g-ind"), dotsEl = $("#g-dots"), playBtn = $("#g-play");
  const steps = [...document.querySelectorAll(".step")];
  const n = steps.length;
  const shotUrl = (name) => `assets/${document.documentElement.dataset.theme === "light" ? "light/" : ""}${name}.jpg`;

  // Build the screens, tabs and dots from the tour's steps, so there's one place to edit the words.
  const slides = steps.map((s, i) => {
    const fig = document.createElement("figure");
    fig.className = "g-slide";
    fig.id = `g-slide-${i}`;
    fig.setAttribute("role", "tabpanel");
    fig.setAttribute("aria-roledescription", "slide");
    fig.setAttribute("aria-label", `${i + 1} of ${n}: ${$("h3", s).textContent}`);
    fig.innerHTML = `<div class="g-phone"><img class="g-shot" alt="" width="540" height="1200" decoding="async"></div>
      <figcaption><h3>${$("h3", s).textContent}</h3><p>${$("p", s).textContent}</p></figcaption>`;
    $(".g-shot", fig).dataset.name = s.dataset.shot;
    $(".g-shot", fig).alt = $(".inline-shot", s)?.alt || "";
    view.append(fig);
    return { el: fig, phone: $(".g-phone", fig), cap: $("figcaption", fig) };
  });
  const tabs = steps.map((s, i) => {
    const b = document.createElement("button");
    b.type = "button";
    b.className = "g-tab";
    b.setAttribute("role", "tab");
    b.setAttribute("aria-controls", `g-slide-${i}`);
    b.textContent = $("h3", s).textContent;
    b.addEventListener("click", () => { stopAuto(); go(i); });
    tabsEl.append(b);
    return b;
  });
  const dots = steps.map(() => { const d = document.createElement("span"); d.innerHTML = "<i></i>"; dotsEl.append(d); return d; });

  // The screens load once the gallery is close, and all together, so swiping never shows a blank one.
  new IntersectionObserver((en, obs) => {
    if (!en[0].isIntersecting) return;
    slides.forEach((sl) => { const img = $(".g-shot", sl.el); img.src = shotUrl(img.dataset.name); });
    obs.disconnect();
  }, { rootMargin: "100% 0px" }).observe(gallery);

  const GAP = 16; // between screens
  const pos = new Spring(0);
  let index = 0, step = 0, mid = 0;
  let drag = null; // the swipe in progress, if any
  let playing = !reduceMotion, inView = false, progress = 0;
  const measure = () => { step = slides[0].el.offsetWidth + GAP; mid = view.clientWidth / 2; };

  function draw() {
    if (!view.clientWidth) return; // hidden on wider screens
    const p = pos.x;
    slides.forEach((sl, i) => {
      const d = i - p, a = Math.min(Math.abs(d), 1);
      // Neighbours sit a little smaller and quieter, and come forward as they reach the middle.
      sl.el.style.transform = `translate3d(${mid - sl.el.offsetWidth / 2 + d * step}px, 0, 0)`;
      sl.phone.style.transform = `scale(${1 - a * 0.1})`;
      sl.phone.style.filter = a ? `brightness(${1 - a * 0.12}) saturate(${1 - a * 0.3})` : "";
      sl.cap.style.opacity = String(Math.max(0, 1 - Math.abs(d) * 1.6));
    });
    // The tab highlight slides between tabs with the screens, not after them.
    const lo = clamp(Math.floor(p), 0, n - 1), hi = clamp(lo + 1, 0, n - 1), f = clamp(p - lo, 0, 1);
    const L = tabs[lo], H = tabs[hi];
    const left = L.offsetLeft + (H.offsetLeft - L.offsetLeft) * f, width = L.offsetWidth + (H.offsetWidth - L.offsetWidth) * f;
    ind.style.transform = `translateX(${left}px)`;
    ind.style.width = `${width}px`;
    dots.forEach((d, i) => { d.style.width = `${8 + 18 * Math.max(0, 1 - Math.abs(i - p))}px`; });
  }

  function settle(i) {
    if (i === index) return;
    index = i;
    tabs.forEach((t, k) => { t.setAttribute("aria-selected", String(k === i)); t.tabIndex = k === i ? 0 : -1; });
    slides.forEach((s, k) => s.el.setAttribute("aria-hidden", String(k !== i)));
    // Keep the chosen tab in view in the row.
    const t = tabs[i], want = t.offsetLeft - (tabsEl.clientWidth - t.offsetWidth) / 2;
    tabsEl.scrollTo({ left: want, behavior: reduceMotion ? "auto" : "smooth" });
  }
  function go(i, velocity = pos.v, bouncy = false) {
    i = clamp(i, 0, n - 1);
    settle(i);
    progress = 0;
    pos.to(i, { response: 0.42, damping: bouncy ? 0.86 : 1, velocity });
  }

  // ---- Playing by itself: the bar in the dot fills, then it moves on. A touch stops it; the button starts it again.
  const DWELL = 4.5;
  const setPlaying = (on) => {
    playing = on;
    gallery.classList.toggle("paused", !on);
    playBtn.setAttribute("aria-label", on ? "Pause" : "Play");
    if (on) loop.wake();
  };
  const stopAuto = () => { if (playing) setPlaying(false); };
  playBtn.addEventListener("click", () => {
    if (!playing && index === n - 1 && progress >= 1) go(0);
    setPlaying(!playing);
  });
  new IntersectionObserver((en) => { inView = en[0].isIntersecting; if (inView) loop.wake(); }, { threshold: 0.6 }).observe(view);
  document.addEventListener("visibilitychange", () => { if (!document.hidden) loop.wake(); });

  loop.add((dt) => {
    let busy = pos.step(dt);
    if (playing && inView && !drag && !document.hidden && view.clientWidth) {
      progress += dt / DWELL;
      if (progress >= 1) {
        if (index < n - 1) go(index + 1);
        else { progress = 0; go(0); } // back to the start, the way it came
      }
      busy = true;
    }
    dots.forEach((d, i) => { d.firstChild.style.transform = `scaleX(${i === index ? (playing ? progress : 1) : 0})`; });
    draw();
    return busy;
  });

  // ---- Swiping: sideways belongs to the gallery, up and down still scrolls the page.
  view.addEventListener("pointerdown", (e) => {
    if (e.button !== 0) return;
    measure();
    drag = { id: e.pointerId, x0: e.clientX, y0: e.clientY, p0: pos.x, from: index, locked: false, track: tracker(), moved: false };
    drag.track.add(e);
    pos.set(pos.x); // caught where it is, even mid-way
  });
  view.addEventListener("pointermove", (e) => {
    if (!drag || e.pointerId !== drag.id) return;
    drag.track.add(e);
    const dx = e.clientX - drag.x0, dy = e.clientY - drag.y0;
    if (!drag.locked) {
      if (Math.hypot(dx, dy) < 8) return;
      if (Math.abs(dy) > Math.abs(dx)) { release(e, true); return; } // that's a scroll
      drag.locked = true;
      drag.moved = true;
      view.setPointerCapture(e.pointerId);
      stopAuto();
    }
    let p = drag.p0 - dx / step;
    // Past the first or last screen it gives a little, and pulls back.
    if (p < 0) p = -rubber(-p * step, view.clientWidth) / step;
    else if (p > n - 1) p = n - 1 + rubber((p - n + 1) * step, view.clientWidth) / step;
    pos.set(p);
    draw();
  });
  view.addEventListener("touchmove", (e) => { if (drag && drag.locked) e.preventDefault(); }, { passive: false });
  function release(e, cancelled = false) {
    if (!drag || e.pointerId !== drag.id) return;
    const d = drag;
    drag = null;
    if (!d.locked) {
      // A tap on a neighbour brings it to the middle.
      if (!cancelled && e.type === "pointerup") {
        const hit = slides.findIndex((s) => s.el.contains(e.target));
        if (hit >= 0 && hit !== index) { stopAuto(); go(hit, 0); }
        else pos.to(index, { response: 0.42 });
      } else pos.to(index, { response: 0.42 });
      return;
    }
    const v = -d.track.speed().x / step; // screens per second
    // Where the flick is heading decides, one screen at a time, like turning a page.
    const landing = pos.x + project(v * step, 0.99) / step;
    const target = clamp(Math.round(landing), d.from - 1, d.from + 1);
    if (target !== index) buzz(6);
    go(target, v, Math.abs(v) > 1.2);
  }
  view.addEventListener("pointerup", release);
  view.addEventListener("pointercancel", (e) => release(e, true));
  // A link-free gallery: stop images being dragged out like files.
  view.addEventListener("dragstart", (e) => e.preventDefault());

  tabsEl.addEventListener("keydown", (e) => {
    const dir = e.key === "ArrowRight" ? 1 : e.key === "ArrowLeft" ? -1 : 0;
    const jump = e.key === "Home" ? 0 : e.key === "End" ? n - 1 : null;
    if (!dir && jump === null) return;
    e.preventDefault();
    stopAuto();
    go(jump ?? index + dir);
    tabs[index].focus();
  });

  addEventListener("resize", () => { measure(); draw(); });
  measure();
  index = -1;
  settle(0);
  setPlaying(playing);
  draw();
})();
