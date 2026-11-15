/* Inspecteur : formulaires de la carte sélectionnée (étape, action, état de dialogue) et des propriétés de la quête / du NPC. */
(function () {
  'use strict';
  const E = window.E;
  const h = E.h, F = E.F, Ops = E.Ops;

  const QUEST_TYPES = [{ v: 'principale', l: 'Principale' }, { v: 'secondaire', l: 'Secondaire' }, { v: 'contrée', l: 'Contrée' }];
  const NPC_TYPES = [{ v: '', l: '(aucun)' }, { v: 'discour', l: 'Discours' }, { v: 'marchand', l: 'Marchand' }, { v: 'scripté', l: 'Scripté' }];
  const OBJ_TYPES = [{ v: 'externe', l: 'Externe (avancé par un script)' }, { v: 'action', l: 'Action (casser, tuer...)' }, { v: 'item', l: 'Objet dans l\'inventaire' }, { v: 'special', l: 'Spécial (métier, quête...)' }];
  const SPECIALS = ['jobs', 'quest', 'variable', 'advancement', 'rank'];
  const OPS = [{ v: 'equal', l: '=' }, { v: 'superior_strict', l: '>' }, { v: 'superior', l: '≥' }, { v: 'inferior_strict', l: '<' }, { v: 'inferior', l: '≤' }];
  const SET_OPS = [{ v: 'set', l: 'définir' }, { v: 'add', l: 'ajouter' }, { v: 'remove', l: 'retirer' }];
  const OPERAND_TYPES = [{ v: 'value', l: 'Valeur fixe' }, { v: 'quest', l: 'Progression d\'une quête' }, { v: 'variable', l: 'Variable Skript' }, { v: 'level', l: 'Niveau du joueur' },
    { v: 'rank', l: 'Rang du joueur' }, { v: 'item', l: 'Nombre d\'objets' }, { v: 'player_nbt', l: 'Donnée du joueur (NBT)' }, { v: 'player_metadata', l: 'Métadonnée du joueur' },
    { v: 'npc_nbt', l: 'Donnée du NPC' }, { v: 'position', l: 'Position du NPC' }, { v: 'now', l: 'Heure actuelle' }];

  E.Inspector = function (container, app) {
    this.container = container;
    this.app = app;
  };
  const P = E.Inspector.prototype;

  P.render = function (sel, focus) {
    const c = E.clear(this.container);
    const app = this.app;
    if (!app.ent) { c.appendChild(h('div', { class: 'empty' }, 'Choisissez une quête ou un NPC dans le menu.')); return; }
    const scrollTop = this.container.scrollTop;
    let node;
    if (!sel) node = app.ent.kind === 'quest' ? questProps(app) : npcProps(app);
    else if (sel.t === 'step') node = stepForm(app, sel.i);
    else if (sel.t === 'node') node = nodeForm(app, sel.id);
    else if (sel.t === 'state') node = stateForm(app, sel.a, sel.s, focus);
    else node = h('div', { class: 'empty' }, 'Sélection inconnue.');
    c.appendChild(node);
    this.container.scrollTop = scrollTop;
    if (focus && focus.text !== undefined) {
      const el = c.querySelector('[data-itext="' + focus.text + '"]');
      if (el) { el.scrollIntoView({ block: 'nearest' }); el.classList.add('flash'); setTimeout(() => el.classList.remove('flash'), 1200); }
    }
  };

  // ── Aides ──────────────────────────────────────────────────────────────

  function script(app) { return Ops.script(app.ent.tree); }

  function heading(icon, title, sub, actions) {
    return h('div', { class: 'ihead' }, h('span', { class: 'ico' }, icon), h('div', { class: 'itit' }, h('div', { class: 't' }, title), sub ? h('div', { class: 's' }, sub) : null), actions || null);
  }

  /** Sélecteur d'une action existante (+ bouton « choisir sur le graphe »). */
  function nodePicker(app, current, onChange, opts) {
    opts = opts || {};
    const sc = script(app);
    const options = [{ v: '', l: opts.none || '(aucune)' }];
    for (const id of Object.keys(sc)) {
      if (!Ops.isAct(sc[id]) || id === opts.exclude) continue;
      if (opts.filter && !opts.filter(sc[id], id)) continue;
      options.push({ v: id, l: E.nodeTitle(sc, id, app.ctx) });
    }
    const sel = F.select(options, current || '', v => onChange(v || undefined));
    const pick = F.btn('🎯', () => {
      app.pick(s => s.t === 'node' && s.id !== opts.exclude && (!opts.filter || opts.filter(sc[s.id], s.id)), s => onChange(s.id), 'Cliquez sur l\'action à relier');
    }, 'mini', 'Choisir sur le graphe');
    const go = current && sc[current] ? F.btn('↗', () => app.select({ t: 'node', id: current }), 'mini', 'Voir cette action') : null;
    return h('div', { class: 'picker' }, sel, pick, go);
  }

  /** Sélecteur d'état de dialogue (du dialogue donné). */
  function statePicker(app, aid, current, onChange, opts) {
    opts = opts || {};
    const node = script(app)[aid];
    const dlg = node && E.isObj(node.dialogues) ? node.dialogues : {};
    const options = [{ v: '', l: opts.none || '(aucun)' }].concat(Object.keys(dlg).map(n => ({ v: n, l: n })));
    const sel = F.select(options, current || '', v => onChange(v || undefined));
    const pick = F.btn('🎯', () => app.pick(s => s.t === 'state' && s.a === aid, s => onChange(s.s), 'Cliquez sur l\'état cible'), 'mini', 'Choisir sur le graphe');
    return h('div', { class: 'picker' }, sel, pick);
  }

  function questSelect(app, current, onChange) {
    const options = [{ v: '', l: '(choisir une quête)' }].concat(app.catalogQuests().map(q => ({ v: q.id, l: E.plain(q.name) + '  (' + q.id + ')' })));
    return F.select(options, current || '', v => onChange(v));
  }

  function npcSelect(app, current, onChange, none) {
    const options = [{ v: '', l: none || '(ce NPC)' }].concat(app.catalogNpcs().map(n => ({ v: n.id, l: E.plain(n.name) + '  (' + n.id + ')' })));
    return F.select(options, current || '', v => onChange(v || undefined));
  }

  /** Sélecteur de condition (existante), avec édition et création. */
  function conditionPicker(app, current, onChange, label) {
    const sc = script(app);
    const conds = E.conditionList(sc, app.ctx);
    const options = [{ v: '', l: 'Toujours (aucune condition)' }].concat(conds.map(c => ({ v: c.id, l: E.truncate(c.desc, 70) })));
    const sel = F.select(options, current && current !== 'true' ? current : '', v => onChange(v || undefined));
    const edit = current && sc[current] && Ops.isCond(sc[current]) ? F.btn('✎', () => openConditionDialog(app, current), 'mini', 'Modifier cette condition') : null;
    const add = F.btn('＋', () => {
      let id;
      app.mutate(t => { id = Ops.addCondition(t, app.ent.kind === 'quest' ? app.ent.id : null); }, { structural: true });
      onChange(id);
      openConditionDialog(app, id);
    }, 'mini', 'Nouvelle condition');
    return F.field(label || 'Condition', h('div', { class: 'picker' }, sel, edit, add));
  }

  function chipsOf(app, nodes) { return nodes; }

  // ── Propriétés de la quête ─────────────────────────────────────────────
  function questProps(app) {
    const t = app.ent.tree;
    const body = h('div', { class: 'iform' },
      heading('📜', E.mc(t.name || app.ent.id), 'Quête ' + app.ent.id + ' · cliquez sur une carte pour la modifier'),
      F.section('Propriétés', [
        F.field('Nom (couleurs : &a, <red>, <#ff8800>)', F.area(t.name, v => app.mutate(x => { x.name = v; }, { key: 'name' }), { rows: 1, preview: true })),
        F.field('Type', F.select(QUEST_TYPES, t.type || 'principale', v => app.mutate(x => { x.type = v; }, { structural: true }))),
        F.field('Catégorie (monde du menu)', F.text(t['catégorie'] !== undefined ? t['catégorie'] : t.category, v => app.mutate(x => { if (x.category !== undefined && x['catégorie'] === undefined) x.category = v; else x['catégorie'] = v; }, { key: 'cat' }), { list: 'worlds-list' })),
        F.field('Priorité (ordre dans le menu)', F.num(t.priority, v => app.mutate(x => { x.priority = v; }, { key: 'prio' }), { int: true, optional: true })),
        F.field('Variables de la quête (une par ligne)', F.lines(t.variables, v => app.mutate(x => { x.variables = v; }, { key: 'vars' }))),
        F.field('Icône (JSON : id, head...)', F.json(t.item, v => app.mutate(x => { if (v === undefined) delete x.item; else x.item = v; }, { key: 'item' })))
      ]),
      F.section('Ajouter', [
        F.row(F.btn('＋ Étape', () => addStep(app, t.steps ? t.steps.length : 0), 'primary'),
          F.btn('＋ Action de script', () => addNodeMenu(app))),
        h('div', { class: 'fhint' }, 'Les étapes se relient avec « Puis… » ; les actions de script (récompense `reward`, etc.) se relient depuis une liste.')
      ]),
      unlockersSection(app));
    return body;
  }

  function unlockersSection(app) {
    const us = app.ent.unlockers || [];
    if (!us.length) return F.section('NPC qui font avancer cette quête', h('div', { class: 'fhint' }, 'Aucun NPC n\'a d\'action accessible sur cette quête.'), { collapsed: true });
    const seen = new Set();
    const items = [];
    for (const u of us) {
      const k = u.npc + u.mode + u.toStep + u.substep;
      if (seen.has(k)) continue;
      seen.add(k);
      const what = u.mode === 'new' ? 'lance la quête' : u.mode === 'substep' ? 'objectif ' + (u.substep + 1) + ' de l\'étape ' + (u.toStep + 1) : 'étape ' + (u.toStep + 1);
      items.push(h('div', { class: 'lrow' }, E.chip('npc', u.npc, app.ctx), h('span', { class: 'dim' }, ' → ' + what)));
    }
    return F.section('NPC qui font avancer cette quête', items, { count: items.length });
  }

  function addNodeMenu(app) {
    const types = Object.keys(E.ACTIONS);
    const sel = h('select', null, h('option', { value: '' }, 'Type d\'action…'), types.map(t => h('option', { value: t }, E.ACTIONS[t].icon + ' ' + E.ACTIONS[t].label)));
    const dlg = modal('Nouvelle action', h('div', null, F.field('Type', sel)), [
      F.btn('Annuler', () => dlg.close()),
      F.btn('Ajouter', () => {
        if (!sel.value) return;
        let id;
        app.mutate(t => { id = Ops.addNode(t, sel.value); }, { structural: true });
        dlg.close();
        app.select({ t: 'node', id }, true);
      }, 'primary')]);
  }
  E.addNodeMenu = addNodeMenu;

  function addStep(app, at) {
    app.mutate(t => { Ops.insertStep(t, at); }, { structural: true });
    app.select({ t: 'step', i: at }, true);
  }

  // ── Propriétés d'un NPC ────────────────────────────────────────────────
  function npcProps(app) {
    const t = app.ent.tree;
    const entities = E.isObj(t.entity) ? Object.keys(t.entity).map(k => {
      const e = t.entity[k] || {}, l = e.location || {};
      return h('div', { class: 'lrow' }, h('b', null, 'Position ' + E.str(e.position, 1)), ' · ' + E.str(l.world, '?') + ' ' + [l.x, l.y, l.z].map(v => Math.round(E.num(v, 0))).join(' '),
        h('div', { class: 'dim small' }, k));
    }) : [];
    const drives = app.ent.drives || [];
    return h('div', { class: 'iform' },
      heading('🧑', E.mc(t.name || app.ent.id), 'NPC ' + app.ent.id + ' · cliquez sur une carte pour la modifier'),
      F.section('Propriétés', [
        F.field('Nom affiché', F.area(t.name, v => app.mutate(x => { if (v === '') delete x.name; else x.name = v; }, { key: 'name' }), { rows: 1, preview: true })),
        F.field('Texte au survol', F.area(t.hover, v => app.mutate(x => { if (v === '') delete x.hover; else x.hover = v; }, { key: 'hover' }), { rows: 1 })),
        F.field('Type', F.select(NPC_TYPES, t.type || '', v => app.mutate(x => { if (v === '') delete x.type; else x.type = v; }, { structural: true }))),
        F.check('Nom toujours visible', t.name_visible, v => app.mutate(x => { x.name_visible = v; }, { key: 'nv' })),
        F.field('Message de rencontre', F.area(t['rencontre'] !== undefined ? t['rencontre'] : t.meet, v => app.mutate(x => { if (x.meet !== undefined && x['rencontre'] === undefined) x.meet = v; else x['rencontre'] = v; }, { key: 'meet' }), { rows: 2, preview: true })),
        F.field('Phrases (une par ligne)', F.lines(t.text, v => app.mutate(x => { x.text = v; }, { key: 'text' }))),
        F.field('Phrases quand on l\'attaque (une par ligne)', F.lines(t.attaque, v => app.mutate(x => { x.attaque = v; }, { key: 'atk' })))
      ]),
      F.section('Échanges', [
        F.field('Échanges du NPC (trade)', F.trade(t.trade, v => app.mutate(x => { x.trade = v; }, { key: 'trade' }), v => app.mutate(x => { x.trade = v; }, { structural: true }))),
        F.field('trades (JSON, lots nommés ; mêmes entrées item/count/placeholders/parameters)', F.json(t.trades, v => app.mutate(x => { if (v === undefined) delete x.trades; else x.trades = v; }, { key: 'trades' })))
      ], { collapsed: true }),
      F.section('Positions dans le monde', entities.length ? entities : h('div', { class: 'fhint' }, 'Aucune entité liée.'), { collapsed: true, count: entities.length }),
      F.section('Ajouter', [F.row(F.btn('＋ Action de script', () => addNodeMenu(app), 'primary'))]),
      drives.length ? F.section('Quêtes que ce NPC fait avancer', drives.map(d => h('div', { class: 'lrow' }, E.chip('quest', d.quest, app.ctx),
        h('span', { class: 'dim' }, ' ' + (d.mode === 'step' ? '→ étape ' + (d.step + 1) : d.mode === 'new' ? 'lance la quête' : d.mode === 'substep' ? 'objectif ' + (d.substep + 1) + ' de l\'étape ' + (d.step + 1) : d.mode)))), { count: drives.length }) : null);
  }

  // ── Étape d'une quête ──────────────────────────────────────────────────
  function stepForm(app, i) {
    const t = app.ent.tree;
    const st = t.steps && t.steps[i];
    if (!st) return h('div', { class: 'empty' }, 'Cette étape n\'existe plus.');
    const total = t.steps.length;
    const nextOptions = t.steps.map((s, k) => ({ v: k, l: 'Étape ' + (k + 1) + ' — ' + E.truncate(E.plain(s.text), 36) })).concat([{ v: -1, l: 'Fin de la quête' }]);
    const nextValue = st.next === undefined ? (i + 1 < total ? i + 1 : -1) : (st.next >= 0 && st.next < total ? st.next : -1);
    const objectives = Array.isArray(st.objectives) ? st.objectives : [];
    return h('div', { class: 'iform' },
      heading('①', 'Étape ' + (i + 1), 'sur ' + total, h('span', { class: 'tools' },
        F.btn('↑', () => { app.mutate(x => Ops.moveStep(x, i, i - 1), { structural: true }); app.select({ t: 'step', i: i - 1 }, true); }, 'mini', 'Monter l\'étape'),
        F.btn('↓', () => { app.mutate(x => Ops.moveStep(x, i, i + 1), { structural: true }); app.select({ t: 'step', i: i + 1 }, true); }, 'mini', 'Descendre l\'étape'),
        F.btn('🗑', () => {
          if (!confirm('Supprimer l\'étape ' + (i + 1) + ' ? Les NPC qui visent des numéros d\'étape ne sont pas mis à jour.')) return;
          app.mutate(x => Ops.removeStep(x, i), { structural: true });
          app.select(null, true);
        }, 'mini danger', 'Supprimer l\'étape'))),
      F.section('Étape', [
        F.field('Texte affiché au joueur', F.area(st.text, v => app.mutate(x => { x.steps[i].text = v; }, { key: 'st-text-' + i }), { rows: 3, preview: true })),
        F.field('Étape suivante (automatique à la fin des objectifs)', F.select(nextOptions, nextValue, v => app.mutate(x => {
          const n = parseInt(v, 10);
          x.steps[i].next = n < 0 ? x.steps.length : n;
        }, { structural: true }))),
        conditionPicker(app, st.condition, v => app.mutate(x => { if (v === undefined) delete x.steps[i].condition; else x.steps[i].condition = v; }, { structural: true }), 'Condition pour valider l\'étape')
      ]),
      F.section('Objectifs', [
        objectives.map((o, oi) => objectiveForm(app, i, oi, objectives.length)),
        F.btn('＋ Objectif', () => app.mutate(x => {
          const s = x.steps[i];
          if (!Array.isArray(s.objectives)) s.objectives = [];
          s.objectives.push({ type: 'externe', count: 1, text: '' });
        }, { structural: true }), 'primary')
      ], { count: objectives.length }),
      F.section('Insérer', [F.row(F.btn('＋ Étape avant', () => addStep(app, i)), F.btn('＋ Étape après', () => addStep(app, i + 1)))], { collapsed: true }),
      stepNpcSection(app, i));
  }

  function stepNpcSection(app, i) {
    const us = (app.ent.unlockers || []).filter(u => u.toStep === i || u.mode === 'new' && i === 0);
    if (!us.length) return null;
    const seen = new Set();
    const items = [];
    for (const u of us) {
      const k = u.npc + u.mode + u.toStep + u.substep;
      if (seen.has(k)) continue;
      seen.add(k);
      items.push(h('div', { class: 'lrow' }, E.chip('npc', u.npc, app.ctx), h('span', { class: 'dim' }, u.mode === 'new' ? ' lance la quête' : u.mode === 'substep' ? ' avance l\'objectif ' + (u.substep + 1) : ' place sur cette étape')));
    }
    return F.section('Débloquée par', items, { count: items.length });
  }

  function objectiveForm(app, si, oi, count) {
    const o = app.ent.tree.steps[si].objectives[oi];
    const type = o.type === 'spécial' ? 'special' : (o.type || 'externe');
    const set = fn => app.mutate(x => fn(x.steps[si].objectives[oi]), { key: 'o-' + si + '-' + oi, structural: false });
    const setS = fn => app.mutate(x => fn(x.steps[si].objectives[oi]), { structural: true });
    const dest = Array.isArray(o.destinations) ? o.destinations : [];
    const known = ['type', 'count', 'text', 'item', 'action', 'destinations'];
    const extra = {};
    Object.keys(o).forEach(k => { if (!known.includes(k)) extra[k] = o[k]; });
    return h('div', { class: 'subcard' },
      h('div', { class: 'subhead' }, h('b', null, 'Objectif ' + (oi + 1)), F.tools(oi, count, {
        up: oi > 0 ? () => { app.mutate(x => F.move(x.steps[si].objectives, oi, -1), { structural: true }); } : null,
        down: oi < count - 1 ? () => { app.mutate(x => F.move(x.steps[si].objectives, oi, 1), { structural: true }); } : null,
        remove: () => app.mutate(x => { x.steps[si].objectives.splice(oi, 1); }, { structural: true })
      })),
      F.field('Type', F.select(OBJ_TYPES, type, v => setS(x => { x.type = v; }))),
      F.row(F.field('Quantité', F.num(o.count, v => set(x => { x.count = v; }), { int: true, min: 1 })),
        type === 'item' ? F.field('Objet (item custom ou matériau)', F.itemId(o.item, v => set(x => { x.item = v; }))) : null,
        type === 'action' ? F.field('Action (ex. break.stone)', F.text(o.action, v => set(x => { x.action = v; }))) : null,
        type === 'special' ? F.field('Vérification', F.select(SPECIALS, o.action, v => setS(x => { x.action = v; }))) : null),
      F.field('Texte de l\'objectif', F.area(o.text, v => set(x => { x.text = v; }), { rows: 2, preview: true })),
      F.section('Destinations (boussole)', [
        dest.map((d, di) => F.row(
          F.field('Monde', F.text(d.world, v => set(x => { x.destinations[di].world = v; }))),
          F.field('X', F.num(d.x, v => set(x => { x.destinations[di].x = v; }))),
          F.field('Y', F.num(d.y, v => set(x => { x.destinations[di].y = v; }))),
          F.field('Z', F.num(d.z, v => set(x => { x.destinations[di].z = v; }))),
          F.btn('✕', () => setS(x => { x.destinations.splice(di, 1); }), 'mini danger'))),
        F.btn('＋ Destination', () => setS(x => { if (!Array.isArray(x.destinations)) x.destinations = []; x.destinations.push({ type: 'objective', world: app.ent.tree['catégorie'] || '', x: 0, y: 64, z: 0 }); }))
      ], { collapsed: dest.length === 0, count: dest.length }),
      Object.keys(extra).length ? F.field('Autres champs', F.json(extra, v => setS(x => { Object.keys(extra).forEach(k => delete x[k]); if (E.isObj(v)) Object.assign(x, v); }))) : null);
  }

  // ── Action de script ───────────────────────────────────────────────────
  function nodeForm(app, id) {
    const sc = script(app);
    const n = sc[id];
    if (!n || !Ops.isAct(n)) return h('div', { class: 'empty' }, 'Cette action n\'existe plus.');
    const type = n.action || 'list';
    const info = E.actionInfo(type);
    const set = (fn, opts) => app.mutate(t => { fn(Ops.script(t)[id]); }, opts || { key: 'n-' + id });
    const setS = fn => set(fn, { structural: true });
    const refs = Ops.refsTo(app.ent.tree, id);
    const isRoot = (app.ent.kind === 'npc' && id === 'main') || (app.ent.kind === 'quest' && id === 'reward');
    const known = KNOWN[type] || [];
    const extra = {};
    Object.keys(n).forEach(k => { if (!known.includes(k) && k !== 'action' && k !== 'condition' && k !== 'indicator') extra[k] = n[k]; });
    const typeOptions = Object.keys(E.ACTIONS).map(k => ({ v: k, l: E.ACTIONS[k].icon + ' ' + E.ACTIONS[k].label }));

    const fields = [];
    switch (type) {
      case 'list': fields.push(listFields(app, id, n, set, setS)); break;
      case 'dialogue': fields.push(dialogueFields(app, id, n)); break;
      case 'quest': fields.push(questFields(app, n, set, setS)); break;
      case 'message': fields.push(F.field('Texte', F.area(n.text, v => set(x => { x.text = v; }), { rows: 3, preview: true }))); break;
      case 'title': fields.push(F.field('Titre', F.area(n.title, v => set(x => { x.title = v; }), { rows: 1, preview: true })), F.field('Sous-titre', F.area(n.subtitle, v => set(x => { x.subtitle = v; }), { rows: 1, preview: true }))); break;
      case 'give': fields.push(F.field('Objets donnés', F.itemEntries(n.items, v => set(x => { x.items = v; }), v => setS(x => { x.items = v; })))); break;
      case 'money': fields.push(F.row(F.field('Opération', F.select(SET_OPS, n.operator || 'add', v => set(x => { x.operator = v; }))), F.field('Montant', F.num(n.value, v => set(x => { x.value = v; }))))); break;
      case 'experience': fields.push(F.row(F.field('Opération', F.select(SET_OPS, n.operator || 'add', v => set(x => { x.operator = v; }))), F.field('Valeur', F.num(n.value, v => set(x => { x.value = v; })))),
        F.check('En points d\'expérience (sinon en niveaux)', n.mode, v => set(x => { x.mode = v; }))); break;
      case 'damage': fields.push(F.field('Dégâts', F.num(n.value, v => set(x => { x.value = v; }))), F.check('Dégâts du vide (ignorent l\'armure)', n.mode, v => set(x => { x.mode = v; }))); break;
      case 'trade': fields.push(F.field('Échange (clé du NPC)', F.select([{ v: '', l: '(choisir)' }].concat(Object.keys(E.isObj(app.ent.tree.trades) ? app.ent.tree.trades : {}).map(k => ({ v: k, l: k }))), n.trade, v => set(x => { x.trade = v; })))); break;
      case 'variable': fields.push(F.field('Variable Skript ({p} = UUID du joueur)', F.text(n.variable, v => set(x => { x.variable = v; }))),
        F.row(F.field('Opération', F.select(SET_OPS, n.operator || 'set', v => set(x => { x.operator = v; }))), F.field('Valeur', F.text(n.value, v => set(x => { x.value = Ops.parseValue(v); }))))); break;
      case 'function': fields.push(F.field('Fonction Skript', F.text(n.function, v => set(x => { x.function = v; })))); break;
      case 'command': fields.push(F.field('Commande ({player} = pseudo)', F.text(n.command, v => set(x => { x.command = v; }))), F.field('Exécutée par', F.select([{ v: 'console', l: 'La console' }, { v: 'player', l: 'Le joueur' }], n.as || 'console', v => set(x => { x.as = v; })))); break;
      case 'wait': fields.push(F.field('Durée (ticks, 20 = 1 s)', F.num(n.time, v => set(x => { x.time = v; }), { int: true, min: 0 }))); break;
      case 'sound': fields.push(F.field('Son', F.text(n.sound, v => set(x => { x.sound = v; }))), F.row(F.field('Volume', F.num(n.volume, v => set(x => { x.volume = v; }))), F.field('Hauteur', F.num(n.pitch, v => set(x => { x.pitch = v; }))))); break;
      case 'teleport': fields.push(F.field('Monde', F.text(n.world, v => set(x => { x.world = v; }))), F.row(...['x', 'y', 'z'].map(k => F.field(k.toUpperCase(), F.num(n[k], v => set(xx => { xx[k] = v; }))))), F.row(F.field('Yaw', F.num(n.yaw, v => set(x => { x.yaw = v; }))), F.field('Pitch', F.num(n.pitch, v => set(x => { x.pitch = v; }))))); break;
      case 'move': fields.push(moveFields(app, id, n, set, setS)); break;
      default: break;
    }

    return h('div', { class: 'iform' },
      heading(info.icon, info.label, id + (isRoot ? ' · point d\'entrée' : ''), h('span', { class: 'tools' },
        F.btn('⧉', () => { let nid; app.mutate(t => { nid = Ops.duplicateNode(t, id); }, { structural: true }); app.select({ t: 'node', id: nid }, true); }, 'mini', 'Dupliquer'),
        F.btn('🗑', () => {
          if (isRoot && !confirm('C\'est le point d\'entrée de ce script. Le supprimer ?')) return;
          if (refs.length && !confirm('Cette action est utilisée à ' + refs.length + ' endroit(s) : les liens seront retirés. Supprimer ?')) return;
          app.mutate(t => Ops.removeNode(t, id), { structural: true });
          app.select(null, true);
        }, 'mini danger', 'Supprimer'))),
      F.section('Action', [
        F.field('Type d\'action', F.select(typeOptions, type, v => setS(x => {
          const d = Ops.defaults(v);
          for (const k of Object.keys(x)) if (k !== 'condition') delete x[k];
          Object.assign(x, d);
        }), null), 'Changer le type remplace les champs de l\'action.'),
        conditionPicker(app, n.condition, v => setS(x => { if (v === undefined) delete x.condition; else x.condition = v; }), 'Condition pour jouer cette action (dans une liste)'),
        app.ent.kind === 'npc' && type !== 'list' ? F.field('Indicateur au-dessus du NPC', F.select([{ v: '', l: '(aucun)' }].concat(E.INDICATORS.map(i => ({ v: String(i.v), l: i.label }))), n.indicator === undefined ? '' : String(n.indicator),
          v => setS(x => { if (v === '') delete x.indicator; else x.indicator = parseInt(v, 10); })), 'Affiché quand cette action est celle que le NPC joue pour le joueur (premier nœud non-liste atteint depuis « main »).') : null,
        fields
      ]),
      Object.keys(extra).length ? F.section('Autres champs (JSON)', F.json(extra, v => setS(x => { Object.keys(extra).forEach(k => delete x[k]); if (E.isObj(v)) Object.assign(x, v); })), { collapsed: true }) : null,
      refs.length ? F.section('Utilisée par', refs.map(r => h('div', { class: 'lrow small' }, refText(r))), { collapsed: true, count: refs.length }) : null);
  }

  // anciens noms de mode : « walk » (éditeur) = constant, « follow » (supprimé) = wait
  function modeOf(m) { return m === 'walk' || m === 'constant' || m === 'constent' ? 'constant' : (m === 'wait' || m === 'follow' ? 'wait' : 'instant'); }

  const KNOWN = {
    list: ['mode', 'liste'], dialogue: ['dialogues'], quest: ['mode', 'quest', 'step', 'substep', 'variable', 'value', 'operator'], message: ['text'], title: ['title', 'subtitle'],
    give: ['items'], money: ['operator', 'value'], experience: ['operator', 'value', 'mode'], damage: ['value', 'mode'], trade: ['trade'], variable: ['variable', 'operator', 'value'],
    function: ['function'], command: ['command', 'as'], wait: ['time'], sound: ['sound', 'volume', 'pitch'], teleport: ['world', 'x', 'y', 'z', 'yaw', 'pitch'],
    move: ['npc', 'position', 'destination', 'world', 'x', 'y', 'z', 'real', 'mode', 'scope', 'speed', 'rules', 'on_stop', 'on_continue']
  };

  function refText(r) {
    switch (r.kind) {
      case 'list': return 'Liste ' + r.node.slice(0, 8);
      case 'guard': return 'Condition de ' + r.node.slice(0, 8);
      case 'text-action': return 'Dialogue ' + r.node.slice(0, 6) + ' › ' + r.state + ' › texte ' + (r.text + 1);
      case 'answer-action': return 'Dialogue ' + r.node.slice(0, 6) + ' › ' + r.state + ' › réponse ' + (r.answer + 1);
      case 'step-cond': return 'Étape ' + (r.step + 1);
      default: return r.kind + ' ' + (r.node || '').slice(0, 8);
    }
  }

  function listFields(app, id, n, set, setS) {
    const liste = Array.isArray(n.liste) ? n.liste : [];
    const rows = liste.map((c, ci) => h('div', { class: 'lrow' },
      h('span', { class: 'idx' }, String(ci + 1)),
      nodePicker(app, c, v => setS(x => { if (v === undefined) x.liste.splice(ci, 1); else x.liste[ci] = v; }), { exclude: id, none: '(retirer)' }),
      F.tools(ci, liste.length, {
        up: ci > 0 ? () => setS(x => F.move(x.liste, ci, -1)) : null,
        down: ci < liste.length - 1 ? () => setS(x => F.move(x.liste, ci, 1)) : null,
        remove: () => setS(x => { x.liste.splice(ci, 1); })
      })));
    const typeSel = h('select', null, h('option', { value: '' }, '＋ Nouvelle action…'), Object.keys(E.ACTIONS).map(t => h('option', { value: t }, E.ACTIONS[t].icon + ' ' + E.ACTIONS[t].label)));
    typeSel.addEventListener('change', () => {
      if (!typeSel.value) return;
      let nid;
      app.mutate(t => { nid = Ops.addNode(t, typeSel.value); const node = Ops.script(t)[id]; if (!Array.isArray(node.liste)) node.liste = []; node.liste.push(nid); }, { structural: true });
      app.select({ t: 'node', id: nid }, true);
    });
    const existing = nodePicker(app, '', v => { if (v) setS(x => { if (!Array.isArray(x.liste)) x.liste = []; x.liste.push(v); }); }, { exclude: id, none: '＋ Ajouter une action existante…' });
    return [
      F.field('Mode', F.select([{ v: 'list', l: 'Toutes celles dont la condition est vraie' }, { v: 'single', l: 'La première dont la condition est vraie' }], n.mode || 'list', v => setS(x => { x.mode = v; }))),
      h('div', { class: 'flabel' }, 'Actions jouées (dans l\'ordre)'), rows.length ? rows : h('div', { class: 'fhint' }, 'Liste vide.'),
      existing, typeSel
    ];
  }

  function dialogueFields(app, id, n) {
    const dlg = E.isObj(n.dialogues) ? n.dialogues : {};
    const names = Object.keys(dlg);
    return [
      h('div', { class: 'frow' }, F.btn('⤢ Ouvrir dans une grande fenêtre', () => app.openDialogue(id)), F.btn('🔗 Lien direct', () => app.dialogueLinkDialog(id), null, 'Adresse et syntaxe Skript qui ouvrent l\'éditeur directement sur ce dialogue')),
      h('div', { class: 'flabel' }, 'États du dialogue (cliquez pour modifier les textes)'),
      names.map(s => h('div', { class: 'lrow' }, F.btn('💬 ' + s, () => app.select({ t: 'state', a: id, s }, true), 'link'), h('span', { class: 'dim small' }, (Array.isArray(dlg[s].texts) ? dlg[s].texts.length : 0) + ' texte(s)'))),
      F.btn('＋ État de dialogue', () => {
        let name;
        app.mutate(t => { name = Ops.addState(t, id, 'nouveau'); }, { structural: true });
        app.select({ t: 'state', a: id, s: name }, true);
      }, 'primary'),
      h('div', { class: 'fhint' }, 'Le joueur démarre à « start » ; « reprise » (on_start) dit où il reprend en recliquant sur le NPC.')
    ];
  }

  function questFields(app, n, set, setS) {
    const mode = n.mode || 'step';
    const out = [
      F.field('Action sur la quête', F.select([{ v: 'new', l: 'Débloquer la quête' }, { v: 'step', l: 'Placer à une étape' }, { v: 'substep', l: 'Avancer un objectif' }, { v: 'variable', l: 'Modifier une variable de quête' }], mode, v => setS(x => { x.mode = v; }))),
      F.field('Quête', questSelect(app, n.quest, v => setS(x => { x.quest = v; })))
    ];
    if (n.quest && app.ctx.exists('quest', n.quest)) out.push(h('div', { class: 'lrow' }, E.chip('quest', n.quest, app.ctx), h('span', { class: 'dim small' }, ' (cliquer pour l\'ouvrir)')));
    if (mode === 'step' || mode === 'substep') out.push(F.field('Étape n°', F.num(n.step === undefined ? undefined : E.num(n.step, 0) + 1, v => set(x => { x.step = Math.max(0, v - 1); }), { int: true, min: 1 })));
    if (mode === 'substep') out.push(F.row(F.field('Objectif n°', F.num(n.substep === undefined ? undefined : E.num(n.substep, 0) + 1, v => set(x => { x.substep = Math.max(0, v - 1); }), { int: true, min: 1 })),
      F.field('Opération', F.select(SET_OPS, n.operator || 'add', v => set(x => { x.operator = v; }))), F.field('Valeur', F.num(n.value, v => set(x => { x.value = v; })))));
    if (mode === 'variable') out.push(F.field('Variable', F.text(n.variable, v => set(x => { x.variable = v; }))),
      F.row(F.field('Opération', F.select(SET_OPS, n.operator || 'set', v => set(x => { x.operator = v; }))), F.field('Valeur', F.text(n.value, v => set(x => { x.value = Ops.parseValue(v); })))));
    return out;
  }

  function moveFields(app, id, n, set, setS) {
    const lists = ['on_stop', 'on_continue'].map(k => {
      const arr = Array.isArray(n[k]) ? n[k] : [];
      return [h('div', { class: 'flabel' }, k === 'on_stop' ? 'Actions quand le NPC s\'arrête' : 'Actions quand il repart'),
        arr.map((c, ci) => h('div', { class: 'lrow' }, nodePicker(app, c, v => setS(x => { if (v === undefined) x[k].splice(ci, 1); else x[k][ci] = v; }), { exclude: id, none: '(retirer)' }))),
        nodePicker(app, '', v => { if (v) setS(x => { if (!Array.isArray(x[k])) x[k] = []; x[k].push(v); }); }, { exclude: id, none: '＋ Ajouter une action…' })];
    });
    const fixed = n.destination === 'location';
    const real = fixed && (n.real === true || n.real === 1);
    return [
      F.field('NPC déplacé', npcSelect(app, n.npc, v => setS(x => { if (v === undefined) delete x.npc; else x.npc = v; }), '(ce NPC)')),
      F.field('Destination', F.select([{ v: 'position', l: 'Une position du NPC (toujours pour le joueur seul)' }, { v: 'location', l: 'Des coordonnées fixes' }], fixed ? 'location' : 'position',
        v => setS(x => { if (v === 'location') x.destination = 'location'; else delete x.destination; }))),
      fixed
        ? [F.field('Monde', F.text(n.world, v => set(x => { x.world = v; })), 'Vide = le monde du NPC.'),
          F.row(...['x', 'y', 'z'].map(k => F.field(k.toUpperCase(), F.num(n[k], v => set(xx => { xx[k] = v; }))))),
          F.field('Qui voit le NPC marcher', F.select([{ v: 'player', l: "Le joueur seul (packets, le serveur ne bouge pas l'entité)" }, { v: 'real', l: "Tout le monde (l'entité marche vraiment)" }], n.real === true || n.real === 1 ? 'real' : 'player',
            v => setS(x => { if (v === 'real') x.real = true; else delete x.real; })), "Réel : la position enregistrée du NPC est mise à jour à l'arrivée.")]
        : F.field('Position n°', F.num(n.position, v => set(x => { x.position = v; }), { int: true, min: 1 })),
      F.field('Mode', F.select([{ v: 'instant', l: 'Instantané (téléportation)' }, { v: 'constant', l: 'Marche à vitesse constante' }, { v: 'wait', l: 'Marche, accélère et attend le joueur' }], modeOf(n.mode), v => set(x => { x.mode = v; }))),
      modeOf(n.mode) !== 'constant' ? null : F.field('Vitesse (0 à 4)', F.num(n.speed, v => set(x => { x.speed = v; }), { int: true, min: 0, max: 4 })),
      real ? null : F.field('Portée', F.select([{ v: 'personal', l: 'Personnel (le joueur seul)' }, { v: 'global', l: 'Global (tous les joueurs connectés)' }], n.scope === 'global' ? 'global' : 'personal', v => set(x => { if (v === 'global') x.scope = v; else delete x.scope; }))),
      modeOf(n.mode) === 'instant' ? null : F.field('Règles de coût du trajet (long pathfinding)',
        F.select([{ v: '', l: '(choisir une variable)' }].concat(E.pathRules.map(r => ({ v: r, l: r }))), n.rules || '', v => set(x => { if (v) x.rules = v; else delete x.rules; }))),
      lists
    ];
  }

  // ── État de dialogue ───────────────────────────────────────────────────
  function stateForm(app, aid, name, focus) {
    const sc = script(app);
    const node = sc[aid];
    const st = node && E.isObj(node.dialogues) ? node.dialogues[name] : null;
    if (!st) return h('div', { class: 'empty' }, 'Cet état n\'existe plus.');
    const texts = Array.isArray(st.texts) ? st.texts : [];
    const setSt = (fn, opts) => app.mutate(t => { fn(Ops.script(t)[aid].dialogues[name]); }, opts || { structural: true });
    const nameInput = h('input', { type: 'text', value: name, spellcheck: 'false' });
    nameInput.addEventListener('change', () => {
      const nn = nameInput.value.trim();
      if (!nn || nn === name) { nameInput.value = name; return; }
      if (node.dialogues[nn]) { E.toast('Un état porte déjà ce nom.', 'error'); nameInput.value = name; return; }
      app.mutate(t => { Ops.renameState(t, aid, name, nn); }, { structural: true });
      app.select({ t: 'state', a: aid, s: nn }, true);
    });
    return h('div', { class: 'iform' },
      heading('💬', 'État « ' + name + ' »', 'dialogue ' + aid.slice(0, 8), h('span', { class: 'tools' },
        F.btn('↰', () => app.select({ t: 'node', id: aid }, true), 'mini', 'Voir le dialogue'),
        F.btn('🗑', () => {
          if (Object.keys(node.dialogues).length <= 1 && !confirm('C\'est le dernier état du dialogue. Le supprimer ?')) return;
          if (!confirm('Supprimer l\'état « ' + name + ' » ? Les réponses qui y mènent perdront leur cible.')) return;
          app.mutate(t => Ops.removeState(t, aid, name), { structural: true });
          app.select({ t: 'node', id: aid }, true);
        }, 'mini danger', 'Supprimer l\'état'))),
      F.section('État', [
        F.field('Nom de l\'état (les liens sont mis à jour)', nameInput),
        F.field('Reprise : état où le joueur reprend en recliquant sur le NPC', statePicker(app, aid, st.on_start, v => setSt(x => { if (v === undefined) delete x.on_start; else x.on_start = v; }), { none: '(le même)' }))
      ]),
      texts.map((t, ti) => textForm(app, aid, name, ti, texts.length, sc, setSt)),
      F.btn('＋ Texte (variante selon une condition)', () => setSt(x => { if (!Array.isArray(x.texts)) x.texts = []; x.texts.push({ answer_mode: 1, time: 120, distance: 15, text: '' }); }), 'primary'),
      h('div', { class: 'fhint' }, 'Le premier texte dont la condition est vraie est affiché ; mettez les cas particuliers en premier.'));
  }

  function textForm(app, aid, name, ti, total, sc, setSt) {
    const st = sc[aid].dialogues[name];
    const t = st.texts[ti];
    const setT = (fn, key) => app.mutate(x => { fn(Ops.script(x)[aid].dialogues[name].texts[ti]); }, key ? { key: 't-' + aid + name + ti + key } : { structural: true });
    const answers = Array.isArray(t.answers) ? t.answers : [];
    const body = [
      conditionPicker(app, t.condition, v => setT(x => { if (v === undefined) delete x.condition; else x.condition = v; }), 'Condition pour afficher ce texte'),
      F.field('Qui parle (autre NPC)', npcSelect(app, t.npc, v => setT(x => { if (v === undefined) delete x.npc; else x.npc = v; }), '(ce NPC)')),
      F.field('Texte du NPC', F.area(t.text, v => setT(x => { x.text = v; }, 'text'), { rows: 4, preview: true })),
      F.field('Texte quand le joueur reprend la discussion (optionnel)', F.area(t.text_reprise, v => setT(x => { if (v === '') delete x.text_reprise; else x.text_reprise = v; }, 'rep'), { rows: 2 })),
      F.row(F.field('Durée de réponse (s)', F.num(t.time, v => setT(x => { x.time = v; }, 'time'), { optional: true })), F.field('Distance max (blocs)', F.num(t.distance, v => setT(x => { x.distance = v; }, 'dist'), { optional: true })),
        F.field('Réponses', F.select([{ v: 1, l: 'Liste' }, { v: 2, l: 'En ligne' }], t.answer_mode || 1, v => setT(x => { x.answer_mode = parseInt(v, 10); })))),
      F.row(F.field('Puis enchaîner sur l\'état', statePicker(app, aid, t.next, v => setT(x => { if (v === undefined) delete x.next; else x.next = v; }), { none: '(attendre une réponse)' }))),
      F.field('Action jouée avec ce texte', nodePicker(app, t.action, v => setT(x => { if (v === undefined) delete x.action; else x.action = v; }), {})),
      h('div', { class: 'flabel' }, 'Réponses du joueur'),
      answers.map((a, ai) => answerForm(app, aid, name, ti, ai, answers.length, sc)),
      F.btn('＋ Réponse', () => setT(x => { if (!Array.isArray(x.answers)) x.answers = []; x.answers.push({ text: 'Réponse', target: Object.keys(sc[aid].dialogues)[0] }); }), 'primary')
    ];
    const wrap = F.section('Texte ' + (ti + 1) + (t.condition && t.condition !== 'true' ? ' 🔒' : ''), body, { count: answers.length });
    wrap.setAttribute('data-itext', ti);
    wrap.querySelector('summary').appendChild(F.tools(ti, total, {
      up: ti > 0 ? () => setSt(x => F.move(x.texts, ti, -1)) : null,
      down: ti < total - 1 ? () => setSt(x => F.move(x.texts, ti, 1)) : null,
      dup: () => setSt(x => { x.texts.splice(ti + 1, 0, E.clone(x.texts[ti])); }),
      remove: () => setSt(x => { x.texts.splice(ti, 1); })
    }));
    return wrap;
  }

  function answerForm(app, aid, name, ti, ai, total, sc) {
    const a = sc[aid].dialogues[name].texts[ti].answers[ai];
    const setA = (fn, key) => app.mutate(x => { fn(Ops.script(x)[aid].dialogues[name].texts[ti].answers[ai]); }, key ? { key: 'a-' + aid + name + ti + ai + key } : { structural: true });
    const targetDialogue = a.target_dialogue || aid;
    const dialogueNodes = Object.keys(sc).filter(k => Ops.isAct(sc[k]) && sc[k].action === 'dialogue');
    return h('div', { class: 'subcard' },
      h('div', { class: 'subhead' }, h('b', null, 'Réponse ' + (ai + 1)), F.tools(ai, total, {
        up: ai > 0 ? () => setT_(app, aid, name, ti, x => F.move(x.answers, ai, -1)) : null,
        down: ai < total - 1 ? () => setT_(app, aid, name, ti, x => F.move(x.answers, ai, 1)) : null,
        remove: () => setT_(app, aid, name, ti, x => { x.answers.splice(ai, 1); })
      })),
      F.field('Texte de la réponse', F.area(a.text, v => setA(x => { x.text = v; }, 'text'), { rows: 2, preview: true })),
      F.field('Infobulle (optionnelle)', F.text(a.hover, v => setA(x => { if (v === '') delete x.hover; else x.hover = v; }, 'hover'))),
      conditionPicker(app, a.condition, v => setA(x => { if (v === undefined) delete x.condition; else x.condition = v; }), 'Condition pour proposer cette réponse'),
      F.field('Mène à l\'état', statePicker(app, targetDialogue, a.target === '<none>' ? undefined : a.target, v => setA(x => { if (v === undefined) { delete x.target; } else x.target = v; }), { none: '(aucun : ferme le dialogue)' })),
      dialogueNodes.length > 1 ? F.field('…dans le dialogue', F.select(dialogueNodes.map(k => ({ v: k, l: k.slice(0, 8) + (k === aid ? ' (celui-ci)' : '') })), targetDialogue, v => setA(x => { if (v === aid) delete x.target_dialogue; else { x.target_dialogue = v; delete x.target; } }))) : null,
      F.field('Action jouée au clic', nodePicker(app, a.action === '<none>' ? undefined : a.action, v => setA(x => { if (v === undefined) delete x.action; else x.action = v; }), {})));
  }

  function setT_(app, aid, name, ti, fn) {
    app.mutate(x => { fn(Ops.script(x)[aid].dialogues[name].texts[ti]); }, { structural: true });
  }

  // ── Fenêtre de modification d'une condition ────────────────────────────
  function modal(title, body, footer) {
    const back = h('div', { class: 'modal-back' });
    const box = h('div', { class: 'modal' }, h('div', { class: 'mhead' }, title, h('button', { class: 'x', onclick: () => close() }, '✕')), h('div', { class: 'mbody' }, body), h('div', { class: 'mfoot' }, footer || []));
    back.appendChild(box);
    back.addEventListener('pointerdown', ev => { if (ev.target === back) close(); });
    document.body.appendChild(back);
    function close() { back.remove(); if (api.onClose) api.onClose(); }
    const api = { close, body: box.querySelector('.mbody'), foot: box.querySelector('.mfoot'), el: back };
    return api;
  }
  E.modal = modal;

  function openConditionDialog(app, id) {
    const dlg = modal('Condition', h('div'), []);
    const render = () => {
      const sc = script(app);
      E.clear(dlg.body);
      if (!sc[id]) { dlg.close(); return; }
      dlg.body.appendChild(condForm(app, id, 0, render));
      E.clear(dlg.foot);
      dlg.foot.appendChild(h('div', { class: 'cdesc' }, '→ ', E.describeCondition(sc, id, app.ctx)));
      dlg.foot.appendChild(F.btn('Supprimer', () => {
        const refs = Ops.refsTo(app.ent.tree, id);
        if (refs.length && !confirm('Cette condition est utilisée à ' + refs.length + ' endroit(s). Elle sera retirée partout. Supprimer ?')) return;
        app.mutate(t => Ops.removeNode(t, id), { structural: true });
        dlg.close();
      }, 'danger'));
      dlg.foot.appendChild(F.btn('Fermer', () => dlg.close(), 'primary'));
    };
    dlg.onClose = () => app.rerenderInspector();
    render();
  }
  E.openConditionDialog = openConditionDialog;

  function condForm(app, id, depth, rerender) {
    const sc = script(app);
    const n = sc[id];
    if (!n || !Ops.isCond(n)) return h('div', { class: 'fhint bad' }, 'Condition introuvable (' + id + ').');
    const set = fn => { app.mutate(t => { fn(Ops.script(t)[id]); }, { structural: false, key: 'c-' + id }); };
    const setS = fn => { app.mutate(t => { fn(Ops.script(t)[id]); }, { structural: true }); rerender(); };
    const type = n.condition;
    const out = [];
    out.push(F.row(F.field('Type', F.select([{ v: 'compare', l: 'Comparer deux valeurs' }, { v: 'between', l: 'Entre deux valeurs' }, { v: 'list', l: 'Combiner des conditions' }].concat(['compare', 'between', 'list'].includes(type) ? [] : [{ v: type, l: type }]), type, v => setS(x => {
      if (v === 'list') { x.condition = 'list'; x.mode = 'all'; x.liste = []; delete x.value1; delete x.value2; delete x.value3; delete x.operator; }
      else if (v === 'between') { x.condition = 'between'; x.value1 = { type: 'value', value: 0 }; x.value2 = { type: 'level' }; x.value3 = { type: 'value', value: 10 }; delete x.liste; delete x.mode; delete x.operator; }
      else { x.condition = 'compare'; x.operator = 'equal'; x.value1 = x.value1 || { type: 'level' }; x.value2 = { type: 'value', value: 0 }; delete x.value3; delete x.liste; delete x.mode; }
    }))), F.check('Inverser (NON)', n.reverse, v => setS(x => { x.reverse = v; }))));
    if (type === 'compare') {
      out.push(operandForm(app, 'Valeur 1', id, 'value1', rerender),
        F.field('Opérateur', F.select(OPS, n.operator || 'equal', v => set(x => { x.operator = v; }))),
        operandForm(app, 'Valeur 2', id, 'value2', rerender));
    } else if (type === 'between') {
      out.push(operandForm(app, 'Minimum', id, 'value1', rerender), operandForm(app, 'Valeur testée', id, 'value2', rerender), operandForm(app, 'Maximum', id, 'value3', rerender));
    } else if (type === 'list') {
      const liste = Array.isArray(n.liste) ? n.liste : [];
      out.push(F.field('Vraie quand', F.select([{ v: 'all', l: 'Toutes les conditions sont vraies (ET)' }, { v: 'single', l: 'Au moins une est vraie (OU)' }], n.mode || 'single', v => setS(x => { x.mode = v; }))));
      liste.forEach((c, ci) => {
        const conds = E.conditionList(sc, app.ctx).filter(k => k.id !== id);
        const sel = F.select(conds.map(k => ({ v: k.id, l: E.truncate(k.desc, 60) })), c, v => setS(x => { x.liste[ci] = v; }));
        out.push(h('div', { class: 'nested', style: { marginLeft: Math.min(depth, 3) * 8 + 'px' } },
          h('div', { class: 'lrow' }, h('span', { class: 'idx' }, String(ci + 1)), sel,
            F.btn('✕', () => setS(x => { x.liste.splice(ci, 1); }), 'mini danger')),
          depth < 3 && sc[c] && Ops.isCond(sc[c]) ? condForm(app, c, depth + 1, rerender) : null));
      });
      out.push(F.btn('＋ Sous-condition', () => {
        let nid;
        app.mutate(t => { nid = Ops.addCondition(t, app.ent.kind === 'quest' ? app.ent.id : null); Ops.script(t)[id].liste.push(nid); }, { structural: true });
        rerender();
      }));
    } else {
      out.push(F.field('Champs (JSON)', F.json(n, v => { if (E.isObj(v) && v.condition) app.mutate(t => { Ops.script(t)[id] = v; }, { key: 'cj-' + id }); })));
    }
    return h('div', { class: 'condform' }, out);
  }

  /** Une opérande (value1 / value2 / value3) de la condition `id`. */
  function operandForm(app, label, id, field, rerender) {
    const cur = script(app)[id][field];
    const op = E.isObj(cur) ? cur : { type: 'value', value: 0 };
    const type = op.type || 'value';
    const replace = o => { app.mutate(t => { Ops.script(t)[id][field] = o; }, { structural: true }); rerender(); };
    const quiet = (fn, key) => app.mutate(t => { const sc = Ops.script(t)[id]; if (!E.isObj(sc[field])) sc[field] = { type }; fn(sc[field]); }, { key: 'op-' + id + field + key });
    const fields = [];
    const typeSel = F.select(OPERAND_TYPES, type, v => {
      const base = { type: v };
      if (v === 'value') base.value = 0;
      if (v === 'quest') base.quest = app.ent.kind === 'quest' ? app.ent.id : '';
      replace(base);
    });
    switch (type) {
      case 'value': fields.push(F.field('Valeur', F.text(op.value, v => quiet(o => { o.value = Ops.parseValue(v); }, 'v')))); break;
      case 'variable': fields.push(F.field('Variable Skript', F.text(op.variable, v => quiet(o => { o.variable = v; }, 'var')))); break;
      case 'item': fields.push(F.field('Objet (item custom ou matériau)', F.itemId(op.item, v => quiet(o => { o.item = v; }, 'item')))); break;
      case 'player_nbt': case 'player_metadata': case 'npc_nbt': fields.push(F.field('Chemin', F.text(op.path, v => quiet(o => { o.path = v; }, 'path')))); break;
      case 'position': fields.push(F.field('NPC (vide = ce NPC)', npcSelect(app, op.npc, v => { const o = { type: 'position' }; if (v) o.npc = v; replace(o); }, '(ce NPC)'))); break;
      case 'quest': {
        const kind = op.variable !== undefined ? 'variable' : op.substep !== undefined ? 'substep' : 'step';
        fields.push(F.field('Quête', questSelect(app, op.quest, v => replace(Object.assign({}, op, { quest: v })))),
          F.field('Valeur lue', F.select([{ v: 'step', l: 'Étape actuelle (−1 = pas commencée)' }, { v: 'variable', l: 'Une variable de la quête' }, { v: 'substep', l: 'Progression d\'un objectif' }], kind, v => {
            const o = { type: 'quest', quest: op.quest };
            if (v === 'variable') o.variable = '';
            if (v === 'substep') { o.step = 0; o.substep = 0; }
            replace(o);
          })));
        if (kind === 'variable') fields.push(F.field('Variable', F.text(op.variable, v => quiet(o => { o.variable = v; }, 'qvar'))));
        if (kind === 'substep') fields.push(F.row(F.field('Étape n°', F.num(E.num(op.step, 0) + 1, v => quiet(o => { o.step = Math.max(0, v - 1); }, 'st'), { int: true, min: 1 })),
          F.field('Objectif n°', F.num(E.num(op.substep, 0) + 1, v => quiet(o => { o.substep = Math.max(0, v - 1); }, 'sub'), { int: true, min: 1 }))));
        if (kind === 'step') fields.push(h('div', { class: 'fhint' }, 'Les étapes sont comptées à partir de 0 : étape n°1 = 0, n°2 = 1... ; −1 = quête pas commencée.'));
        break;
      }
      default: break;
    }
    return h('div', { class: 'operand' }, h('div', { class: 'flabel' }, label), F.row(F.field('Source', typeSel)), fields);
  }
})();
