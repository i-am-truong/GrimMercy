package myModule;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

import jsclub.codefest.sdk.algorithm.PathUtils;
import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.GameMap;
import jsclub.codefest.sdk.model.obstacles.Obstacle;

public class LocalPathUtils {
    public static int distance(Node x, Node y) {
        return Math.abs(x.x - y.x) + Math.abs(x.y - y.y);
    }

    public static Node getCenterOfMap(int mapSize) {
        return new Node(mapSize / 2, mapSize / 2);
    }

    public static boolean checkInsideSafeArea(Node current, int safeZone, int mapSize) {
        Node center = getCenterOfMap(mapSize);
        return Math.abs(current.getX() - center.getX()) < safeZone && Math.abs(current.getY() - center.getY()) < safeZone;
    }
    public static String getShortestPath(GameMap gameMap, List<Node> restrictedNodes, Node current, final Node target, boolean skipDarkArea, boolean isLoot) {
        int[] Dx = new int[]{-1, 1, 0, 0};
        int[] Dy = new int[]{0, 0, -1, 1};
        int mapSize = gameMap.getMapSize();
        int safeZone = gameMap.getSafeZone();
        List<Obstacle> initThings = gameMap.getListIndestructibles();
        List<Node> listIndestructibleNodes = new ArrayList(initThings);
        listIndestructibleNodes.addAll(restrictedNodes);
        if(isLoot){
            listIndestructibleNodes.removeIf((node) -> node.x == target.x && node.y == target.y);
        }
        ArrayList<ArrayList<Integer>> isRestrictedNodes = new ArrayList(mapSize + 5);
        final ArrayList<ArrayList<Integer>> g = new ArrayList(mapSize + 5);
        ArrayList<ArrayList<Integer>> trace = new ArrayList(mapSize + 5);

        for(int i = 0; i < mapSize + 1; ++i) {
            isRestrictedNodes.add(new ArrayList(mapSize + 5));
            g.add(new ArrayList(mapSize + 5));
            trace.add(new ArrayList(mapSize + 5));

            for(int j = 0; j < mapSize + 1; ++j) {
                ((ArrayList)isRestrictedNodes.get(i)).add(0);
                ((ArrayList)g.get(i)).add(-1);
                ((ArrayList)trace.get(i)).add(-1);
            }
        }

        for(Node point : listIndestructibleNodes) {
            if (point.x >= 0 && point.x < mapSize && point.y >= 0 && point.y < mapSize) {
                ((ArrayList)isRestrictedNodes.get(point.x)).set(point.y, 1);
            }
        }

        PriorityQueue<Node> openSet = new PriorityQueue(new Comparator<Node>() {
            public int compare(Node n1, Node n2) {
                return Integer.compare((Integer)((ArrayList)g.get(n1.x)).get(n1.y) + PathUtils.distance(n1, target), (Integer)((ArrayList)g.get(n2.x)).get(n2.y) + PathUtils.distance(n2, target));
            }
        });
        openSet.add(new Node(current.x, current.y));
        ((ArrayList)g.get(current.x)).set(current.y, 0);
        StringBuilder ans = new StringBuilder();
        boolean existPath = false;

        while(!openSet.isEmpty()) {
            Node u = (Node)openSet.poll();
            if (u.x == target.x && u.y == target.y) {
                int dir = -1;
                for(existPath = true; u.x != current.x || u.y != current.y; u.y -= Dy[dir]) {
                    dir = (Integer)((ArrayList)trace.get(u.x)).get(u.y);
                    if (dir == 0) {
                        ans.append('l');
                    } else if (dir == 1) {
                        ans.append('r');
                    } else if (dir == 2) {
                        ans.append('d');
                    } else {
                        ans.append('u');
                    }

                    u.x -= Dx[dir];
                }

                ans.reverse();
                break;
            }

            for(int dir = 0; dir < 4; ++dir) {
                int x = u.x + Dx[dir];
                int y = u.y + Dy[dir];
                if (x >= 0 && y >= 0 && x < mapSize && y < mapSize && (Integer)((ArrayList)isRestrictedNodes.get(x)).get(y) != 1 && (skipDarkArea || checkInsideSafeArea(new Node(x, y), safeZone, gameMap.getMapSize()))) {
                    int cost = (Integer)((ArrayList)g.get(u.x)).get(u.y) + 1;
                    if ((Integer)((ArrayList)g.get(x)).get(y) == -1 || (Integer)((ArrayList)g.get(x)).get(y) > cost) {
                        ((ArrayList)g.get(x)).set(y, cost);
                        ((ArrayList)trace.get(x)).set(y, dir);
                        openSet.add(new Node(x, y));
                    }
                }
            }
        }

        if (!existPath) {
            return null;
        } else {
            return ans.toString();
        }
    }
}
