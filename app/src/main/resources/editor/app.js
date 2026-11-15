/* Application : menu des quêtes / NPC, brouillons, annuler / refaire, enregistrement et suivi de la validation en jeu. */
(function () {
  'use strict';
  const E = window.E;
  const h = E.h, F = E.F;
  const DRAFT_KEY = 'skwhy-quest-editor-drafts';

  const app = E.app = {
    status: null, catalog: { worlds: [] }, quests: new Map(), npcs: new Map(),
    folds: new Map(),      // "quest:id" -> {min, hide} cartes réduites / branches masquées
    ents: new Map(),       // "quest:id" -> entité ouverte (brouillon)
    ent: null, sel: null,
    undo: [], redo: [], lastKey: null, lastTime: 0,
    history: [], timeOffset: 0, locked: false
  };

  const $ = id => document.getElementById(id);
  const keyOf = (kind, id) => kind + ':' + id;
  const PREF_KEY = 'skwhy-quest-editor-prefs';
  function pref(name, def) {
    try { const all = JSON.parse(localStorage.getItem(PREF_KEY) || '{}'); return all[name] === undefined ? def : all[name]; } catch (e) { return def; }
  }
  function setPref(name, value) {
    try { const all = JSON.parse(localStorage.getItem(PREF_KEY) || '{}'); all[name] = value; localStorage.setItem(PREF_KEY, JSON.stringify(all)); } catch (e) { /* stockage indisponible */ }
  }

  // ── Contexte pour les cartes ───────────────────────────────────────────
  app.ctx = {
    questName: id => app.quests.has(id) ? app.quests.get(id).name : id,
    npcName: id => app.npcs.has(id) ? app.npcs.get(id).name : id,
    exists: (kind, id) => (kind === 'quest' ? app.quests : app.npcs).has(id),
    open: (kind, id) => app.open(kind, id)
  };
  app.ctx.opts = { merge: pref('merge', false) === true };
  app.catalogQuests = () => [...app.quests.values()];
  app.catalogNpcs = () => [...app.npcs.values()];

  // ── Démarrage ──────────────────────────────────────────────────────────
  async function init() {
    app.canvas = new E.Canvas($('viewport'), {
      onSelect: (sel, focus) => { app.sel = sel; if (sel) document.body.classList.remove('insp-closed'); app.inspector.render(sel, focus); },
      onOpen: id => openPopup(id),
      onFold: () => app.minimap && app.minimap.draw(),
      onView: v => { $('zoomlabel').textContent = Math.round(v.k * 100) + ' %'; if (app.minimap) app.minimap.draw(); }
    });
    app.minimap = new E.Minimap($('minimap'), app.canvas);
    app.inspector = new E.Inspector($('inspector'), app);
    bindUi();
    try {
      app.status = await E.api('status');
      app.timeOffset = app.status.now - Date.now();
      await loadCatalog();
    } catch (e) { return fatal(e); }
    renderMenu();
    if (app.status.state !== 'open') { showWait(); }
    startClock();
    if (app.status.kind) {
      await app.open(app.status.kind, app.status.id, { first: true });
      const m = /[#&]dialogue=([^&]*)/.exec(location.hash || '');
      if (m && app.ent) {
        let did = m[1];
        try { did = decodeURIComponent(did); } catch (e) { /* tel quel */ }
        const node = E.Ops.script(app.ent.tree)[did];
        if (node && node.action === 'dialogue') openPopup(did);
        else E.toast('Dialogue « ' + did + ' » introuvable dans ' + app.ent.id + '.', 'error');
      }
    } else showMenuOnly();
  }

  /** Lien sans cible : on n'ouvre rien, le menu propose la liste des quêtes et des NPC. */
  function showMenuOnly() {
    renderHeader();
    $('emptygraph').textContent = 'Choisissez une quête ou un NPC dans le menu de gauche.';
    $('emptygraph').classList.remove('hidden');
    app.inspector.render(null);
  }

  async function loadCatalog() {
    const data = await E.api('catalog');
    app.catalog = data;
    app.quests.clear(); app.npcs.clear();
    for (const w of data.worlds) {
      for (const q of w.quests) app.quests.set(q.id, Object.assign({ world: w.name }, q));
      for (const n of w.npcs) app.npcs.set(n.id, Object.assign({ world: w.name }, n));
    }
    const dl = $('worlds-list');
    E.clear(dl);
    data.worlds.forEach(w => { if (w.name) dl.appendChild(h('option', { value: w.name })); });
    E.pathRules = Array.isArray(data.pathRules) ? data.pathRules.slice() : [];
    E.items.clear();
    const il = $('items-list');
    E.clear(il);
    for (const it of data.items || []) {
      E.items.set(it.id, it);
      il.appendChild(h('option', { value: it.id }, E.plain(it.name) || it.material));
    }
  }

  function fatal(e) {
    const msg = e && e.closed ? 'Le lien est fermé ou a expiré. Demandez un nouveau lien en jeu.' : (e && e.message) || 'Erreur';
    overlay('🔒', 'Lien indisponible', msg);
  }

  function overlay(icon, title, text, extra) {
    const o = $('overlay');
    E.clear(o);
    o.appendChild(h('div', { class: 'ocard' }, h('div', { class: 'oicon' }, icon), h('h2', null, title), h('p', null, text), extra || null));
    o.classList.remove('hidden');
  }
  function closeOverlay() { $('overlay').classList.add('hidden'); }

  // ── Ouvrir une quête ou un NPC ─────────────────────────────────────────
  app.open = async function (kind, id, opts) {
    opts = opts || {};
    if (app.pending) { app.canvas.stopPick(); hidePick(); }
    const k = keyOf(kind, id);
    let ent = app.ents.get(k);
    if (!ent) {
      setLoading(true, id);
      try {
        // seule la définition demandée est chargée ; une fois chargée elle reste en mémoire jusqu'à la fermeture de la page
        const data = await E.api(kind + '/' + encodeURIComponent(id));
        ent = { kind, id, base: data.version, orig: JSON.stringify(data.raw), tree: E.clone(data.raw), unlockers: [], drives: data.drives || [], dirty: false };
        const draft = loadDraft(kind, id);
        if (draft && draft.base === ent.base) { ent.tree = draft.tree; ent.dirty = JSON.stringify(ent.tree) !== ent.orig; E.toast('Brouillon restauré pour ' + id, 'info'); }
        else if (draft) { E.toast('Le fichier a changé depuis votre brouillon de ' + id + ' : brouillon ignoré.', 'error'); dropDraft(kind, id); }
        app.ents.set(k, ent);
      } catch (e) {
        setLoading(false);
        if (e.closed) return fatal(e);
        return E.toast('Impossible d\'ouvrir ' + id + ' : ' + e.message, 'error');
      }
      setLoading(false);
      if (kind === 'quest') loadUnlockers(ent);
    }
    if (app.ent && !opts.back && !opts.first && (app.ent.kind !== kind || app.ent.id !== id)) app.history.push({ kind: app.ent.kind, id: app.ent.id });
    closePopup();
    app.ent = ent;
    app.sel = null;
    app.canvas.touched = false;
    if (!app.folds.has(keyOf(kind, id))) app.folds.set(keyOf(kind, id), { min: new Set(), hide: new Set() });
    app.canvas.fold = app.folds.get(keyOf(kind, id));
    $('emptygraph').classList.add('hidden');
    renderHeader();
    renderGraph(false);
    app.inspector.render(null);
    renderMenu();
    $('back').disabled = app.history.length === 0;
  };

  /** Indicateur de chargement (la première ouverture d'une quête ou d'un NPC demande le serveur). */
  function setLoading(on, id) {
    $('loading').classList.toggle('hidden', !on);
    if (on) $('loadingtext').textContent = 'Chargement de ' + id + '…';
  }

  /** Les NPC qui font avancer la quête sont analysés par le serveur à part : la quête s'affiche d'abord, ils arrivent ensuite. */
  async function loadUnlockers(ent) {
    try {
      const res = await E.api('unlockers/' + encodeURIComponent(ent.id));
      ent.unlockers = res.unlockers || [];
    } catch (e) { return; }
    if (app.ent === ent) {
      renderGraph(app.canvas.touched);
      if (!app.sel) app.inspector.render(null);
    }
  }

  function loadDraft(kind, id) {
    try { const all = JSON.parse(localStorage.getItem(DRAFT_KEY) || '{}'); return all[keyOf(kind, id)] || null; } catch (e) { return null; }
  }
  function saveDrafts() {
    try {
      const all = JSON.parse(localStorage.getItem(DRAFT_KEY) || '{}');
      for (const [k, ent] of app.ents) {
        if (ent.dirty) all[k] = { base: ent.base, tree: ent.tree, at: Date.now() };
        else delete all[k];
      }
      localStorage.setItem(DRAFT_KEY, JSON.stringify(all));
    } catch (e) { /* stockage indisponible : les brouillons ne survivent pas à un rechargement */ }
  }
  function dropDraft(kind, id) {
    try { const all = JSON.parse(localStorage.getItem(DRAFT_KEY) || '{}'); delete all[keyOf(kind, id)]; localStorage.setItem(DRAFT_KEY, JSON.stringify(all)); } catch (e) { /* idem */ }
  }
  const saveDraftsLater = E.debounce(saveDrafts, 600);

  // ── Graphe ─────────────────────────────────────────────────────────────
  function renderGraph(keepView) {
    if (!app.ent) return;
    const graph = app.ent.kind === 'quest' ? E.buildQuestGraph(app.ent, app.ctx) : E.buildNpcGraph(app.ent, app.ctx);
    app.canvas.setGraph(graph, { keepView });
    app.minimap.draw();
    app.canvas.selected = app.sel;
    app.canvas.refreshSelection();
    $('emptygraph').textContent = 'Rien à afficher ici. Utilisez « Ajouter » pour créer une étape ou une action.';
    $('emptygraph').classList.toggle('hidden', graph.nodes.length > 0);
    refreshPopup(false);
  }
  const renderGraphLater = E.debounce(() => renderGraph(true), 160);

  function renderHeader() {
    const ent = app.ent;
    const name = ent ? (ent.tree.name || ent.id) : '';
    const t = $('title');
    E.clear(t);
    if (!ent) t.appendChild(h('span', null, 'Éditeur de quêtes et de NPC'));
    if (ent) {
      t.appendChild(h('span', { class: 'kind ' + ent.kind }, ent.kind === 'quest' ? 'Quête' : 'NPC'));
      t.appendChild(E.mc(name, '#e8ecf4'));
      t.appendChild(h('span', { class: 'tid' }, ent.id));
    }
    for (const id of ['indgen', 'indclean']) $(id).classList.toggle('hidden', !(ent && ent.kind === 'npc'));
    const dirty = dirtyEnts();
    const btn = $('save');
    btn.disabled = app.locked || dirty.length === 0;
    $('savecount').textContent = dirty.length ? String(dirty.length) : '';
    $('undo').disabled = app.undo.length === 0 || app.locked;
    $('redo').disabled = app.redo.length === 0 || app.locked;
  }

  function dirtyEnts() { return [...app.ents.values()].filter(e => e.dirty); }

  // ── Modifications ──────────────────────────────────────────────────────
  /**
   * Applique une modification à la définition ouverte.
   * @param opts.key  regroupe les frappes successives dans une seule étape « annuler »
   * @param opts.structural  redessine aussi l'inspecteur
   */
  app.mutate = function (fn, opts) {
    if (app.locked || !app.ent) return;
    opts = opts || {};
    const ent = app.ent;
    const now = Date.now();
    const coalesce = opts.key && opts.key === app.lastKey && now - app.lastTime < 1500 && app.undo.length && app.undo[app.undo.length - 1].k === keyOf(ent.kind, ent.id);
    if (!coalesce) {
      app.undo.push({ k: keyOf(ent.kind, ent.id), json: JSON.stringify(ent.tree) });
      if (app.undo.length > 80) app.undo.shift();
      app.redo = [];
    }
    app.lastKey = opts.key || null;
    app.lastTime = now;
    fn(ent.tree);
    afterChange(opts.structural);
  };

  app.touch = function () { afterChange(false); };

  function afterChange(structural) {
    const ent = app.ent;
    ent.dirty = JSON.stringify(ent.tree) !== ent.orig;
    renderHeader();
    renderMenuMarks();
    saveDraftsLater();
    renderGraphLater();
    if (structural) {
      renderGraphLater.flush();
      app.rerenderInspector();
    }
  }

  app.rerenderInspector = function () { app.inspector.render(app.sel); };

  app.select = function (sel, center) {
    app.sel = sel;
    if (sel) document.body.classList.remove('insp-closed');
    app.canvas.select(sel, false);
    app.inspector.render(sel);
    if (center && sel) {
      renderGraphLater.flush();
      const id = app.canvas.nodeIdOf(sel);
      if (id) app.canvas.centerOn(id);
    }
  };

  async function applyUndo(from, to) {
    if (!from.length || app.locked) return;
    const snap = from.pop();
    const [kind, ...rest] = snap.k.split(':');
    const id = rest.join(':');
    if (!app.ent || app.ent.kind !== kind || app.ent.id !== id) await app.open(kind, id, { back: true });
    const ent = app.ent;
    to.push({ k: snap.k, json: JSON.stringify(ent.tree) });
    ent.tree = JSON.parse(snap.json);
    ent.dirty = JSON.stringify(ent.tree) !== ent.orig;
    app.lastKey = null;
    renderHeader(); renderMenuMarks(); saveDraftsLater();
    if (app.sel && !app.canvas.nodeIdOf(app.sel)) app.sel = null;
    renderGraph(true);
    app.inspector.render(app.sel);
  }

  // ── Choisir une cible sur le graphe ────────────────────────────────────
  app.pick = function (filter, cb, hint) {
    app.pending = true;
    $('pickbar').classList.remove('hidden');
    $('picktext').textContent = hint || 'Cliquez sur une carte du graphe';
    app.canvas.startPick(filter, (sel, spec) => { app.pending = false; hidePick(); cb(sel, spec); });
  };
  function hidePick() { $('pickbar').classList.add('hidden'); app.pending = false; }

  // ── Menu latéral ───────────────────────────────────────────────────────
  const openWorlds = new Set();
  function renderMenu() {
    renderList('quests', 'quest', $('q-search').value);
    renderList('npcs', 'npc', $('n-search').value);
  }
  function renderMenuMarks() {
    document.querySelectorAll('.entry').forEach(el => {
      const e = app.ents.get(el.getAttribute('data-k'));
      el.classList.toggle('dirty', !!(e && e.dirty));
    });
  }

  function renderList(listId, kind, query) {
    const box = $('list-' + listId);
    E.clear(box);
    const q = (query || '').trim().toLowerCase();
    let total = 0;
    for (const w of app.catalog.worlds) {
      const items = (kind === 'quest' ? w.quests : w.npcs).filter(it => !q || (E.plain(it.name) + ' ' + it.id).toLowerCase().includes(q));
      if (!items.length) continue;
      total += items.length;
      const wkey = kind + '|' + w.name;
      const det = h('details', { class: 'wgroup' });
      const hasCurrent = app.ent && app.ent.kind === kind && items.some(it => it.id === app.ent.id);
      det.open = !!q || openWorlds.has(wkey) || hasCurrent;
      det.addEventListener('toggle', () => { if (det.open) openWorlds.add(wkey); else openWorlds.delete(wkey); });
      det.appendChild(h('summary', null, h('span', { class: 'wname' }, w.name || 'Sans position'), h('span', { class: 'count' }, String(items.length))));
      for (const it of items) {
        const k = keyOf(kind, it.id);
        const e = app.ents.get(k);
        const cur = app.ent && app.ent.kind === kind && app.ent.id === it.id;
        det.appendChild(h('div', { class: 'entry' + (cur ? ' current' : '') + (e && e.dirty ? ' dirty' : ''), 'data-k': k, title: it.id, onclick: () => app.open(kind, it.id) },
          h('span', { class: 'ename' }, E.mc(it.name || it.id)), h('span', { class: 'eid' }, it.id),
          kind === 'quest' ? h('span', { class: 'etype' }, it.type) : null, h('span', { class: 'mark', title: 'Modifié' }, '●')));
      }
      box.appendChild(det);
    }
    if (!total) box.appendChild(h('div', { class: 'none' }, q ? 'Aucun résultat.' : 'Aucun élément.'));
    const cnt = $('count-' + listId);
    if (cnt) cnt.textContent = String(kind === 'quest' ? app.quests.size : app.npcs.size);
  }

  // ── Barre d'outils ─────────────────────────────────────────────────────
  function bindUi() {
    $('q-search').addEventListener('input', E.debounce(() => renderList('quests', 'quest', $('q-search').value), 120));
    $('n-search').addEventListener('input', E.debounce(() => renderList('npcs', 'npc', $('n-search').value), 120));
    $('tab-side').addEventListener('click', () => document.body.classList.toggle('menu-closed'));
    $('menu').addEventListener('click', () => document.body.classList.toggle('menu-closed'));
    $('back').addEventListener('click', () => { const p = app.history.pop(); if (p) app.open(p.kind, p.id, { back: true }); });
    $('undo').addEventListener('click', () => applyUndo(app.undo, app.redo));
    $('redo').addEventListener('click', () => applyUndo(app.redo, app.undo));
    $('foldall').addEventListener('click', () => app.canvas.foldAll(true));
    $('unfoldall').addEventListener('click', () => app.canvas.foldAll(false));
    $('fit').addEventListener('click', () => app.canvas.fit({ all: true }));
    $('zin').addEventListener('click', () => app.canvas.zoomBy(1.25));
    $('zout').addEventListener('click', () => app.canvas.zoomBy(0.8));
    $('props').addEventListener('click', () => { app.sel = null; app.canvas.select(null, false); app.inspector.render(null); });
    $('add').addEventListener('click', () => {
      if (!app.ent) return;
      if (app.ent.kind === 'quest') {
        const dlg = E.modal('Ajouter', h('div', { class: 'addmenu' },
          F.btn('📋 Une étape', () => { dlg.close(); app.mutate(t => { E.Ops.insertStep(t, (t.steps || []).length); }, { structural: true }); app.select({ t: 'step', i: app.ent.tree.steps.length - 1 }, true); }, 'big'),
          F.btn('⚙ Une action de script', () => { dlg.close(); E.addNodeMenu(app); }, 'big')));
      } else E.addNodeMenu(app);
    });
    $('save').addEventListener('click', openSave);
    $('pickcancel').addEventListener('click', () => { app.canvas.stopPick(); hidePick(); });
    $('closeinsp').addEventListener('click', () => document.body.classList.toggle('insp-closed'));
    $('mergedlg').classList.toggle('primary', app.ctx.opts.merge);
    $('mergedlg').addEventListener('click', () => {
      app.ctx.opts.merge = !app.ctx.opts.merge;
      setPref('merge', app.ctx.opts.merge);
      $('mergedlg').classList.toggle('primary', app.ctx.opts.merge);
      if (app.sel && app.sel.t === 'state' && app.ctx.opts.merge) app.sel = { t: 'node', id: app.sel.a };
      renderGraph(false);
      app.inspector.render(app.sel);
    });
    $('indgen').addEventListener('click', generateIndicators);
    $('indclean').addEventListener('click', cleanIndicators);
    bindResizers();
    document.addEventListener('keydown', ev => {
      const typing = /^(input|textarea|select)$/i.test((ev.target.tagName || ''));
      if (ev.key === 'Escape') { if (app.popup && !app.pending && !document.querySelector('.modal-back')) closePopup(); else if (app.pending) { app.canvas.stopPick(); hidePick(); } else { const m = document.querySelector('.modal-back'); if (m) m.remove(); else app.canvas.select(null, true); } }
      if ((ev.ctrlKey || ev.metaKey) && ev.key.toLowerCase() === 'z' && !typing) { ev.preventDefault(); applyUndo(app.undo, app.redo); }
      if ((ev.ctrlKey || ev.metaKey) && (ev.key.toLowerCase() === 'y' || (ev.shiftKey && ev.key.toLowerCase() === 'z')) && !typing) { ev.preventDefault(); applyUndo(app.redo, app.undo); }
      if ((ev.ctrlKey || ev.metaKey) && ev.key.toLowerCase() === 's') { ev.preventDefault(); if (!$('save').disabled) openSave(); }
    });
    window.addEventListener('beforeunload', ev => { if (dirtyEnts().length && !app.locked) { saveDrafts(); ev.preventDefault(); ev.returnValue = ''; } });
  }

  // ── Largeur des colonnes latérales ─────────────────────────────────────
  function bindResizers() {
    const root = document.documentElement;
    const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
    const set = (name, px) => root.style.setProperty(name, px + 'px');
    set('--side-w', clamp(pref('sidew', 290), 160, 700));
    set('--insp-w', clamp(pref('inspw', 410), 280, 1000));
    const bind = (id, name, key, calc, min, max) => {
      const el = $(id);
      let on = false;
      el.addEventListener('pointerdown', ev => { on = true; try { el.setPointerCapture(ev.pointerId); } catch (e) { /* événement synthétique */ } el.classList.add('drag'); document.body.classList.add('resizing'); ev.preventDefault(); });
      el.addEventListener('pointermove', ev => { if (on) set(name, clamp(calc(ev.clientX), min, max)); });
      const end = () => {
        if (!on) return;
        on = false; el.classList.remove('drag'); document.body.classList.remove('resizing');
        setPref(key, parseInt(getComputedStyle(root).getPropertyValue(name), 10));
        if (app.minimap) app.minimap.draw();
      };
      el.addEventListener('pointerup', end);
      el.addEventListener('pointercancel', end);
    };
    bind('rz-side', '--side-w', 'sidew', x => x, 160, 700);
    bind('rz-insp', '--insp-w', 'inspw', x => window.innerWidth - x, 280, 1000);
  }

  // ── Fenêtre d'un dialogue ──────────────────────────────────────────────
  function openPopup(id) {
    if (!app.ent) return;
    closePopup();
    const view = h('div', { class: 'bpview' });
    const box = h('div', { class: 'bigpop' },
      h('div', { class: 'bphead' }, h('span', null, '💬 Dialogue'), h('span', { class: 'tid' }, id),
        h('span', { class: 'bphint' }, 'à gauche : actions qui mènent ici · à droite : actions où il débouche · molette : zoom'),
        h('span', { class: 'sp' }), F.btn('🔗 Lien direct', () => app.dialogueLinkDialog(id), 'mini'), F.btn('Fermer (Échap)', closePopup, 'mini')),
      view);
    document.body.appendChild(box);
    const canvas = new E.Canvas(view, {
      onSelect: (sel, focus) => { app.sel = sel; app.canvas.select(sel, false); document.body.classList.remove('insp-closed'); app.inspector.render(sel, focus); }
    });
    app.popup = { id, box, canvas };
    refreshPopup(true);
  }

  function refreshPopup(first) {
    const p = app.popup;
    if (!p) return;
    const graph = app.ent ? E.buildDialogueGraph(app.ent, p.id, app.ctx) : { nodes: [] };
    if (!graph.nodes.length) return closePopup();
    p.canvas.setGraph(graph, { keepView: !first });
    p.canvas.selected = app.sel;
    p.canvas.refreshSelection();
  }

  app.openDialogue = id => openPopup(id);

  /** Adresse qui ouvre directement la fenêtre d'un dialogue (tant que le lien de la page est valable) et syntaxe Skript qui en génère une nouvelle. */
  app.dialogueLinkDialog = function (did) {
    if (!app.ent) return;
    const url = location.origin + location.pathname + '#dialogue=' + encodeURIComponent(did);
    const kind = app.ent.kind === 'npc' ? 'quest npc' : 'quest';
    const skript = 'send ' + kind + ' editor link of ' + kind + ' "' + app.ent.id + '" at dialogue "' + did + '" to player';
    const field = (label, text) => {
      const input = h('input', { type: 'text', readonly: 'readonly', value: text });
      input.addEventListener('focus', () => input.select());
      return F.field(label, h('div', { class: 'picker' }, input, F.btn('Copier', async () => {
        try { await navigator.clipboard.writeText(text); E.toast('Copié.', 'info'); } catch (e) { input.select(); E.toast('Sélectionné : copiez avec Ctrl+C.', 'info'); }
      }, 'mini')));
    };
    const dlg = E.modal('Lien direct vers ce dialogue', h('div', null,
      field('Adresse (ouvre la fenêtre du dialogue ; valable tant que le lien de l\'éditeur l\'est)', url),
      field('Syntaxe Skript (envoie un nouveau lien qui s\'ouvre sur ce dialogue)', skript)), []);
    dlg.foot.appendChild(F.btn('Fermer', () => dlg.close(), 'primary'));
  };

  function closePopup() {
    if (!app.popup) return;
    app.popup.box.remove();
    app.popup = null;
  }

  // ── Indicateurs de quête ───────────────────────────────────────────────
  // « ? » rouge pour une nouvelle quête, « ! » du type de la quête pour une progression (voir Ops.INDICATOR_DEFAULTS).
  const questTypeOf = qid => (app.quests.get(qid) || {}).type;

  function generateIndicators() {
    if (!app.ent || app.ent.kind !== 'npc' || app.locked) return;
    const probe = E.Ops.generateIndicators(E.clone(app.ent.tree), questTypeOf);
    if (!probe.added) { E.toast(probe.kept ? 'Rien à ajouter : ' + probe.kept + ' action(s) ont déjà un indicateur.' : 'Aucune action de ce NPC ne mène à une évolution de quête.', 'info'); return; }
    app.mutate(t => E.Ops.generateIndicators(t, questTypeOf), { structural: true });
    E.toast(probe.added + ' indicateur(s) ajouté(s)' + (probe.kept ? ', ' + probe.kept + ' déjà présent(s) conservé(s)' : '') + '.', 'info');
  }

  function cleanIndicators() {
    if (!app.ent || app.ent.kind !== 'npc' || app.locked) return;
    const n = E.Ops.clearIndicators(E.clone(app.ent.tree));
    if (!n) return E.toast('Aucun indicateur à retirer.', 'info');
    if (!confirm('Retirer les ' + n + ' indicateur(s) de ce NPC ?')) return;
    app.mutate(t => E.Ops.clearIndicators(t), { structural: true });
    E.toast(n + ' indicateur(s) retiré(s).', 'info');
  }

  // ── Enregistrement ─────────────────────────────────────────────────────
  function openSave() {
    const dirty = dirtyEnts();
    if (!dirty.length || app.locked) return;
    const list = h('div', { class: 'savelist' }, dirty.map(e => h('div', { class: 'srow' },
      h('span', { class: 'kind ' + e.kind }, e.kind === 'quest' ? 'Quête' : 'NPC'), h('b', null, E.mc(e.tree.name || e.id)), h('span', { class: 'tid' }, e.id),
      F.btn('Voir', () => { dlg.close(); app.open(e.kind, e.id); }, 'mini'))));
    const dlg = E.modal('Envoyer les modifications', h('div', null,
      h('p', null, dirty.length + ' élément' + (dirty.length > 1 ? 's' : '') + ' modifié' + (dirty.length > 1 ? 's' : '') + '. Après l\'envoi, ',
        h('b', null, app.status.player), ' devra les valider ou les annuler dans le jeu (un récapitulatif s\'affiche en survolant le message).'),
      list, h('p', { class: 'fhint' }, 'Le lien devient en lecture seule après l\'envoi.')), []);
    const send = F.btn('Envoyer', async () => {
      send.disabled = true;
      await submit(dirty, false, dlg);
    }, 'primary');
    dlg.foot.appendChild(F.btn('Annuler', () => dlg.close()));
    dlg.foot.appendChild(send);
  }

  async function submit(dirty, force, dlg) {
    try {
      const res = await E.api('submit', { force, entries: dirty.map(e => ({ kind: e.kind, id: e.id, base: e.base, tree: e.tree })) });
      if (dlg) dlg.close();
      app.submitted = dirty.map(e => keyOf(e.kind, e.id));
      app.locked = true;
      app.status.state = 'submitted';
      renderHeader();
      showWait(res);
    } catch (e) {
      if (e.closed) { if (dlg) dlg.close(); return fatal(e); }
      if (e.status === 409 && e.data && e.data.conflicts) { if (dlg) dlg.close(); return conflictDialog(dirty, e.data.conflicts); }
      E.toast(e.message, 'error');
      if (dlg) dlg.close();
    }
  }

  function conflictDialog(dirty, conflicts) {
    const names = conflicts.map(c => c.kind + ' ' + c.id);
    const dlg = E.modal('Conflit de version', h('div', null,
      h('p', null, 'Ces éléments ont été modifiés (par un autre éditeur ou en jeu) depuis que vous les avez ouverts :'),
      h('ul', null, names.map(n => h('li', null, n))),
      h('p', null, h('b', null, 'Écraser'), ' remplace la version actuelle par la vôtre. ', h('b', null, 'Recharger'), ' abandonne vos changements sur ces éléments et reprend la version actuelle.')), []);
    dlg.foot.appendChild(F.btn('Fermer', () => dlg.close()));
    dlg.foot.appendChild(F.btn('Recharger ces éléments', async () => {
      dlg.close();
      for (const c of conflicts) { app.ents.delete(keyOf(c.kind, c.id)); dropDraft(c.kind, c.id); }
      const cur = app.ent;
      app.ent = null;
      await app.open(cur.kind, cur.id, { first: true });
      E.toast('Éléments rechargés.', 'info');
    }));
    dlg.foot.appendChild(F.btn('Écraser', async () => { dlg.close(); await submit(dirty, true, null); }, 'danger'));
  }

  // ── Attente de la réponse du joueur ────────────────────────────────────
  let pollTimer = null;
  function showWait() {
    app.locked = true;
    renderHeader();
    clearInterval(pollTimer);
    pollTimer = setInterval(poll, 1800);
    poll();
  }

  async function poll() {
    let st;
    try { st = await E.api('status'); }
    catch (e) {
      clearInterval(pollTimer);
      if (app.status.state === 'applied' || app.status.state === 'cancelled') return overlay('🔒', 'Lien fermé', 'Le lien de l\'éditeur a été fermé. Demandez-en un nouveau en jeu si besoin.');
      return fatal(e);
    }
    app.status = st;
    app.timeOffset = st.now - Date.now();
    if (st.renewedKey) {
      clearInterval(pollTimer);
      finishSubmitted(st.state);
      overlay('↻', 'Nouveau lien', 'Le lien a été renouvelé en jeu. Ouverture du nouvel éditeur…');
      setTimeout(() => { location.href = location.pathname.replace(/[^/]+\/$/, st.renewedKey + '/') + location.hash; }, 900);
      return;
    }
    const player = st.player;
    if (st.state === 'submitted') {
      overlay('⏳', 'En attente de validation en jeu', player + (st.online ? ' doit valider ou annuler les modifications dans le chat du jeu.' : ' est hors ligne : les modifications lui seront proposées à sa prochaine connexion.'),
        summaryBox(st.entries));
    } else if (st.state === 'applied') {
      finishSubmitted('applied');
      overlay('✅', 'Modifications appliquées', 'Les fichiers ont été enregistrés et rechargés. Si ' + player + ' choisit « Renouveler » dans le jeu, cette page s\'ouvrira avec un nouveau lien (valable encore ' + remaining() + ').', summaryBox(st.entries));
    } else if (st.state === 'cancelled') {
      finishSubmitted('cancelled');
      overlay('✖', 'Modifications annulées', player + ' a annulé : rien n\'a été écrit. Vos changements restent enregistrés dans ce navigateur. Si ' + player + ' renouvelle le lien en jeu (encore ' + remaining() + '), vous pourrez les renvoyer.', summaryBox(st.entries));
    } else {
      clearInterval(pollTimer); app.locked = false; closeOverlay(); renderHeader();
    }
  }

  function finishSubmitted(state) {
    if (state === 'applied' && app.submitted) {
      for (const k of app.submitted) { const e = app.ents.get(k); if (e) { dropDraft(e.kind, e.id); e.dirty = false; e.orig = JSON.stringify(e.tree); } }
      app.submitted = null;
    }
  }

  function summaryBox(entries) {
    if (!entries || !entries.length) return null;
    return h('div', { class: 'savelist' }, entries.map(e => h('div', { class: 'srow' },
      h('span', { class: 'kind ' + e.kind }, e.kind === 'quest' ? 'Quête' : 'NPC'), h('b', null, e.id),
      h('span', { class: 'stat add' }, '+' + e.added), h('span', { class: 'stat del' }, '−' + e.removed), h('span', { class: 'stat mod' }, '~' + e.modified),
      e.sections && e.sections.length ? h('span', { class: 'dim small' }, e.sections.join(', ')) : null)));
  }

  function remaining() {
    const ms = Math.max(0, app.status.expiresAt - (Date.now() + app.timeOffset));
    return Math.max(1, Math.round(ms / 60000)) + ' min';
  }

  // ── Compte à rebours de la clef ────────────────────────────────────────
  function startClock() {
    const tick = () => {
      if (!app.status) return;
      const ms = app.status.expiresAt - (Date.now() + app.timeOffset);
      const el = $('clock');
      if (ms <= 0) { el.textContent = 'expiré'; el.className = 'clock bad'; if ($('overlay').classList.contains('hidden')) fatal({ closed: true }); return; }
      const m = Math.floor(ms / 60000), s = Math.floor(ms / 1000) % 60;
      el.textContent = (m >= 60 ? Math.floor(m / 60) + ' h ' + String(m % 60).padStart(2, '0') : m + ':' + String(s).padStart(2, '0'));
      el.className = 'clock' + (ms < 10 * 60000 ? ' warn' : '');
      el.title = 'Le lien expire dans ' + Math.ceil(ms / 60000) + ' min';
      if (ms < 10 * 60000 && !app.warned && app.status.state === 'open') { app.warned = true; E.toast('Le lien expire dans moins de 10 minutes : envoyez vos modifications.', 'error'); }
    };
    tick();
    setInterval(tick, 1000);
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
})();
