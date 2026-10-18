// Leftovers website: the live safe-to-spend demo, the screens gallery, the detection examples, the assistant card,
// and What's new and the latest version, read from GitHub so the page never needs editing for a release.
(() => {
  "use strict";
  const $ = (s, el = document) => el.querySelector(s);
  const $$ = (s, el = document) => [...el.querySelectorAll(s)];
  const rupees = new Intl.NumberFormat("en-IN", { maximumFractionDigits: 0 });
  const fmt = (n) => (n < 0 ? "−₹" : "₹") + rupees.format(Math.abs(Math.round(n)));
  const reduceMotion = matchMedia("(prefers-reduced-motion: reduce)").matches;

  // Theme: follow the phone or computer, unless switched here.
  const root = document.documentElement;
  const saved = (() => { try { return localStorage.getItem("theme"); } catch { return null; } })();
  const prefersLight = matchMedia("(prefers-color-scheme: light)");
  const applyTheme = (t) => { root.dataset.theme = t; };
  applyTheme(saved || (prefersLight.matches ? "light" : "dark"));
  prefersLight.addEventListener("change", (e) => { if (!saved) applyTheme(e.matches ? "light" : "dark"); });
  $("#theme").addEventListener("click", () => {
    const next = root.dataset.theme === "light" ? "dark" : "light";
    applyTheme(next);
    try { localStorage.setItem("theme", next); } catch {}
  });

  // ---- The demo: the same sum as the app's Home ----
  const now = new Date();
  const daysInMonth = new Date(now.getFullYear(), now.getMonth() + 1, 0).getDate();
  const day = now.getDate();
  const daysLeft = daysInMonth - day + 1; // today counts
  const hour = now.getHours();
  $("#greeting").textContent = (hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening") +
    ", " + now.toLocaleDateString("en-IN", { weekday: "short", day: "numeric", month: "short" });
  $("#month-name").textContent = now.toLocaleDateString("en-IN", { month: "long", year: "numeric" });

  const bills = [
    { name: "Rent", amount: 18000, on: true },
    { name: "Netflix", amount: 649, on: true },
    { name: "Mobile plan", amount: 399, on: true },
    { name: "Gym", amount: 1500, on: false },
  ];
  const state = { budget: 50000, spends: [] };
  // What a careful spender has already used before today: a bit under the even pace.
  const spentBefore = () => Math.max(0, Math.round((state.budget - 20548) * ((day - 1) / daysInMonth) * 0.9 / 10) * 10);

  const billsEl = $("#bills");
  bills.forEach((b, i) => {
    const chip = document.createElement("button");
    chip.type = "button";
    chip.className = "chip";
    chip.textContent = `${b.name} ${fmt(b.amount)}`;
    chip.setAttribute("aria-pressed", String(b.on));
    chip.addEventListener("click", () => {
      b.on = !b.on;
      chip.setAttribute("aria-pressed", String(b.on));
      render();
    });
    billsEl.append(chip);
  });

  // Odometer: each digit is a reel of 0–9 that slides to its number.
  const safeEl = $("#safe");
  function setOdometer(value) {
    const text = fmt(value);
    const cells = [...text];
    // Rebuild only when the shape (separators, length) changes; otherwise just move the reels.
    const shape = cells.map((c) => (/\d/.test(c) ? "d" : c)).join("");
    if (safeEl.dataset.shape !== shape) {
      safeEl.dataset.shape = shape;
      safeEl.innerHTML = "";
      cells.forEach((c) => {
        if (/\d/.test(c)) {
          const col = document.createElement("span");
          col.className = "col";
          const reel = document.createElement("span");
          reel.className = "reel";
          for (let d = 0; d <= 9; d++) { const s = document.createElement("span"); s.textContent = d; reel.append(s); }
          col.append(reel);
          safeEl.append(col);
        } else {
          const s = document.createElement("span");
          s.className = "sym";
          s.textContent = c;
          safeEl.append(s);
        }
      });
    }
    const reels = $$(".reel", safeEl);
    const digits = cells.filter((c) => /\d/.test(c));
    // Each digit is 1.05em tall (see .odometer .reel span); the first draw lands without rolling.
    const place = () => reels.forEach((r, i) => { r.style.transform = `translateY(${-Number(digits[i]) * 1.05}em)`; });
    if (!safeEl.dataset.ready) {
      reels.forEach((r) => { r.style.transition = "none"; });
      place();
      void safeEl.offsetWidth;
      reels.forEach((r) => { r.style.transition = ""; });
      safeEl.dataset.ready = "1";
    } else {
      requestAnimationFrame(place);
    }
    safeEl.setAttribute("aria-label", `Safe to spend today: ${text.replace("−", "minus ")}`);
  }

  function render() {
    const billsDue = bills.filter((b) => b.on).reduce((s, b) => s + b.amount, 0);
    const before = spentBefore();
    const allowance = Math.max(0, (state.budget - before - billsDue) / daysLeft);
    const spentToday = state.spends.reduce((s, x) => s + x.amount, 0);
    const safe = allowance - spentToday;
    setOdometer(safe);
    safeEl.classList.toggle("over", safe < 0);
    const fill = $("#bar-fill");
    fill.style.width = allowance > 0 ? Math.min(100, (spentToday / allowance) * 100) + "%" : "100%";
    fill.classList.toggle("over", safe < 0);
    $("#spent-today").textContent = `${fmt(spentToday)} of ${fmt(allowance)}`;
    $("#days-left").textContent = daysLeft === 1 ? "Last day of the month" : `${daysLeft} days left`;
    $("#budget-out").textContent = fmt(state.budget);
    $("#sum").innerHTML =
      `${fmt(state.budget)} budget, minus <b>${fmt(before)}</b> already spent and <b>${fmt(billsDue)}</b> in bills still due, ` +
      `spread over ${daysLeft} ${daysLeft === 1 ? "day" : "days"} is <b>${fmt(allowance)}</b> a day.` +
      (spentToday ? ` After today's <b>${fmt(spentToday)}</b>, ${safe < 0 ? `you're <b>${fmt(-safe)}</b> over` : `<b>${fmt(safe)}</b> is left`}.` : "");

    const log = $("#log");
    log.innerHTML = "";
    state.spends.slice(-3).reverse().forEach((s) => {
      const li = document.createElement("li");
      li.innerHTML = `<span>${s.note}<small>${s.time}</small></span><span>${fmt(-s.amount)}</span>`;
      log.append(li);
    });
    $("#log-empty").hidden = state.spends.length > 0;
    $("#undo").hidden = state.spends.length === 0;
  }

  $$("[data-spend]").forEach((chip) => chip.addEventListener("click", () => {
    const t = new Date();
    state.spends.push({ amount: Number(chip.dataset.spend), note: chip.dataset.note, time: t.toLocaleTimeString("en-IN", { hour: "numeric", minute: "2-digit" }) });
    if (state.spends.length > 6) state.spends.shift();
    render();
  }));
  $("#undo").addEventListener("click", () => { state.spends.pop(); render(); });
  $("#budget").addEventListener("input", (e) => { state.budget = Number(e.target.value); render(); });
  render();

  // ---- Screens gallery ----
  const gallery = $("#gallery");
  const step = () => (gallery.querySelector("li")?.getBoundingClientRect().width || 260) + 22;
  $("#prev").addEventListener("click", () => gallery.scrollBy({ left: -step(), behavior: reduceMotion ? "auto" : "smooth" }));
  $("#next").addEventListener("click", () => gallery.scrollBy({ left: step(), behavior: reduceMotion ? "auto" : "smooth" }));
  gallery.addEventListener("keydown", (e) => {
    if (e.key === "ArrowRight") { e.preventDefault(); $("#next").click(); }
    if (e.key === "ArrowLeft") { e.preventDefault(); $("#prev").click(); }
  });

  // ---- What it notices: real message formats and what the app makes of them ----
  const messages = [
    { from: "AD-HDFCBK", text: "Sent Rs.250.00 From HDFC Bank A/C *1234 To SWIGGY On 07/10/26 Ref 612345678901 Not You? Call 18002586161",
      who: "Swiggy", amount: 250, sub: "Suggested for HDFC Bank, from the card digits 1234" },
    { from: "Google Pay", text: "₹120 paid to Third Wave Coffee. UPI transaction ID 612345678902.",
      who: "Third Wave Coffee", amount: 120, sub: "From a payment app notification, into the account you picked for it" },
    { from: "AD-KOTAKB", text: "Your A/c XX1234 is credited with INR 75,000.00 on 01-10-2026 by NEFT from ACME TECH PVT LTD. Avl Bal INR 87000.00",
      who: "Acme Tech Pvt Ltd", amount: 75000, income: true, sub: "Suggested as income" },
    { from: "AD-ICICIB", text: "123456 is your OTP for a transaction of Rs 500.00 at SWIGGY. Do not share it with anyone.",
      skip: true },
  ];
  const tabs = $$('[role="tab"]');
  function showMessage(i) {
    const m = messages[i];
    tabs.forEach((t, k) => { t.setAttribute("aria-selected", String(k === i)); t.tabIndex = k === i ? 0 : -1; });
    $("#msg-from").textContent = m.from;
    $("#msg-text").textContent = m.text;
    const s = $("#suggestion");
    s.style.animation = "none"; void s.offsetWidth; s.style.animation = "";
    s.innerHTML = m.skip
      ? `<div class="s-skip"><strong>Skipped</strong>A one-time password isn't a payment, so nothing is suggested.</div>`
      : `<div class="s-top"><span class="s-who">${m.who}</span><span class="s-amt ${m.income ? "in" : ""}">${m.income ? "+" : ""}${fmt(m.amount)}</span></div>` +
        `<div class="s-sub">${m.sub}. Tap to add it, or dismiss.</div>`;
  }
  tabs.forEach((t, i) => {
    t.addEventListener("click", () => showMessage(i));
    t.addEventListener("keydown", (e) => {
      const d = e.key === "ArrowRight" ? 1 : e.key === "ArrowLeft" ? -1 : 0;
      if (d) { e.preventDefault(); const n = (i + d + tabs.length) % tabs.length; tabs[n].focus(); showMessage(n); }
    });
  });
  showMessage(0);

  // ---- The assistant's change card ----
  const change = $("#change");
  const settle = (text) => {
    change.classList.add("done");
    const r = document.createElement("div");
    r.className = "result";
    r.textContent = text;
    change.append(r);
  };
  $("#confirm").addEventListener("click", () => settle("Added ₹250 for Coffee today"));
  $("#not-now").addEventListener("click", () => settle("Nothing was changed"));

  // ---- Latest version and What's new, from GitHub ----
  const repo = "saisaharsh3/Leftovers";
  fetch(`https://api.github.com/repos/${repo}/releases/latest`, { headers: { Accept: "application/vnd.github+json" } })
    .then((r) => (r.ok ? r.json() : Promise.reject()))
    .then((rel) => {
      const version = (rel.tag_name || "").replace(/^v/, "");
      const apk = (rel.assets || []).find((a) => a.name.endsWith(".apk"));
      if (apk) { $("#apk").href = apk.browser_download_url; $("#apk2").href = apk.browser_download_url; }
      const date = rel.published_at ? new Date(rel.published_at).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" }) : "";
      if (version) {
        $("#version-line").textContent = `Version ${version}${date ? `, released ${date}` : ""}. Free and open source. Android 8.0 or newer.`;
        $("#apk2").textContent = `Download ${version}`;
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
      let current = null;
      for (const line of md.split("\n")) {
        const head = line.match(/^## (.+)$/);
        if (head) { current = { title: head[1].trim(), items: [] }; sections.push(current); continue; }
        if (!current) continue;
        if (/^- /.test(line)) current.items.push(line.slice(2).trim());
        else if (/^\s{2,}\S/.test(line) && current.items.length) current.items[current.items.length - 1] += " " + line.trim();
      }
      const released = sections.filter((s) => /^\d/.test(s.title) && s.items.length).slice(0, 5);
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
