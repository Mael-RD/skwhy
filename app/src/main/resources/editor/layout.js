/*
 * Disposition en couches (gauche -> droite) d'un graphe orienté, avec routage orthogonal des liens :
 *  - chaque lien a sa propre « voie » verticale dans l'espace entre deux colonnes (jamais deux liens superposés) ;
 *  - les liens qui sautent des colonnes passent par des points intermédiaires, donc ne traversent pas les cartes ;
 *  - les liens entrants d'une carte arrivent à des hauteurs différentes ;
 *  - les cycles sont coupés (liens « retour » tracés en courbe pointillée).
 * Pur (sans DOM) : testable avec Node.
 */
(function (root) {
  'use strict';

  const OPTS = { vgap: 26, compGap: 90, laneMin: 7, laneMax: 13, gapMin: 74, gapMax: 420, round: 7 };

  /**
   * @param g { nodes: [{id, w, h, root?, ports?: {name: yOffset}}], edges: [{id, from, port?, to}] }
   * @return { nodes: {id: {x, y, layer, comp}}, edges: {id: {d, pts, back, sx, sy, ex, ey}}, width, height, comps: [{x,y,w,h,ids}] }
   */
  function run(g, userOpts) {
    const o = Object.assign({}, OPTS, userOpts || {});
    const nodes = new Map();
    g.nodes.forEach((n, i) => nodes.set(n.id, { n, i, id: n.id, out: [], inn: [] }));
    const edges = [];
    for (const e of g.edges) {
      if (!nodes.has(e.from) || !nodes.has(e.to) || e.from === e.to) continue;
      const rec = { e, from: nodes.get(e.from), to: nodes.get(e.to), back: false };
      rec.from.out.push(rec);
      rec.to.inn.push(rec);
      edges.push(rec);
    }

    // composantes connexes (le graphe sans orientation)
    const comp = new Map();
    const comps = [];
    for (const n of nodes.values()) {
      if (comp.has(n.id)) continue;
      const list = [];
      const stack = [n];
      comp.set(n.id, comps.length);
      while (stack.length) {
        const x = stack.pop();
        list.push(x);
        for (const r of x.out) if (!comp.has(r.to.id)) { comp.set(r.to.id, comps.length); stack.push(r.to); }
        for (const r of x.inn) if (!comp.has(r.from.id)) { comp.set(r.from.id, comps.length); stack.push(r.from); }
      }
      list.sort((a, b) => a.i - b.i);
      comps.push(list);
    }
    // celles qui contiennent une racine d'abord
    const order = comps.map((c, idx) => ({ c, idx, hasRoot: c.some(x => x.n.root) })).sort((a, b) => (b.hasRoot - a.hasRoot) || (a.c[0].i - b.c[0].i));

    const result = { nodes: {}, edges: {}, width: 0, height: 0, comps: [] };
    let yBase = 0;
    for (const entry of order) {
      const r = layoutComponent(entry.c, o);
      for (const x of entry.c) {
        const p = r.pos.get(x.id);
        result.nodes[x.id] = { x: p.x, y: p.y + yBase, layer: p.layer, comp: entry.idx };
      }
      for (const [id, path] of r.paths) {
        path.pts.forEach(pt => { pt[1] += yBase; });
        path.sy += yBase; path.ey += yBase;
        path.d = pathData(path.pts, path.back, o.round, path);
        result.edges[id] = path;
      }
      result.comps.push({ x: 0, y: yBase, w: r.width, h: r.height, ids: entry.c.map(x => x.id), root: entry.hasRoot });
      result.width = Math.max(result.width, r.width);
      yBase += r.height + o.compGap;
    }
    result.height = Math.max(0, yBase - o.compGap);
    return result;
  }

  function layoutComponent(list, o) {
    const inComp = new Set(list.map(x => x.id));
    // 1. cycles : DFS depuis les racines
    const color = new Map();
    const roots = list.filter(x => x.n.root);
    const starts = roots.concat(list.filter(x => !x.n.root && x.inn.length === 0), list);
    const dfsIndex = new Map();
    let counter = 0;
    for (const s of starts) {
      if (color.has(s.id)) continue;
      const stack = [{ x: s, k: 0 }];
      color.set(s.id, 1);
      dfsIndex.set(s.id, counter++);
      while (stack.length) {
        const top = stack[stack.length - 1];
        if (top.k >= top.x.out.length) { color.set(top.x.id, 2); stack.pop(); continue; }
        const r = top.x.out[top.k++];
        const c = color.get(r.to.id);
        if (c === 1) r.back = true;
        else if (c === undefined) { color.set(r.to.id, 1); dfsIndex.set(r.to.id, counter++); stack.push({ x: r.to, k: 0 }); }
      }
    }
    const fwd = x => x.out.filter(r => !r.back);
    const bwd = x => x.inn.filter(r => !r.back);

    // 2. couches : plus long chemin
    const layer = new Map();
    const indeg = new Map();
    list.forEach(x => indeg.set(x.id, bwd(x).length));
    const queue = list.filter(x => indeg.get(x.id) === 0);
    queue.forEach(x => layer.set(x.id, 0));
    const topo = [];
    for (let qi = 0; qi < queue.length; qi++) {
      const x = queue[qi];
      topo.push(x);
      for (const r of fwd(x)) {
        layer.set(r.to.id, Math.max(layer.get(r.to.id) || 0, layer.get(x.id) + 1));
        indeg.set(r.to.id, indeg.get(r.to.id) - 1);
        if (indeg.get(r.to.id) === 0) queue.push(r.to);
      }
    }
    // les sources (hors racines) se rapprochent de leur première destination
    for (const x of list) {
      if (x.n.root || bwd(x).length > 0 || fwd(x).length === 0) continue;
      const m = Math.min(...fwd(x).map(r => layer.get(r.to.id)));
      if (m - 1 > layer.get(x.id)) layer.set(x.id, m - 1);
    }
    // 3. éléments (cartes + points de passage des liens longs)
    const items = new Map();
    const layers = [];
    const addItem = (it, l) => { it.layer = l; (layers[l] = layers[l] || []).push(it); items.set(it.key, it); };
    list.forEach(x => addItem({ key: x.id, real: x, w: x.n.w, h: x.n.h, preds: [], succs: [] }, layer.get(x.id)));
    const segs = []; // segments entre colonnes voisines
    const chains = new Map();
    let dummyN = 0;
    const sortedEdges = [];
    for (const x of list) for (const r of x.out) if (inComp.has(r.to.id)) sortedEdges.push(r);
    for (const r of sortedEdges) {
      if (r.back) continue;
      const la = layer.get(r.from.id), lb = layer.get(r.to.id);
      const chain = [items.get(r.from.id)];
      for (let l = la + 1; l < lb; l++) {
        const d = { key: '~' + (dummyN++), dummy: true, w: 0, h: 2, preds: [], succs: [], edge: r };
        addItem(d, l);
        chain.push(d);
      }
      chain.push(items.get(r.to.id));
      chains.set(r, chain);
      for (let k = 0; k + 1 < chain.length; k++) {
        const s = { a: chain[k], b: chain[k + 1], r, first: k === 0, last: k + 2 === chain.length };
        segs.push(s);
        chain[k].succs.push(s);
        chain[k + 1].preds.push(s);
      }
    }
    for (let l = 0; l < layers.length; l++) layers[l] = layers[l] || [];

    // 4. ordre dans les colonnes : barycentres
    layers.forEach(col => col.sort((p, q) => (p.real && q.real ? (dfsIndex.get(p.key) - dfsIndex.get(q.key)) : 0) || ((p.real ? p.real.i : 1e9) - (q.real ? q.real.i : 1e9))));
    const setPos = () => layers.forEach(col => col.forEach((it, i) => { it.pos = i; }));
    setPos();
    for (let iter = 0; iter < 14; iter++) {
      if (iter % 2 === 0) {
        for (let l = 1; l < layers.length; l++) sortBy(layers[l], it => it.preds.map(s => s.a.pos), setPos);
      } else {
        for (let l = layers.length - 2; l >= 0; l--) sortBy(layers[l], it => it.succs.map(s => s.b.pos), setPos);
      }
    }

    // 5. positions : x par colonne, y par empilement guidé par les voisins
    const segCount = layers.map((_, l) => segs.filter(s => s.a.layer === l).length);
    const colX = [], colW = [];
    let x = 0;
    for (let l = 0; l < layers.length; l++) {
      colX[l] = x;
      colW[l] = layers[l].reduce((m, it) => Math.max(m, it.w), 0);
      const gap = Math.max(o.gapMin, Math.min(o.gapMax, 44 + 10 * segCount[l]));
      x += colW[l] + gap;
    }
    layers.forEach((col, l) => col.forEach(it => { it.x = colX[l]; }));
    layers.forEach(col => { let y = 0; col.forEach(it => { it.y = y; y += it.h + (it.dummy ? 8 : o.vgap); }); });

    const srcY = (s) => s.a.y + (s.a.real ? portY(s.a.real.n, s.r.e.port) : s.a.h / 2);
    const pack = (col, desired, down) => {
      if (down) {
        let y = -1e9;
        col.forEach(it => { const gap = it.dummy ? 8 : o.vgap; it.y = Math.max(desired(it), y); y = it.y + it.h + gap; });
      } else {
        let y = 1e9;
        for (let i = col.length - 1; i >= 0; i--) {
          const it = col[i]; const gap = it.dummy ? 8 : o.vgap;
          it.y = Math.min(desired(it), y - it.h); y = it.y - gap;
        }
      }
    };
    for (let round = 0; round < 6; round++) {
      if (round % 2 === 0) {
        for (let l = 1; l < layers.length; l++) {
          pack(layers[l], it => it.preds.length ? avg(it.preds.map(s => srcY(s) - entryGuess(s))) : it.y, true);
        }
      } else {
        for (let l = layers.length - 2; l >= 0; l--) {
          pack(layers[l], it => it.succs.length ? avg(it.succs.map(s => s.b.y + (s.b.real ? s.b.h / 2 : s.b.h / 2) - (s.a.real ? portY(s.a.real.n, s.r.e.port) : 0))) : it.y, false);
        }
      }
      // éviter les chevauchements après chaque tour
      layers.forEach(col => pack(col, it => it.y, true));
    }
    let minY = Infinity, maxY = -Infinity, maxX = 0;
    layers.forEach(col => col.forEach(it => { if (!it.dummy) { minY = Math.min(minY, it.y); maxY = Math.max(maxY, it.y + it.h); } }));
    if (!isFinite(minY)) { minY = 0; maxY = 0; }
    layers.forEach(col => col.forEach(it => { it.y -= minY; }));
    layers.forEach((col, l) => col.forEach(it => { maxX = Math.max(maxX, it.x + it.w); }));

    // 6. points d'entrée sur les cartes
    const entry = new Map(); // seg -> y
    for (const col of layers) for (const it of col) {
      if (!it.real || !it.preds.length) continue;
      const incoming = it.preds.slice().sort((p, q) => srcY(p) - srcY(q));
      const n = incoming.length;
      incoming.forEach((s, k) => {
        const top = it.y + Math.min(11, it.h / 3), bottom = it.y + it.h - Math.min(11, it.h / 3);
        entry.set(s, n === 1 ? it.y + Math.min(24, it.h / 2) : top + (bottom - top) * k / (n - 1));
      });
    }
    // 7. voies verticales par espace entre colonnes
    const lane = new Map();
    for (let l = 0; l < layers.length - 1; l++) {
      const here = segs.filter(s => s.a.layer === l);
      const sy = s => srcY(s);
      const ey = s => s.b.real ? entry.get(s) : s.b.y + s.b.h / 2;
      const down = here.filter(s => ey(s) > sy(s) + 0.5).sort((p, q) => sy(q) - sy(p) || ey(q) - ey(p));
      const up = here.filter(s => ey(s) < sy(s) - 0.5).sort((p, q) => sy(p) - sy(q) || ey(p) - ey(q));
      const straight = here.filter(s => Math.abs(ey(s) - sy(s)) <= 0.5);
      const ordered = up.concat(down);
      const gap = colX[l + 1] - (colX[l] + colW[l]);
      const step = Math.max(o.laneMin, Math.min(o.laneMax, (gap - 28) / Math.max(1, ordered.length)));
      const startX = colX[l] + colW[l] + 14 + Math.max(0, (gap - 28 - step * ordered.length) / 2);
      ordered.forEach((s, k) => lane.set(s, startX + k * step));
      straight.forEach(s => lane.set(s, null));
    }

    // 8. tracés
    const pos = new Map();
    const paths = new Map();
    for (const xx of list) pos.set(xx.id, { x: items.get(xx.id).x, y: items.get(xx.id).y, layer: layer.get(xx.id) });
    for (const r of sortedEdges) {
      const chain = chains.get(r);
      if (!chain) {
        // lien retour : courbe entre la droite de la source et la gauche de la cible
        const a = items.get(r.from.id), b = items.get(r.to.id);
        const sx = a.x + a.w, sy2 = a.y + portY(a.real.n, r.e.port);
        const ex = b.x, ey2 = b.y + Math.min(24, b.h / 2);
        paths.set(r.e.id, { pts: [[sx, sy2], [ex, ey2]], back: true, sx, sy: sy2, ex, ey: ey2, e: r.e });
        continue;
      }
      const pts = [];
      for (let k = 0; k + 1 < chain.length; k++) {
        const s = segs.find(q => q.r === r && q.a === chain[k]);
        const a = s.a, b = s.b;
        const sxp = a.dummy ? a.x : a.x + a.w;
        const syp = a.dummy ? a.y + a.h / 2 : a.y + portY(a.real.n, r.e.port);
        const exp = b.x;
        const eyp = b.dummy ? b.y + b.h / 2 : entry.get(s);
        const lx = lane.get(s);
        if (k === 0) pts.push([sxp, syp]);
        if (lx !== null && lx !== undefined && Math.abs(eyp - syp) > 0.5) { pts.push([lx, syp]); pts.push([lx, eyp]); }
        else if (Math.abs(eyp - syp) > 0.5) { const mx = (sxp + exp) / 2; pts.push([mx, syp]); pts.push([mx, eyp]); }
        pts.push([exp, eyp]);
      }
      const first = pts[0], last = pts[pts.length - 1];
      paths.set(r.e.id, { pts: dedupe(pts), back: false, sx: first[0], sy: first[1], ex: last[0], ey: last[1], e: r.e });
    }
    return { pos, paths, width: maxX, height: Math.max(0, maxY - minY) };
  }

  function sortBy(col, neighbours, setPos) {
    const keyed = col.map((it, i) => { const ns = neighbours(it); return { it, i, b: ns.length ? avg(ns) : null }; });
    // les éléments sans voisin gardent leur place relative
    const fixed = keyed.filter(k => k.b === null);
    const movable = keyed.filter(k => k.b !== null).sort((p, q) => p.b - q.b || p.i - q.i);
    const out = new Array(col.length);
    fixed.forEach(k => { out[k.i] = k.it; });
    let m = 0;
    for (let i = 0; i < out.length; i++) if (!out[i]) out[i] = movable[m++].it;
    for (let i = 0; i < out.length; i++) col[i] = out[i];
    setPos();
  }

  function avg(a) { return a.reduce((s, v) => s + v, 0) / a.length; }
  function portY(n, port) {
    if (port && n.ports && n.ports[port] !== undefined) return n.ports[port];
    return n.outY !== undefined ? n.outY : Math.min(22, n.h / 2);
  }
  function entryGuess(s) { return s.b.real ? Math.min(22, s.b.h / 2) : 1; }
  function dedupe(pts) {
    const out = [];
    for (const p of pts) { const l = out[out.length - 1]; if (!l || Math.abs(l[0] - p[0]) > 0.01 || Math.abs(l[1] - p[1]) > 0.01) out.push(p); }
    return out;
  }

  /** Chemin SVG : polyligne aux coins arrondis, ou courbe pour un lien retour. */
  function pathData(pts, back, radius, p) {
    if (back) {
      const [sx, sy] = pts[0], [ex, ey] = pts[pts.length - 1];
      const dx = Math.max(60, Math.abs(sx - ex) / 3);
      return 'M' + sx + ' ' + sy + ' C' + (sx + dx) + ' ' + (sy + 40) + ',' + (ex - dx) + ' ' + (ey - 40) + ',' + ex + ' ' + ey;
    }
    if (pts.length < 2) return '';
    let d = 'M' + pts[0][0] + ' ' + pts[0][1];
    for (let i = 1; i < pts.length - 1; i++) {
      const a = pts[i - 1], b = pts[i], c = pts[i + 1];
      const r = Math.min(radius, dist(a, b) / 2, dist(b, c) / 2);
      const p1 = toward(b, a, r), p2 = toward(b, c, r);
      d += ' L' + p1[0] + ' ' + p1[1] + ' Q' + b[0] + ' ' + b[1] + ' ' + p2[0] + ' ' + p2[1];
    }
    const l = pts[pts.length - 1];
    return d + ' L' + l[0] + ' ' + l[1];
  }
  function dist(a, b) { return Math.hypot(a[0] - b[0], a[1] - b[1]); }
  function toward(from, to, r) {
    const d = dist(from, to) || 1;
    return [from[0] + (to[0] - from[0]) * r / d, from[1] + (to[1] - from[1]) * r / d];
  }

  const API = { run, OPTS };
  if (typeof module !== 'undefined' && module.exports) module.exports = API;
  else root.E = Object.assign(root.E || {}, { Layout: API });
})(typeof window !== 'undefined' ? window : globalThis);
