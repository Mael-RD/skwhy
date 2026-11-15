/* Utilitaires de l'éditeur : DOM, appels API, couleurs Minecraft, identifiants. Tout vit dans l'objet global E. */
(function () {
  'use strict';
  const E = window.E = window.E || {};

  // ── DOM ───────────────────────────────────────────────────────────────
  E.h = function (tag, attrs, ...children) {
    const el = document.createElement(tag);
    if (attrs) {
      for (const k of Object.keys(attrs)) {
        const v = attrs[k];
        if (v === undefined || v === null || v === false) continue;
        if (k === 'class') el.className = v;
        else if (k === 'style' && typeof v === 'object') Object.assign(el.style, v);
        else if (k.startsWith('on') && typeof v === 'function') el.addEventListener(k.slice(2), v);
        else if (k === 'html') el.innerHTML = v;
        else if (v === true) el.setAttribute(k, '');
        else el.setAttribute(k, v);
      }
    }
    E.append(el, children);
    return el;
  };
  E.append = function (el, children) {
    for (const c of children) {
      if (c === null || c === undefined || c === false) continue;
      if (Array.isArray(c)) E.append(el, c);
      else if (c instanceof Node) el.appendChild(c);
      else el.appendChild(document.createTextNode(String(c)));
    }
    return el;
  };
  E.svg = function (tag, attrs) {
    const el = document.createElementNS('http://www.w3.org/2000/svg', tag);
    if (attrs) for (const k of Object.keys(attrs)) el.setAttribute(k, attrs[k]);
    return el;
  };
  E.clear = function (el) { while (el.firstChild) el.removeChild(el.firstChild); return el; };
  E.debounce = function (fn, ms) {
    let t = null;
    const wrapped = function (...a) { clearTimeout(t); t = setTimeout(() => fn.apply(null, a), ms); };
    wrapped.flush = function (...a) { clearTimeout(t); fn.apply(null, a); };
    return wrapped;
  };
  E.clone = function (v) { return v === undefined ? undefined : JSON.parse(JSON.stringify(v)); };
  E.uuid = function () {
    if (window.crypto && crypto.randomUUID) return crypto.randomUUID();
    const r = () => Math.floor(Math.random() * 65536).toString(16).padStart(4, '0');
    return r() + r() + '-' + r() + '-4' + r().slice(1) + '-a' + r().slice(1) + '-' + r() + r() + r();
  };
  E.isObj = function (v) { return v !== null && typeof v === 'object' && !Array.isArray(v); };
  E.str = function (v, d) { return v === undefined || v === null ? (d === undefined ? '' : d) : String(v); };
  E.num = function (v, d) {
    const n = typeof v === 'number' ? v : parseFloat(v);
    return Number.isFinite(n) ? n : d;
  };
  E.truncate = function (s, n) {
    s = String(s === undefined || s === null ? '' : s).replace(/\s+/g, ' ').trim();
    return s.length > n ? s.slice(0, n - 1) + '…' : s;
  };

  // ── Appels API ────────────────────────────────────────────────────────
  E.api = async function (path, body) {
    const opts = body === undefined ? {} : { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) };
    let res;
    try {
      res = await fetch('api/' + path, opts);
    } catch (e) {
      const err = new Error('Connexion fermée par le serveur (lien expiré ou fermé).');
      err.closed = true;
      throw err;
    }
    let data = null;
    try { data = await res.json(); } catch (e) { /* corps vide */ }
    if (!res.ok) {
      const err = new Error((data && data.error) || ('Erreur ' + res.status));
      err.status = res.status;
      err.data = data;
      throw err;
    }
    return data;
  };

  // ── Couleurs Minecraft ────────────────────────────────────────────────
  const COLORS = {
    black: '#000000', dark_blue: '#0000aa', dark_green: '#00aa00', dark_aqua: '#00aaaa', dark_red: '#aa0000',
    dark_purple: '#aa00aa', gold: '#ffaa00', gray: '#aaaaaa', grey: '#aaaaaa', dark_gray: '#555555', dark_grey: '#555555',
    blue: '#5555ff', green: '#55ff55', aqua: '#55ffff', red: '#ff5555', light_purple: '#ff55ff', yellow: '#ffff55', white: '#ffffff'
  };
  const CODES = {
    '0': '#000000', '1': '#0000aa', '2': '#00aa00', '3': '#00aaaa', '4': '#aa0000', '5': '#aa00aa', '6': '#ffaa00', '7': '#aaaaaa',
    '8': '#555555', '9': '#5555ff', a: '#55ff55', b: '#55ffff', c: '#ff5555', d: '#ff55ff', e: '#ffff55', f: '#ffffff'
  };
  const FORMATS = { l: 'bold', o: 'italic', n: 'underline', m: 'strike', k: 'obf' };
  const TAG_FORMATS = { bold: 'bold', b: 'bold', italic: 'italic', i: 'italic', em: 'italic', underlined: 'underline', u: 'underline',
    strikethrough: 'strike', st: 'strike', obfuscated: 'obf', obf: 'obf' };

  /** Texte avec couleurs (&a, §a, <red>, <#ff8800>, <bold>...) en éléments DOM (jamais de HTML brut). */
  E.mc = function (text, fallbackColor) {
    const root = document.createElement('span');
    text = E.str(text);
    const re = /[&§]([0-9a-fk-orA-FK-OR#])(?:([0-9a-fA-F]{6}))?|<(\/?)([a-zA-Z_#0-9:]+)(?::[^>]*)?>/g;
    let state = { color: null, bold: false, italic: false, underline: false, strike: false, obf: false };
    let last = 0, m;
    const emit = (s) => {
      if (!s) return;
      const span = document.createElement('span');
      span.textContent = s;
      const c = state.color || fallbackColor;
      if (c) span.style.color = c;
      if (state.bold) span.style.fontWeight = '700';
      if (state.italic) span.style.fontStyle = 'italic';
      const deco = [state.underline ? 'underline' : '', state.strike ? 'line-through' : ''].filter(Boolean).join(' ');
      if (deco) span.style.textDecoration = deco;
      root.appendChild(span);
    };
    while ((m = re.exec(text)) !== null) {
      emit(text.slice(last, m.index));
      last = re.lastIndex;
      if (m[1] !== undefined) {
        const code = m[1].toLowerCase();
        if (code === 'r') state = { color: null, bold: false, italic: false, underline: false, strike: false, obf: false };
        else if (CODES[code]) state = { color: CODES[code], bold: false, italic: false, underline: false, strike: false, obf: false };
        else if (FORMATS[code]) state[FORMATS[code]] = true;
        else if (code === '#' && m[2]) state.color = '#' + m[2];
        else { emit(m[0]); }
      } else {
        const closing = m[3] === '/';
        const tag = m[4].toLowerCase();
        if (tag === 'reset') state = { color: null, bold: false, italic: false, underline: false, strike: false, obf: false };
        else if (/^#[0-9a-f]{6}$/.test(tag)) state.color = closing ? null : tag;
        else if (COLORS[tag]) state.color = closing ? null : COLORS[tag];
        else if (tag === 'color' || tag === 'c') { if (closing) state.color = null; }
        else if (TAG_FORMATS[tag]) state[TAG_FORMATS[tag]] = !closing;
        else emit(m[0]);
      }
    }
    emit(text.slice(last));
    return root;
  };
  E.plain = function (text) {
    return E.str(text).replace(/[&§][0-9a-fk-orA-FK-OR]/g, '').replace(/<\/?[a-zA-Z_#:0-9]+(?::[^>]*)?>/g, '');
  };

  /** Items custom du catalogue (id -> {id, name, material}), rempli par app.js. */
  E.items = new Map();
  // Variables Skript de règles de coût proposées à l'action « move » (config quests.pathfinding_rules)
  E.pathRules = [];
  E.itemInfo = function (id) { return id ? E.items.get(String(id).trim().toLowerCase().replace(/\s+/g, '_')) || null : null; };
  /** Texte court d'une entrée d'item de quête : id, matériau ou nom d'une table. */
  E.itemLabel = function (entry) {
    if (!E.isObj(entry)) return E.str(entry);
    let name;
    if (E.isObj(entry.item)) name = E.plain(entry.item.name) || E.str(entry.item.material_id || entry.item.id, '?');
    else name = E.str(entry.item !== undefined ? entry.item : (entry.id || entry.name), '?');
    const count = entry.count !== undefined ? entry.count : entry.amount;
    return E.truncate(name, 28) + (count && count !== 1 ? ' ×' + count : '');
  };

  // ── Toasts ────────────────────────────────────────────────────────────
  E.toast = function (message, kind) {
    const box = document.getElementById('toasts');
    if (!box) return;
    const el = E.h('div', { class: 'toast ' + (kind || 'info') }, message);
    box.appendChild(el);
    setTimeout(() => { el.classList.add('out'); setTimeout(() => el.remove(), 400); }, kind === 'error' ? 6000 : 3000);
  };
})();
