/*
 * Modèles de graphe : transforme la définition brute d'une quête ou d'un NPC en cartes (éléments DOM) et en liens.
 * Les conditions ne sont pas des cartes : elles sont décrites en clair sur la carte qu'elles gardent.
 */
(function () {
  'use strict';
  const E = window.E;
  const h = E.h;

  // ── Types d'actions ────────────────────────────────────────────────────
  E.ACTIONS = {
    list: { icon: '☰', label: 'Liste', color: 'violet' },
    dialogue: { icon: '💬', label: 'Dialogue', color: 'blue' },
    quest: { icon: '📜', label: 'Quête', color: 'gold' },
    message: { icon: '✉', label: 'Message', color: 'teal' },
    title: { icon: '🅣', label: 'Titre', color: 'teal' },
    give: { icon: '🎁', label: 'Donner des objets', color: 'green' },
    money: { icon: '₪', label: 'Argent', color: 'green' },
    experience: { icon: '✦', label: 'Expérience', color: 'green' },
    damage: { icon: '♥', label: 'Dégâts', color: 'red' },
    trade: { icon: '⇄', label: 'Échange', color: 'green' },
    variable: { icon: 'x=', label: 'Variable Skript', color: 'gray' },
    function: { icon: 'ƒ', label: 'Fonction Skript', color: 'gray' },
    command: { icon: '/', label: 'Commande', color: 'gray' },
    wait: { icon: '⏱', label: 'Attendre', color: 'gray' },
    move: { icon: '➜', label: 'Déplacer le NPC', color: 'orange' },
    sound: { icon: '♪', label: 'Son', color: 'teal' },
    teleport: { icon: '⤳', label: 'Téléporter', color: 'orange' }
  };
  E.actionInfo = function (type) {
    return E.ACTIONS[type] || { icon: '⚙', label: type, color: 'gray' };
  };

  // ── Conditions ─────────────────────────────────────────────────────────
  const OP = { equal: '=', superior_strict: '>', inferior_strict: '<', superior: '≥', inferior: '≤' };
  E.OPERATORS = OP;

  E.isCondition = function (n) { return E.isObj(n) && n.condition !== undefined && n.action === undefined; };
  E.isAction = function (n) { return E.isObj(n) && !E.isCondition(n); };

  function isQuestStep(op) {
    return E.isObj(op) && op.type === 'quest' && op.variable === undefined && op.step === undefined && op.substep === undefined;
  }

  E.describeOperand = function (op, ctx) {
    if (!E.isObj(op)) return '?';
    const type = op.type || 'value';
    switch (type) {
      case 'value': return op.value === undefined ? '∅' : (typeof op.value === 'string' ? '"' + op.value + '"' : String(op.value));
      case 'level': return 'niveau du joueur';
      case 'rank': return 'rang du joueur';
      case 'now': return 'heure actuelle';
      case 'variable': return 'variable ' + E.str(op.variable);
      case 'player_nbt': return 'donnée joueur ' + E.str(op.path);
      case 'player_metadata': return 'métadonnée ' + E.str(op.path);
      case 'npc_nbt': return 'donnée NPC ' + E.str(op.path);
      case 'item': return 'nombre de ' + E.str(op.item);
      case 'position': return 'position' + (op.npc ? ' de ' + op.npc : ' du NPC');
      case 'quest': {
        const q = ctx && ctx.questName ? E.plain(ctx.questName(op.quest)) : E.str(op.quest);
        if (op.variable !== undefined) return 'variable « ' + op.variable + ' » de ' + q;
        if (op.step !== undefined && op.substep !== undefined) return 'objectif ' + (E.num(op.substep, 0) + 1) + ' de l\'étape ' + (E.num(op.step, 0) + 1) + ' de ' + q;
        return 'étape de ' + q;
      }
      default: return type;
    }
  };

  E.describeCondition = function (script, id, ctx, depth) {
    depth = depth || 0;
    if (id === undefined || id === null || id === 'true') return 'toujours';
    if (id === 'false') return 'jamais';
    const n = script && script[id];
    if (!E.isObj(n) || !E.isCondition(n)) return '⚠ condition introuvable (' + E.truncate(id, 12) + ')';
    if (depth > 6) return '…';
    const neg = n.reverse === true || n.reverse === 1;
    let text;
    if (n.condition === 'list') {
      const parts = (Array.isArray(n.liste) ? n.liste : []).map(c => E.describeCondition(script, c, ctx, depth + 1));
      text = parts.length ? (parts.length > 1 ? '(' + parts.join(n.mode === 'all' ? ' ET ' : ' OU ') + ')' : parts[0]) : '(liste vide)';
    } else if (n.condition === 'compare') {
      const a = n.value1, b = n.value2, op = OP[n.operator || 'equal'] || '?';
      if (isQuestStep(a) && E.isObj(b) && (b.type || 'value') === 'value' && typeof E.num(b.value, null) === 'number') {
        const v = E.num(b.value, 0);
        text = 'étape de ' + E.describeOperand(Object.assign({}, a, { step: undefined }), ctx).replace(/^étape de /, '') + ' ' + op + ' ' + (v < 0 ? 'non commencée' : 'étape n°' + (v + 1));
      } else {
        text = E.describeOperand(a, ctx) + ' ' + op + ' ' + E.describeOperand(b, ctx);
      }
    } else if (n.condition === 'between') {
      text = E.describeOperand(n.value1, ctx) + ' ≤ ' + E.describeOperand(n.value2, ctx) + ' ≤ ' + E.describeOperand(n.value3, ctx);
    } else {
      text = 'condition « ' + n.condition + ' »';
    }
    return neg ? 'NON ' + text : text;
  };

  /** Conditions du script, pour les listes déroulantes. */
  E.conditionList = function (script, ctx) {
    const out = [];
    for (const id of Object.keys(script || {})) {
      if (E.isCondition(script[id])) out.push({ id, desc: E.describeCondition(script, id, ctx) });
    }
    return out;
  };

  // ── Éléments communs ───────────────────────────────────────────────────
  function lock(desc) {
    return h('div', { class: 'guard', title: desc }, h('span', { class: 'ico' }, '🔒'), h('span', { class: 'gtxt' }, E.truncate(desc, 90)));
  }

  function chip(kind, id, ctx, extra) {
    const exists = ctx.exists(kind, id);
    const name = kind === 'quest' ? ctx.questName(id) : ctx.npcName(id);
    const el = h('span', { class: 'chip ' + kind + (exists ? '' : ' missing'), title: (exists ? 'Ouvrir ' : 'Introuvable : ') + id },
      h('span', { class: 'ico' }, kind === 'quest' ? '📜' : '🧑'), E.mc(name || id), extra ? ' ' + extra : null);
    if (exists) el.addEventListener('click', ev => { ev.stopPropagation(); ctx.open(kind, id); });
    return el;
  }
  E.chip = chip;

  function row(port, ...children) {
    return h('div', { class: 'row', 'data-port': port }, ...children);
  }

  function endNode() {
    return { id: 'end', w: 120, root: false, sel: null, el: h('div', { class: 'card endcard' }, h('span', { class: 'ico' }, '🏁'), 'Quête terminée') };
  }

  // ── Graphe d'une quête ─────────────────────────────────────────────────
  E.buildQuestGraph = function (ent, ctx) {
    const raw = ent.tree;
    const steps = Array.isArray(raw.steps) ? raw.steps : [];
    const script = E.isObj(raw.script) ? raw.script : {};
    const nodes = [], edges = [];
    let needEnd = false;

    // NPC regroupés par (npc, but)
    const groups = new Map();
    for (const u of (ent.unlockers || [])) {
      const key = [u.npc, u.mode, u.toStep, u.substep].join('|');
      let g = groups.get(key);
      if (!g) { g = Object.assign({ nodes: [], from: new Set(), any: false }, u); groups.set(key, g); }
      g.nodes.push(u.node);
      if (u.fromSteps == null) g.any = true; else u.fromSteps.forEach(s => g.from.add(s));
    }
    const unlockedBy = new Map();
    for (const g of groups.values()) {
      if (g.mode === 'substep') continue;
      const arr = unlockedBy.get(g.toStep) || [];
      arr.push(g);
      unlockedBy.set(g.toStep, arr);
    }

    steps.forEach((st, i) => {
      const next = st.next === undefined ? i + 1 : E.num(st.next, i + 1);
      const objectives = Array.isArray(st.objectives) ? st.objectives : [];
      const card = h('div', { class: 'card step' },
        h('div', { class: 'head' },
          h('span', { class: 'badge' }, String(i + 1)),
          h('span', { class: 'title' }, 'Étape ' + (i + 1)),
          st.condition ? h('span', { class: 'tag lockt', title: E.describeCondition(script, st.condition, ctx) }, '🔒') : null),
        st.condition ? lock(E.describeCondition(script, st.condition, ctx)) : null,
        h('div', { class: 'text' }, E.mc(E.truncate(st.text, 200) || '(pas de texte)')),
        objectives.length ? h('div', { class: 'objs' }, objectives.map((o, oi) => objectiveRow(o, oi, i, groups, ctx))) : null,
        unlockedBy.get(i) ? h('div', { class: 'by' }, h('span', { class: 'ico' }, '🔓'), 'Débloquée par ',
          unlockedBy.get(i).map((g, k) => [k ? ', ' : '', chip('npc', g.npc, ctx)])) : null,
        row('next', h('span', { class: 'next' }, next >= steps.length || next < 0 ? 'fin de la quête' : 'puis étape ' + (next + 1)), h('span', { class: 'dot' })));
      nodes.push({ id: 's:' + i, w: 310, root: i === 0, sel: { t: 'step', i }, el: card });
      if (next >= steps.length || next < 0) { needEnd = true; edges.push({ id: 'n:' + i, from: 's:' + i, port: 'next', to: 'end', cls: 'next' }); }
      else if (next !== i) edges.push({ id: 'n:' + i, from: 's:' + i, port: 'next', to: 's:' + next, cls: 'next' });
    });
    if (needEnd) nodes.push(endNode());

    // NPC qui font avancer la quête
    for (const g of groups.values()) {
      const nid = 'u:' + g.npc + ':' + g.mode + ':' + g.toStep + ':' + g.substep;
      const what = g.mode === 'new' ? 'lance la quête' : g.mode === 'substep'
        ? 'avance l\'objectif ' + (g.substep + 1) + ' de l\'étape ' + (g.toStep + 1) : 'place à l\'étape ' + (g.toStep + 1);
      const el = h('div', { class: 'card npcchip', title: 'Ouvrir ce NPC' },
        h('div', { class: 'head' }, h('span', { class: 'ico' }, '🧑'), h('span', { class: 'title' }, E.mc(ctx.npcName(g.npc) || g.npc))),
        h('div', { class: 'sub' }, what),
        !g.any && g.from.size ? h('div', { class: 'sub dim' }, 'depuis ' + fromText([...g.from])) : null);
      el.addEventListener('click', () => ctx.open('npc', g.npc));
      nodes.push({ id: nid, w: 220, sel: null, el, satellite: true });
      const to = g.mode === 'new' ? 0 : g.toStep;
      if (to >= 0 && to < steps.length) edges.push({ id: 'ue:' + nid, from: nid, to: 's:' + to, cls: 'unlock' });
      if (!g.any) for (const s of g.from) {
        if (s >= 0 && s < steps.length && s !== to) edges.push({ id: 'uf:' + nid + ':' + s, from: 's:' + s, port: 'next', to: nid, cls: 'cond' });
      }
    }

    const scriptPart = buildScript(script, ['reward'], ctx, 'q', 'quest', ctx.opts);
    return { nodes: nodes.concat(scriptPart.nodes), edges: edges.concat(scriptPart.edges), captions: scriptPart.captions };
  };

  function fromText(list) {
    const names = list.sort((a, b) => a - b).map(s => s < 0 ? 'quête non commencée' : 'étape ' + (s + 1));
    return names.join(', ');
  }

  function objectiveRow(o, oi, stepIndex, groups, ctx) {
    const type = o.type || 'externe';
    let icon = '💬', body;
    if (type === 'item') { icon = '📦'; body = E.str(o.count, 1) + '× ' + E.str(o.item, '?'); }
    else if (type === 'action') { icon = '⚔'; body = E.str(o.count, 1) + '× ' + E.str(o.action, '?'); }
    else if (type === 'special' || type === 'spécial') { icon = '✨'; body = 'spécial : ' + E.str(o.action, '?'); }
    else { icon = '💬'; body = E.str(o.text, 'objectif externe'); }
    const npcs = [];
    for (const g of groups.values()) if (g.mode === 'substep' && g.toStep === stepIndex && g.substep === oi) npcs.push(g.npc);
    return h('div', { class: 'obj', title: E.str(o.text) }, h('span', { class: 'ico' }, icon), h('span', { class: 'otxt' }, E.truncate(body, 60)),
      o.destinations && o.destinations.length ? h('span', { class: 'tag', title: 'Destinations' }, '📍' + o.destinations.length) : null,
      npcs.map(n => chip('npc', n, ctx)));
  }

  // ── Graphe d'un NPC ────────────────────────────────────────────────────
  E.buildNpcGraph = function (ent, ctx) {
    const script = E.isObj(ent.tree.script) ? ent.tree.script : {};
    return buildScript(script, ['main'], ctx, 'n', 'npc', ctx.opts);
  };

  // ── Indicateurs de quête (au-dessus des NPC en jeu) ────────────────────
  // Mêmes glyphes et mêmes couleurs que QuestIndicator (index 0 à 4) : <red>?, #ff8800 !, #44e1eb !, <dark_purple>🗡, #1cee13 🛈
  E.INDICATORS = [
    { v: 0, text: '?', color: '#ff5555', label: '? rouge : nouvelle quête' },
    { v: 1, text: '!', color: '#ff8800', label: '! orange : progression, quête principale' },
    { v: 2, text: '!', color: '#44e1eb', label: '! cyan : progression, quête secondaire' },
    { v: 3, text: '🗡', color: '#aa00aa', label: '🗡 violet : progression, quête de contrée' },
    { v: 4, text: '🛈', color: '#1cee13', label: '🛈 vert : information' }
  ];
  E.indicatorOf = function (n) {
    if (!E.isObj(n) || (n.action || 'list') === 'list') return null;
    const v = Number(n.indicator);
    return Number.isInteger(v) && v >= 0 && v < E.INDICATORS.length ? E.INDICATORS[v] : null;
  };
  function indicatorBadge(n) {
    const ind = E.indicatorOf(n);
    return ind ? h('div', { class: 'ind', style: { color: ind.color }, title: 'Indicateur au-dessus du NPC : ' + ind.label }, ind.text) : null;
  }

  function helpers(script, ctx, prefix) {
    const exists = id => typeof id === 'string' && E.isAction(script[id]);
    const title = id => {
      const n = script[id];
      if (!n) return '⚠ ' + E.truncate(id, 10);
      return E.actionInfo(n.action || 'list').icon + ' ' + shortSummary(n, ctx);
    };
    return { exists, title, aid: id => prefix + ':' + id, sid: (a, name) => prefix + ':' + a + ':' + name };
  }

  /**
   * Cartes des états d'un dialogue et liens entre elles. Un lien qui sort du dialogue (vers une action, ou vers un autre
   * dialogue) porte `ext` = l'identifiant de l'action visée : la vue compacte et la fenêtre du dialogue s'en servent.
   */
  function stateCards(script, id, ctx, hp) {
    const { exists, title, sid, aid } = hp;
    const nodes = [], edges = [];
    const n = script[id];
    const dlg = E.isObj(n.dialogues) ? n.dialogues : {};
    for (const name of Object.keys(dlg)) {
      const st = dlg[name] || {};
      const texts = Array.isArray(st.texts) ? st.texts : [];
      const blocks = [];
      texts.forEach((t, ti) => {
        const cond = t.condition && t.condition !== 'true' ? E.describeCondition(script, t.condition, ctx) : null;
        const lines = [];
        if (cond) lines.push(lock(cond));
        if (t.npc) lines.push(h('div', { class: 'speaker' }, 'parle : ', chip('npc', t.npc, ctx)));
        lines.push(h('div', { class: 'text' }, t.text !== undefined ? E.mc(E.truncate(t.text, 220)) : h('i', { class: 'dim' }, '(aucun texte : enchaîne seulement)')));
        if (t.text_reprise !== undefined) lines.push(h('div', { class: 'reprise', title: E.str(t.text_reprise) }, '↺ ' + E.truncate(E.plain(t.text_reprise), 70)));
        const rws = [];
        (Array.isArray(t.answers) ? t.answers : []).forEach((a, ai) => {
          const ac = a.condition && a.condition !== 'true' ? E.describeCondition(script, a.condition, ctx) : null;
          const targetOk = a.target === undefined || a.target === '<none>' || targetExists(script, id, a);
          rws.push(h('div', { class: 'row answer', 'data-port': 'a' + ti + '_' + ai + 't', 'data-port2': 'a' + ti + '_' + ai + 'a' },
            h('span', { class: 'ico' }, '➤'), h('span', { class: 'rtxt' }, E.mc(E.truncate(a.text, 80) || '(réponse vide)')),
            ac ? h('span', { class: 'tag lockt', title: ac }, '🔒') : null,
            a.action && a.action !== '<none>' ? h('span', { class: 'tag act', title: exists(a.action) ? title(a.action) : '⚠ action introuvable' }, '⚡') : null,
            !targetOk ? h('span', { class: 'tag bad', title: 'État cible introuvable : ' + a.target }, '⚠') : null,
            h('span', { class: 'dot' })));
          if (a.target && a.target !== '<none>' && targetOk) {
            const tdlg = a.target_dialogue || id;
            edges.push({ id: 'at:' + id + ':' + name + ':' + ti + ':' + ai, from: sid(id, name), port: 'a' + ti + '_' + ai + 't', to: sid(tdlg, a.target), cls: 'answer', ext: tdlg !== id ? tdlg : undefined });
          }
          if (a.action && a.action !== '<none>' && exists(a.action)) {
            edges.push({ id: 'aa:' + id + ':' + name + ':' + ti + ':' + ai, from: sid(id, name), port: 'a' + ti + '_' + ai + 'a', to: aid(a.action), cls: 'action', ext: a.action });
          }
        });
        if (t.action && exists(t.action)) {
          rws.push(row('x' + ti, h('span', { class: 'ico' }, '⚡'), h('span', { class: 'rtxt' }, title(t.action)), h('span', { class: 'dot' })));
          edges.push({ id: 'ta:' + id + ':' + name + ':' + ti, from: sid(id, name), port: 'x' + ti, to: aid(t.action), cls: 'action', ext: t.action });
        } else if (t.action) {
          rws.push(row('x' + ti, h('span', { class: 'ico' }, '⚡'), h('span', { class: 'rtxt bad' }, '⚠ action introuvable')));
        }
        if (t.next !== undefined && dlg[t.next]) {
          rws.push(row('n' + ti, h('span', { class: 'ico' }, '⏩'), h('span', { class: 'rtxt' }, 'puis « ' + E.truncate(t.next, 24) + ' »' + (t.time ? ' après ' + t.time + ' s' : '')), h('span', { class: 'dot' })));
          edges.push({ id: 'tn:' + id + ':' + name + ':' + ti, from: sid(id, name), port: 'n' + ti, to: sid(id, t.next), cls: 'next' });
        } else if (t.next !== undefined) {
          rws.push(row('n' + ti, h('span', { class: 'ico' }, '⏩'), h('span', { class: 'rtxt bad' }, '⚠ état « ' + t.next + ' » introuvable')));
        }
        blocks.push(h('div', { class: 'tblock', 'data-text': ti }, lines, rws));
      });
      if (st.on_start && st.on_start !== name && dlg[st.on_start]) {
        edges.push({ id: 'os:' + id + ':' + name, from: sid(id, name), port: 'os', to: sid(id, st.on_start), cls: 'resume' });
      }
      const card = h('div', { class: 'card state' },
        h('div', { class: 'head' }, h('span', { class: 'ico' }, '💬'), h('span', { class: 'title' }, name), h('span', { class: 'idtag', title: 'Dialogue ' + id }, id.slice(0, 6))),
        blocks.length ? blocks : h('div', { class: 'sub dim' }, '(aucun texte)'),
        st.on_start && st.on_start !== name ? row('os', h('span', { class: 'ico' }, '↺'), h('span', { class: 'rtxt dim' }, 'reprise : « ' + E.truncate(st.on_start, 24) + ' »'), h('span', { class: 'dot' })) : null);
      nodes.push({ id: sid(id, name), w: 300, root: false, sel: { t: 'state', a: id, s: name }, el: card });
    }
    return { nodes, edges };
  }

  /** Sorties cumulées d'un dialogue : une entrée par action visée (de n'importe quel état). */
  function dialogueOutputs(sub, id) {
    const outs = new Map();
    for (const e of sub.edges) {
      if (!e.ext || e.ext === id) continue;
      const o = outs.get(e.ext) || { to: e.ext, count: 0, cls: e.cls };
      o.count++;
      if (e.cls === 'action') o.cls = 'action';
      outs.set(e.ext, o);
    }
    return [...outs.values()];
  }

  /** Actions qui mènent à l'action `id` (liste, état d'un autre dialogue, réponse, déplacement...). */
  function referrersOf(script, id) {
    const out = new Set();
    for (const nid of Object.keys(script)) {
      const n = script[nid];
      if (nid === id || !E.isAction(n)) continue;
      if (Array.isArray(n.liste) && n.liste.includes(id)) out.add(nid);
      for (const k of ['on_stop', 'on_continue']) if (Array.isArray(n[k]) && n[k].includes(id)) out.add(nid);
      if (!E.isObj(n.dialogues)) continue;
      for (const sn of Object.keys(n.dialogues)) {
        for (const t of (Array.isArray(n.dialogues[sn].texts) ? n.dialogues[sn].texts : [])) {
          if (t.action === id) out.add(nid);
          for (const a of (Array.isArray(t.answers) ? t.answers : [])) if (a.action === id || a.target_dialogue === id) out.add(nid);
        }
      }
    }
    return [...out];
  }

  function miniAction(script, ctx, id, zid) {
    const n = script[id];
    const info = E.actionInfo(n.action || 'list');
    const el = h('div', { class: 'card act zonecard c-' + info.color, title: 'Sélectionner cette action' },
      h('div', { class: 'head' }, h('span', { class: 'ico' }, info.icon), h('span', { class: 'title' }, shortSummary(n, ctx)), h('span', { class: 'idtag', title: id }, id.slice(0, 6))),
      indicatorBadge(n));
    return { id: zid, w: 230, root: false, sel: { t: 'node', id }, el };
  }

  /** Graphe d'un seul dialogue : ses états au centre, les actions qui y mènent à gauche et celles où il débouche à droite. */
  E.buildDialogueGraph = function (ent, id, ctx) {
    const script = E.isObj(ent.tree.script) ? ent.tree.script : {};
    const prefix = ent.kind === 'quest' ? 'q' : 'n';
    const n = script[id];
    if (!E.isAction(n) || n.action !== 'dialogue') return { nodes: [], edges: [], zones: { left: [], right: [] } };
    const hp = helpers(script, ctx, prefix);
    const sub = stateCards(script, id, ctx, hp);
    const nodes = sub.nodes.slice(), edges = [], left = [], right = [];
    const inner = new Set(nodes.map(x => x.id));
    for (const e of sub.edges) {
      if (e.ext && e.ext !== id) {
        const zid = 'o:' + e.ext;
        if (!right.includes(zid)) { right.push(zid); nodes.push(miniAction(script, ctx, e.ext, zid)); }
        edges.push({ id: e.id, from: e.from, port: e.port, to: zid, cls: e.cls });
      } else if (!e.ext && inner.has(e.to)) edges.push(e);
    }
    const dlg = E.isObj(n.dialogues) ? n.dialogues : {};
    const first = dlg.start ? 'start' : Object.keys(dlg)[0];
    for (const src of referrersOf(script, id)) {
      const zid = 'i:' + src;
      left.push(zid);
      nodes.push(miniAction(script, ctx, src, zid));
      if (first) edges.push({ id: 'in:' + src, from: zid, to: hp.sid(id, first), cls: 'action' });
    }
    const start = nodes.find(x => x.id === hp.sid(id, first));
    if (start) start.root = true;
    return { nodes, edges, zones: { left, right }, captions: [] };
  };

  // ── Script (actions, dialogues) ────────────────────────────────────────
  function buildScript(script, rootIds, ctx, prefix, kind, opts) {
    opts = opts || {};
    const nodes = [], edges = [], captions = [];
    const ids = Object.keys(script).filter(id => E.isAction(script[id]));
    if (!ids.length) return { nodes, edges, captions };
    const hp = helpers(script, ctx, prefix);
    const { aid, sid, exists, title } = hp;

    const roots = new Set(rootIds.filter(r => exists(r)));
    const subs = new Map(); // dialogue -> états + liens
    for (const id of ids) {
      const n = script[id];
      const type = n.action || 'list';
      const info = E.actionInfo(type);
      const guard = typeof n.condition === 'string' ? E.describeCondition(script, n.condition, ctx) : null;
      const isRoot = roots.has(id);
      const body = [];
      if (guard) body.push(lock(guard));
      const rows = [];
      if (type === 'list') {
        const liste = Array.isArray(n.liste) ? n.liste : [];
        body.push(h('div', { class: 'sub' }, n.mode === 'single' ? 'joue le premier dont la condition est vraie' : 'joue tous ceux dont la condition est vraie'));
        liste.forEach((c, ci) => {
          const ok = exists(c);
          const g = ok && typeof script[c].condition === 'string' ? E.describeCondition(script, script[c].condition, ctx) : null;
          rows.push(row('c' + ci, h('span', { class: 'idx' }, String(ci + 1)), h('span', { class: ok ? 'rtxt' : 'rtxt bad', title: g || '' }, ok ? title(c) : '⚠ nœud introuvable'), g ? h('span', { class: 'tag lockt', title: g }, '🔒') : null, h('span', { class: 'dot' })));
          if (ok) edges.push({ id: 'l:' + id + ':' + ci, from: aid(id), port: 'c' + ci, to: aid(c), cls: 'list' });
        });
        if (!liste.length) body.push(h('div', { class: 'sub dim' }, '(liste vide)'));
      } else if (type === 'dialogue') {
        const dlg = E.isObj(n.dialogues) ? n.dialogues : {};
        const names = Object.keys(dlg);
        const sub = stateCards(script, id, ctx, hp);
        subs.set(id, sub);
        body.push(h('div', { class: 'sub' }, names.length + ' état' + (names.length > 1 ? 's' : '') + ' de dialogue' + (opts.merge ? ' (compacté)' : '')));
        if (opts.merge) {
          const outs = dialogueOutputs(sub, id).filter(o => exists(o.to));
          outs.forEach((o, k) => {
            rows.push(row('o' + k, h('span', { class: 'ico' }, o.cls === 'answer' ? '💬' : '⚡'), h('span', { class: 'rtxt' }, title(o.to)),
              o.count > 1 ? h('span', { class: 'tag', title: o.count + ' sorties vers cette action' }, '×' + o.count) : null, h('span', { class: 'dot' })));
            edges.push({ id: 'dm:' + id + ':' + o.to, from: aid(id), port: 'o' + k, to: aid(o.to), cls: o.cls });
          });
          if (!outs.length) body.push(h('div', { class: 'sub dim' }, '(aucune sortie)'));
        } else {
          rows.push(row('start', h('span', { class: dlg.start ? 'rtxt' : 'rtxt bad' }, dlg.start ? 'début : « start »' : '⚠ pas d\'état « start »'), h('span', { class: 'dot' })));
          if (dlg.start) edges.push({ id: 's:' + id, from: aid(id), port: 'start', to: sid(id, 'start'), cls: 'start' });
        }
      } else {
        actionBody(n, type, ctx, body, rows, edges, aid(id), exists, aid);
      }
      const card = h('div', { class: 'card act c-' + info.color + (isRoot ? ' root' : '') },
        h('div', { class: 'head' }, h('span', { class: 'ico' }, info.icon), h('span', { class: 'title' }, info.label),
          isRoot ? h('span', { class: 'tag' }, '▶ ' + id) : h('span', { class: 'idtag', title: id }, id.slice(0, 8))),
        body, rows, kind === 'npc' ? indicatorBadge(n) : null);
      const spec = { id: aid(id), w: 270, root: isRoot, sel: { t: 'node', id }, el: card };
      if (type === 'dialogue') spec.openDialogue = id;
      nodes.push(spec);
    }

    // états de dialogue (absents en vue compacte : tout est dans la carte de l'action)
    if (!opts.merge) {
      for (const sub of subs.values()) {
        sub.nodes.forEach(x => nodes.push(x));
        sub.edges.forEach(x => edges.push(x));
      }
    }

    // légendes des groupes non atteints
    captions.push({ text: 'Points d\'entrée : ' + [...roots].join(', ') });
    return { nodes, edges, captions };
  }

  function targetExists(script, id, a) {
    const dlg = a.target_dialogue ? script[a.target_dialogue] : script[id];
    return !!(dlg && E.isObj(dlg.dialogues) && dlg.dialogues[a.target]);
  }

  function shortSummary(n, ctx) {
    const t = n.action || 'list';
    switch (t) {
      case 'list': return 'Liste (' + (Array.isArray(n.liste) ? n.liste.length : 0) + ')';
      case 'dialogue': return 'Dialogue';
      case 'quest': return 'Quête ' + E.plain(ctx.questName(n.quest) || n.quest || '?') + (n.mode === 'step' ? ' → étape ' + (E.num(n.step, 0) + 1) : ' : ' + E.str(n.mode));
      case 'message': return E.truncate(E.plain(n.text), 40);
      case 'wait': return 'Attendre ' + E.str(n.time, 0) + ' ticks';
      case 'variable': return E.str(n.variable) + ' ' + E.str(n.operator, 'set');
      case 'function': return 'ƒ ' + E.str(n.function);
      case 'command': return '/' + E.truncate(n.command, 30);
      case 'give': return 'Donner ' + (Array.isArray(n.items) ? n.items.length : 0) + ' objet(s)';
      case 'money': return E.str(n.operator, 'add') + ' ' + E.str(n.value) + ' ₪';
      default: return E.actionInfo(t).label;
    }
  }

  function actionBody(n, type, ctx, body, rows, edges, from, exists, aid) {
    const line = (...c) => body.push(h('div', { class: 'kv' }, ...c));
    switch (type) {
      case 'quest': {
        const mode = E.str(n.mode, '?');
        line(n.quest ? chip('quest', n.quest, ctx) : h('i', { class: 'dim' }, 'quête non choisie'));
        const detail = { new: 'débloque la quête', step: 'place à l\'étape ' + (E.num(n.step, 0) + 1), substep: 'objectif ' + (E.num(n.substep, 0) + 1) + ' de l\'étape ' + (E.num(n.step, 0) + 1) + ' ' + E.str(n.operator, 'add') + ' ' + E.str(n.value, 1),
          variable: 'variable « ' + E.str(n.variable) + ' » ' + E.str(n.operator, 'set') + ' ' + E.str(n.value) }[mode] || mode;
        line(h('span', { class: 'dim' }, detail));
        break;
      }
      case 'message': line(E.mc(E.truncate(n.text, 140))); break;
      case 'title': line(E.mc(E.truncate(n.title, 60)), h('div', { class: 'dim' }, E.mc(E.truncate(n.subtitle, 60)))); break;
      case 'give': line((Array.isArray(n.items) ? n.items : []).map(i => E.itemLabel(i)).join(', ') || '(aucun objet)'); break;
      case 'money': line(E.str(n.operator, 'add') + ' ' + E.str(n.value, 0) + ' ₪'); break;
      case 'experience': line(E.str(n.operator, 'add') + ' ' + E.str(n.value, 0) + (n.mode ? ' points' : ' niveaux')); break;
      case 'damage': line(E.str(n.value, 0) + ' dégâts'); break;
      case 'variable': line(E.truncate(n.variable, 40), ' ' + E.str(n.operator, 'set') + ' ', E.truncate(E.str(n.value), 30)); break;
      case 'function': line('ƒ ' + E.str(n.function)); break;
      case 'command': line('/' + E.truncate(n.command, 60), h('div', { class: 'dim' }, 'en tant que ' + (n.as === 'player' ? 'joueur' : 'console'))); break;
      case 'wait': line(E.str(n.time, 0) + ' ticks (' + (E.num(n.time, 0) / 20).toFixed(1) + ' s)'); break;
      case 'sound': line(E.str(n.sound, 'ui.button.click')); break;
      case 'teleport': line(E.str(n.world) + ' ' + [n.x, n.y, n.z].map(v => Math.round(E.num(v, 0))).join(' ')); break;
      case 'trade': line('échange « ' + E.str(n.trade) + ' »'); break;
      case 'move': {
        line(n.npc ? ['NPC ', chip('npc', n.npc, ctx)] : 'ce NPC', n.destination === 'location' ? ' → ' + [n.x, n.y, n.z].map(v => Math.round(E.num(v, 0))).join(' ') : ' → position ' + E.str(n.position, 1), h('div', { class: 'dim' }, E.str(n.mode, 'instant') + (n.destination === 'location' && (n.real === true || n.real === 1) ? ' · réel' : '')));
        ['on_stop', 'on_continue'].forEach(k => {
          (Array.isArray(n[k]) ? n[k] : []).forEach((c, ci) => {
            const p = k + ci;
            rows.push(row(p, h('span', { class: 'ico' }, k === 'on_stop' ? '⏹' : '▶'), h('span', { class: exists(c) ? 'rtxt' : 'rtxt bad' }, (k === 'on_stop' ? 'à l\'arrêt : ' : 'à la reprise : ') + (exists(c) ? E.truncate(E.plain(c), 10) : '⚠ introuvable')), h('span', { class: 'dot' })));
            if (exists(c)) edges.push({ id: 'm:' + from + ':' + p, from, port: p, to: aid(c), cls: 'action' });
          });
        });
        break;
      }
      default: line(h('span', { class: 'dim' }, 'type « ' + type + ' » (champs en JSON)'));
    }
  }

  E.targetExists = targetExists;
  /** Titre court d'une action pour les listes déroulantes. */
  E.nodeTitle = function (script, id, ctx) {
    const n = script[id];
    if (!n) return '⚠ ' + id;
    return E.actionInfo(n.action || 'list').icon + ' ' + shortSummary(n, ctx) + '  [' + id.slice(0, 4) + ']';
  };
})();
