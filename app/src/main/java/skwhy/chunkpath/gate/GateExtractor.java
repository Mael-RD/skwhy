package skwhy.chunkpath.gate;

import skwhy.chunkpath.Direction;
import skwhy.chunkpath.graph.ChunkNavMesh;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GateExtractor {

    /** Seuil de fusion par défaut, en coût pondéré (pas en blocs bruts). */
    public static final int DEFAULT_MERGE_THRESHOLD = 20;

    private GateExtractor() {
    }

    @SuppressWarnings("null")
    public static List<Gate> extract(ChunkNavMesh mesh, int chunkX, int chunkZ, int mergeThreshold) {
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

            int[] clusterRoot = clusterBoundaryNodes(mesh, boundary, mergeThreshold);

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

        // Distances porte-à-porte : Dijkstra multi-source depuis chaque porte, en prenant
        // pour chaque autre porte la distance minimale parmi tous ses noeuds membres
        // (distance "région à région", plus fidèle que représentant-à-représentant).
        for (int i = 0; i < gates.size(); i++) {
            Gate gate = gates.get(i);
            int[] dist = mesh.dijkstraMultiSource(gateNodeIdsList.get(i), Integer.MAX_VALUE);
            for (int j = 0; j < gates.size(); j++) {
                if (i == j) continue;
                int min = Integer.MAX_VALUE;
                for (int nodeId : gateNodeIdsList.get(j)) {
                    if (dist[nodeId] < min) min = dist[nodeId];
                }
                if (min != Integer.MAX_VALUE) {
                    gate.internalDistances.put(gates.get(j).id, min);
                }
            }
        }

        return gates;
    }

    /**
     * Union-Find sur les noeuds de bord d'UN SEUL bord : deux noeuds sont fusionnés dès
     * qu'un Dijkstra borné (cutoff = mergeThreshold - 1, car "strictement inférieur à
     * mergeThreshold") trouve une distance sous le seuil. La fusion est transitive : si
     * A~C et C~B alors A,B,C finissent dans le même groupe même si dist(A,B) >= seuil.
     */
    private static int[] clusterBoundaryNodes(ChunkNavMesh mesh, List<Integer> boundary, int mergeThreshold) {
        int n = boundary.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;

        int cutoff = mergeThreshold - 1;
        for (int i = 0; i < n; i++) {
            int[] dist = mesh.dijkstraFrom(boundary.get(i), cutoff);
            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                int d = dist[boundary.get(j)];
                if (d != Integer.MAX_VALUE && d <= cutoff) {
                    union(parent, i, j);
                }
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
