// Leftovers B: Make it yours. The app's Home drawn live over a background you pick: one of the wallpapers, the glow,
// or your own picture, which is read in this browser and never sent anywhere. Each new background materialises
// (sharpening and settling into place) instead of just fading; the phone tilts toward the pointer, or with a finger,
// and the picture moves a little behind the glass, like it's further away. Same springs and clock as the rest.
(() => {
  "use strict";
  const { Spring, loop, clamp, buzz, reduceMotion, fmt } = window.LO;
  const $ = (s, el = document) => el.querySelector(s);
  const stage = $("#look-stage");
  if (!stage) return;
  const phone = $("#look-phone"), bgBox = $("#look-bg"), dim = $("#look-dim"), ui = $("#look-ui"), halo = $("#look-halo");
  const walls = [...document.querySelectorAll("#walls .wall")];
  const blurIn = $("#look-blur"), dimIn = $("#look-dimming"), blurOut = $("#blur-out"), dimOut = $("#dim-out");

  // The month and greeting, as the app would show them today.
  const now = new Date();
  $("#lk-month").textContent = now.toLocaleDateString("en-IN", { month: "long", year: "numeric" });
  const h = now.getHours();
  $("#lk-greet").textContent = `${h < 12 ? "Good morning" : h < 17 ? "Good afternoon" : "Good evening"} · ${now.toLocaleDateString("en-IN", { weekday: "short", day: "numeric", month: "short" })}`;

  // ---- Backgrounds: each one a layer over the last, which goes once the new one has fully arrived.
  let blur = +blurIn.value;
  const layers = [];
  function show(src) {
    const el = document.createElement("div");
    el.className = "look-layer" + (src ? "" : " glow");
    if (src) el.style.backgroundImage = `url("${src}")`;
    const L = { el, p: new Spring(0) };
    const go = () => { bgBox.append(el); layers.push(L); L.p.to(1, { response: 0.6 }); loop.wake(); };
    if (!src) return go();
    // Waits for the picture, so it never arrives half-drawn.
    const img = new Image();
    img.onload = img.onerror = go;
    img.src = src;
  }

  // ---- Tilt: toward the pointer with a mouse; a finger drags it, and it springs back with the speed it was let go at.
  const rx = new Spring(0), ry = new Spring(0);
  const fine = matchMedia("(hover: hover) and (pointer: fine)").matches;
  const MAX_X = 7, MAX_Y = 10;
  if (!reduceMotion && fine) {
    stage.addEventListener("pointermove", (e) => {
      const r = stage.getBoundingClientRect();
      const nx = clamp(((e.clientX - r.left) / r.width) * 2 - 1, -1, 1), ny = clamp(((e.clientY - r.top) / r.height) * 2 - 1, -1, 1);
      rx.to(-ny * MAX_X, { response: 0.5 });
      ry.to(nx * MAX_Y, { response: 0.5 });
    });
    stage.addEventListener("pointerleave", () => { rx.to(0, { response: 0.7 }); ry.to(0, { response: 0.7 }); });
  }
  if (!reduceMotion && !fine) {
    let drag = null;
    phone.addEventListener("pointerdown", (e) => {
      if (e.target.closest("button")) return;
      drag = { id: e.pointerId, x: e.clientX, y: e.clientY, t: e.timeStamp, vx: 0, vy: 0, on: false };
    });
    addEventListener("pointermove", (e) => {
      if (!drag || e.pointerId !== drag.id) return;
      const dx = e.clientX - drag.x, dy = e.clientY - drag.y;
      // Sideways first means a tilt; up and down stays a scroll (the phone allows only vertical panning).
      if (!drag.on && Math.abs(dx) < 8) return;
      drag.on = true;
      const dt = Math.max(1, e.timeStamp - drag.t) / 1000;
      drag.vy = (dx / 6 - ry.x) / dt; drag.vx = (-dy / 10 - rx.x) / dt; drag.t = e.timeStamp;
      // Resists more the further it goes, as if held on a spring.
      ry.set(MAX_Y * 1.6 * Math.tanh(dx / 160));
      rx.set(MAX_X * 1.2 * Math.tanh(-dy / 220));
      loop.wake();
    });
    const up = (e) => {
      if (!drag || e.pointerId !== drag.id) return;
      // Let go after a throw: back to rest with a little overshoot, carrying the finger's speed.
      if (drag.on) { ry.to(0, { response: 0.55, damping: 0.62, velocity: clamp(drag.vy, -300, 300) }); rx.to(0, { response: 0.55, damping: 0.62, velocity: clamp(drag.vx, -200, 200) }); buzz(6); }
      drag = null;
    };
    addEventListener("pointerup", up);
    addEventListener("pointercancel", up);
  }

  // ---- One clock for all of it.
  loop.add((dt) => {
    let busy = false;
    for (const L of layers) if (L.p.step(dt)) busy = true;
    if (rx.step(dt)) busy = true;
    if (ry.step(dt)) busy = true;
    // The picture sits behind the glass: it moves the other way, a little, as the phone tilts.
    const px = -ry.x * 1.6, py = rx.x * 1.6;
    for (const L of layers) {
      const k = clamp(L.p.x, 0, 1);
      L.el.style.opacity = String(k);
      L.el.style.transform = `translate3d(${px}px, ${py}px, 0) scale(${1.06 + 0.1 * (1 - k)})`;
      const b = blur + 18 * (1 - k);
      L.el.style.filter = b > 0.2 ? `blur(${b.toFixed(1)}px)` : "none";
    }
    // Older backgrounds go once the newest is fully in.
    const top = layers[layers.length - 1];
    if (top && !top.p.active && top.p.x >= 1) while (layers.length > 1) layers.shift().el.remove();
    phone.style.transform = `perspective(1000px) rotateX(${rx.x.toFixed(2)}deg) rotateY(${ry.x.toFixed(2)}deg)`;
    ui.style.transform = `translate3d(${(ry.x * 0.5).toFixed(2)}px, ${(-rx.x * 0.5).toFixed(2)}px, 0)`;
    phone.style.setProperty("--sheen", `${(ry.x * 6).toFixed(1)}%`);
    return busy;
  });

  // ---- Picking a background.
  const own = $("#own"), ownPic = $("#own-pic"), file = $("#look-file");
  let ownUrl = null;
  const choose = (el, src, tone) => {
    walls.forEach((w) => w.setAttribute("aria-checked", String(w === el)));
    halo.style.background = tone;
    show(src);
    buzz(8);
  };
  walls.forEach((w) => {
    if (w === own) return;
    w.addEventListener("click", () => { if (w.getAttribute("aria-checked") !== "true") choose(w, w.dataset.src, w.dataset.tone); });
  });
  file.addEventListener("change", () => {
    const f = file.files && file.files[0];
    if (!f || !f.type.startsWith("image/")) return;
    if (ownUrl) URL.revokeObjectURL(ownUrl);
    ownUrl = URL.createObjectURL(f);
    ownPic.style.backgroundImage = `url("${ownUrl}")`;
    ownPic.classList.add("set");
    choose(own, ownUrl, "#8c82ff");
    file.value = "";
  });

  // ---- Glass: the two kinds the app has over a photo. The marker slides between them.
  const seg = $("#glass"), ind = $("#seg-ind"), glassNote = $("#glass-note");
  const segBtns = [...seg.querySelectorAll("button")];
  const indX = new Spring(0), indW = new Spring(0);
  const placeInd = (btn, instant) => {
    const x = btn.offsetLeft, w = btn.offsetWidth;
    if (instant) { indX.set(x); indW.set(w); } else { indX.to(x, { response: 0.35 }); indW.to(w, { response: 0.35 }); }
    loop.wake();
  };
  loop.add((dt) => {
    const a = indX.step(dt), b = indW.step(dt);
    ind.style.transform = `translateX(${indX.x}px)`;
    ind.style.width = `${indW.x}px`;
    return a || b;
  });
  segBtns.forEach((btn) => btn.addEventListener("click", () => {
    segBtns.forEach((b) => b.setAttribute("aria-checked", String(b === btn)));
    const frosted = btn.dataset.glass === "frosted";
    phone.classList.toggle("frosted", frosted);
    glassNote.textContent = frosted ? "Frosted glass is lighter on the phone, for smooth scrolling on any of them." : "Liquid glass bends the picture at its edges, like real glass.";
    placeInd(btn);
    buzz(6);
  }));
  requestAnimationFrame(() => placeInd(segBtns[0], true));
  addEventListener("resize", () => placeInd(segBtns.find((b) => b.getAttribute("aria-checked") === "true"), true));

  // ---- Blur and dimming, as the app's sliders.
  const blurName = (v) => (v === 0 ? "Sharp" : v <= 6 ? "Soft" : v <= 14 ? "Medium" : "Strong");
  blurIn.addEventListener("input", () => { blur = +blurIn.value; blurOut.textContent = blurName(blur); loop.wake(); });
  dimIn.addEventListener("input", () => { dim.style.opacity = String(dimIn.value / 100); dimOut.textContent = `${dimIn.value}%`; });
  dim.style.opacity = String(dimIn.value / 100);
  blurOut.textContent = blurName(blur);

  // ---- Arjun paying back: the row folds away, the balance counts up, and a note says where it went. Then it comes back.
  const pay = $("#lk-pay"), ok = $("#lk-ok"), toast = $("#lk-toast"), bal = $("#lk-balance");
  const START = 205812, money = new Spring(START);
  loop.add((dt) => { const m = money.step(dt); bal.textContent = fmt(money.x); return m; });
  let settled = false;
  ok.addEventListener("click", () => {
    if (settled) return;
    settled = true;
    pay.classList.add("done");
    toast.classList.add("show");
    money.to(START + 2500, { response: 0.9 });
    buzz([10, 40, 14]);
    setTimeout(() => toast.classList.remove("show"), 2600);
    setTimeout(() => { pay.classList.remove("done"); money.to(START, { response: 0.6 }); settled = false; }, 4200);
  });

  show(walls.find((w) => w.getAttribute("aria-checked") === "true").dataset.src);
  halo.style.background = walls.find((w) => w.getAttribute("aria-checked") === "true").dataset.tone;
})();
