/* Petits composants de formulaire de l'inspecteur. Les écouteurs reçoivent la nouvelle valeur ; l'appelant décide quoi modifier. */
(function () {
  'use strict';
  const E = window.E;
  const h = E.h;
  const F = E.F = {};

  F.field = function (label, control, hint, cls) {
    return h('label', { class: 'field ' + (cls || '') }, h('span', { class: 'flabel' }, label), control, hint ? h('span', { class: 'fhint' }, hint) : null);
  };

  F.text = function (value, onChange, opts) {
    opts = opts || {};
    const el = h('input', { type: 'text', value: E.str(value), placeholder: opts.placeholder || '', list: opts.list || null, spellcheck: 'false' });
    el.addEventListener('input', () => onChange(el.value));
    return el;
  };

  F.area = function (value, onChange, opts) {
    opts = opts || {};
    const el = h('textarea', { rows: opts.rows || 3, placeholder: opts.placeholder || '', spellcheck: opts.spellcheck ? 'true' : 'false' });
    el.value = E.str(value);
    const fit = () => { el.style.height = 'auto'; el.style.height = Math.min(320, Math.max(52, el.scrollHeight + 2)) + 'px'; };
    el.addEventListener('input', () => { onChange(el.value); fit(); if (prev) refresh(); });
    let prev = null;
    const refresh = () => { E.clear(prev); prev.appendChild(E.mc(el.value, '#d8dee9')); };
    setTimeout(fit, 0);
    if (opts.preview) {
      prev = h('div', { class: 'mcprev' });
      refresh();
      return h('div', { class: 'areawrap' }, el, prev);
    }
    return el;
  };

  /** Nombre ; {optional:true} : vide = valeur retirée (undefined). */
  F.num = function (value, onChange, opts) {
    opts = opts || {};
    const el = h('input', { type: 'number', value: value === undefined || value === null ? '' : String(value), step: opts.step || 'any', min: opts.min !== undefined ? opts.min : null, max: opts.max !== undefined ? opts.max : null, placeholder: opts.placeholder || '' });
    el.addEventListener('input', () => {
      if (el.value === '') { if (opts.optional) onChange(undefined); return; }
      const n = parseFloat(el.value);
      if (Number.isFinite(n)) onChange(opts.int ? Math.round(n) : n);
    });
    return el;
  };

  F.check = function (label, value, onChange) {
    const el = h('input', { type: 'checkbox' });
    el.checked = value === true || value === 1 || value === 'true';
    el.addEventListener('change', () => onChange(el.checked));
    return h('label', { class: 'check' }, el, h('span', null, label));
  };

  /** options : [{v, l}] ou [valeur] */
  F.select = function (options, value, onChange, opts) {
    opts = opts || {};
    const el = h('select');
    let found = false;
    for (const o of options) {
      const v = typeof o === 'object' ? o.v : o;
      const l = typeof o === 'object' ? o.l : o;
      const opt = h('option', { value: String(v) }, l);
      if (String(v) === String(value === undefined || value === null ? '' : value)) { opt.selected = true; found = true; }
      el.appendChild(opt);
    }
    if (!found && value !== undefined && value !== null && value !== '') {
      const opt = h('option', { value: String(value), selected: true }, '⚠ ' + value);
      el.insertBefore(opt, el.firstChild);
    }
    el.addEventListener('change', () => onChange(el.value));
    return el;
  };

  /** Liste de lignes (tableau de textes). */
  F.lines = function (value, onChange, opts) {
    const arr = Array.isArray(value) ? value : (value === undefined ? [] : [value]);
    return F.area(arr.join('\n'), v => onChange(v.split('\n').filter(s => s.trim() !== '')), Object.assign({ rows: 3 }, opts || {}));
  };

  /** JSON libre ; n'appelle onChange que si le JSON est valide. */
  F.json = function (value, onChange, opts) {
    opts = opts || {};
    const el = h('textarea', { rows: opts.rows || 4, spellcheck: 'false', class: 'json' });
    el.value = value === undefined ? '' : JSON.stringify(value, null, 2);
    const msg = h('div', { class: 'fhint bad hidden' }, 'JSON invalide');
    el.addEventListener('input', () => {
      el.style.height = 'auto'; el.style.height = Math.min(360, Math.max(60, el.scrollHeight + 2)) + 'px';
      if (el.value.trim() === '') { msg.classList.add('hidden'); onChange(undefined); return; }
      try { const v = JSON.parse(el.value); msg.classList.add('hidden'); onChange(v); }
      catch (e) { msg.classList.remove('hidden'); }
    });
    setTimeout(() => { el.style.height = Math.min(360, Math.max(60, el.scrollHeight + 2)) + 'px'; }, 0);
    return h('div', { class: 'jsonwrap' }, el, msg);
  };

  // ── Items des quêtes ────────────────────────────────────────────────────

  const ITEM_FLAGS = [{ v: 'owner', l: 'Nominatif (owner)' }, { v: 'safe', l: 'safe' }, { v: 'save', l: 'save' }, { v: 'invulnerable', l: 'Invulnérable' }, { v: 'preserve', l: 'preserve' }];

  /** Id d'item custom (liste du catalogue) ou matériau vanilla. */
  F.itemId = function (value, onChange) {
    return F.text(value, onChange, { list: 'items-list', placeholder: 'id d\'item custom ou matériau (stone)' });
  };

  /**
   * Une entrée d'item : {item, count, placeholders, parameters}. item = id d'item custom ou matériau, ou une table au format d'une
   * définition d'item (material_id, name...) pour un item utilisé à un seul endroit. onChange reçoit l'entrée complète.
   */
  F.itemEntry = function (entry, onChange, opts) {
    opts = opts || {};
    let cur = E.isObj(entry) ? JSON.parse(JSON.stringify(entry)) : {};
    if (cur.params !== undefined && cur.placeholders === undefined) { cur.placeholders = cur.params; delete cur.params; }
    const emit = fn => { fn(cur); onChange(JSON.parse(JSON.stringify(cur))); };
    const inline = E.isObj(cur.item);
    const info = h('span', { class: 'fhint' });
    const refreshInfo = () => {
      const c = E.isObj(cur.item) ? null : E.itemInfo(cur.item);
      info.textContent = c ? 'Item custom : ' + (E.plain(c.name) || c.material) : (cur.item && !E.isObj(cur.item) ? 'Matériau ou id inconnu du catalogue' : '');
    };
    refreshInfo();
    const kind = F.select([{ v: 'id', l: 'Item custom / matériau' }, { v: 'table', l: 'Table (item unique, format d\'une définition)' }], inline ? 'table' : 'id', v => {
      emit(c => { c.item = v === 'table' ? { material_id: E.isObj(c.item) ? c.item.material_id : (c.item || 'stone') } : (E.isObj(c.item) ? (c.item.material_id || '') : c.item); });
      if (opts.restructure) opts.restructure(JSON.parse(JSON.stringify(cur)));
    });
    const control = inline
      ? F.json(cur.item, v => emit(c => { c.item = v === undefined ? {} : v; }), { rows: 5 })
      : F.itemId(cur.item, v => { emit(c => { c.item = v; }); refreshInfo(); });
    const flags = h('div', { class: 'flags' }, ITEM_FLAGS.map(f => F.check(f.l, Array.isArray(cur.parameters) && cur.parameters.includes(f.v), on => emit(c => {
      const list = Array.isArray(c.parameters) ? c.parameters.slice() : [];
      const at = list.indexOf(f.v);
      if (on && at < 0) list.push(f.v);
      if (!on && at >= 0) list.splice(at, 1);
      if (list.length) c.parameters = list; else delete c.parameters;
    }))));
    return h('div', { class: 'subcard' },
      opts.title ? h('div', { class: 'subhead' }, h('b', null, opts.title)) : null,
      F.field('Nature', kind),
      F.row(F.field(inline ? 'Définition' : 'Item', control, null, 'grow'), F.field('Quantité', F.num(cur.count, v => emit(c => { if (v === undefined) delete c.count; else c.count = v; }), { int: true, min: 1, optional: true, placeholder: '1' }))),
      inline ? null : info,
      F.field('Valeurs des {clés} (JSON, ex. {"value": 50} pour un billet)', F.json(cur.placeholders, v => emit(c => { if (v === undefined) delete c.placeholders; else c.placeholders = v; }), { rows: 2 })),
      F.field('Paramètres de l\'item', flags));
  };

  /** Liste d'entrées d'item. onChange : modification d'un champ ; onStructure : ajout, suppression, déplacement (l'inspecteur est redessiné). */
  F.itemEntries = function (list, onChange, onStructure) {
    const arr = Array.isArray(list) ? JSON.parse(JSON.stringify(list)) : [];
    const rows = arr.map((entry, i) => h('div', { class: 'itemrow' },
      F.itemEntry(entry, v => { arr[i] = v; onChange(JSON.parse(JSON.stringify(arr))); }, { title: 'Item ' + (i + 1), restructure: v => { arr[i] = v; onStructure(JSON.parse(JSON.stringify(arr))); } }),
      F.tools(i, arr.length, {
        up: i > 0 ? () => { F.move(arr, i, -1); onStructure(arr); } : null,
        down: i < arr.length - 1 ? () => { F.move(arr, i, 1); onStructure(arr); } : null,
        remove: () => { arr.splice(i, 1); onStructure(arr); }
      })));
    return h('div', { class: 'itemlist' }, rows, F.btn('＋ Item', () => { arr.push({ item: '', count: 1 }); onStructure(arr); }, 'primary'));
  };

  /** Un échange {condition, uses, action, buy1, buy2, result}. */
  F.tradeEntry = function (trade, onChange, onStructure, opts) {
    opts = opts || {};
    const cur = E.isObj(trade) ? JSON.parse(JSON.stringify(trade)) : {};
    const emit = fn => { fn(cur); onChange(JSON.parse(JSON.stringify(cur))); };
    const slot = (key, label) => {
      if (!E.isObj(cur[key])) {
        return F.row(h('span', { class: 'flabel' }, label), F.btn('＋ ' + label, () => { cur[key] = { item: '', count: 1 }; onStructure(JSON.parse(JSON.stringify(cur))); }, 'mini'));
      }
      return h('div', null, F.itemEntry(cur[key], v => emit(c => { c[key] = v; }), { title: label, restructure: v => { cur[key] = v; onStructure(JSON.parse(JSON.stringify(cur))); } }),
        key === 'result' ? null : F.btn('✕ ' + label, () => { delete cur[key]; onStructure(JSON.parse(JSON.stringify(cur))); }, 'mini danger'));
    };
    return h('div', { class: 'subcard' },
      opts.title ? h('div', { class: 'subhead' }, h('b', null, opts.title)) : null,
      slot('buy1', 'Achat 1'), slot('buy2', 'Achat 2'), slot('result', 'Résultat'),
      F.row(F.field('Condition (id de script)', F.text(cur.condition, v => emit(c => { if (v === '') delete c.condition; else c.condition = v; }))),
        F.field('Utilisations', F.num(cur.uses, v => emit(c => { if (v === undefined) delete c.uses; else c.uses = v; }), { int: true, min: 1, optional: true })),
        F.field('Action (id de script)', F.text(cur.action, v => emit(c => { if (v === '') delete c.action; else c.action = v; }))))
    );
  };

  /** Un lot d'échanges {name, trades: [...]}. */
  F.trade = function (trade, onChange, onStructure) {
    const cur = E.isObj(trade) ? JSON.parse(JSON.stringify(trade)) : {};
    if (!Array.isArray(cur.trades)) cur.trades = [];
    const snapshot = () => JSON.parse(JSON.stringify(cur));
    const rows = cur.trades.map((t, i) => h('div', { class: 'itemrow' },
      F.tradeEntry(t, v => { cur.trades[i] = v; onChange(snapshot()); }, v => { cur.trades[i] = v; onStructure(snapshot()); }, { title: 'Échange ' + (i + 1) }),
      F.tools(i, cur.trades.length, {
        up: i > 0 ? () => { F.move(cur.trades, i, -1); onStructure(snapshot()); } : null,
        down: i < cur.trades.length - 1 ? () => { F.move(cur.trades, i, 1); onStructure(snapshot()); } : null,
        dup: () => { cur.trades.splice(i + 1, 0, JSON.parse(JSON.stringify(t))); onStructure(snapshot()); },
        remove: () => { cur.trades.splice(i, 1); onStructure(snapshot()); }
      })));
    return h('div', { class: 'itemlist' },
      F.field('Titre du menu', F.text(cur.name, v => { if (v === '') delete cur.name; else cur.name = v; onChange(snapshot()); })),
      rows, F.btn('＋ Échange', () => { cur.trades.push({ buy1: { item: '', count: 1 }, result: { item: '', count: 1 } }); onStructure(snapshot()); }, 'primary'));
  };

  F.btn = function (label, onClick, cls, title) {
    return h('button', { type: 'button', class: 'btn ' + (cls || ''), title: title || null, onclick: ev => { ev.preventDefault(); onClick(ev); } }, label);
  };

  F.section = function (title, body, opts) {
    opts = opts || {};
    const det = h('details', { class: 'section' }, h('summary', null, title, opts.count !== undefined ? h('span', { class: 'count' }, String(opts.count)) : null), h('div', { class: 'sbody' }, body));
    if (!opts.collapsed) det.open = true;
    return det;
  };

  F.row = function (...children) { return h('div', { class: 'frow' }, ...children); };

  /** Boutons ↑ ↓ ✕ d'une ligne de liste. */
  F.tools = function (index, length, handlers) {
    return h('span', { class: 'tools' },
      handlers.up ? F.btn('↑', handlers.up, 'mini', 'Monter') : null,
      handlers.down ? F.btn('↓', handlers.down, 'mini', 'Descendre') : null,
      handlers.dup ? F.btn('⧉', handlers.dup, 'mini', 'Dupliquer') : null,
      handlers.remove ? F.btn('✕', handlers.remove, 'mini danger', 'Supprimer') : null);
  };

  F.move = function (arr, i, d) {
    const j = i + d;
    if (j < 0 || j >= arr.length) return false;
    const t = arr[i]; arr[i] = arr[j]; arr[j] = t;
    return true;
  };
})();
