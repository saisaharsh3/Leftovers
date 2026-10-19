// Leftovers B. Everything that moves is a spring solved from its current position and speed, so a finger can take
// over at any moment, a throw keeps its speed, and nothing jumps. One requestAnimationFrame clock drives them all.
(() => {
  "use strict";
  const $ = (s, el = document) => el.querySelector(s);
  const $$ = (s, el = document) => [...el.querySelectorAll(s)];
  const reduceMotion = matchMedia("(prefers-reduced-motion: reduce)").matches;
  const rupees = new Intl.NumberFormat("en-IN", { maximumFractionDigits: 0 });
  const fmt = (n) => (n < 0 ? "−₹" : "₹") + rupees.format(Math.abs(Math.round(n)));
  const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
  const buzz = (ms) => { try { if (matchMedia("(pointer: coarse)").matches) navigator.vibrate?.(ms); } catch {} };

  // ---- Springs ----
  // response: seconds to get there; damping: 1 settles without overshoot, below 1 overshoots (only after a throw).
  class Spring {
    constructor(value = 0) { this.x = value; this.v = 0; this.target = value; this.response = 0.4; this.damping = 1; this.active = false; }
    to(target, { response = 0.4, damping = 1, velocity = this.v } = {}) {
      this.target = target; this.response = response; this.damping = damping; this.v = velocity; this.active = true;
      if (reduceMotion) { this.x = target; this.v = 0; this.active = false; }
      loop.wake();
    }
    set(value) { this.x = value; this.target = value; this.v = 0; this.active = false; }
    step(dt) {
      if (!this.active) return false;
      const w = (2 * Math.PI) / this.response, z = this.damping, c1 = this.x - this.target;
      if (z >= 1) {
        const c2 = this.v + w * c1, e = Math.exp(-w * dt);
        this.x = this.target + (c1 + c2 * dt) * e;
        this.v = (c2 - w * (c1 + c2 * dt)) * e;
      } else {
        const wd = w * Math.sqrt(1 - z * z), c2 = (this.v + z * w * c1) / wd, e = Math.exp(-z * w * dt);
        const cos = Math.cos(wd * dt), sin = Math.sin(wd * dt);
        this.x = this.target + e * (c1 * cos + c2 * sin);
        this.v = e * ((c2 * wd - z * w * c1) * cos - (c1 * wd + z * w * c2) * sin);
      }
      if (Math.abs(this.x - this.target) < 0.05 && Math.abs(this.v) < 0.5) { this.x = this.target; this.v = 0; this.active = false; }
      return true;
    }
  }
  const loop = {
    jobs: new Set(), running: false, last: 0,
    add(fn) { this.jobs.add(fn); this.wake(); },
    wake() { if (!this.running) { this.running = true; this.last = 0; requestAnimationFrame((t) => this.frame(t)); } },
    frame(t) {
      const dt = this.last ? Math.min(0.05, (t - this.last) / 1000) : 1 / 60;
      this.last = t;
      let busy = false;
      this.jobs.forEach((fn) => { if (fn(dt)) busy = true; });
      if (busy) requestAnimationFrame((n) => this.frame(n)); else this.running = false;
    },
  };
  // Where a throw would come to rest (the projection Apple uses for scrolling).
  const project = (v, d = 0.998) => ((v / 1000) * d) / (1 - d);
  const rubber = (over, dim, c = 0.55) => (over * dim * c) / (dim + c * Math.abs(over));
  // Speed from the last ~100 ms of pointer moves.
  const tracker = () => {
    const h = [];
    return {
      add(e) { h.push({ t: e.timeStamp, x: e.clientX, y: e.clientY }); while (h.length > 2 && e.timeStamp - h[0].t > 100) h.shift(); },
      speed() {
        if (h.length < 2) return { x: 0, y: 0 };
        const a = h[0], b = h[h.length - 1], dt = (b.t - a.t) / 1000 || 1;
        return { x: (b.x - a.x) / dt, y: (b.y - a.y) / dt };
      },
    };
  };

  // ---- Theme ----
  const root = document.documentElement;
  const saved = (() => { try { return localStorage.getItem("theme"); } catch { return null; } })();
  const light = matchMedia("(prefers-color-scheme: light)");
  const shotUrl = (name) => `assets/${root.dataset.theme === "light" ? "light/" : ""}${name}.jpg`;
  const setTheme = (t) => {
    root.dataset.theme = t;
    $$(".inline-shot").forEach((img) => { img.src = shotUrl(img.dataset.name); });
    if (tour.ready) tour.show(tour.current, true);
  };
  $("#theme").addEventListener("click", () => {
    const next = root.dataset.theme === "light" ? "dark" : "light";
    setTheme(next);
    try { localStorage.setItem("theme", next); } catch {}
  });
  light.addEventListener("change", (e) => { if (!saved) setTheme(e.matches ? "light" : "dark"); });

  // ---- Header ----
  const bar = $("#bar");
  const onScroll = () => bar.classList.toggle("scrolled", scrollY > 4);
  addEventListener("scroll", onScroll, { passive: true });
  onScroll();

  // ---- Today: the same sum as the app's Home ----
  const now = new Date();
  const daysInMonth = new Date(now.getFullYear(), now.getMonth() + 1, 0).getDate();
  const daysLeft = daysInMonth - now.getDate() + 1;
  const budget = 50000, bills = 19048;
  const spentBefore = Math.round(((budget - bills) * ((now.getDate() - 1) / daysInMonth) * 0.92) / 10) * 10;
  const allowance = Math.max(0, (budget - spentBefore - bills) / daysLeft);
  $("#days").textContent = daysLeft === 1 ? "Last day of the month" : `${daysLeft} days left`;

  const safeEl = $("#safe"), meter = $("#meter"), spentEl = $("#spent");
  const shown = new Spring(allowance);   // the number counts toward the new value
  let spentToday = 0;
  function drawToday() {
    const safe = shown.x;
    safeEl.textContent = fmt(safe);
    safeEl.classList.toggle("over", safe < 0);
    const used = allowance - safe;
    meter.style.width = `${clamp(allowance ? (used / allowance) * 100 : 100, 0, 100)}%`;
    meter.classList.toggle("over", safe < 0);
  }
  function setSpent(total) {
    spentToday = total;
    spentEl.textContent = `${fmt(total)} spent of ${fmt(allowance)}`;
    safeEl.setAttribute("aria-label", `Safe to spend today: ${fmt(allowance - total).replace("−", "minus ")}`);
    shown.to(allowance - total, { response: 0.55 });
  }
  loop.add((dt) => { const moving = shown.step(dt); drawToday(); return moving; });
  setSpent(0);

  // ---- The deck ----
  const icons = {
    coffee: '<path d="M10 2v2M14 2v2M16 8a1 1 0 0 1 1 1v8a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4V9a1 1 0 0 1 1-1h14a4 4 0 1 1 0 8h-1"/>',
    train: '<rect x="4" y="3" width="16" height="16" rx="2"/><path d="M4 11h16M12 3v8m-4 8-2 3m10-3 2 3"/><path d="M8 15h.01M16 15h.01"/>',
    food: '<path d="M3 2v7c0 1.1.9 2 2 2h4a2 2 0 0 0 2-2V2M7 2v20M21 15V2a5 5 0 0 0-5 5v6c0 1.1.9 2 2 2h3Zm0 0v7"/>',
    cart: '<circle cx="8" cy="21" r="1"/><circle cx="19" cy="21" r="1"/><path d="M2.05 2.05h2l2.66 12.42a2 2 0 0 0 2 1.58h9.78a2 2 0 0 0 1.95-1.57l1.65-7.43H5.12"/>',
    film: '<path d="M20.2 6 3 11l-.9-2.4c-.3-1.1.3-2.2 1.3-2.5l13.5-4c1.1-.3 2.2.3 2.5 1.3Z"/><path d="m6.2 5.3 3.1 3.9M12.4 3.4l3.1 4M3 11h18v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Z"/>',
    car: '<path d="M10 2h4M8 18v2M16 18v2M4 9l2-5h12l2 5"/><rect x="2" y="9" width="20" height="9" rx="2"/><path d="M6 13h.01M18 13h.01"/>',
  };
  const spends = [
    { name: "Coffee", cat: "Food & Dining", amount: 120, icon: "coffee", tint: "#c2410c" },
    { name: "Metro", cat: "Transport", amount: 40, icon: "train", tint: "#2563eb" },
    { name: "Lunch", cat: "Food & Dining", amount: 250, icon: "food", tint: "#c2410c" },
    { name: "Groceries", cat: "Groceries", amount: 1200, icon: "cart", tint: "#15803d" },
    { name: "Movie", cat: "Entertainment", amount: 450, icon: "film", tint: "#7c3aed" },
    { name: "Auto home", cat: "Transport", amount: 90, icon: "car", tint: "#2563eb" },
  ];
  const deck = $("#deck");
  const cards = spends.map((s) => {
    const el = document.createElement("div");
    el.className = "card";
    el.setAttribute("role", "group");
    el.setAttribute("aria-label", `${s.name}, ${s.cat}, ${fmt(s.amount)}`);
    el.innerHTML = `
      <span class="stamp yes">Spend</span><span class="stamp no">Skip</span>
      <div class="c-top"><span class="c-icon" style="background:${s.tint}1f;color:${s.tint}"><svg viewBox="0 0 24 24" aria-hidden="true">${icons[s.icon]}</svg></span>
      <div><div class="c-name">${s.name}</div><div class="c-cat">${s.cat}</div></div></div>
      <div class="c-amount">${fmt(s.amount)}</div>`;
    return { el, data: s, x: new Spring(0), y: new Spring(0), out: 0 };
  });
  const endCard = document.createElement("div");
  endCard.className = "card end";
  endCard.innerHTML = `<div class="c-name">That's today</div><p id="end-text"></p><button class="pill quiet small" type="button" id="again">Start again</button>`;
  deck.append(endCard, ...cards.slice().reverse().map((c) => c.el));
  let top = 0;
  const history = [];

  const width = () => deck.clientWidth || 320;
  function layoutDeck() {
    const w = width();
    const lead = cards[top];
    // How far the top card has gone toward leaving: the next card grows into its place as it does.
    const p = lead ? clamp(Math.abs(lead.x.x) / (w * 0.6), 0, 1) : 0;
    cards.forEach((c, i) => {
      const d = i - top;
      const el = c.el;
      if (d < 0) {
        el.style.transform = `translate3d(${c.x.x}px, ${c.y.x}px, 0) rotate(${(c.x.x / w) * 14}deg)`;
        const gone = Math.abs(c.x.x) > w * 1.6;
        el.style.opacity = gone ? "0" : "1";
        el.style.visibility = gone && !c.x.active ? "hidden" : "";
        el.style.pointerEvents = "none";
        return;
      }
      el.style.visibility = "";
      if (d === 0) {
        const r = (c.x.x / w) * 14;
        el.style.transform = `translate3d(${c.x.x}px, ${c.y.x}px, 0) rotate(${r}deg)`;
        el.style.opacity = "1";
        el.style.zIndex = String(100);
        el.style.pointerEvents = "auto";
        $(".stamp.yes", el).style.opacity = clamp(c.x.x / (w * 0.35), 0, 1);
        $(".stamp.no", el).style.opacity = clamp(-c.x.x / (w * 0.35), 0, 1);
        return;
      }
      const depth = d - p;
      el.style.transform = `translate3d(0, ${clamp(depth, 0, 2) * 12}px, 0) scale(${1 - clamp(depth, 0, 2) * 0.05})`;
      el.style.opacity = d > 2 ? "0" : "1";
      el.style.zIndex = String(100 - d);
      el.style.pointerEvents = "none";
      $$(".stamp", el).forEach((s) => { s.style.opacity = "0"; });
    });
    const done = top >= cards.length;
    endCard.style.zIndex = "1";
    endCard.style.opacity = done || top >= cards.length - 2 ? "1" : "0";
    $("#skip").disabled = done; $("#spend").disabled = done;
    $("#hint").textContent = done ? "" : "Flick a card right to spend it, left to skip it.";
    $("#undo").hidden = history.length === 0;
  }
  loop.add((dt) => {
    let busy = false;
    cards.forEach((c) => { if (c.x.step(dt)) busy = true; if (c.y.step(dt)) busy = true; });
    // A thrown card is committed once it's well clear of the deck.
    layoutDeck();
    return busy;
  });

  function decide(card, dir, vx = 0, vy = 0) {
    const w = width();
    const momentum = Math.abs(vx) > 300;
    card.out = dir;
    card.x.to(dir * (w * 1.9 + 120), { response: 0.45, damping: 1, velocity: vx || dir * 1400 });
    card.y.to(card.y.x + project(vy, 0.99) * 0.4, { response: 0.45, damping: 1, velocity: vy });
    history.push({ index: top, dir });
    top += 1;
    if (dir > 0) setSpent(spentToday + card.data.amount);
    buzz(momentum ? 12 : 8);
    if (top >= cards.length) {
      const left = allowance - spentToday;
      $("#end-text").textContent = left >= 0
        ? `${fmt(left)} left for today, with ${fmt(spentToday)} spent.`
        : `${fmt(-left)} over today. Leftovers spreads that over the rest of the month.`;
    }
    layoutDeck();
  }

  // Dragging the top card: it stays under the finger where it was grabbed, in both directions at once.
  let hold = null;
  deck.addEventListener("pointerdown", (e) => {
    const lead = cards[top];
    if (!lead || !lead.el.contains(e.target) || e.button !== 0) return;
    lead.el.setPointerCapture(e.pointerId);
    lead.el.classList.add("held");
    // Catch it where it is, even if it was springing back.
    hold = { id: e.pointerId, card: lead, x0: e.clientX - lead.x.x, y0: e.clientY - lead.y.x, track: tracker() };
    lead.x.set(lead.x.x); lead.y.set(lead.y.x);
    hold.track.add(e);
  });
  deck.addEventListener("pointermove", (e) => {
    if (!hold || e.pointerId !== hold.id) return;
    const w = width();
    hold.track.add(e);
    hold.card.x.set(e.clientX - hold.x0);
    // Up and down it follows, but resists: it's mostly meant to go sideways.
    hold.card.y.set(rubber(e.clientY - hold.y0, w * 0.6));
    layoutDeck();
  });
  const letGo = (e) => {
    if (!hold || e.pointerId !== hold.id) return;
    const { card, track } = hold;
    hold = null;
    card.el.classList.remove("held");
    const v = track.speed(), w = width();
    // Decide from where the throw is going, not where the finger let go.
    const landing = card.x.x + project(v.x);
    if (Math.abs(landing) > w * 0.5 && Math.sign(landing) === Math.sign(v.x || landing)) {
      decide(card, Math.sign(landing), v.x, v.y);
    } else {
      // Not far enough: it comes home, with a little give because it was moving.
      const springy = Math.hypot(v.x, v.y) > 200;
      card.x.to(0, { response: 0.42, damping: springy ? 0.78 : 1, velocity: v.x });
      card.y.to(0, { response: 0.42, damping: springy ? 0.78 : 1, velocity: v.y });
    }
  };
  deck.addEventListener("pointerup", letGo);
  deck.addEventListener("pointercancel", letGo);

  $("#spend").addEventListener("click", () => { if (cards[top]) decide(cards[top], 1); });
  $("#skip").addEventListener("click", () => { if (cards[top]) decide(cards[top], -1); });
  $("#undo").addEventListener("click", () => {
    const last = history.pop();
    if (!last) return;
    top = last.index;
    const card = cards[top];
    if (last.dir > 0) setSpent(spentToday - card.data.amount);
    // It comes back from the side it left by.
    card.x.to(0, { response: 0.5, damping: 0.85, velocity: -last.dir * 900 });
    card.y.to(0, { response: 0.5, damping: 1 });
    layoutDeck();
  });
  deck.addEventListener("click", (e) => {
    if (!e.target.closest("#again")) return;
    history.length = 0; top = 0;
    cards.forEach((c, i) => { c.x.set(i % 2 ? -width() * 2 : width() * 2); c.y.set(0); c.x.to(0, { response: 0.5 + i * 0.04, damping: 1 }); });
    setSpent(0);
    layoutDeck();
  });
  addEventListener("keydown", (e) => {
    if (!deck.closest(".today-demo").contains(document.activeElement)) return;
    if (e.key === "ArrowRight" && cards[top]) { e.preventDefault(); decide(cards[top], 1); }
    if (e.key === "ArrowLeft" && cards[top]) { e.preventDefault(); decide(cards[top], -1); }
  });
  layoutDeck();

  // ---- Tour: the screen follows the step that's in the middle of the window ----
  const tour = {
    ready: false, current: 0, front: $("#screen-a"), back: $("#screen-b"),
    steps: $$(".step"),
    show(i, force = false) {
      if (i === this.current && !force && this.ready) return;
      this.current = i;
      this.steps.forEach((s, k) => s.classList.toggle("on", k === i));
      const url = shotUrl(this.steps[i].dataset.shot);
      const next = this.back, prev = this.front;
      next.src = url;
      const swap = () => { next.style.opacity = "1"; prev.style.opacity = "0"; this.front = next; this.back = prev; };
      (next.decode ? next.decode() : Promise.resolve()).then(swap, swap);
    },
  };
  tour.front.style.opacity = "1"; tour.back.style.opacity = "0";
  const io = new IntersectionObserver((entries) => {
    entries.forEach((en) => { if (en.isIntersecting) tour.show(tour.steps.indexOf(en.target)); });
  }, { rootMargin: "-45% 0px -45% 0px" });
  tour.steps.forEach((s) => io.observe(s));

  setTheme(saved || (light.matches ? "light" : "dark"));
  tour.front.src = shotUrl(tour.steps[0].dataset.shot);
  tour.steps[0].classList.add("on");
  tour.ready = true;

  // ---- Privacy switches: tap, or drag the thumb; letting go, the direction of the flick decides ----
  $$(".switches li").forEach((row) => {
    const sw = $(".switch", row), thumb = $(".thumb", sw), text = $(".sw-text p", row);
    const fill = document.createElement("span");
    fill.className = "track-fill";
    sw.prepend(fill);
    const travel = () => sw.clientWidth - thumb.clientWidth - 6;
    const pos = new Spring(0); // 0 off, 1 on
    let on = false;
    const draw = () => {
      thumb.style.transform = `translate3d(${pos.x * travel()}px, 0, 0)`;
      fill.style.opacity = clamp(pos.x, 0, 1);
    };
    const set = (value, velocity = 0) => {
      const changed = value !== on;
      on = value;
      sw.setAttribute("aria-checked", String(on));
      row.classList.toggle("on", on);
      text.textContent = on ? text.dataset.on : text.dataset.off;
      pos.to(on ? 1 : 0, { response: 0.32, damping: Math.abs(velocity) > 3 ? 0.8 : 1, velocity });
      if (changed) buzz(10);
    };
    loop.add((dt) => { const m = pos.step(dt); draw(); return m; });
    text.textContent = text.dataset.off;

    let g = null;
    sw.addEventListener("pointerdown", (e) => {
      sw.setPointerCapture(e.pointerId);
      g = { id: e.pointerId, x0: e.clientX, start: pos.x, moved: false, track: tracker() };
      g.track.add(e);
      pos.set(pos.x);
      // Pressed: the thumb widens a little, straight away.
      thumb.style.width = `${thumb.clientWidth + 6}px`;
    });
    sw.addEventListener("pointermove", (e) => {
      if (!g || e.pointerId !== g.id) return;
      const dx = e.clientX - g.x0;
      if (Math.abs(dx) > 4) g.moved = true;
      g.track.add(e);
      const raw = g.start + dx / travel();
      pos.set(raw < 0 ? -rubber(-raw, 1, 0.3) : raw > 1 ? 1 + rubber(raw - 1, 1, 0.3) : raw);
      draw();
    });
    const up = (e) => {
      if (!g || e.pointerId !== g.id) return;
      const moved = g.moved, v = g.track.speed().x / travel();
      g = null;
      thumb.style.width = "";
      if (!moved) { set(!on); return; }
      // The flick's direction wins; a slow drag lands on the nearer side.
      const value = Math.abs(v) > 1.5 ? v > 0 : pos.x > 0.5;
      set(value, v);
    };
    sw.addEventListener("pointerup", up);
    sw.addEventListener("pointercancel", up);
    sw.addEventListener("click", (e) => { if (e.detail === 0) set(!on); }); // keyboard
    draw();
  });

  // ---- The Get Leftovers sheet ----
  const sheet = $("#sheet"), scrim = $("#scrim"), grab = $("#sheet-grab");
  const sheetY = new Spring(1); // 0 open, 1 closed (as a share of its height)
  let sheetOpen = false, opener = null;
  const sheetH = () => sheet.offsetHeight || 400;
  function drawSheet() {
    const y = sheetY.x;
    sheet.style.transform = `translate(-50%, ${y * 100}%)`;
    scrim.style.opacity = clamp(1 - y, 0, 1);
    if (!sheetOpen && !sheetY.active && y >= 0.999) { sheet.hidden = true; scrim.hidden = true; }
  }
  loop.add((dt) => { const m = sheetY.step(dt); drawSheet(); return m; });
  function openSheet(from) {
    opener = from || document.activeElement;
    sheet.hidden = false; scrim.hidden = false;
    sheetOpen = true;
    sheetY.to(0, { response: 0.36, damping: 1 });
    requestAnimationFrame(() => $("a, button", sheet.querySelector(".ways"))?.focus({ preventScroll: true }));
  }
  function closeSheet(velocity = 0, damping = 1) {
    sheetOpen = false;
    sheetY.to(1, { response: 0.34, damping, velocity });
    opener?.focus?.({ preventScroll: true });
  }
  $$("[data-open-sheet]").forEach((b) => b.addEventListener("click", () => openSheet(b)));
  $("#sheet-close").addEventListener("click", () => closeSheet());
  scrim.addEventListener("click", () => closeSheet());
  addEventListener("keydown", (e) => { if (e.key === "Escape" && sheetOpen) closeSheet(); });
  // Keep the keyboard inside the sheet while it's open.
  sheet.addEventListener("keydown", (e) => {
    if (e.key !== "Tab") return;
    const f = $$("a, button", sheet).filter((x) => !x.disabled && x.offsetParent);
    if (e.shiftKey && document.activeElement === f[0]) { e.preventDefault(); f[f.length - 1].focus(); }
    else if (!e.shiftKey && document.activeElement === f[f.length - 1]) { e.preventDefault(); f[0].focus(); }
  });
  let sg = null;
  grab.addEventListener("pointerdown", (e) => {
    if (e.target.closest("button")) return;
    grab.setPointerCapture(e.pointerId);
    sg = { id: e.pointerId, y0: e.clientY, start: sheetY.x, track: tracker() };
    sg.track.add(e);
    sheetY.set(sheetY.x);
  });
  grab.addEventListener("pointermove", (e) => {
    if (!sg || e.pointerId !== sg.id) return;
    sg.track.add(e);
    const h = sheetH();
    const raw = sg.start + (e.clientY - sg.y0) / h;
    // Pulling up past open, it resists.
    sheetY.set(raw < 0 ? -rubber(-raw * h, h, 0.4) / h : raw);
    drawSheet();
  });
  const sheetUp = (e) => {
    if (!sg || e.pointerId !== sg.id) return;
    const h = sheetH(), v = sg.track.speed().y;
    sg = null;
    const landing = sheetY.x + project(v) / h;
    if (landing > 0.5) closeSheet(v / h, Math.abs(v) > 500 ? 0.9 : 1);
    else sheetY.to(0, { response: 0.32, damping: Math.abs(v) > 300 ? 0.8 : 1, velocity: v / h });
  };
  grab.addEventListener("pointerup", sheetUp);
  grab.addEventListener("pointercancel", sheetUp);

  // The Dev build in Obtainium: a one-tap setup that follows the "Dev build" release and its APK.
  const devSettings = {
    includePrereleases: true, fallbackToOlderReleases: false, filterReleaseTitlesByRegEx: "^Dev build",
    filterReleaseNotesByRegEx: "", verifyLatestTag: false, useLatestAssetDateAsReleaseDate: true, releaseTitleAsVersion: false,
    trackOnly: false, versionExtractionRegEx: "", matchGroupToUse: "", versionDetection: false, releaseDateAsVersion: true,
    apkFilterRegEx: "leftovers-dev\\.apk", invertAPKFilter: false, autoApkFilterByArch: true, appName: "Leftovers Dev",
  };
  const devApp = { id: "com.leftovers.app.dev", url: "https://github.com/saisaharsh3/Leftovers", author: "saisaharsh3", name: "Leftovers Dev", preferredApkIndex: 0, additionalSettings: JSON.stringify(devSettings) };
  const deep = "obtainium://app/" + encodeURIComponent(JSON.stringify(devApp));
  $("#dev-obtainium").href = "https://apps.obtainium.imranr.dev/redirect?r=" + encodeURIComponent(deep);

  // ---- Latest version and What's new, from GitHub ----
  const repo = "saisaharsh3/Leftovers";
  fetch(`https://api.github.com/repos/${repo}/releases/latest`, { headers: { Accept: "application/vnd.github+json" } })
    .then((r) => (r.ok ? r.json() : Promise.reject()))
    .then((rel) => {
      const version = (rel.tag_name || "").replace(/^v/, "");
      const apk = (rel.assets || []).find((a) => a.name.endsWith(".apk"));
      if (apk) $("#apk").href = apk.browser_download_url;
      const date = rel.published_at ? new Date(rel.published_at).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" }) : "";
      if (version) {
        $("#version-line").textContent = `Version ${version}${date ? `, released ${date}` : ""}. Free and open source. Android 8.0 or newer.`;
        $("#apk").textContent = `Download ${version}`;
      }
    })
    .catch(() => {});
  const inline = (s) => s
    .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
    .replace(/`([^`]+)`/g, "<code>$1</code>")
    .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")
    .replace(/\*([^*]+)\*/g, "<em>$1</em>")
    .replace(/\[([^\]]+)\]\((https?:[^)\s]+)\)/g, '<a href="$2">$1</a>')
    .replace(/\(#(\d+)\)/g, `(<a href="https://github.com/${repo}/issues/$1">#$1</a>)`);
  fetch(`https://raw.githubusercontent.com/${repo}/main/CHANGELOG.md`)
    .then((r) => (r.ok ? r.text() : Promise.reject()))
    .then((md) => {
      const sections = [];
      let cur = null;
      for (const line of md.split("\n")) {
        const head = line.match(/^## (.+)$/);
        if (head) { cur = { title: head[1].trim(), items: [] }; sections.push(cur); continue; }
        if (!cur) continue;
        if (/^- /.test(line)) cur.items.push(line.slice(2).trim());
        else if (/^\s{2,}\S/.test(line) && cur.items.length) cur.items[cur.items.length - 1] += " " + line.trim();
      }
      const released = sections.filter((s) => /^\d/.test(s.title) && s.items.length).slice(0, 4);
      if (!released.length) throw new Error();
      const box = $("#changelog");
      box.innerHTML = "";
      released.forEach((s, i) => {
        const d = document.createElement("details");
        d.className = "release";
        if (i === 0) d.open = true;
        d.innerHTML = `<summary>Version ${s.title}${i === 0 ? "<small>Latest</small>" : ""}</summary><ul>${s.items.map((it) => `<li>${inline(it)}</li>`).join("")}</ul>`;
        box.append(d);
      });
    })
    .catch(() => {
      $("#changelog").innerHTML = `<p class="muted">What's new in each version is in the <a href="https://github.com/${repo}/blob/main/CHANGELOG.md">changelog on GitHub</a>.</p>`;
    });
})();
