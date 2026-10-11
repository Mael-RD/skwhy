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
