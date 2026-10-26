package skwhy.chunkpath.debug;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import skwhy.chunkpath.ChunkGateService;
import skwhy.chunkpath.block.PathCostRules;
import skwhy.chunkpath.gate.Gate;
import skwhy.chunkpath.graph.ChunkNavMesh;
import skwhy.chunkpath.longnav.LongNavigation;
import skwhy.chunkpath.pathfinding.GatePathfinder;
import skwhy.modules.LongNavigationElements.ChunkPathManager;

import java.util.List;

/**
 * Commande de debug {@code /cpdebug} (permission {@code skwhy.debug}, console incluse) :
 * <ul>
 *   <li>{@code cpdebug cell <monde> <x> <y> <z>} : colonne de praticabilité lue depuis le fichier région</li>
 *   <li>{@code cpdebug path <monde> <x1> <y1> <z1> <x2> <y2> <z2>} : route de portes entre deux points</li>
 *   <li>{@code cpdebug navs} : toutes les long navigations actives</li>
 * </ul>
 */
public final class ChunkPathDebugCommand extends Command {

    /** Réservé à ce debug (la fonctionnalité n'a pas de règle par défaut) : coût 5 pour tout bloc. */
    private static final PathCostRules DEBUG_RULES = new PathCostRules(List.of(new PathCostRules.CostRule(List.of(), List.of(), 5)));


    public ChunkPathDebugCommand() {
        super("cpdebug", "Debug du long pathfinding SkWhy", "/cpdebug <cell|path|navs>", List.of());
        setPermission("skwhy.debug");
    }

    public static void register() {
        Bukkit.getCommandMap().register("skwhy", new ChunkPathDebugCommand());
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        try {
            if (args.length == 0) return false;
            switch (args[0]) {
                case "cell" -> cell(sender, args);
                case "path" -> path(sender, args);
                case "navs" -> navs(sender);
                case "gates" -> {
                    if (!(sender instanceof org.bukkit.entity.Player player)) {
                        sender.sendMessage("/cpdebug gates [on|off|refresh] : joueur uniquement");
                        break;
                    }
                    String mode = args.length < 2 ? (GateDebugOverlay.isOn(player) ? "off" : "on") : args[1];
                    switch (mode) {
                        case "refresh" -> GateDebugOverlay.refresh(player);
                        case "off" -> GateDebugOverlay.toggle(player, false);
                        default -> GateDebugOverlay.toggle(player, true);
                    }
                    sender.sendMessage("gates : " + GateDebugOverlay.summary(player));
                }
                case "shape" -> {
                    World w = Bukkit.getWorld(args[1]);
                    for (int y = Integer.parseInt(args[3]) + 2; y >= Integer.parseInt(args[3]) - 1; y--) {
                        org.bukkit.block.Block b = w.getBlockAt(Integer.parseInt(args[2]), y, Integer.parseInt(args[4]));
                        sender.sendMessage("y=" + y + " " + b.getType() + " " + b.getCollisionShape().getBoundingBoxes());
                    }
                }
                case "collide" -> {
                    for (LongNavigation n : LongNavigation.getRegistry()) {
                        var m = n.getMob();
                        var v = new org.bukkit.util.Vector(Double.parseDouble(args[1]), args.length > 3 ? Double.parseDouble(args[3]) : 0, Double.parseDouble(args[2]));
                        sender.sendMessage("collide " + v + " -> " + skwhy.chunkpath.move.Collide.collide(m, v)
                                + " box=" + m.getMinX() + "," + m.getMinY() + "," + m.getMinZ() + " / " + m.getMaxX() + "," + m.getMaxY() + "," + m.getMaxZ());
                    }
                }
                case "nocollision" -> sender.sendMessage(skwhy.chunkpath.block.BlockClassifier.noCollisionBlocks().size()
                        + " solid blocks without collision: " + skwhy.chunkpath.block.BlockClassifier.noCollisionBlocks());
                case "geometry" -> {
                    skwhy.chunkpath.move.pathcalculator.CostRuleWalkNodeEvaluator.geometryChecks = args.length < 2 || !args[1].equals("off");
                    sender.sendMessage("geometry checks=" + skwhy.chunkpath.move.pathcalculator.CostRuleWalkNodeEvaluator.geometryChecks);
                }
                case "dump" -> LongNavigation.getRegistry().forEach(n -> sender.sendMessage(n.toString()));
                case "pairs" -> {
                    var data = ChunkPathManager.serviceFor(Bukkit.getWorld(args[1]))
                            .getIfKnown(Integer.parseInt(args[2]), Integer.parseInt(args[3]));
                    if (data == null) sender.sendMessage("chunk inconnu");
                    else for (var g : data.gates()) sender.sendMessage("gate " + g.globalId() + " " + g.edge + " rep="
                            + java.util.Arrays.toString(g.representativeWorld) + " internal=" + g.internalDistances
                            + " links=" + g.neighborLinks + " pairCosts=" + g.neighborCosts);
                }
                case "verbose" -> {
                    LongNavigation.verbose = args.length < 2 || !args[1].equals("off");
                    sender.sendMessage("verbose=" + LongNavigation.verbose);
                }
                case "set" -> {
                    String error = args.length < 3 ? "usage : /cpdebug set <clé> <valeur>" : skwhy.chunkpath.debug.ChunkPathTuning.set(args[1], args[2]);
                    sender.sendMessage(error != null ? error : "ok " + skwhy.chunkpath.debug.ChunkPathTuning.describe());
                }
                case "get" -> sender.sendMessage(String.valueOf(skwhy.chunkpath.debug.ChunkPathTuning.describe()));
                case "map" -> map(sender, args);
                case "folder" -> sender.sendMessage(String.valueOf(ChunkPathManager.regionFolderOf(Bukkit.getWorld(args[1]))));
                default -> { return false; }
            }
        } catch (Exception e) {
            sender.sendMessage("erreur : " + e);
            e.printStackTrace();
        }
        return true;
    }

    private static void cell(CommandSender sender, String[] args) {
        World world = Bukkit.getWorld(args[1]);
        int x = Integer.parseInt(args[2]), y = Integer.parseInt(args[3]), z = Integer.parseInt(args[4]);
        // pas de règle par défaut : le debug utilise une règle unique de coût 5 qui couvre tous les blocs
        ChunkNavMesh mesh = ChunkPathManager.serviceFor(world).analyzer().buildMesh(Math.floorDiv(x, 16), Math.floorDiv(z, 16), DEBUG_RULES);
        sender.sendMessage(mesh.debugColumn(Math.floorMod(x, 16), Math.floorMod(z, 16), y - 4, y + 4));
    }

    private static void path(CommandSender sender, String[] args) {
        World world = Bukkit.getWorld(args[1]);
        int[] c = new int[6];
        for (int i = 0; i < 6; i++) c[i] = Integer.parseInt(args[2 + i]);
        ChunkGateService service = ChunkPathManager.serviceFor(world);
        long t0 = System.nanoTime();
        List<Gate> route = GatePathfinder.findPath(service, DEBUG_RULES, c[0], c[1], c[2], c[3], c[4], c[5]);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        sender.sendMessage("route=" + (route == null ? "null" : route.size() + " portes") + " en " + ms + " ms");
        if (route != null) {
            for (Gate g : route) {
                sender.sendMessage("  chunk(" + g.chunkX + "," + g.chunkZ + ") gate " + g.id + " " + g.edge
                        + " rep=" + java.util.Arrays.toString(g.representativeWorld) + " members=" + g.memberBlocksWorld.size()
                        + " y=" + g.memberBlocksWorld.stream().mapToInt(m -> m[1]).min().orElse(0) + ".."
                        + g.memberBlocksWorld.stream().mapToInt(m -> m[1]).max().orElse(0));
            }
        }
    }

    private static void navs(CommandSender sender) {
        for (LongNavigation nav : LongNavigation.getRegistry()) {
            sender.sendMessage("nav id=" + nav.getEntityId() + " status=" + nav.getStatus()
                    + " pos=" + nav.getCurrentLocation() + " end=" + nav.getEnd());
        }
        sender.sendMessage(LongNavigation.getRegistry().size() + " navigation(s)");
    }

    /**
     * {@code cpdebug map <monde> <x1> <z1> <x2> <z2> <y>} : écrit {@code plugins/SkWhy/traces/map.txt}, un caractère par colonne
     * (sol le plus proche de y) : {@code .} coût 1, {@code 2..9} coût / 25, {@code #} coût élevé, {@code X} bloqué, espace = pas de sol.
     * Les règles sont celles de la première long navigation enregistrée.
     */
    private static void map(CommandSender sender, String[] args) throws java.io.IOException {
        World world = Bukkit.getWorld(args[1]);
        int x1 = Integer.parseInt(args[2]), z1 = Integer.parseInt(args[3]), x2 = Integer.parseInt(args[4]), z2 = Integer.parseInt(args[5]);
        int y = Integer.parseInt(args[6]);
        PathCostRules rules = null;
        for (LongNavigation n : LongNavigation.getRegistry()) if (n.getCostRules() != null) { rules = n.getCostRules(); break; }
        if (rules == null) { sender.sendMessage("aucune long navigation avec des règles"); return; }
        // altitude de référence par colonne : celle du point de la dernière trace le plus proche (sinon y)
        java.util.List<double[]> trace = new java.util.ArrayList<>();
        java.io.File[] csvs = new java.io.File(skwhy.SkWhy.getInstance().getDataFolder(), "traces").listFiles((d, n) -> n.startsWith("nav-"));
        if (csvs != null && csvs.length > 0) {
            java.io.File latest = java.util.Arrays.stream(csvs).max(java.util.Comparator.comparingLong(java.io.File::lastModified)).get();
            for (String line : java.nio.file.Files.readAllLines(latest.toPath()).stream().skip(1).toList()) {
                String[] f = line.split(",");
                trace.add(new double[] { Double.parseDouble(f[1]), Double.parseDouble(f[2]), Double.parseDouble(f[3]) });
            }
        }
        java.util.Map<String, Integer> nearTrace = new java.util.TreeMap<>();
        StringBuilder sb = new StringBuilder("x0=" + x1 + " z0=" + z1 + " (une ligne par z, une colonne par x)\n");
        for (int z = z1; z <= z2; z++) {
            for (int x = x1; x <= x2; x++) {
                char c = ' ';
                org.bukkit.Material ground = null;
                int ref = y;
                double best = Double.MAX_VALUE;
                for (double[] t : trace) {
                    double d = (t[0] - x - 0.5) * (t[0] - x - 0.5) + (t[2] - z - 0.5) * (t[2] - z - 0.5);
                    if (d < best) { best = d; ref = (int) Math.round(t[1]); }
                }
                for (int yy = ref + 4; yy >= ref - 4; yy--) {
                    org.bukkit.block.Block below = world.getBlockAt(x, yy - 1, z);
                    if (below.getType().isSolid() && world.getBlockAt(x, yy, z).isPassable() && world.getBlockAt(x, yy + 1, z).isPassable()) {
                        ground = below.getType();
                        int cost = rules.costOf(below.getType().getKey().toString());
                        c = cost < 0 ? 'X' : cost <= 1 ? '.' : cost >= 100 ? '#' : (char) ('0' + Math.min(9, cost / 12));
                        break;
                    }
                }
                sb.append(c);
                if (ground != null && best <= 4.0) {
                    String name = ground.name().toLowerCase() + " (cost " + rules.costOf(ground.getKey().toString()) + ")";
                    nearTrace.merge(name, 1, Integer::sum);
                }
            }
            sb.append('\n');
        }
        java.io.File dir = new java.io.File(skwhy.SkWhy.getInstance().getDataFolder(), "traces");
        dir.mkdirs();
        java.nio.file.Files.writeString(new java.io.File(dir, "map.txt").toPath(), sb.toString());
        java.nio.file.Files.writeString(new java.io.File(dir, "map-stats.txt").toPath(), "sol à moins de 2 blocs de la trace : " + nearTrace + "\n");
        sender.sendMessage("map écrite (" + (x2 - x1 + 1) + "x" + (z2 - z1 + 1) + ")");
    }
}
