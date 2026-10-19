// Leftovers B: small things across the page. Sections rise into place as they come into view, and cards catch a
// soft light under the mouse. Both are only decoration: without this file the page reads exactly the same.
(() => {
  "use strict";
  const { reduceMotion } = window.LO;

  // ---- Rising into view, a beat apart for things side by side.
  if (!reduceMotion && "IntersectionObserver" in window) {
    const sel = [
      "main > section:not(.hero) > h2", "main > section:not(.hero) > .section-lede", ".panel", ".switches", ".facts > div",
      ".changelog", ".closing > *",
    ].join(",");
    const items = [...document.querySelectorAll(sel)].filter((el) => {
      // Already on screen when the page opens: leave it be, so nothing blinks.
      const r = el.getBoundingClientRect();
      return r.top > innerHeight * 0.95;
    });
    const io = new IntersectionObserver((entries) => {
      entries.forEach((en) => {
        if (!en.isIntersecting) return;
        en.target.classList.add("in");
        io.unobserve(en.target);
        // Once there, the class goes, so the element's own transforms (a dragged message) are its own again.
        setTimeout(() => en.target.classList.remove("rv", "in"), 1300);
      });
    }, { rootMargin: "0px 0px -8% 0px" });
    items.forEach((el) => {
      const sibs = el.parentElement ? [...el.parentElement.children].filter((c) => items.includes(c)) : [];
      el.style.setProperty("--d", `${Math.min(sibs.indexOf(el), 4) * 0.07}s`);
      el.classList.add("rv");
      io.observe(el);
    });
  }

  // ---- A light that follows the mouse over cards.
  if (matchMedia("(hover: hover) and (pointer: fine)").matches) {
    const cards = ".panel, .switches, .msg, .mini-app, .ways";
    document.querySelectorAll(cards).forEach((el) => el.classList.add("spot"));
    let lit = null;
    addEventListener("pointermove", (e) => {
      const el = e.target.closest?.(".spot");
      if (lit && lit !== el) lit.classList.remove("lit");
      lit = el;
      if (!el) return;
      const r = el.getBoundingClientRect();
      el.style.setProperty("--mx", `${e.clientX - r.left}px`);
      el.style.setProperty("--my", `${e.clientY - r.top}px`);
      el.classList.add("lit");
    }, { passive: true });
    document.addEventListener("pointerleave", () => { lit?.classList.remove("lit"); lit = null; });
  }
})();
