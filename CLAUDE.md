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
   Un event se teste en le declenchant (Skript, ou `external_tests.py` pour HTTP/Votifier/...). Si un joueur connecte est
   indispensable, ajouter un `skt_skip("...", "necessite un joueur connecte")` pour garder la trace.
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
