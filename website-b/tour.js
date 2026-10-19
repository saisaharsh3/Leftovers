// Leftovers B, wide screens: the tour. The phone stays put while the steps scroll past it, and everything around it
// follows one number, how far through the tour the page is (2.5 is halfway from the third screen to the fourth):
// the screens slide inside the phone, the glow takes the screen's colour, the line beside the steps fills, and notes
// about the screen float in around the phone. The phone also leans toward the pointer, on springs, so it never jerks.
(() => {
  "use strict";
  const { Spring, loop, clamp, reduceMotion } = window.LO;
  const $ = (s, el = document) => el.querySelector(s);
  const stage = $("#stage");
  if (!stage) return;
  const strip = $("#strip"), halo = $("#halo"), calloutsEl = $("#callouts"), rail = $("#rail"), phone = $("#phone");
  const steps = [...document.querySelectorAll(".step")];
  const n = steps.length;
  const wide = matchMedia("(min-width: 901px)");
  const shotUrl = (name) => `assets/${document.documentElement.dataset.theme === "light" ? "light/" : ""}${name}.jpg`;

  // What each screen gets around it: its colour, a few words under the text, and notes beside the phone.
  const extra = {
    home: { color: "#8c82ff", tags: ["Safe to spend today", "Every account", "Bills coming up"],
      notes: [["₹1,909", "safe to spend today", "wallet"], ["3 accounts", "in one balance", "layers"], ["Spotify · 20 Oct", "coming up", "clock"]] },
    add: { color: "#b5d64a", tags: ["Two taps", "Split it", "Any day"],
      notes: [["2 taps", "and it's logged", "tap"], ["Split", "with friends or across categories", "split"], ["Forgot one?", "add it on any day", "plus"]] },
    insights: { color: "#3fbfb2", tags: ["Touch a day", "Where it's heading", "Since last month"],
      notes: [["Touch a bar", "to read that day", "chart"], ["₹1,842", "a day on average", "coins"], ["Groceries −71%", "on last month", "down"]] },
    plan: { color: "#f2a65a", tags: ["Budget", "Subscriptions", "Goals"],
      notes: [["₹26,845", "left this month", "wallet"], ["Subscriptions", "it already knows", "repeat"], ["Goa trip", "₹12,000 put aside", "target"]] },
    calendar: { color: "#6aa8f0", tags: ["Calendar", "Search", "#tags"],
      notes: [["Every day", "on a calendar", "calendar"], ["#trip", "find it by tag", "hash"], ["Add for this day", "the one you forgot", "plus"]] },
    "money-owed": { color: "#ef84ad", tags: ["Who owes you", "Whom you owe", "Pay back in parts"],
      notes: [["₹3,400", "you're owed", "users"], ["Pay back", "a bit at a time", "coins"], ["Kept apart", "from your budget", "shield"]] },
  };
  // Small line icons for the notes (drawn like the app's own icons: round ends, 2px strokes).
  const icons = {
    wallet: "M19 7V4a1 1 0 0 0-1-1H5a2 2 0 0 0 0 4h15a1 1 0 0 1 1 1v4h-3a2 2 0 0 0 0 4h3a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1M3 5v14a2 2 0 0 0 2 2h15a1 1 0 0 0 1-1v-4",
    layers: "M12 2 2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5",
    clock: "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM12 6v6l4 2",
    tap: "M9 9V4.5a1.5 1.5 0 0 1 3 0V12m0-1.5a1.5 1.5 0 0 1 3 0V12m0-1a1.5 1.5 0 0 1 3 0v3a6 6 0 0 1-6 6h-1a6 6 0 0 1-5.2-3L4 14a1.5 1.5 0 0 1 2.6-1.5L9 15",
    split: "M16 3h5v5M8 3H3v5M12 22v-8.3a4 4 0 0 0-1.2-2.8L3 3M15 9l6-6",
    plus: "M12 5v14M5 12h14",
    chart: "M3 3v18h18M8 17v-5M13 17V8M18 17v-9",
    coins: "M8 14a6 6 0 1 0 0-12 6 6 0 0 0 0 12zM18.1 10.4A6 6 0 1 1 10.3 18",
    down: "M22 17l-8.5-8.5-5 5L2 7M16 17h6v-6",
    repeat: "M17 2l4 4-4 4M3 11v-1a4 4 0 0 1 4-4h14M7 22l-4-4 4-4M21 13v1a4 4 0 0 1-4 4H3",
    target: "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM12 18a6 6 0 1 0 0-12 6 6 0 0 0 0 12zM12 14a2 2 0 1 0 0-4 2 2 0 0 0 0 4z",
    calendar: "M8 2v4M16 2v4M3 10h18M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z",
    hash: "M4 9h16M4 15h16M10 3 8 21M16 3l-2 18",
    users: "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM22 21v-2a4 4 0 0 0-3-3.9M16 3.1a4 4 0 0 1 0 7.8",
    shield: "M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z",
  };
  // Where the three notes sit: two to the left of the phone, one overlapping its right edge, lower down.
  const spots = [{ side: "L", top: 12, depth: 1.4 }, { side: "L", top: 46, depth: 2.2 }, { side: "R", top: 80, depth: 1.8 }];

  const shots = [], groups = [];
  steps.forEach((s, i) => {
    const x = extra[s.dataset.shot] || extra.home;
    // The step's number, and a few words under its text.
    const num = document.createElement("p");
    num.className = "step-num";
    num.innerHTML = `<span>${String(i + 1).padStart(2, "0")}</span> / ${String(n).padStart(2, "0")}`;
    s.prepend(num);
    const tags = document.createElement("ul");
    tags.className = "step-tags";
    tags.innerHTML = x.tags.map((t) => `<li>${t}</li>`).join("");
    $("p:not(.step-num)", s).after(tags);
    // Its screen in the phone.
    const img = document.createElement("img");
    img.className = "t-shot";
    img.alt = "";
    img.width = 540; img.height = 1200;
    img.decoding = "async";
    img.dataset.name = s.dataset.shot;
    strip.append(img);
    shots.push(img);
    // Its notes.
    groups.push(x.notes.map(([big, small, icon], k) => {
      const el = document.createElement("div");
      const spot = spots[k];
      el.className = `t-chip ${spot.side}`;
      el.style.top = `${spot.top}%`;
      el.style.setProperty("--chip", x.color);
      el.innerHTML = `<i><svg viewBox="0 0 24 24" aria-hidden="true"><path d="${icons[icon] || icons.plus}"/></svg></i><div><b>${big}</b><small>${small}</small></div>`;
      calloutsEl.append(el);
      return { el, spot, show: new Spring(0) };
    }));
  });
  // The screens load when the tour is near, all at once, so none is blank when it slides in.
  new IntersectionObserver((en, obs) => {
    if (!en[0].isIntersecting) return;
    shots.forEach((img) => { img.src = shotUrl(img.dataset.name); });
    obs.disconnect();
  }, { rootMargin: "100% 0px" }).observe(stage);

  // ---- How far through: from where each step's middle is against the middle of the window.
  const pos = new Spring(0);
  let raw = 0, active = -1;
  // Each screen rests while its step is in the middle, and slides across in the gap between steps.
  const rest = (t) => {
    const i = Math.floor(t), f = t - i;
    const e = clamp((f - 0.3) / 0.4, 0, 1);
    return i + e * e * (3 - 2 * e);
  };
  // Where each step's middle is on the page, measured once (and again when the layout changes), so scrolling
  // itself never asks the browser to lay the page out.
  let centers = [];
  function measure() {
    if (!wide.matches) return;
    centers = steps.map((s) => { const r = s.getBoundingClientRect(); return r.top + scrollY + r.height / 2; });
  }
  // The scroll position read on each frame, straight from the browser: the screens move with the page, never behind it.
  function follow() {
    if (!centers.length) return;
    const mid = scrollY + innerHeight / 2, c = centers;
    let t = 0;
    if (mid <= c[0]) t = 0;
    else if (mid >= c[n - 1]) t = n - 1;
    else for (let i = 0; i < n - 1; i++) if (mid >= c[i] && mid < c[i + 1]) { t = i + (mid - c[i]) / (c[i + 1] - c[i]); break; }
    raw = t;
    pos.set(rest(t));
    setActive(Math.round(t));
  }
  function setActive(i) {
    if (i === active) return;
    active = i;
    steps.forEach((s, k) => s.classList.toggle("on", k === i));
    const color = (extra[steps[i].dataset.shot] || extra.home).color;
    document.documentElement.style.setProperty("--halo", color);
    // The notes for this screen float in one after another; the others drift away.
    groups.forEach((g, gi) => g.forEach((c, k) => {
      clearTimeout(c.timer);
      if (gi === i) c.timer = setTimeout(() => c.show.to(1, { response: 0.42, damping: 0.8 }), reduceMotion ? 0 : 50 + k * 70);
      else c.show.to(0, { response: 0.28, damping: 1 });
    }));
  }

  // ---- Leaning toward the pointer.
  const rx = new Spring(0), ry = new Spring(0);
  const tour = $("#tour");
  if (!reduceMotion) {
    tour.addEventListener("pointermove", (e) => {
      if (e.pointerType !== "mouse" || !wide.matches) return;
      const r = stage.getBoundingClientRect();
      const dx = clamp((e.clientX - (r.left + r.width / 2)) / innerWidth, -0.6, 0.6);
      const dy = clamp((e.clientY - (r.top + r.height / 2)) / innerHeight, -0.6, 0.6);
      ry.to(dx * 22, { response: 0.34, damping: 1 });
      rx.to(-dy * 14, { response: 0.34, damping: 1 });
    });
    tour.addEventListener("pointerleave", () => { rx.to(0, { response: 0.6 }); ry.to(0, { response: 0.6 }); });
  }

  let lastP = NaN;
  loop.add((dt) => {
    if (!wide.matches) return false;
    follow();
    let busy = false;
    if (rx.step(dt)) busy = true;
    if (ry.step(dt)) busy = true;
    groups.forEach((g) => g.forEach((c) => { if (c.show.step(dt)) busy = true; }));
    const p = pos.x;
    // The screens slide sideways, like switching tabs; the one leaving fades a little and steps back.
    // Only the two in view are touched, with transform and opacity, which the graphics chip moves without repainting.
    if (p !== lastP) {
      lastP = p;
      shots.forEach((img, i) => {
        const d = i - p, a = Math.min(Math.abs(d), 1);
        const show = Math.abs(d) < 0.995; // fully out: not even its edge shows
        if (!show) { if (img.style.visibility !== "hidden") img.style.visibility = "hidden"; return; }
        img.style.visibility = "visible";
        img.style.transform = `translate3d(${d * 100}%, 0, 0) scale(${1 - a * 0.06})`;
        img.style.opacity = String(1 - a * 0.4);
      });
    }
    stage.style.transform = `perspective(1400px) rotateX(${rx.x}deg) rotateY(${ry.x}deg)`;
    phone.style.setProperty("--sheen", `${-ry.x * 6}%`);
    halo.style.transform = `translate3d(${-ry.x * 2.2}px, ${rx.x * 2.2}px, 0) scale(${1 + Math.abs(raw - Math.round(raw)) * 0.12})`;
    rail.style.transform = `scaleY(${clamp(p / (n - 1), 0, 1)})`;
    // Notes sit in front of the phone, so they move further with the lean; arriving, they rise and grow from small.
    groups.forEach((g) => g.forEach((c) => {
      const s = c.show.x, depth = c.spot.depth;
      // Gone notes are taken out entirely, so their frosted glass isn't worked out behind nothing.
      const gone = s < 0.01;
      if (gone !== c.gone) { c.gone = gone; c.el.style.visibility = gone ? "hidden" : "visible"; }
      if (gone) return;
      c.el.style.opacity = String(clamp(s, 0, 1));
      c.el.style.transform = `translate3d(${ry.x * depth}px, ${-rx.x * depth + (1 - s) * 18}px, 0) scale(${0.86 + 0.14 * s})`;
    }));
    return busy;
  });

  // Scrolling only asks for the next frame; the frame reads where the page is.
  addEventListener("scroll", () => loop.wake(), { passive: true });
  const remeasure = () => { measure(); lastP = NaN; loop.wake(); };
  addEventListener("resize", remeasure);
  addEventListener("load", remeasure);
  wide.addEventListener("change", remeasure);
  new ResizeObserver(remeasure).observe($("#steps"));
  measure();
  follow();
  loop.wake();
})();
