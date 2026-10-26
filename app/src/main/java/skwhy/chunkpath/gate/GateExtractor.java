package skwhy.chunkpath.gate;

import skwhy.chunkpath.Direction;
import skwhy.chunkpath.graph.ChunkNavMesh;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GateExtractor {

    private GateExtractor() {
    }

    @SuppressWarnings("null")
    public static List<Gate> extract(ChunkNavMesh mesh, int chunkX, int chunkZ) {
        int[] componentOf = mesh.labelComponents();
        List<Gate> gates = new ArrayList<>();
        int nextId = 0;

        // On garde, pour chaque porte créée, ses noeuds internes (indices du mesh) pour
        // calculer ensuite les distances porte-à-porte et la composante associée.
        List<int[]> gateNodeIdsList = new ArrayList<>(); // parallèle à `gates`
        List<Integer> gateComponentList = new ArrayList<>();

        for (Direction dir : Direction.values()) {
            List<Integer> boundary = mesh.boundaryNodes(dir);
            if (boundary.isEmpty()) continue;

            int[] clusterRoot = clusterBoundaryNodes(mesh, boundary);

            Map<Integer, List<Integer>> byRoot = new HashMap<>();
            for (int i = 0; i < boundary.size(); i++) {
                byRoot.computeIfAbsent(clusterRoot[i], k -> new ArrayList<>()).add(boundary.get(i));
            }

            for (List<Integer> clusterNodeIds : byRoot.values()) {
                int[] nodeIdsArr = clusterNodeIds.stream().mapToInt(Integer::intValue).toArray();

                List<int[]> membersWorld = new ArrayList<>();
                long sumX = 0, sumY = 0, sumZ = 0;
                for (int nodeId : nodeIdsArr) {
                    int wx = chunkX * 16 + mesh.x(nodeId);
                    int wy = mesh.y(nodeId);
                    int wz = chunkZ * 16 + mesh.z(nodeId);
                    membersWorld.add(new int[]{wx, wy, wz});
                    sumX += wx;
                    sumY += wy;
                    sumZ += wz;
                }
                double cx = sumX / (double) nodeIdsArr.length;
                double cy = sumY / (double) nodeIdsArr.length;
                double cz = sumZ / (double) nodeIdsArr.length;

                int[] representative = membersWorld.get(0);
                double best = Double.MAX_VALUE;
                for (int[] block : membersWorld) {
                    double dx = block[0] - cx, dy = block[1] - cy, dz = block[2] - cz;
                    double d = dx * dx + dy * dy + dz * dz;
                    if (d < best) {
                        best = d;
                        representative = block;
                    }
                }

                Gate gate = new Gate(chunkX, chunkZ, nextId++, dir, membersWorld, representative);
                gates.add(gate);
                gateNodeIdsList.add(nodeIdsArr);
                gateComponentList.add(componentOf[nodeIdsArr[0]]);
            }
        }

        // Cul-de-sac : la composante interne de cette porte ne touche aucune autre porte.
        Map<Integer, Integer> gatesPerComponent = new HashMap<>();
        for (int comp : gateComponentList) {
            gatesPerComponent.merge(comp, 1, Integer::sum);
        }
        for (int i = 0; i < gates.size(); i++) {
            gates.get(i).deadEndCandidate = gatesPerComponent.get(gateComponentList.get(i)) == 1;
        }

        // Distances porte-à-porte : Dijkstra depuis le bloc central (représentant) de chaque porte,
        // lu au bloc central de l'autre porte. Une distance "région à région" (minimum sur tous
        // les blocs) valait 0 pour deux portes qui se touchent, même très larges.
        int[] representativeNode = new int[gates.size()];
        for (int i = 0; i < gates.size(); i++) {
            int[] r = gates.get(i).representativeWorld;
            representativeNode[i] = mesh.nodeAt(r[0] - chunkX * 16, r[1], r[2] - chunkZ * 16);
        }
        for (int i = 0; i < gates.size(); i++) {
            if (representativeNode[i] < 0) continue;
            Gate gate = gates.get(i);
            int[] dist = mesh.dijkstraMultiSource(new int[]{representativeNode[i]}, Integer.MAX_VALUE);
            for (int j = 0; j < gates.size(); j++) {
                if (i == j || representativeNode[j] < 0) continue;
                int d = dist[representativeNode[j]];
                if (d != Integer.MAX_VALUE) {
                    gate.internalDistances.put(gates.get(j).id, d);
                }
            }
        }

        return gates;
    }

    /**
     * Union-Find sur les noeuds de bord d'UN SEUL bord : deux noeuds de bord directement reliés
     * (voisins dans le maillage : même hauteur, marche ou échelle) ne sont fusionnés que s'ils ont le
     * même coût, c'est-à-dire la même règle de coût (un chemin bordé d'herbe donne des portes séparées).
     * La fusion est transitive : un bord continu de même règle ne forme qu'une porte.
     */
    private static int[] clusterBoundaryNodes(ChunkNavMesh mesh, List<Integer> boundary) {
        int n = boundary.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;

        Map<Integer, Integer> indexOf = new HashMap<>();
        for (int i = 0; i < n; i++) indexOf.put(boundary.get(i), i);

        for (int i = 0; i < n; i++) {
            for (int neighbor : mesh.neighborsOf(boundary.get(i))) {
                Integer j = indexOf.get(neighbor);
                if (j != null && mesh.cost(boundary.get(i)) == mesh.cost(neighbor)) union(parent, i, j);
            }
        }

        int[] root = new int[n];
        for (int i = 0; i < n; i++) root[i] = find(parent, i);
        return root;
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a), rb = find(parent, b);
        if (ra != rb) parent[ra] = rb;
    }
}
