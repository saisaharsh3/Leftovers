// Leftovers B: the hands-on parts. The month you scrub, the goal you plan, and the messages you drop on the app.
// Same springs and clock as app.js (window.LO), so a finger can always take over and nothing jumps.
(() => {
  "use strict";
  const { Spring, loop, project, rubber, tracker, fmt, clamp, buzz, reduceMotion } = window.LO;
  const $ = (s, el = document) => el.querySelector(s);
  const now = new Date();
  const monthName = now.toLocaleDateString("en-IN", { month: "short" });
  const dayLabel = (d) => new Date(now.getFullYear(), now.getMonth(), d).toLocaleDateString("en-IN", { weekday: "short", day: "numeric", month: "short" });

  // ---------------------------------------------------------------- See where it went
  (() => {
    const chart = $("#chart"), barsEl = $("#bars"), pin = $("#pin");
    const days = new Date(now.getFullYear(), now.getMonth() + 1, 0).getDate();
    const today = now.getDate();
    // A believable month: the same every visit (a fixed seed), busier at weekends, a big shop now and then.
    let seed = now.getFullYear() * 12 + now.getMonth();
    const rand = () => ((seed = (seed * 9301 + 49297) % 233280) / 233280);
    const things = [["Coffee", 120], ["Lunch", 250], ["Metro", 40], ["Groceries", 1200], ["Auto", 90], ["Dinner out", 900], ["Movie", 450], ["Pharmacy", 320], ["Snacks", 80], ["Fuel", 1500]];
    const data = Array.from({ length: days }, (_, i) => {
      const d = i + 1;
      if (d > today) return { d, amount: 0, items: [], future: true };
      const weekend = [0, 6].includes(new Date(now.getFullYear(), now.getMonth(), d).getDay());
      const n = 1 + Math.floor(rand() * (weekend ? 4 : 3));
      const items = [];
      for (let k = 0; k < n; k++) items.push(things[Math.floor(rand() * things.length)]);
      return { d, amount: items.reduce((s, it) => s + Math.round(it[1] * (0.7 + rand() * 0.6) / 10) * 10, 0), items: [...new Set(items.map((it) => it[0]))] };
    });
    const spent = data.reduce((s, x) => s + x.amount, 0);
    const max = Math.max(...data.map((x) => x.amount), 1);
    const biggest = data.reduce((a, b) => (b.amount > a.amount ? b : a), data[0]);
    $("#chart-avg").textContent = fmt(spent / today);
    $("#axis-mid").textContent = String(Math.ceil(days / 2));
    $("#axis-end").textContent = String(days);
    chart.setAttribute("aria-valuemax", String(today));

    const cols = data.map((x) => {
      const col = document.createElement("div");
      col.className = "bar-col" + (x.future ? " future" : "");
      const bar = document.createElement("i");
      bar.style.height = x.future ? "5%" : `${Math.max(3, (x.amount / max) * 100)}%`;
      col.append(bar);
      barsEl.append(col);
      return { bar, grow: new Spring(0) };
    });

    const value = new Spring(spent);
    const pinX = new Spring(0);
    let selected = null; // day index, or null for the month
    const label = $("#chart-label"), out = $("#chart-value"), sub = $("#chart-sub");
    const defaultSub = `Biggest day: ${biggest.d} ${monthName}, ${fmt(biggest.amount)}`;
    function select(i) {
      if (i !== null) i = clamp(i, 0, today - 1);
      if (i === selected) return;
      selected = i;
      if (i === null) {
        label.textContent = "This month so far";
        sub.textContent = defaultSub;
        value.to(spent, { response: 0.35 });
        chart.removeAttribute("aria-valuenow");
        chart.setAttribute("aria-valuetext", `This month so far, ${fmt(spent)}`);
      } else {
        const x = data[i];
        label.textContent = dayLabel(x.d);
        sub.textContent = x.items.join(", ");
        value.to(x.amount, { response: 0.28 });
        chart.setAttribute("aria-valuenow", String(x.d));
        chart.setAttribute("aria-valuetext", `${dayLabel(x.d)}, ${fmt(x.amount)}: ${x.items.join(", ")}`);
      }
      cols.forEach((c, k) => { c.bar.style.opacity = selected === null ? "" : k === selected ? "1" : ".14"; });
      pin.style.opacity = selected === null ? "0" : "1";
    }
    sub.textContent = defaultSub;
    const colCenter = (i) => {
      const r = barsEl.children[i].getBoundingClientRect(), c = chart.getBoundingClientRect();
      return r.left - c.left + r.width / 2;
    };
    const indexAt = (clientX) => {
      const c = chart.getBoundingClientRect();
      return Math.floor(clamp((clientX - c.left) / c.width, 0, 0.9999) * days);
    };
    loop.add((dt) => {
      let busy = value.step(dt);
      out.textContent = fmt(value.x);
      cols.forEach((c) => { if (c.grow.step(dt)) busy = true; c.bar.style.transform = `scaleY(${c.grow.x})`; });
      if (pinX.step(dt)) busy = true;
      pin.style.transform = `translateX(${pinX.x}px)`;
      return busy;
    });
    // The bars grow in once, the first time the chart comes into view.
    new IntersectionObserver((entries, obs) => {
      if (!entries[0].isIntersecting) return;
      cols.forEach((c, k) => setTimeout(() => c.grow.to(1, { response: 0.5, damping: 1 }), reduceMotion ? 0 : k * 18));
      obs.disconnect();
    }, { threshold: 0.4 }).observe(chart);

    // Scrubbing: the pin stays exactly under the finger; the day under it lights up straight away.
    let scrub = null;
    const follow = (clientX) => {
      const c = chart.getBoundingClientRect();
      pinX.set(clamp(clientX - c.left, 0, c.width));
      select(indexAt(clientX));
    };
    chart.addEventListener("pointerdown", (e) => {
      scrub = { id: e.pointerId, x0: e.clientX, y0: e.clientY, touch: e.pointerType !== "mouse", locked: e.pointerType === "mouse" };
      if (scrub.locked) chart.setPointerCapture(e.pointerId);
      follow(e.clientX);
    });
    chart.addEventListener("pointermove", (e) => {
      if (!scrub) { if (e.pointerType === "mouse") follow(e.clientX); return; } // hover reads days too
      if (e.pointerId !== scrub.id) return;
      if (!scrub.locked) {
        const dx = e.clientX - scrub.x0, dy = e.clientY - scrub.y0;
        if (Math.hypot(dx, dy) < 8) return;
        if (Math.abs(dy) > Math.abs(dx)) { scrub = null; select(null); return; } // a vertical swipe scrolls the page
        scrub.locked = true;
        chart.setPointerCapture(e.pointerId);
      }
      follow(e.clientX);
    });
    const end = (e) => {
      if (!scrub || e.pointerId !== scrub.id) return;
      scrub = null;
      // The pin settles on the middle of its day.
      if (selected !== null) pinX.to(colCenter(selected), { response: 0.22 });
      if (e.pointerType !== "mouse") setTimeout(() => { if (!scrub) select(null); }, 2200);
    };
    // Once it's clearly a sideways scrub, the page stays put even if the finger drifts up or down.
    chart.addEventListener("touchmove", (e) => { if (scrub && scrub.locked) e.preventDefault(); }, { passive: false });
    chart.addEventListener("pointerup", end);
    chart.addEventListener("pointercancel", end);
    chart.addEventListener("pointerleave", (e) => { if (e.pointerType === "mouse" && !scrub) select(null); });
    chart.addEventListener("keydown", (e) => {
      const dir = e.key === "ArrowRight" ? 1 : e.key === "ArrowLeft" ? -1 : 0;
      if (e.key === "Escape") { select(null); return; }
      if (e.key === "Home" || e.key === "End") { e.preventDefault(); select(e.key === "Home" ? 0 : today - 1); pinX.to(colCenter(selected), { response: 0.22 }); return; }
      if (!dir) return;
      e.preventDefault();
      select(selected === null ? (dir > 0 ? 0 : today - 1) : selected + dir);
      pinX.to(colCenter(selected), { response: 0.22 });
    });
    chart.addEventListener("blur", () => select(null));
  })();

  // ---------------------------------------------------------------- Save for something
  (() => {
    const goals = [
      { name: "New phone", target: 60000 },
      { name: "Trip to Goa", target: 30000 },
      { name: "Laptop", target: 90000 },
      { name: "Emergency fund", target: 150000 },
    ];
    const BUDGET = 50000, C = 2 * Math.PI * 52, MIN = 1, MAX = 24;
    let goal = goals[0], months = 12;
    const chips = $("#goal-chips"), track = $("#months"), thumb = $("#months-thumb"), fill = $("#months-fill");
    const ring = $("#ring"), pctEl = $("#ring-pct"), monthlyEl = $("#goal-monthly"), subEl = $("#goal-sub"), monthsOut = $("#months-out");
    const pct = new Spring(0), monthly = new Spring(0), thumbP = new Spring((months - MIN) / (MAX - MIN));

    goals.forEach((g, i) => {
      const b = document.createElement("button");
      b.type = "button";
      b.className = "goal-chip";
      b.setAttribute("role", "radio");
      b.setAttribute("aria-checked", String(i === 0));
      b.textContent = `${g.name} ${fmt(g.target)}`;
      b.addEventListener("click", () => {
        goal = g;
        [...chips.children].forEach((c) => c.setAttribute("aria-checked", String(c === b)));
        update();
      });
      chips.append(b);
    });
    function update() {
      const m = goal.target / months;
      monthly.to(m, { response: 0.4 });
      pct.to(m / BUDGET, { response: 0.5 });
      const by = new Date(now.getFullYear(), now.getMonth() + months - 1, 1).toLocaleDateString("en-IN", { month: "short", year: "numeric" });
      subEl.textContent = m > BUDGET
        ? `More than your whole ${fmt(BUDGET)} budget. Give it a few more months.`
        : `About ${fmt(m / 30)} a day, to have it by ${by}.`;
      monthsOut.textContent = months === 1 ? "1 month" : `${months} months`;
      track.setAttribute("aria-valuenow", String(months));
      track.setAttribute("aria-valuetext", `${months} months, ${fmt(m)} a month`);
    }
    loop.add((dt) => {
      let busy = pct.step(dt);
      if (monthly.step(dt)) busy = true;
      if (thumbP.step(dt)) busy = true;
      const p = clamp(pct.x, 0, 1);
      ring.style.strokeDashoffset = String(C * (1 - p));
      ring.classList.toggle("heavy", pct.x > 0.3);
      pctEl.textContent = `${Math.round(pct.x * 100)}%`;
      monthlyEl.textContent = fmt(monthly.x);
      const w = track.clientWidth;
      thumb.style.transform = `translateX(${thumbP.x * w}px)`;
      fill.style.width = `${clamp(thumbP.x, 0, 1) * w}px`;
      return busy;
    });

    // The thumb follows the finger; past either end it resists; letting go, it lands on the month the flick was heading for.
    const fromX = (clientX) => (clientX - track.getBoundingClientRect().left) / track.clientWidth;
    const setMonths = (m) => {
      m = clamp(Math.round(m), MIN, MAX);
      if (m !== months) { months = m; update(); buzz(4); }
    };
    let g = null;
    track.addEventListener("pointerdown", (e) => {
      thumb.classList.add("held");
      g = { id: e.pointerId, x0: e.clientX, y0: e.clientY, locked: e.pointerType === "mouse", track: tracker(), grabbed: Math.abs(fromX(e.clientX) - thumbP.x) * track.clientWidth < 26, offset: fromX(e.clientX) - thumbP.x };
      g.track.add(e);
      if (g.locked) track.setPointerCapture(e.pointerId);
      thumbP.set(thumbP.x);
    });
    track.addEventListener("pointermove", (e) => {
      if (!g || e.pointerId !== g.id) return;
      g.track.add(e);
      if (!g.locked) {
        const dx = e.clientX - g.x0, dy = e.clientY - g.y0;
        if (Math.hypot(dx, dy) < 8) return;
        if (Math.abs(dy) > Math.abs(dx)) { g = null; thumb.classList.remove("held"); return; }
        g.locked = true;
        track.setPointerCapture(e.pointerId);
      }
      // Grabbed by the thumb: keep where it was held. Grabbed on the track: the thumb comes to the finger.
      const raw = fromX(e.clientX) - (g.grabbed ? g.offset : 0);
      thumbP.set(raw < 0 ? -rubber(-raw, 1, 0.2) : raw > 1 ? 1 + rubber(raw - 1, 1, 0.2) : raw);
      setMonths(MIN + clamp(thumbP.x, 0, 1) * (MAX - MIN));
    });
    const up = (e) => {
      if (!g || e.pointerId !== g.id) return;
      thumb.classList.remove("held");
      const w = track.clientWidth;
      let landing;
      if (!g.locked) landing = fromX(e.clientX); // a tap on the track
      else landing = thumbP.x + project(g.track.speed().x, 0.99) / w;
      g = null;
      setMonths(MIN + clamp(landing, 0, 1) * (MAX - MIN));
      thumbP.to((months - MIN) / (MAX - MIN), { response: 0.3, damping: 1 });
    };
    track.addEventListener("touchmove", (e) => { if (g && g.locked) e.preventDefault(); }, { passive: false });
    track.addEventListener("pointerup", up);
    track.addEventListener("pointercancel", up);
    track.addEventListener("keydown", (e) => {
      const dir = e.key === "ArrowRight" || e.key === "ArrowUp" ? 1 : e.key === "ArrowLeft" || e.key === "ArrowDown" ? -1 : 0;
      const jump = e.key === "Home" ? MIN : e.key === "End" ? MAX : null;
      if (!dir && jump === null) return;
      e.preventDefault();
      setMonths(jump ?? months + dir);
      thumbP.to((months - MIN) / (MAX - MIN), { response: 0.25 });
    });
    addEventListener("resize", () => loop.wake());
    update();
  })();

  // ---------------------------------------------------------------- It notices payments
  (() => {
    const list = $("#messages"), app = $("#mini-app"), appList = $("#mini-list"), empty = $("#mini-empty"), note = $("#mini-note"), reset = $("#detect-reset");
    const msgs = [
      { from: "AD-HDFCBK", text: "Sent Rs.250.00 From HDFC Bank A/C *1234 To SWIGGY On 07/10/26 Ref 612345678901. Not You? Call 18002586161",
        who: "Swiggy", amount: 250, sub: "HDFC Bank, from a bank text" },
      { from: "Google Pay", text: "₹120 paid to Third Wave Coffee. UPI transaction ID 612345678902.",
        who: "Third Wave Coffee", amount: 120, sub: "Google Pay notification" },
      { from: "AD-KOTAKB", text: "Your A/c XX1234 is credited with INR 75,000.00 on 01-10-2026 by NEFT from ACME TECH PVT LTD.",
        who: "Acme Tech Pvt Ltd", amount: 75000, income: true, sub: "Kotak, money in" },
      { from: "AD-ICICIB", text: "123456 is your OTP for a transaction of Rs 500.00 at SWIGGY. Do not share it with anyone.", otp: true },
    ];
    const items = msgs.map((m) => {
      const li = document.createElement("li");
      li.className = "msg";
      li.innerHTML = `<div class="msg-from"><span>${m.from}</span><button type="button">Send to the app</button></div><p>${m.text}</p>`;
      list.append(li);
      return { m, el: li, x: new Spring(0), y: new Spring(0), s: new Spring(1), o: new Spring(1), used: false, flying: false };
    });
    function draw() {
      items.forEach((it) => { it.el.style.transform = `translate3d(${it.x.x}px, ${it.y.x}px, 0) scale(${it.s.x})`; it.el.style.opacity = String(clamp(it.o.x, 0, 1)); });
    }
    let hold = null; // the message being carried, if any
    loop.add((dt) => {
      let busy = false;
      items.forEach((it) => { [it.x, it.y, it.s, it.o].forEach((sp) => { if (sp.step(dt)) busy = true; }); });
      if (hold && hold.lifted) place();
      items.forEach((it) => { if (it.flying && !it.x.active && !it.y.active) arrive(it); });
      draw();
      return busy;
    });

    const appCenterFrom = (it) => {
      const a = app.getBoundingClientRect(), r = it.el.getBoundingClientRect();
      // r includes the current transform; the delta is measured from the item's home position.
      return { dx: a.left + a.width / 2 - (r.left - it.x.x + r.width / 2), dy: a.top + 90 - (r.top - it.y.x + r.height / 2) };
    };
    const over = (clientX, clientY) => {
      const a = app.getBoundingClientRect();
      return clientX > a.left && clientX < a.right && clientY > a.top && clientY < a.bottom;
    };
    function send(it, vx = 0, vy = 0) {
      if (it.used) return;
      const { dx, dy } = appCenterFrom(it);
      it.flying = true;
      it.x.to(dx, { response: 0.42, velocity: vx });
      it.y.to(dy, { response: 0.42, velocity: vy });
      it.s.to(0.35, { response: 0.42 });
      it.o.to(0, { response: 0.5 });
      if (reduceMotion) arrive(it);
    }
    function arrive(it) {
      it.flying = false;
      app.classList.remove("ready");
      if (it.m.otp) {
        // A one-time password isn't a payment: it's handed back.
        it.x.to(0, { response: 0.5, damping: 0.75 });
        it.y.to(0, { response: 0.5, damping: 0.75 });
        it.s.to(1, { response: 0.4 });
        it.o.to(1, { response: 0.3 });
        note.textContent = "Skipped: a one-time password isn't a payment, so nothing is suggested.";
        buzz([8, 60, 8]);
        return;
      }
      it.used = true;
      it.el.classList.add("used");
      empty.hidden = true;
      const row = document.createElement("li");
      row.className = "mini-item";
      row.innerHTML = `<div><b>${it.m.who}</b><small>${it.m.sub}</small></div><span class="amt ${it.m.income ? "in" : ""}">${it.m.income ? "+" : ""}${fmt(it.m.amount)}</span><button class="add" type="button">Add</button>`;
      $(".add", row).addEventListener("click", (e) => { row.classList.add("added"); e.target.textContent = "Added"; e.target.disabled = true; buzz(10); });
      appList.prepend(row);
      // The new suggestion settles into the list.
      const pop = new Spring(0.9), fade = new Spring(0);
      pop.to(1, { response: 0.35, damping: 0.8 }); fade.to(1, { response: 0.3 });
      loop.add((dt) => { const a = pop.step(dt), b = fade.step(dt); row.style.transform = `scale(${pop.x})`; row.style.opacity = String(clamp(fade.x, 0, 1)); return a || b; });
      note.textContent = it.m.income ? "Money coming in is suggested as income." : "Nothing is logged until you tap Add.";
      reset.hidden = false;
      buzz(10);
    }

    // Picking one up: straight away with a mouse; on a touch screen, a short press first, so the page still scrolls.
    list.addEventListener("pointerdown", (e) => {
      const el = e.target.closest(".msg");
      const it = items.find((x) => x.el === el);
      if (!it || it.used || it.flying || e.target.closest("button")) return;
      // Where it was grabbed, measured from its middle at full size, so that point stays under the finger as it grows or shrinks.
      const r = el.getBoundingClientRect(), mx = r.left + r.width / 2, my = r.top + r.height / 2;
      hold = { it, id: e.pointerId, sx: e.clientX, sy: e.clientY, fx: e.clientX, fy: e.clientY, cx: mx - it.x.x, cy: my - it.y.x,
        gx: (e.clientX - mx) / it.s.x, gy: (e.clientY - my) / it.s.x, track: tracker(), lifted: false };
      hold.track.add(e);
      const lift = () => {
        if (!hold || hold.it !== it) return;
        hold.lifted = true;
        el.classList.add("held");
        el.setPointerCapture(e.pointerId);
        it.s.to(1.04, { response: 0.25 });
        buzz(6);
      };
      if (e.pointerType === "mouse") lift(); else hold.timer = setTimeout(lift, 220);
    });
    function place() {
      const { it } = hold;
      it.x.set(hold.fx - hold.cx - it.s.x * hold.gx);
      it.y.set(hold.fy - hold.cy - it.s.x * hold.gy);
    }
    // While a message is held on a touch screen, the page doesn't scroll under it.
    // On the list only, so scrolling anywhere else on the page never waits for this.
    list.addEventListener("touchmove", (e) => { if (hold && hold.lifted) e.preventDefault(); }, { passive: false });
    addEventListener("pointermove", (e) => {
      if (!hold || e.pointerId !== hold.id) return;
      if (!hold.lifted) {
        // Moved before the press finished: that's a scroll, not a pick-up.
        if (Math.hypot(e.clientX - hold.sx, e.clientY - hold.sy) > 8) { clearTimeout(hold.timer); hold = null; }
        return;
      }
      hold.track.add(e);
      hold.fx = e.clientX; hold.fy = e.clientY;
      place();
      // Over the app it shrinks toward it, so you can see it will fit; away again, it grows back.
      const on = over(e.clientX, e.clientY);
      if (on !== hold.on) { hold.on = on; hold.it.s.to(on ? 0.62 : 1.04, { response: 0.25 }); }
      app.classList.toggle("ready", on);
      draw();
    });
    const drop = (e) => {
      if (!hold || e.pointerId !== hold.id) return;
      clearTimeout(hold.timer);
      const { it, lifted, track } = hold;
      hold = null;
      if (!lifted) return;
      it.el.classList.remove("held");
      const v = track.speed();
      // Thrown toward the app counts too: decide from where it's heading.
      const ax = e.clientX + project(v.x, 0.99) * 0.6, ay = e.clientY + project(v.y, 0.99) * 0.6;
      if (over(e.clientX, e.clientY) || over(ax, ay)) send(it, v.x, v.y);
      else {
        app.classList.remove("ready");
        const fast = Math.hypot(v.x, v.y) > 400;
        it.x.to(0, { response: 0.45, damping: fast ? 0.8 : 1, velocity: v.x });
        it.y.to(0, { response: 0.45, damping: fast ? 0.8 : 1, velocity: v.y });
        it.s.to(1, { response: 0.3 });
      }
    };
    // A long press is how a message is picked up, so it shouldn't also open the browser's menu.
    list.addEventListener("contextmenu", (e) => { if (e.target.closest(".msg")) e.preventDefault(); });
    addEventListener("pointerup", drop);
    addEventListener("pointercancel", drop);
    list.addEventListener("click", (e) => {
      const btn = e.target.closest(".msg-from button");
      if (!btn) return;
      const it = items.find((x) => x.el.contains(btn));
      if (it) send(it);
    });
    reset.addEventListener("click", () => {
      items.forEach((it) => { it.used = false; it.flying = false; it.el.classList.remove("used"); it.x.set(0); it.y.set(0); it.s.set(0.9); it.o.set(0); it.s.to(1, { response: 0.4 }); it.o.to(1, { response: 0.4 }); });
      appList.querySelectorAll(".mini-item").forEach((r) => r.remove());
      empty.hidden = false;
      note.textContent = "";
      reset.hidden = true;
      draw();
    });
    draw();
  })();
})();
