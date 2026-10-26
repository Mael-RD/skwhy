package skwhy.quest.model;

import java.util.Map;

import skwhy.quest.Tree;

/** Destination d'une étape ou d'un objectif, utilisée par la boussole. */
public record QuestDestination(Kind kind, String npc, String world, double x, double y, double z, double radius) {

    public enum Kind { NPC, POINT, FARM }

    public static QuestDestination parse(Map<String, Object> tree) {
        String type = Tree.str(tree, "type");
        if (type == null) return new QuestDestination(Kind.NPC, Tree.str(tree, "npc"), null, 0, 0, 0, 0);
        if ("farm".equals(type)) {
            return new QuestDestination(Kind.FARM, null, Tree.str(tree, "world"), Tree.dbl(tree, "x", 0), 0,
                Tree.dbl(tree, "z", 0), Tree.dbl(tree, "radius", 10));
        }
        return new QuestDestination(Kind.POINT, null, Tree.str(tree, "world"), Tree.dbl(tree, "x", 0),
            Tree.dbl(tree, "y", 0), Tree.dbl(tree, "z", 0), 0);
    }
}
