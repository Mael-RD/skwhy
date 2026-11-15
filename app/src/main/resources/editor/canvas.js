/* Zone de travail : affiche un graphe (cartes + liens), déplacement / zoom, sélection, choix d'une cible sur le graphe. */
(function () {
  'use strict';
  const E = window.E;
  const h = E.h;
  const MARGIN = 60;
  const ARROW_CLASSES = ['list', 'start', 'answer', 'action', 'next', 'resume', 'unlock', 'cond'];

  E.Canvas = function (viewport, handlers) {
    const self = this;
    this.handlers = handlers || {};
    this.viewport = viewport;
    this.world = h('div', { class: 'world' });
    this.svg = E.svg('svg', { class: 'edges' });
    this.defs = E.svg('defs');
    for (const c of ARROW_CLASSES) {
      const m = E.svg('marker', { id: 'arrow-' + c, viewBox: '0 0 10 10', refX: '9', refY: '5', markerWidth: '7', markerHeight: '7', orient: 'auto', class: 'mk-' + c });
      m.appendChild(E.svg('path', { d: 'M0 0 L10 5 L0 10 z' }));
      this.defs.appendChild(m);
    }
    this.svg.appendChild(this.defs);
    this.edgeLayer = E.svg('g');
    this.svg.appendChild(this.edgeLayer);
    this.world.appendChild(this.svg);
    this.nodeLayer = h('div', { class: 'nodes' });
    this.world.appendChild(this.nodeLayer);
    this.viewport.appendChild(this.world);
    this.view = { x: 20, y: 20, k: 1 };
    this.nodes = new Map();   // id -> {spec, x, y, w, h}
    this.edgeEls = new Map(); // id -> {el, from, to}
    this.size = { w: 0, h: 0 };
    this.selected = null;
    this.pick = null;
    this.fold = { min: new Set(), hide: new Set() }; // cartes réduites / cartes dont la suite est masquée
    this.graph = null;
    this.applyView();
    viewport.addEventListener('selectstart', ev => ev.preventDefault());
    viewport.addEventListener('dragstart', ev => ev.preventDefault());

    // déplacement
    let drag = null;
    viewport.addEventListener('pointerdown', ev => {
      if (ev.button !== 0 && ev.button !== 1) return;
      if (ev.target.closest('.card') && ev.button === 0) { drag = { card: true, x: ev.clientX, y: ev.clientY, moved: false, target: ev.target }; return; }
      drag = { x: ev.clientX, y: ev.clientY, vx: self.view.x, vy: self.view.y, moved: false };
      viewport.setPointerCapture(ev.pointerId);
      viewport.classList.add('panning');
    });
    viewport.addEventListener('pointermove', ev => {
      if (!drag || drag.card) return;
      const dx = ev.clientX - drag.x, dy = ev.clientY - drag.y;
      if (Math.abs(dx) + Math.abs(dy) > 3) { drag.moved = true; self.touched = true; }
      self.view.x = drag.vx + dx; self.view.y = drag.vy + dy;
      self.applyView();
    });
    const end = ev => {
      if (!drag) return;
      const wasCard = drag.card, moved = drag.moved || (drag.card && Math.abs(ev.clientX - drag.x) + Math.abs(ev.clientY - drag.y) > 6);
      const downTarget = drag.target;
      drag = null;
      viewport.classList.remove('panning');
      if (!wasCard && !moved && !ev.target.closest('.card')) self.select(null, true);
      else if (wasCard && !moved) self.activate(downTarget && downTarget.isConnected ? downTarget : ev.target);
    };
    viewport.addEventListener('pointerup', end);
    viewport.addEventListener('pointercancel', () => { drag = null; viewport.classList.remove('panning'); });
    viewport.addEventListener('wheel', ev => {
      ev.preventDefault();
      self.touched = true;
      const rect = viewport.getBoundingClientRect();
      const px = ev.clientX - rect.left, py = ev.clientY - rect.top;
      const factor = Math.exp(-ev.deltaY * (ev.ctrlKey ? 0.01 : 0.0016));
      self.zoomAt(px, py, factor);
    }, { passive: false });
    // un clic synthétique (tests, accessibilité) passe par le même chemin
    this.nodeLayer.addEventListener('click', ev => { if (!ev.isTrusted) self.activate(ev.target); });
    this.nodeLayer.addEventListener('pointerover', ev => {
      const card = ev.target.closest('.card');
      self.hover(card ? card.getAttribute('data-id') : null);
    });
    this.nodeLayer.addEventListener('pointerleave', () => self.hover(null));
  };

  const P = E.Canvas.prototype;

  /** Clic sur une carte (ou un de ses éléments) : sélection, ou choix de la cible en mode « relier ». */
  P.activate = function (target) {
    const card = target && target.closest ? target.closest('.card') : null;
    if (!card) return;
    const open = target.closest('[data-open]');
    if (open) { if (this.handlers.onOpen) this.handlers.onOpen(open.getAttribute('data-open')); return; }
    const ctl = target.closest('[data-fold]');
    if (ctl) { this.toggleFold(card.getAttribute('data-id'), ctl.getAttribute('data-fold')); return; }
    const n = this.nodes.get(card.getAttribute('data-id'));
    if (!n || !n.spec.sel) return;
    if (this.pick) { this.pick(n.spec.sel, n.spec); return; }
    const rowEl = target.closest('.row, .tblock');
    this.select(n.spec.sel, true, rowEl ? this.focusOf(rowEl) : null);
  };

  P.focusOf = function (rowEl) {
    const block = rowEl.closest('.tblock');
    if (!block) return null;
    const f = { text: parseInt(block.getAttribute('data-text'), 10) };
    const port = rowEl.getAttribute('data-port') || '';
    const m = /^a(\d+)_(\d+)/.exec(port);
    if (m) f.answer = parseInt(m[2], 10);
    return f;
  };

  P.applyView = function () {
    this.world.style.transform = 'translate(' + this.view.x + 'px,' + this.view.y + 'px) scale(' + this.view.k + ')';
    // zoom arrière : les cartes n'affichent plus que leur titre, en gros
    this.world.classList.toggle('lod', this.view.k < 0.42);
    this.world.style.setProperty('--inv', (1 / this.view.k).toFixed(2));
    if (this.handlers.onView) this.handlers.onView(this.view);
  };

  P.zoomAt = function (px, py, factor) {
    const k = Math.max(0.04, Math.min(2.2, this.view.k * factor));
    const f = k / this.view.k;
    this.view.x = px - (px - this.view.x) * f;
    this.view.y = py - (py - this.view.y) * f;
    this.view.k = k;
    this.applyView();
  };

  P.zoomBy = function (factor) {
    const r = this.viewport.getBoundingClientRect();
    this.zoomAt(r.width / 2, r.height / 2, factor);
  };

  /**
   * Cadre le graphe. Un graphe trop grand pour rester lisible s'ouvre sur son point d'entrée (la mini-carte et
   * « Vue d'ensemble » donnent le reste) ; {all: true} cadre tout, même illisible.
   */
  P.fit = function (opts) {
    const r = this.viewport.getBoundingClientRect();
    if (!this.size.w || !r.width) return;
    const kAll = Math.max(0.04, Math.min(1, Math.min((r.width - 40) / (this.size.w + MARGIN * 2), (r.height - 40) / (this.size.h + MARGIN * 2))));
    if (!(opts && opts.all) && kAll < 0.4) {
      let first = null;
      for (const n of this.nodes.values()) { if (n.spec.root) { first = n; break; } if (!first) first = n; }
      this.view.k = 0.7;
      this.view.x = 30 - (first.x - MARGIN) * 0.7;
      this.view.y = Math.max(20, r.height / 2 - (first.y + first.h / 2) * 0.7);
      this.applyView();
      return;
    }
    this.view.k = kAll;
    this.view.x = Math.max(20, (r.width - (this.size.w + MARGIN * 2) * kAll) / 2);
    this.view.y = Math.max(20, (r.height - (this.size.h + MARGIN * 2) * kAll) / 2);
    this.applyView();
  };

  /** Centre la vue sur une carte (sans changer le zoom). */
  P.centerOn = function (id) {
    const n = this.nodes.get(id);
    if (!n) return;
    const r = this.viewport.getBoundingClientRect();
    this.view.x = r.width / 2 - (n.x + n.w / 2) * this.view.k;
    this.view.y = r.height / 2 - (n.y + n.h / 2) * this.view.k;
    this.applyView();
  };

  /** Réduit / déploie une carte ('min') ou masque / montre tout ce qui la suit ('hide'), en gardant la carte à la même place à l'écran. */
  P.toggleFold = function (id, what) {
    const set = what === 'hide' ? this.fold.hide : this.fold.min;
    if (set.has(id)) set.delete(id); else set.add(id);
    this.relayout(id);
  };

  /** Réduit toutes les cartes (true) ou déploie tout (false). */
  P.foldAll = function (collapse) {
    this.fold.hide.clear();
    this.fold.min.clear();
    if (collapse) for (const [id, n] of this.nodes) if (n.spec.el.querySelector('.head')) this.fold.min.add(id);
    this.relayout(null);
    this.fit({ all: true });
  };

  P.relayout = function (anchorId) {
    if (!this.graph) return;
    const before = anchorId && this.nodes.get(anchorId);
    const bx = before ? before.x * this.view.k + this.view.x : 0, by = before ? before.y * this.view.k + this.view.y : 0;
    this.setGraph(this.graph, { keepView: true });
    const after = anchorId && this.nodes.get(anchorId);
    if (after) {
      this.view.x = bx - after.x * this.view.k;
      this.view.y = by - after.y * this.view.k;
      this.applyView();
    }
    this.touched = true;
    if (this.handlers.onFold) this.handlers.onFold();
  };

  /** Cartes masquées : tout ce qui n'est joignable que par une carte dont la suite est repliée. */
  function hiddenSet(graph, hide) {
    const out = new Map();
    for (const e of graph.edges) {
      if (!out.has(e.from)) out.set(e.from, []);
      out.get(e.from).push(e.to);
    }
    const ids = new Set(graph.nodes.map(n => n.id));
    const cand = new Set();
    const stack = [];
    for (const f of hide) if (ids.has(f)) stack.push(f);
    while (stack.length) {
      for (const t of out.get(stack.pop()) || []) if (!cand.has(t)) { cand.add(t); stack.push(t); }
    }
    for (const f of hide) cand.delete(f);
    if (!cand.size) return { hidden: cand, counts: new Map() };
    // joignables sans passer par une carte repliée
    const seen = new Set();
    const st = graph.nodes.map(n => n.id).filter(id => !cand.has(id));
    st.forEach(id => seen.add(id));
    while (st.length) {
      const id = st.pop();
      if (hide.has(id)) continue;
      for (const t of out.get(id) || []) if (!seen.has(t)) { seen.add(t); st.push(t); }
    }
    const hidden = new Set([...cand].filter(id => !seen.has(id)));
    const counts = new Map();
    for (const f of hide) {
      if (!ids.has(f)) continue;
      const reach = new Set(), q = [f];
      while (q.length) for (const t of out.get(q.pop()) || []) if (hidden.has(t) && !reach.has(t)) { reach.add(t); q.push(t); }
      counts.set(f, reach.size);
    }
    return { hidden, counts };
  }

  /** Boutons de repli sur l'en-tête d'une carte et pastille « n masquées ». */
  function decorate(canvas, s, hasOut, hiddenCount) {
    const el = s.el;
    el.querySelectorAll('.foldctl, .foldnote').forEach(x => x.remove());
    const head = el.querySelector('.head');
    el.classList.toggle('mini', canvas.fold.min.has(s.id));
    el.classList.toggle('folded', canvas.fold.hide.has(s.id));
    if (!head) return;
    const min = canvas.fold.min.has(s.id), hid = canvas.fold.hide.has(s.id);
    const ctl = h('span', { class: 'foldctl' },
      s.openDialogue ? h('button', { class: 'foldbtn', 'data-open': s.openDialogue, title: 'Ouvrir ce dialogue dans une grande fenêtre' }, '⤢') : null,
      hasOut || hid ? h('button', { class: 'foldbtn' + (hid ? ' on' : ''), 'data-fold': 'hide', title: hid ? 'Montrer la suite de cette carte' : 'Masquer tout ce qui suit cette carte' }, hid ? '⤵' : '⤴') : null,
      h('button', { class: 'foldbtn' + (min ? ' on' : ''), 'data-fold': 'min', title: min ? 'Déployer le contenu' : 'Réduire (ne garder que le titre)' }, min ? '▸' : '▾'));
    head.appendChild(ctl);
    if (hid) el.appendChild(h('div', { class: 'foldnote', 'data-fold': 'hide', title: 'Montrer la suite' }, '⋯ ' + hiddenCount + ' carte' + (hiddenCount > 1 ? 's' : '') + ' masquée' + (hiddenCount > 1 ? 's' : '')));
  }

  /**
   * Affiche un graphe. {keepView: true} garde le zoom et la position (modification en cours).
   */
  P.setGraph = function (graph, opts) {
    opts = opts || {};
    E.clear(this.nodeLayer);
    E.clear(this.edgeLayer);
    this.nodes.clear();
    this.edgeEls.clear();
    this.graph = graph;
    // 0. cartes masquées / réduites
    const fold = hiddenSet(graph, this.fold.hide);
    const hasOut = new Set(graph.edges.map(e => e.from));
    for (const s of graph.nodes) decorate(this, s, hasOut.has(s.id), fold.counts.get(s.id) || 0);
    const specs = graph.nodes.filter(s => !fold.hidden.has(s.id));
    const shown = new Set(specs.map(s => s.id));
    const edgeList = graph.edges.filter(e => shown.has(e.from) && shown.has(e.to));
    // 1. mesure des cartes
    const staging = h('div', { class: 'nodes staging' });
    this.world.appendChild(staging);
    for (const s of specs) {
      s.el.classList.add('card');
      s.el.setAttribute('data-id', s.id);
      s.el.style.width = s.w + 'px';
      s.el.style.height = ''; // hauteur naturelle (sinon une carte réduite garde l'ancienne)
      staging.appendChild(s.el);
    }
    const metrics = specs.map(s => {
      const ports = {};
      const top = s.el.getBoundingClientRect().top;
      const scale = this.view.k || 1;
      (s.el.classList.contains('mini') ? [] : s.el.querySelectorAll('[data-port]')).forEach(r => {
        const rr = r.getBoundingClientRect();
        const y = (rr.top - top) / scale + rr.height / scale / 2;
        const p2 = r.getAttribute('data-port2');
        ports[r.getAttribute('data-port')] = p2 ? y - 3 : y;
        if (p2) ports[p2] = y + 3;
      });
      const hd = s.el.querySelector('.head');
      return { id: s.id, w: s.w, h: s.el.offsetHeight, root: !!s.root, ports, outY: hd ? hd.offsetHeight / 2 : undefined };
    });
    staging.remove();
    // 2. disposition (les cartes des zones latérales sont posées à part, à gauche et à droite du graphe central)
    const zones = graph.zones || { left: [], right: [] };
    const inLeft = new Set(zones.left), inRight = new Set(zones.right);
    const isZone = id => inLeft.has(id) || inRight.has(id);
    const innerMetrics = metrics.filter(m => !isZone(m.id));
    const innerEdges = edgeList.filter(e => !isZone(e.from) && !isZone(e.to));
    const res = E.Layout.run({
      nodes: innerMetrics,
      edges: innerEdges.map(e => ({ id: e.id, from: e.from, to: e.to, port: e.port }))
    });
    const mOf = new Map(metrics.map(m => [m.id, m]));
    const ZGAP = 110, ZSP = 22;
    const colH = ids => ids.filter(id => mOf.has(id)).reduce((t, id) => t + mOf.get(id).h + ZSP, -ZSP);
    const colW = ids => ids.reduce((w, id) => Math.max(w, mOf.has(id) ? mOf.get(id).w : 0), 0);
    const leftIds = zones.left.filter(id => mOf.has(id)), rightIds = zones.right.filter(id => mOf.has(id));
    const lw = leftIds.length ? colW(leftIds) + ZGAP : 0, rw = rightIds.length ? colW(rightIds) + ZGAP : 0;
    const totalH = Math.max(res.height, leftIds.length ? colH(leftIds) : 0, rightIds.length ? colH(rightIds) : 0);
    const dy = (totalH - res.height) / 2;
    const pos = {};
    for (const m of innerMetrics) { const p = res.nodes[m.id]; pos[m.id] = { x: p.x + lw, y: p.y + dy }; }
    const stack = (ids, x) => { let y = (totalH - colH(ids)) / 2; for (const id of ids) { pos[id] = { x, y }; y += mOf.get(id).h + ZSP; } };
    stack(leftIds, 0);
    stack(rightIds, lw + res.width + rw - colW(rightIds));
    this.size = { w: lw + res.width + rw, h: totalH };
    this.svg.setAttribute('width', Math.max(1, this.size.w + MARGIN * 2));
    this.svg.setAttribute('height', Math.max(1, this.size.h + MARGIN * 2));
    this.svg.style.overflow = 'visible';
    this.edgeLayer.setAttribute('transform', 'translate(' + MARGIN + ',' + MARGIN + ')');
    // 3. cartes
    for (const s of specs) {
      const p = pos[s.id];
      const m = mOf.get(s.id);
      s.el.style.left = (p.x + MARGIN) + 'px';
      s.el.style.top = (p.y + MARGIN) + 'px';
      s.el.style.height = m.h + 'px';
      this.nodeLayer.appendChild(s.el);
      this.nodes.set(s.id, { spec: s, x: p.x + MARGIN, y: p.y + MARGIN, w: s.w, h: m.h });
    }
    // 4. liens (chacun a un tracé visible et un tracé invisible, plus large, qui reçoit le survol)
    const addEdge = (e, d, cls, shift) => {
      const path = E.svg('path', { d, class: 'edge e-' + e.cls + (cls || ''), 'marker-end': 'url(#arrow-' + e.cls + ')' });
      const hit = E.svg('path', { d, class: 'edgehit' });
      if (shift) { path.setAttribute('transform', shift); hit.setAttribute('transform', shift); }
      this.edgeLayer.appendChild(path);
      this.edgeLayer.appendChild(hit);
      hit.addEventListener('pointerenter', () => this.setHot(e.id));
      hit.addEventListener('pointerleave', () => this.setHot(null));
      this.edgeEls.set(e.id, { el: path, from: e.from, to: e.to });
    };
    const shift = 'translate(' + lw + ',' + dy + ')';
    for (const e of innerEdges) {
      const p = res.edges[e.id];
      if (p) addEdge(e, p.d, p.back ? ' back' : '', shift);
    }
    for (const e of edgeList) {
      if (!isZone(e.from) && !isZone(e.to)) continue;
      const a = pos[e.from], b = pos[e.to], ma = mOf.get(e.from), mb = mOf.get(e.to);
      const head = m => m.outY !== undefined ? m.outY : Math.min(22, m.h / 2);
      const sx = a.x + ma.w, sy = a.y + (ma.ports[e.port] !== undefined ? ma.ports[e.port] : head(ma));
      const ex = b.x, ey = b.y + (isZone(e.to) ? head(mb) : Math.min(24, mb.h / 2));
      const dx = Math.max(40, Math.abs(ex - sx) / 2);
      addEdge(e, 'M' + sx + ' ' + sy + ' C' + (sx + dx) + ' ' + sy + ',' + (ex - dx) + ' ' + ey + ',' + ex + ' ' + ey, '', null);
    }
    // légendes des groupes de cartes non reliés au point d'entrée
    const rootComp = res.comps.some(c => c.root);
    for (const c of res.comps) {
      if (rootComp && !c.root && !graph.zones) {
        const cap = h('div', { class: 'caption', style: { left: (MARGIN + lw) + 'px', top: (MARGIN + dy + c.y - 34) + 'px' } },
          'Non atteint depuis le point d\'entrée');
        this.nodeLayer.appendChild(cap);
      }
    }
    if (!opts.keepView) this.fit();
    this.refreshSelection();
  };

  P.select = function (sel, notify, focus) {
    this.selected = sel;
    this.refreshSelection();
    if (notify && this.handlers.onSelect) this.handlers.onSelect(sel, focus);
  };

  function same(a, b) {
    if (!a || !b) return false;
    return a.t === b.t && a.i === b.i && a.id === b.id && a.a === b.a && a.s === b.s;
  }

  P.refreshSelection = function () {
    const ids = new Set();
    for (const [id, n] of this.nodes) {
      const on = same(n.spec.sel, this.selected);
      n.spec.el.classList.toggle('sel', on);
      if (on) ids.add(id);
    }
    for (const e of this.edgeEls.values()) e.el.classList.toggle('hl', ids.has(e.from) || ids.has(e.to));
  };

  /** Met en valeur un lien (survol) : les autres s'estompent, les deux cartes reliées sont soulignées. */
  P.setHot = function (id) {
    if (this.hotId === id) return;
    this.hotId = id;
    this.edgeLayer.classList.toggle('hasHot', !!id);
    const ends = new Set();
    for (const [eid, e] of this.edgeEls) {
      const on = eid === id;
      e.el.classList.toggle('hot', on);
      if (on) { ends.add(e.from); ends.add(e.to); }
    }
    for (const [nid, n] of this.nodes) n.spec.el.classList.toggle('linked', ends.has(nid));
  };

  P.hover = function (id) {
    for (const e of this.edgeEls.values()) e.el.classList.toggle('hover', !!id && (e.from === id || e.to === id));
  };

  P.nodeIdOf = function (sel) {
    for (const [id, n] of this.nodes) if (same(n.spec.sel, sel)) return id;
    return null;
  };

  /** Mode « choisir une cible » : le prochain clic sur une carte appelle callback(sel, spec). */
  P.startPick = function (filter, callback) {
    this.pick = (sel, spec) => {
      if (filter && !filter(sel)) { E.toast('Cette carte ne convient pas ici.', 'error'); return; }
      this.stopPick();
      callback(sel, spec);
    };
    this.viewport.classList.add('picking');
    for (const n of this.nodes.values()) n.spec.el.classList.toggle('pickable', !!n.spec.sel && (!filter || filter(n.spec.sel)));
  };

  P.stopPick = function () {
    this.pick = null;
    this.viewport.classList.remove('picking');
    for (const n of this.nodes.values()) n.spec.el.classList.remove('pickable');
  };

  // ── Mini-carte ──────────────────────────────────────────────────────────
  E.Minimap = function (box, canvas) {
    const self = this;
    this.box = box;
    this.canvas = canvas;
    this.cv = h('canvas', { width: 220, height: 150 });
    box.appendChild(this.cv);
    this.pending = false;
    let drag = false;
    const move = ev => {
      const r = self.cv.getBoundingClientRect();
      const m = self.metrics();
      if (!m) return;
      const wx = (ev.clientX - r.left) / m.s - m.ox, wy = (ev.clientY - r.top) / m.s - m.oy;
      const vp = canvas.viewport.getBoundingClientRect();
      canvas.view.x = vp.width / 2 - wx * canvas.view.k;
      canvas.view.y = vp.height / 2 - wy * canvas.view.k;
      canvas.applyView();
    };
    this.cv.addEventListener('pointerdown', ev => { drag = true; self.cv.setPointerCapture(ev.pointerId); move(ev); });
    this.cv.addEventListener('pointermove', ev => { if (drag) move(ev); });
    this.cv.addEventListener('pointerup', () => { drag = false; });
  };
  E.Minimap.prototype.metrics = function () {
    const sz = this.canvas.size;
    if (!sz.w) return null;
    const w = sz.w + 2 * MARGIN, hh = sz.h + 2 * MARGIN;
    const s = Math.min(this.cv.width / w, this.cv.height / hh);
    return { s, ox: (this.cv.width / s - w) / 2, oy: (this.cv.height / s - hh) / 2 };
  };
  E.Minimap.prototype.draw = function () {
    if (this.pending) return;
    this.pending = true;
    requestAnimationFrame(() => {
      this.pending = false;
      const c = this.canvas, m = this.metrics();
      const show = c.nodes.size > 10 && m;
      this.box.classList.toggle('hidden', !show);
      if (!show) return;
      const g = this.cv.getContext('2d');
      g.clearRect(0, 0, this.cv.width, this.cv.height);
      g.save();
      g.scale(m.s, m.s);
      g.translate(m.ox, m.oy);
      for (const n of c.nodes.values()) {
        const cls = n.spec.el.className;
        g.fillStyle = cls.includes('step') ? '#f5c04a' : cls.includes('state') ? '#5aa2ff' : cls.includes('npcchip') ? '#b58cff' : cls.includes('c-gold') ? '#f5c04a' : cls.includes('c-green') ? '#6bd66b' : cls.includes('c-teal') ? '#4ad6a7' : cls.includes('c-red') ? '#ff6b6b' : cls.includes('c-orange') ? '#ff9f5a' : '#8f9ab0';
        g.fillRect(n.x, n.y, n.w, n.h);
      }
      const vp = c.viewport.getBoundingClientRect();
      g.strokeStyle = '#ffffff';
      g.lineWidth = 2 / m.s;
      g.strokeRect(-c.view.x / c.view.k, -c.view.y / c.view.k, vp.width / c.view.k, vp.height / c.view.k);
      g.restore();
    });
  };
})();
