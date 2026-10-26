# SkWhy - consignes de travail

Addon Skript (Java, Gradle) : modules `FakeDisplayElements`, `NavigationElements`, `LongNavigationElements`/`ChunkPath`,
`ApiElements`, `RandomStuff`. Code dans `app/src/main/java/skwhy`.

## Serveur de test (serveur vide) et RCON
- Serveur : `C:\Users\orimi\Desktop\proxti\serveur vide` (Purpur, Skript 2.16, packetevents, NuVotifier, SkriptHubDocsTool).
  Ne PAS toucher au serveur complet (`...\proxti\full copy`) pour les tests.
- **RCON est activee** sur le serveur vide (`enable-rcon=true`, port 25575, mot de passe dans `server.properties`).
  Toujours piloter le serveur via RCON (`sk reload ...`, `stop`, commandes) au lieu de le redemarrer a la main.
  Le client RCON et le runner sont dans `serveur vide\plugins\Skript\scripts\skwhy-tests\` (`rcon.py`, `run_tests.py`).
- Demarrer/arreter : `python run_tests.py start|stop|restart` (dans ce dossier). Un redemarrage n'est necessaire que pour
  recharger le jar SkWhy (`./gradlew copyJarToPlugins` copie le jar dans `serveur vide\plugins`, puis `run_tests.py restart`).

## Tests (obligatoire)
Les tests de SkWhy sont dans `C:\Users\orimi\Desktop\proxti\serveur vide\plugins\Skript\scripts\skwhy-tests\`
(voir son `README.md`). Ils jouent le role de tests unitaires et sont tries par **version / module / type de syntaxe**
(`v1.4.0/random-stuff/sections.sk`...).

**A chaque nouvelle syntaxe, modification de syntaxe (pattern, type, changer, comportement) ou correction de bug :**
1. Ajouter/mettre a jour les tests dans le bon dossier `v<version de @Since>/<module>/<type>.sk` (nouvelle version = nouveau dossier).
   Couvrir : cas nominal, chaque pattern/alias, set/add/remove/delete/reset si supportes, valeurs limites, entrees invalides
   (`*-errors.sk` avec `# skt-expect-errors: N`), absence de joueur (liste vide) et cas ou la valeur est non definie.
   Un event se teste en le declenchant (Skript, ou `external_tests.py` pour HTTP/Votifier/...). Si un joueur est
   indispensable, ecrire le test dans un `players.sk` : `set {_p} to random element of all players` (ou `all players` pour une liste),
   avec `skt_skip` si personne n'est en ligne, et placer ce qui est visible (blocs, entites) par rapport au joueur.
2. Documenter la syntaxe (description, exemples, since, event-values) - les events aussi : `addDescription/addExamples/addSince`
   dans l'enregistrement `BukkitSyntaxInfos.Event` (voir `RandomStuffModule`, `APIModule`).
3. Reconstruire le jar (`./gradlew copyJarToPlugins`), `python run_tests.py restart`, lancer `/gendocs` (via RCON : `gendocs`),
   puis `python run_tests.py gen-docs-examples` : chaque exemple de la doc est parse (un exemple de doc invalide = echec).
4. `python run_tests.py coverage` doit afficher 100 % des syntaxes referencees ; `python run_tests.py run` doit etre vert.
   Un bug connu reste dans les tests (commentaire `# BUG connu`) tant qu'il n'est pas corrige.
5. Rapport des erreurs : `serveur vide\...\skwhy-tests\last-run.md`.

## Tests JUnit (sans serveur)
`./gradlew :app:test` (aucun serveur) : logique pure du plugin dans `app/src/test/java/skwhy` (`data` Vec3/Quat4/GlobalTransformation, `chunkpath` NBT, portes,
`ChunkGateStore`, `RegionChunkLoader`, maillage `ChunkNavMesh`, A*/`BinaryHeap`/`Node`, `BlockClassifier`, `PathCostRules`, `quest`, `util`).
`TestChunks` (`chunkpath/testsupport`) fabrique des chunks NBT en memoire et demarre MockBukkit (Material/Tag). Skript, lui, ne se charge pas dans MockBukkit
(`Skript` est `final`) : les syntaxes restent testees par les `.sk` du serveur vide. Nouvelle logique pure => test JUnit en plus.

## Doc des addons (SkriptHubDocsTool)
- Plugin installe : version 1.17 **patchee** (sources : `C:\Users\orimi\Desktop\proxti\SkriptHubDocsTool-src`). Le patch rattache
  aux addons les events enregistres via l'API moderne (`SimpleEvent`) en comparant le nom de l'addon ; sans lui les events
  n'apparaissent pas dans `plugins/SkriptHubDocsTool/documentation/SkWhy.json`. Ne pas remplacer par le jar officiel.
- Generer la doc : RCON `gendocs` sur le serveur vide (le jar SkWhy doit etre a jour).
- Les events doivent etre enregistres avec `BukkitSyntaxInfos.Event` + `addDescription/addExamples/addSince`
  (+ `addRequiredPlugins`, `addKeywords`) ; les patterns d'event ne doivent PAS commencer par `[on]` (Skript l'ajoute).

## Regles de code / doc apprises (a respecter)
- **Texte avec couleurs** : ne jamais lire `&a` a la main. Convertir toute chaine Skript avec `skwhy.util.TextComponents.fromSkript(...)`
  (`TextComponentParser` de Skript = meme filtre MiniMessage que `send` / noms d'items : `<red>`, `<#ff8800>`, `<bold>`, `&a`). Les scripts
  n'ont pas a ecrire `formatted`. Docs et exemples utilisent les balises `<green>`, pas `&a`. (Corrige sur `release/1.4.0` ; a recuperer par le merge dans `main`.)
- **Changers (`acceptChange`)** : accepter `Player.class, Player[].class` (ou `X[]`) pour pouvoir `set/add/remove` une liste ; `REMOVE_ALL` doit
  declarer les memes classes que `REMOVE` (sinon `ArrayIndexOutOfBounds` au chargement) ; pour « vider », utiliser `DELETE`/`RESET` avec `new Class<?>[0]`.
  Ne jamais s'appuyer sur une expression de pattern optionnelle (NPE) dans `change`.
- **`%objects%`** : passer par `LiteralUtils.defendExpression` + `canInitSafely` (sinon `UnparsedLiterals must be converted before use` avec `145`).
- **Cles de section** (`mode: head`, `alignment: center`, `head: <base64>`) : repli sur une chaine litterale quand le parse echoue (mots non quotes).
- **Un display retire d'un groupe** doit liberer sa `GlobalTransformation` (`releaseGlobalTransformation`) pour pouvoir etre re-ajoute.
- Valeurs entieres (cout d'une path cost rule) : arrondir (`Math.round`) et le dire dans la doc.
- **Exemples de doc** : chaque `@Examples` doit parser (verifie par `docs-examples/`). Pas de `[a new fake item display]` entre crochets,
  utiliser les vrais patterns (`fake display scale of`, pas `scale of`), pas de virgule parasite apres un `vector(...)`.
- `split %string% at regex %string%` **conserve les separateurs** (liste `[texte, separateur, texte, ...]`, 2n+1 elements) : comportement voulu, documente.
- Les events : `BukkitSyntaxInfos.Event` + doc (voir plus haut), event-values citees dans la description, pas de `[on]` dans le pattern.

## Long pathfinding (regles apprises)
- Les chunks sont lus **depuis les fichiers region sur disque** (`ChunkNavMesh`) : format NBT >= 1.16 = les entrees ne chevauchent jamais deux longs.
  Les bases `*.db` de format 1 (ancien decodage faux) sont sauvegardees en `.v1.bak` et repartent vides (a ré-enregistrer).
- Dossier region = `world.getWorldFolder()/region` (un monde par dossier : `villes`, `event`...), pas seulement `overworld`.
- Une `LongNavigation` est **en pause par defaut** : `set pause ticks ... to 0` pour qu'elle marche. `speed` = blocs par tick.
- Ne jamais lire un bloc d'un chunk non charge pendant la recherche A* (`NodeEvaluator.isColumnLoaded`) : Paper le charge/genere en synchrone.
- Dans une fonction Skript un `wait` ne retarde pas l'appelant : les tests de marche sont dans le trigger (`walk.sk`).
- Une entite reelle pilotee perd son IA/gravite vanilla (rendues a l'arrivee, a l'echec ou au `destroy`). `world.getGameTime()` (pas `getFullTime()`) pour les delais.
- **Portes** : `can open doors of long navigation` (booleen, vrai par defaut, `LongNavigationDoors`). `DoorOpener` (dans `LongMob`) ouvre une porte
  (toutes sauf `iron_door`, les deux moities) quand elle est dans les 2 prochains noeuds du chemin a < 2 blocs, et la referme apres le passage / au `destroy`.
  Portillons et trappes ne sont jamais ouverts (type de chemin bloque => detour). L'enregistreur (`BlockClassifier`) garde les portes traversables.
- **Teleportations forcees** (`teleportMob`, log WARN `téléportation forcée`, compteur `forced teleports of long navigation`) = dernier recours : un trajet sain n'en
  a jamais besoin, les tests (`terrain.sk`, `doors.sk`, `resume.sk`) verifient `0`. Causes trouvees : portes fermees, plaques de pression (`Material.isSolid()` vrai
  alors que sans collision), `getFloorLevel` plafonne a 1.0 (barrieres/murets 1.5 vus comme marche) et `getBoundingBox()` au lieu de la forme de collision,
  `Collide.collectColliders` sans marge d'1 bloc (barriere enracinee dans la cellule du dessous ignoree => mob bloque, pas de marche possible). Toujours
  utiliser `getCollisionShape()` (jamais `getBoundingBox()`) pour le sol/les collisions.
- **Pause** : `set pause ticks ... to -1`/`N` rend l'entite a son IA (gravite) ; a la reprise `LongNavigation.onResume` reverifie la position : sur le chemin => continue,
  a moins de 16 blocs => petit A* pour rejoindre le noeud le plus proche puis reprend le chemin, plus loin => `recomputeRoute`.
- Un waypoint (bloc de porte enregistre) devenu inaccessible (mur construit depuis) est saute : fin de jambe partielle a < 3 blocs, ou `no progress` => waypoint
  suivant, la teleportation n'arrive qu'ensuite.
- Tests : plateformes dans le ciel (y=199) construites par `execute in minecraft:overworld run fill ...` puis `save-all flush` AVANT `register chunk path` (le terrain est lu
  sur disque). `fill` est limite a **32768 blocs** : au-dela la commande echoue en silence (murs/obstacles restes d'un test precedent).
- **Waypoints** : `RouteWaypoints` remplace les representants de portes (une porte = un amas de blocs de bord, parfois a des hauteurs tres differentes) par des paires de
  cellules **adjacentes de part et d'autre de la frontiere**, les plus proches de la position courante.
- **Liens perimes** : `ChunkGateService.reanalyze` purge chez les voisins les `neighborLinks` vers le chunk re-analyse (ids renumerotes => routes impossibles, ex. descente a y=-62).
  Test : `v1.5.0/chunk-path/relink.sk`. Apres mise a jour du jar, re-enregistrer les chunks deja enregistres.
- **Geometrie du corps** (`CostRuleWalkNodeEvaluator.hasRoomToStand` / `stepClear`, interrupteur `/cpdebug geometry on|off`) : un noeud doit laisser la place au volume du mob
  (trappes-meubles, etageres, tapis + linteau) et le deplacement entre deux noeuds ne doit rencontrer aucune forme de collision (hors marche <= 0.6). Descente : le corps garde sa hauteur
  tant qu'il n'a pas quitte la corniche. Restent des cas decoratifs impossibles (villageois 1.95 sur un tapis devant une porte de 2) => teleportation de dernier recours.
- Build : si un autre travail en cours (ex. `skwhy/quest`) casse la compilation, construire une copie sans ce dossier ; le serveur full utilise le jar copie a la main dans `plugins/skwhy-1.5.0.jar`.
- Debug : commande `/cpdebug` (console) `cell|path|navs|dump|shape|collide|folder|verbose [off]`. `verbose` journalise chaque transition d'etat.
- Serveur full (`full copy`, monde `villes`) : demarrage ~5 min ; ne pas le redemarrer sans necessite. Sondes (commandes lpgo/lpst...) dans `plugins/Skript/scripts/lp-debug/-probe.sk` (desactivees : retirer le `-` + `sk reload`).

## Long pathfinding : trajet fin (apres le choix des portes)
Rien de ce qui suit ne touche au graphe de portes (`ChunkNavMesh`, `GatePathfinder`) : tout agit **apres** que la liste des portes est decidee.
- **Reglages a chaud** : `/cpdebug set <cle> <valeur>` / `/cpdebug get` (RCON), classe `ChunkPathTuning` : `localscale`, `edgeweight`, `edgedist`, `smooth`, `smoothspan`, `crosscost`,
  `crossmargin`, `goalweight`, `lineweight`, `lookahead`... Pas de redemarrage pour les comparer. L'etat persiste entre deux runs : toujours le fixer completement avant une comparaison.
- **Petite echelle** (`CostRuleWalkNodeEvaluator.costFor`) : ouverture morphologique du champ de couts (erosion = minimum sur la fenetre, puis dilatation = maximum des valeurs erodees) :
  une tache plus chere que son entourage et plus etroite que la fenetre (herbe dans un chemin) est ignoree ; une zone large garde son cout. Taille = propriete Skript
  `cost smoothing of long navigation` (defaut 4 = `ChunkPathTuning.localScale`, 0 = couts bloc par bloc, `LongMob.costSmoothing`). Seuls les couts positifs sont lisses : un bloc
  non marchable (`-1`) le reste.
- **Centrage** (`edgePenalty`) : surcout (`edgeweight`, decroit sur `edgedist` blocs) pour une case a cote d'un mur, d'un vide ou d'un sol deux fois plus cher.
- **Lissage du trajet** (`CostRuleWalkNodeEvaluator.smooth`, appele par `LongPathFinder.reconstructPath`) : « string pulling » : un noeud saute si le mob peut marcher droit
  (meme niveau, cases `WALKABLE`, `stepClear`) pour un cout <= celui des noeuds sautes ; les noeuds non plain-pied (portes...) sont gardes.
- **Franchissement des bordures** (`RouteWaypoints.refine`, appele par `LongNavigation.refineCrossing` avant chaque jambe) : la case de passage est rechoisie une fois, avec les couts
  reels du sol (`crosscost`), une marge laterale (`crossmargin` x blocs manquants sur 0..3 : passer au milieu d'un chemin) et l'attrait de l'arrivee / de la droite depart->arrivee
  (`goalweight`, `lineweight`). Les waypoints initiaux (`build`) ne regardent que les distances (terrain pas encore charge) : sans `refine` ils tombaient sur des cases a 200.
- **Mesurer** : `/cpdebug set trace on` ecrit `plugins/SkWhy/traces/nav-*.csv`, `/cpdebug map <monde> x1 z1 x2 z2 y` ecrit `map.txt` + `map-stats.txt`. Scenarios reproductibles sur plateforme :
  `serveur vide/plugins/Skript/scripts/lp-debug/vrules.sk` (`/vplat`, `/vgw`, regles de l'utilisateur) + `lp-debug/scenarios.py` (`build|run|all|matrix <reglages>` : plateforme par `fill`, `save-all flush`, `register chunk path`, marche,
  mesure de l'ecart a la droite / virages / marge ; terrain construit une fois par scenario, plusieurs reglages compares ensuite). Le monde `villes` est copie dans le serveur vide (`load world "villes"` dans `vrules.sk`,
  `villes.db` copie aussi) ; le premier trajet apres un redemarrage echoue tant que `{-rules::*}` n'est pas charge (`/vrules2`).
- Tests : `v1.5.0/long-navigation/cost-smoothing.sk` (+ `-errors.sk`), JUnit `RouteWaypointsTest` (`refine`), `PathCostRulesTest` (`costOf(Material)`).

## Long navigation : chemin d'un joueur / `blocks of long navigation` (since 1.5.0)
- Une navigation dont l'entite est un **joueur** (`LongNavigation.isObserver()`) n'est **jamais** pilotee : `tick()` sort tout de suite, pas de gravite coupee, pas de teleportation (`set current location` ignore).
  Elle ne sert qu'a `next %integer% blocks of long navigation` / `all blocks of long navigation` (liste de `Location`, centre du bloc des pieds, la plus proche d'abord). L'expression marche aussi sur toute autre navigation
  (meme en pause) : c'est ce qui permet de la tester sans joueur (`path-blocks.sk`, navigation par id).
- **L'apercu rejoue les decisions de la marche** (`LongNavigation.previewLeg`, sans toucher a `tickFollowing`) depuis une position virtuelle (fin de la jambe precedente) : waypoints deja atteints (`RouteWaypoints.isReached` = `hasReached`),
  `refine` du passage sur une **copie** des waypoints (`RouteWaypoints.copy`, remise a zero a chaque `restart`), `previewShortcut` (= `tryShortcut`/`tryThroughLeg`), `previewMoveTo` (sortie tolerante + `snapExit`), cases coupees des que le waypoint est « atteint ».
  Toute modification de `tickFollowing`/`tryShortcut`/`moveToWaypoint`/`hasReached` doit etre repercutee dans ces copies. Test : `v1.5.0/long-navigation/path-blocks-walk.sk` (apercu vs villageois qui marche).
- **Cout des deniveles** (`CostRuleWalkNodeEvaluator.stepCost`, ajoute par `LongPathFinder` au `g` de chaque deplacement) : monter/descendre d'**au moins un bloc entier** (ecart de niveau de pieds > 0.5) ajoute le plus cher des deux sols, lu dans les regles **brutes** (jamais lisse : l'ouverture morphologique ne doit pas rendre gratuit le pas sur un bloc cher isole) ; <= 0.5 (dalles, escaliers a l'endroit) = gratuit. Ne touche pas au graphe de portes. Test : `v1.5.0/long-navigation/step-cost.sk`.
- **Retour sur le chemin de l'apercu** (`PathPreview.rejoin`) : on essaie jusqu'a 3 cases gardees (la plus proche, une intermediaire, la plus loin a <= 16 blocs) et on garde celle dont `cout du retour + cout restant du chemin garde` est le plus bas (une case plus avancee gagne si elle coute au plus 5 % de plus, `ADVANCE_BIAS`). Les couts viennent du `g` des noeuds A* (`LongNavigation.pathCells`).
- **Passage de bordure decide de pres** (`CROSSING_DECISION_RANGE` = 16 blocs, marche reelle ET apercu) : un franchissement de chunk non encore decide (ni `refine` ni `snapExit`) a plus de 16 blocs n'est pas fige : la jambe qui le vise est **coupee** des qu'elle passe a <= 16 blocs (`cutWaypoint`/`cutBase` dans la marche, `Leg.next = index` dans l'apercu) et une nouvelle jambe repart de la, avec `refine` depuis cette position. Un raccourci (`tryShortcut`/`previewShortcut`) affine aussi le waypoint vise s'il est proche. Sans cela le crochet de 2 blocs (ex. x=13 puis x=11) venait du choix brut fait de loin. Test : `v1.5.0/long-navigation/path-crossing.sk`, `RouteWaypointsTest` (`isSettled`).
- Calcul a la demande, cache dans `PathPreview` (logique pure, `PathPreviewTest`) : la case courante reste en tete du cache (sinon la 1re case, a 1 bloc, serait prise pour une case deja passee). Reutilisation, du moins cher au plus cher :
  position sur le chemin (<= ~1.4 bloc) = on retire le passe ; un peu a cote (< 16) = une petite jambe de retour ; dans un chunk de la route = on garde les portes (`previewTargetIndex`) et on ne recherche que la jambe courante ;
  hors des chunks de la route = `recomputeRoute()`. Demander plus de cases ne cherche que les jambes manquantes. Changer fin/regles/hitbox/lissage/portes/parametre vide le cache (`invalidatePreview`).
- Une jambe = deux cellules de la route dans le meme chunk. Chunk charge : A* reel (`LongPathFinder` + `LongMob` sonde virtuel pose sur le depart, `setNeverLoadChunks(true)`), chemin complete jusqu'a la cible s'il s'arrete court.
  Chunk non charge : lu dans le fichier region (`ChunkNavMesh.pathBetween`) **seulement si** `force chunk loading of long navigation` est vrai, sinon la liste s'arrete la. Rien n'est jamais charge par l'apercu.
- **Piege** : l'A* lit les blocs voisins d'un noeud (collisions, voisins pour cactus/feu : +-1 bloc) et **charge de facon synchrone** le chunk d'a cote ; sans garde, l'apercu charge la route entiere en cascade.
  `NodeEvaluator.setNeverLoadChunks` exige que les chunks de la colonne et de ses 8 voisines soient charges. Un chunk charge a aussi des voisins « frontiere » (niveau 32/33) comptes charges : l'ecart est de 2 chunks apres un chunk a ticket.
  `execute if loaded` n'est PAS un bon indicateur (il exige un chunk qui tique) : se fier a `world.isChunkLoaded`.
- `force chunk loading of long navigation` (defaut faux) : pour une vraie marche, `requestChunks` (appele quand la navigation attend un chunk, `WAITING_FOR_CHUNK`) demande en asynchrone (`getChunkAtAsync(.., false)`, jamais de generation) les chunks entre l'entite et le waypoint.
  Non testable sur le serveur vide (sans joueur aucun chunk voisin ne tique, l'entite est invalidee) : verifie en lisant le journal (`WAITING_FOR_CHUNK -> FOLLOWING` ~1 s apres).
- Tests : `v1.5.0/long-navigation/path-blocks.sk` (plateforme du spawn, navigation par id), `path-blocks-unloaded.sk` (plateforme x 4000..4063, z 3984..4031, chunks 251+ dechargees ; attend le dechargement, SKIP si trop long),
  `path-blocks-errors.sk`, `players.sk` (SKIP sans joueur). Le premier test apres un redemarrage peut voir les chunks du spawn pas encore charges : `wait 6 seconds` apres `forceload add`.

## Custom entities (module `custom_entity`, `since` 1.6.0)
- Definitions : dossier `plugins/SkWhy/custom-entities/*.yml` (config `custom_entity.folder`), une cle de premier niveau par id (`CustomEntityDef`, `CustomEntityRegistry`).
  Un entite reelle = `Interaction` dont les NBT custom sont l'id (PDC `skwhy:custom_entity`) et ses surcharges eventuelles (PDC `skwhy:custom_entity_overrides`, JSON) ; une fausse = packets uniquement
  (interaction + visuel), surcharges en memoire.
  Le visuel (type, nom, equipement, scale, skin mannequin, metadata brute) est toujours envoye par packet depuis la definition (`CustomEntityPackets`).
- Le visuel ne monte PAS sur l'interaction (un passager est place au sommet de la hitbox) : `CustomEntityManager.tick` le teleporte a chaque tick.
  Les clics sur le visuel/la fausse interaction arrivent par packet (`INTERACT_ENTITY`, ids dans `byPacketId`) et declenchent `on custom entity interact`.
- **Surcharges par entite** : `CustomEntityDef.withOverrides` (chemin YAML -> String/Double/Boolean, `kindOf`/`checkOverride` valident), `CustomEntity.effective(base)` (cache), `CustomEntityManager.defOf`
  (toujours l'utiliser a la place de `registry.get` pour l'apparence/hitbox/IA). Syntaxes : section `set %~objects% to a new real|fake custom entity "id" at loc:` (cles = expressions Skript parsees ;
  mot seul non quote accepte pour type/ai/equipment/metadata/skin), `custom entity parameter "x" of e`, `custom entity overrides of e`. Tests : `overrides.sk`, `overrides-errors.sk`.
- **IA vanilla** (parametre `ai: <type de mob>`, `CustomEntityBrain`, seule classe NMS) : un vrai `Mob` NMS est cree (`EntityType.create(level, ...)`) mais **jamais ajoute au monde** (invisible, pas dans les listes
  d'entites, les autres entites ne le voient pas), tick a la main (`mob.tick()`) a chaque tick par `CustomEntityManager.tickBrains` ; l'entite custom (fausse : position + packets ; reelle :
  `snapTo` NMS sur l'interaction) prend sa position/rotation, la tete suit `getYHeadRot`. Une teleportation externe replace le mob. Chunk non charge = IA figee (aucun chargement). `make custom entity e walk to loc`
  = navigation vanilla (`moveTo`) ; un mob neuf n'est « au sol » qu'apres `setOnGround` (sinon `moveTo` echoue). Les goals continuent (errance) : ne pas tester « ne marche plus » apres un delai.
  Build : `paperweight-userdev 2.0.0-beta.23` (la .24 exige Gradle 9.7) + `paperDevBundle("26.2.build.129-stable")` (noms Mojang, pas de reobf). Test : `ai.sk` (plateforme y=199, `skt-wait: 45`).
- Tests : fixture `plugins/SkWhy/custom-entities/skt_tests.yml` du serveur vide (`skt_wolf`, `skt_mannequin`), tests dans `v1.6.0/custom-entity/`.
  Sans joueur a proximite le chunk de spawn n'est pas charge : `spawnReal` charge le chunk et retourne `null` si le serveur refuse l'entite.
  Le rendu reel (packets vus par un client) n'est verifie que par `players.sk` (SKIP sans joueur) : a regarder en jeu.

## Branches et release
- `main` contient tout, y compris le **long pathfinding** (package `skwhy.chunkpath`, `ChunkPathModule`, `modules/LongNavigationElements`, syntaxes
  chunk path / long navigation, `since` 1.4.0 et 1.5.0) qui n'est pas termine.
- `release/1.4.0` (publiee sur `origin`, release GitHub `1.4.0` faite) = `main` **sans** le long pathfinding. Elle a ete **mergee dans `main`** puis le commit
  qui retirait le long pathfinding a ete **revert** : `main` = toutes les corrections de la 1.4.0 + le long pathfinding. Pour une future release sans
  fonctionnalite inachevee, recommencer : branche `release/x.y.z` depuis `main`, retirer les modules inacheves (commit dedie), corriger sur cette branche,
  merger dans `main` puis `git revert` du commit de retrait.
- Ne pas pousser ni creer de release/tag sans que l'utilisateur le demande. `gh` n'est pas installe. Fins de messages de commit :
  `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.
- `app/bin/main/**` (classes compilees par l'IDE) est suivi par git : ne pas les commiter dans mes commits (n'ajouter que `app/src`, `CLAUDE.md`, etc.),
  mais supprimer avec `git rm` les classes des fichiers sources supprimes.
- Pour tester la release : `git checkout release/1.4.0`, `./gradlew clean copyJarToPlugins`, revenir sur `main`. Le jar du serveur vide est alors celui de la branche
  compilee en dernier : reconstruire depuis `main` pour retrouver le long pathfinding (et renommer les tests desactives `-all.sk` en `all.sk`).

## Patchnote / release GitHub
1. Compiler la branche de release, `python run_tests.py restart`, `python run_tests.py gendocs`, `python run_tests.py run` (vert).
2. Generer la patchnote avec le script du serveur vide (voir son `CLAUDE.md`, section « Patchnote ») : `serveur vide\plugins\SkriptHubDocsTool\documentation\patchnote_v<version>.md`.
3. Ajouter a la main les corrections de bugs (le script ne liste que les nouveautes), puis tag + release GitHub quand l'utilisateur le demande.
