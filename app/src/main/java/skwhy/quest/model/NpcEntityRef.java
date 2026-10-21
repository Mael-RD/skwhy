package skwhy.quest.model;

/** Une entité du monde qui représente un NPC à une position donnée (les « positions » 1, 2, 3... d'un même NPC). */
public record NpcEntityRef(String npcId, String entityUuid, int position, String world, double x, double y, double z) {
}
