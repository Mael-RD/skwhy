/* Opérations sur l'arbre d'une quête ou d'un NPC (ajout / suppression / renommage avec mise à jour des références). Sans DOM. */
(function (root) {
  'use strict';
  const E = root.E = root.E || {};
  const isObj = v => v !== null && typeof v === 'object' && !Array.isArray(v);
  const isCond = n => isObj(n) && n.condition !== undefined && n.action === undefined;
  const isAct = n => isObj(n) && !isCond(n);
  const uuid = () => (root.crypto && root.crypto.randomUUID) ? root.crypto.randomUUID() :
    'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => { const r = Math.random() * 16 | 0; return (c === 'x' ? r : (r & 3 | 8)).toString(16); });

  const Ops = {};

  Ops.script = function (tree) {
    if (!isObj(tree.script)) tree.script = {};
    return tree.script;
  };

  /** Valeurs par défaut d'une nouvelle action. */
  Ops.defaults = function (type) {
    switch (type) {
      case 'list': return { action: 'list', mode: 'list', liste: [] };
      case 'dialogue': return { action: 'dialogue', dialogues: { start: { on_start: 'start', texts: [{ answer_mode: 1, time: 120, distance: 15, text: 'Bonjour.' }] } } };
      case 'quest': return { action: 'quest', mode: 'step', quest: '', step: 0 };
      case 'message': return { action: 'message', text: '' };
      case 'title': return { action: 'title', title: '', subtitle: '' };
      case 'give': return { action: 'give', items: [] };
      case 'money': return { action: 'money', operator: 'add', value: 10 };
      case 'experience': return { action: 'experience', operator: 'add', value: 1, mode: false };
      case 'damage': return { action: 'damage', value: 1, mode: false };
      case 'trade': return { action: 'trade', trade: '' };
      case 'variable': return { action: 'variable', variable: '', operator: 'set', value: 1 };
      case 'function': return { action: 'function', function: '' };
      case 'command': return { action: 'command', command: '', as: 'console' };
      case 'wait': return { action: 'wait', time: 20 };
      case 'move': return { action: 'move', position: 1, mode: 'instant', speed: 2, rules: '' };
      case 'sound': return { action: 'sound', sound: 'ui.button.click', volume: 1, pitch: 1 };
      case 'teleport': return { action: 'teleport', world: '', x: 0, y: 64, z: 0, yaw: 0, pitch: 0 };
      default: return { action: type };
    }
  };

  Ops.addNode = function (tree, type, data) {
    const script = Ops.script(tree);
    const id = uuid();
    script[id] = data || Ops.defaults(type);
    return id;
  };

  Ops.addCondition = function (tree, questId) {
    const script = Ops.script(tree);
    const id = uuid();
    script[id] = {
      condition: 'compare', operator: 'equal', reverse: false,
      value1: questId ? { type: 'quest', quest: questId } : { type: 'level' },
      value2: { type: 'value', value: 0 }
    };
    return id;
  };

  /** Où un nœud est utilisé : liste de descriptions (pour prévenir avant de supprimer). */
  Ops.refsTo = function (tree, id) {
    const out = [];
    const script = isObj(tree.script) ? tree.script : {};
    for (const nid of Object.keys(script)) {
      const n = script[nid];
      if (!isObj(n) || nid === id) continue;
      if (Array.isArray(n.liste) && n.liste.includes(id)) out.push({ kind: 'list', node: nid });
      if (n.condition === id && isAct(n)) out.push({ kind: 'guard', node: nid });
      for (const k of ['on_stop', 'on_continue']) if (Array.isArray(n[k]) && n[k].includes(id)) out.push({ kind: k, node: nid });
      if (isObj(n.dialogues)) {
        for (const sn of Object.keys(n.dialogues)) {
          const texts = Array.isArray(n.dialogues[sn].texts) ? n.dialogues[sn].texts : [];
          texts.forEach((t, ti) => {
            if (t.action === id) out.push({ kind: 'text-action', node: nid, state: sn, text: ti });
            if (t.condition === id) out.push({ kind: 'text-cond', node: nid, state: sn, text: ti });
            (Array.isArray(t.answers) ? t.answers : []).forEach((a, ai) => {
              if (a.action === id) out.push({ kind: 'answer-action', node: nid, state: sn, text: ti, answer: ai });
              if (a.condition === id) out.push({ kind: 'answer-cond', node: nid, state: sn, text: ti, answer: ai });
            });
          });
        }
      }
    }
    (Array.isArray(tree.steps) ? tree.steps : []).forEach((s, i) => { if (s.condition === id) out.push({ kind: 'step-cond', step: i }); });
    return out;
  };

  /** Supprime un nœud et toutes les références vers lui. */
  Ops.removeNode = function (tree, id) {
    const script = Ops.script(tree);
    delete script[id];
    for (const nid of Object.keys(script)) {
      const n = script[nid];
      if (!isObj(n)) continue;
      if (Array.isArray(n.liste)) n.liste = n.liste.filter(c => c !== id);
      if (isAct(n) && n.condition === id) delete n.condition;
      for (const k of ['on_stop', 'on_continue']) if (Array.isArray(n[k])) n[k] = n[k].filter(c => c !== id);
      if (isObj(n.dialogues)) {
        for (const sn of Object.keys(n.dialogues)) {
          for (const t of (Array.isArray(n.dialogues[sn].texts) ? n.dialogues[sn].texts : [])) {
            if (t.action === id) delete t.action;
            if (t.condition === id) delete t.condition;
            for (const a of (Array.isArray(t.answers) ? t.answers : [])) {
              if (a.action === id) delete a.action;
              if (a.condition === id) delete a.condition;
              if (a.target_dialogue === id) { delete a.target_dialogue; delete a.target; }
            }
          }
        }
      }
    }
    (Array.isArray(tree.steps) ? tree.steps : []).forEach(s => { if (s.condition === id) delete s.condition; });
  };

  Ops.duplicateNode = function (tree, id) {
    const script = Ops.script(tree);
    const copy = JSON.parse(JSON.stringify(script[id]));
    const nid = uuid();
    script[nid] = copy;
    return nid;
  };

  // ── États de dialogue ────────────────────────────────────────────────
  Ops.uniqueStateName = function (dialogues, base) {
    let name = base || 'nouveau', k = 2;
    while (dialogues[name]) name = (base || 'nouveau') + '-' + k++;
    return name;
  };

  Ops.addState = function (tree, aid, name, texts) {
    const node = Ops.script(tree)[aid];
    if (!isObj(node.dialogues)) node.dialogues = {};
    const n = Ops.uniqueStateName(node.dialogues, name);
    node.dialogues[n] = { on_start: n, texts: texts || [{ answer_mode: 1, time: 120, distance: 15, text: '' }] };
    return n;
  };

  Ops.renameState = function (tree, aid, oldName, newName) {
    const script = Ops.script(tree);
    const node = script[aid];
    if (!node || !node.dialogues || !node.dialogues[oldName] || oldName === newName || !newName || node.dialogues[newName]) return false;
    const rebuilt = {};
    for (const k of Object.keys(node.dialogues)) rebuilt[k === oldName ? newName : k] = node.dialogues[k];
    node.dialogues = rebuilt;
    // références : dans ce dialogue puis dans ceux qui visent ce dialogue avec target_dialogue
    for (const nid of Object.keys(script)) {
      const n = script[nid];
      if (!isObj(n) || !isObj(n.dialogues)) continue;
      for (const sn of Object.keys(n.dialogues)) {
        const st = n.dialogues[sn];
        if (nid === aid && st.on_start === oldName) st.on_start = newName;
        for (const t of (Array.isArray(st.texts) ? st.texts : [])) {
          if (nid === aid && t.next === oldName) t.next = newName;
          for (const a of (Array.isArray(t.answers) ? t.answers : [])) {
            const targetDialogue = a.target_dialogue || nid;
            if (targetDialogue === aid && a.target === oldName) a.target = newName;
          }
        }
      }
    }
    return true;
  };

  Ops.removeState = function (tree, aid, name) {
    const script = Ops.script(tree);
    const node = script[aid];
    if (!node || !node.dialogues) return;
    delete node.dialogues[name];
    for (const nid of Object.keys(script)) {
      const n = script[nid];
      if (!isObj(n) || !isObj(n.dialogues)) continue;
      for (const sn of Object.keys(n.dialogues)) {
        const st = n.dialogues[sn];
        if (nid === aid && st.on_start === name) st.on_start = sn;
        for (const t of (Array.isArray(st.texts) ? st.texts : [])) {
          if (nid === aid && t.next === name) delete t.next;
          for (const a of (Array.isArray(t.answers) ? t.answers : [])) {
            if ((a.target_dialogue || nid) === aid && a.target === name) { delete a.target; delete a.target_dialogue; }
          }
        }
      }
    }
  };

  // ── Étapes d'une quête ───────────────────────────────────────────────
  function remapNext(steps, f) {
    steps.forEach(s => { if (s.next !== undefined && typeof s.next === 'number') s.next = f(s.next); });
  }

  Ops.insertStep = function (tree, at, step) {
    if (!Array.isArray(tree.steps)) tree.steps = [];
    at = Math.max(0, Math.min(tree.steps.length, at));
    remapNext(tree.steps, n => n >= at ? n + 1 : n);
    tree.steps.splice(at, 0, step || { text: '', objectives: [{ type: 'externe', count: 1, text: '' }] });
    return at;
  };

  Ops.removeStep = function (tree, i) {
    const steps = tree.steps;
    if (!Array.isArray(steps) || i < 0 || i >= steps.length) return;
    const removed = steps[i];
    const after = removed.next === undefined ? i + 1 : removed.next;
    steps.splice(i, 1);
    remapNext(steps, n => {
      if (n === i) return after > i ? after - 1 : after;
      return n > i ? n - 1 : n;
    });
  };

  Ops.moveStep = function (tree, from, to) {
    const steps = tree.steps;
    if (!Array.isArray(steps) || from === to || to < 0 || to >= steps.length) return;
    const perm = idx => {
      if (idx === from) return to;
      if (from < to && idx > from && idx <= to) return idx - 1;
      if (from > to && idx >= to && idx < from) return idx + 1;
      return idx;
    };
    remapNext(steps, perm);
    const [s] = steps.splice(from, 1);
    steps.splice(to, 0, s);
  };

  Ops.parseValue = function (text) {
    if (typeof text !== 'string') return text;
    const t = text.trim();
    if (t === 'true') return true;
    if (t === 'false') return false;
    if (t !== '' && /^-?\d+$/.test(t)) return parseInt(t, 10);
    if (t !== '' && /^-?\d*\.\d+$/.test(t)) return parseFloat(t);
    return text;
  };

  // ── Indicateurs de quête ──────────────────────────────────────────────
  // Index du jeu : 0 = ? rouge, 1 = ! orange (principale), 2 = ! cyan (secondaire), 3 = 🗡 (contrée), 4 = 🛈 (info).
  // Nouvelle quête : « ? » rouge (0). Progression : « ! » de la couleur du type de la quête (1 principale, 2 secondaire, 3 contrée).
  Ops.INDICATOR_DEFAULTS = {
    new: { principale: 0, secondaire: 0, 'contrée': 0 },
    progress: { principale: 1, secondaire: 2, 'contrée': 3 }
  };

  /** Type de quête normalisé : principale, secondaire ou contrée. */
  Ops.questKind = function (type) {
    const t = String(type || 'principale').toLowerCase();
    if (t.indexOf('second') === 0) return 'secondaire';
    if (t === 'contrée' || t === 'contree' || t === 'region' || t === 'région' || t === 'country') return 'contrée';
    return 'principale';
  };

  /** Actions que `n` peut jouer ensuite (liste, réponses et textes de dialogue, arrêt / reprise d'un déplacement). */
  Ops.actionRefs = function (n) {
    const out = new Set();
    if (!isObj(n)) return [];
    if (Array.isArray(n.liste)) n.liste.forEach(c => out.add(c));
    for (const k of ['on_stop', 'on_continue']) if (Array.isArray(n[k])) n[k].forEach(c => out.add(c));
    if (isObj(n.dialogues)) {
      for (const sn of Object.keys(n.dialogues)) {
        const st = n.dialogues[sn];
        for (const t of (st && Array.isArray(st.texts) ? st.texts : [])) {
          if (typeof t.action === 'string') out.add(t.action);
          for (const a of (Array.isArray(t.answers) ? t.answers : [])) {
            if (typeof a.action === 'string') out.add(a.action);
            if (typeof a.target_dialogue === 'string') out.add(a.target_dialogue);
          }
        }
      }
    }
    return [...out];
  };

  const isQuestEvolution = n => isObj(n) && n.action === 'quest' && (n.mode === 'new' || n.mode === 'step' || n.mode === 'substep');

  /**
   * Pose un indicateur sur chaque action que le NPC affiche (premier nœud non-liste atteint depuis `main`) et qui mène à
   * une évolution de quête : quête nouvelle = « ? » rouge, quête qui avance = « ! » du type de la quête.
   * Ne retire ni n'écrase aucun indicateur déjà présent.
   * @param questTypeOf  id de quête -> type (principale / secondaire / contrée)
   * @param cfg  {new: {type: index}, progress: {type: index}} (défaut : INDICATOR_DEFAULTS)
   * @return {added, kept} nombre d'indicateurs posés / d'actions qui en avaient déjà un
   */
  Ops.generateIndicators = function (tree, questTypeOf, cfg) {
    const script = isObj(tree.script) ? tree.script : {};
    cfg = cfg || Ops.INDICATOR_DEFAULTS;
    const res = { added: 0, kept: 0 };
    const candidates = [];
    const seen = new Set();
    const queue = isAct(script.main) ? ['main'] : [];
    while (queue.length) {
      const id = queue.shift();
      if (seen.has(id) || !isAct(script[id])) continue;
      seen.add(id);
      if ((script[id].action || 'list') === 'list') queue.push(...(Array.isArray(script[id].liste) ? script[id].liste : []));
      else candidates.push(id);
    }
    const rank = { principale: 3, secondaire: 2, 'contrée': 1 };
    for (const id of candidates) {
      if (script[id].indicator !== undefined) { res.kept++; continue; }
      let best = null;
      const visited = new Set();
      const stack = [id];
      while (stack.length) {
        const cur = stack.pop();
        if (visited.has(cur) || !isAct(script[cur])) continue;
        visited.add(cur);
        const n = script[cur];
        if (isQuestEvolution(n)) {
          const kind = Ops.questKind(questTypeOf ? questTypeOf(n.quest) : null);
          const isNew = n.mode === 'new';
          const score = (isNew ? 10 : 0) + rank[kind];
          if (!best || score > best.score) best = { score, v: (isNew ? cfg.new : cfg.progress)[kind] };
        }
        stack.push(...Ops.actionRefs(n));
      }
      if (best && Number.isInteger(best.v) && best.v >= 0) { script[id].indicator = best.v; res.added++; }
    }
    return res;
  };

  /** Retire tous les indicateurs du script. @return le nombre d'indicateurs retirés */
  Ops.clearIndicators = function (tree) {
    const script = isObj(tree.script) ? tree.script : {};
    let n = 0;
    for (const id of Object.keys(script)) if (isObj(script[id]) && script[id].indicator !== undefined) { delete script[id].indicator; n++; }
    return n;
  };

  Ops.isCond = isCond;
  Ops.isAct = isAct;
  E.Ops = Ops;
  if (typeof module !== 'undefined' && module.exports) module.exports = Ops;
})(typeof window !== 'undefined' ? window : globalThis);
