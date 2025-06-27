package myModule;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

import jsclub.codefest.sdk.Hero;
import jsclub.codefest.sdk.algorithm.PathUtils;
import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.Element;
import jsclub.codefest.sdk.model.GameMap;
import jsclub.codefest.sdk.model.obstacles.Obstacle;
import jsclub.codefest.sdk.model.players.Player;
import jsclub.codefest.sdk.model.weapon.Weapon;


public class LocalUtil {


    /**
     * Gom các node cần đi loot:
     *
     * @param map          gameMap hiện tại
     * @param current      vị trí hiện tại của player
     * @param inv          inventory của bạn (chứa thông tin healIds, vũ khí, armor…)
     * @param healIds      mảng 4 phần tử chứa ID các healing item (có null nếu chưa đầy)
     * @return list các Node cần ghé để pickup
     */
    public static List<Node> collectLootTargets(
            GameMap map,
            Node current,
            InventoryLocal inv,
            String[] healIds
    ) {
        List<Node> targets = new ArrayList<>();
        int x = current.x, y = current.y;
        if (!inv.hasGun() &&  existsWithin(map.getAllGun(), current, 5)) {
            addNearbySafeGunTargets(map, current, 5, targets);
            return targets;
        }

        if (inv.hasMelee() && !inv.hasGun() && existsWithin(map.getAllGun(), current, 10)) {
            addNearbySafeGunTargets(map, current, 10, targets);
            return targets;
        }

        if (healIds[3] == null) {
            addTargetsFromList(
                    map.getListHealingItems(),
                    item -> new Node(item.getX(), item.getY()),
                    map,
                    targets
            );
        }

        if (!inv.hasHelmet()) {
            addTargetsFromList(
                    map.getListArmors(),
                    a -> new Node(a.getX(), a.getY()),
                    map,
                    targets,
                    armor -> armor.getId().equals("WOODEN_HELMET")
                            || armor.getId().equals("MAGIC_HELMET")
            );
        }
        if (!inv.hasArmor()) {
            addTargetsFromList(
                    map.getListArmors(),
                    a -> new Node(a.getX(), a.getY()),
                    map,
                    targets,
                    armor -> armor.getId().equals("ARMOR")
                            || armor.getId().equals("MAGIC_ARMOR")
            );
        }

        // 4) Vũ khí thiếu
        if (!inv.hasGun()) {
            addTargetsFromList(map.getAllGun(), w -> new Node(w.getX(), w.getY()), map, targets);
        }
        if (!inv.hasMelee()) {
            addTargetsFromList(map.getAllMelee(), w -> new Node(w.getX(), w.getY()), map, targets);
        }
        if (!inv.hasThrowable()) {
            addTargetsFromList(map.getAllThrowable(), w -> new Node(w.getX(), w.getY()), map, targets);
        }


        if (!inv.hasGun() || !inv.hasMelee() || !inv.hasThrowable() || !inv.hasHelmet() || !inv.hasArmor() || healIds[3] == null) {
            System.out.println("5) Nếu vẫn thiếu bất kỳ thứ gì → tìm chest");
            targets.addAll(findNearbyChests(map, x, y));
        }

        return targets;
    }

    // ------ Các helper bên dưới ------

    private static <T> void addTargetsFromList(
            List<T> items,
            Function<T, Node> toNode,
            GameMap map,
            List<Node> outTargets
    ) {
        addTargetsFromList(items, toNode, map, outTargets, item -> true);
    }

    private static <T> void addTargetsFromList(
            List<T> items,
            Function<T, Node> toNode,
            GameMap map,
            List<Node> outTargets,
            Predicate<T> filter
    ) {
        for (T it : items) {
            if (!filter.test(it)) continue;
            Node n = toNode.apply(it);
            if (PathUtils.checkInsideSafeArea(n, map.getSafeZone(), map.getMapSize())) {
                outTargets.add(n);
            }
        }
    }
    private static <T extends Element> boolean existsWithin(List<T> items, Node cur, int maxDist) {
        for (T it : items) {
            if (PathUtils.distance(it,cur) <= maxDist) {
                return true;
            }
        }
        return false;
    }
    private static void addNearbySafeGunTargets(GameMap map, Node cur, int maxDist, List<Node> out) {
        for (Weapon g : map.getAllGun()) {
            Node n = new Node(g.getX(), g.getY());
            if (PathUtils.distance(n, cur) <= maxDist &&
                    PathUtils.checkInsideSafeArea(n, map.getSafeZone(), map.getMapSize())) {
                out.add(n);

            }
        }
    }

    private static List<Node> findNearbyChests(GameMap map, int x, int y) {
        List<Node> result = new ArrayList<>();
        List<Obstacle> chests = map.getListChests();
        int size = map.getMapSize();
        for (int r = 1; r < size; r++) {
            for (Obstacle c : chests) {
                if (c.getHp() == 0) continue;
                Node pos = new Node(c.getX(), c.getY());
                if (!PathUtils.checkInsideSafeArea(pos, map.getSafeZone(), size)) continue;
                if (Math.abs(c.getX() - x) <= 10*r && Math.abs(c.getY() - y) <= 10*r) {
                    int cx = c.getX(), cy = c.getY();
                    if (cx == x) {
                        result.add(new Node(cx, cy > y ? cy-1 : cy+1));
                    } else {
                        result.add(new Node(cx + (cx < x ? 1 : -1), cy));
                    }
                }
            }
            if (!result.isEmpty()) break;
        }
        return result;
    }

    /**
     * Tìm đường đi “tốt nhất”:
     *   - Ưu tiên tới các node trong targets
     *   - Nếu không tới được, chạy trốn/quay góc map
     *
     * @param map            gameMap hiện tại
     * @param restricted     danh sách nodes phải tránh
     * @param current        node hiện tại
     * @param targets        list các node muốn tới (đã generate bởi collectLootTargets)
     * @param otherPlayers   list Player khác để bổ sung avoid nếu cần
     * @param gocFlags       boolean[4] đánh dấu góc 1–4: true nghĩa lần tới sẽ thử góc đó
     * @return chuỗi directions (vd "rruuld...") hoặc null nếu hoàn toàn không tìm được
     */
    public static String findBestPath(
            GameMap map,
            List<Node> restricted,
            Node current,
            List<Node> targets,
            Node currentNodeTarget,
            List<Player> otherPlayers,
            boolean[] gocFlags

    ) {
        // 1) Thử theo từng target
        int minDistance = Integer.MAX_VALUE;
        String path = null;
        for (Node tgt : targets) {
            if(PathUtils.distance(tgt,current)< minDistance){
                minDistance = PathUtils.distance(tgt,current);

            }
        }

        path = PathUtils.getShortestPath(map, restricted, current, currentNodeTarget, false);
        if(path!= null && !path.isEmpty()){
            return path;
        }

        for (Player p : otherPlayers) {
            restricted.add(new Node(p.getX(), p.getY()));
        }

        int size = map.getMapSize();
        String escapePath = null;
        // Góc 1: dưới-trái; 2: dưới-phải; 3: trên-phải; 4: trên-trái
        if (gocFlags[0]) {
            escapePath = tryCorner(map, restricted, current, size / 2 - 1, size / 2 - 1);
            gocFlags[0] = false;
            gocFlags[1] = true;  // lần sau chuyển qua góc 2
        } else if (gocFlags[1]) {
            escapePath = tryCorner(map, restricted, current, size / 2 - 1, size - size / 2);
            gocFlags[1] = false;
            gocFlags[2] = true;
        } else if (gocFlags[2]) {
            escapePath = tryCorner(map, restricted, current, size - size / 2, size - size / 2);
            gocFlags[2] = false;
            gocFlags[3] = true;
        } else if (gocFlags[3]) {
            escapePath = tryCorner(map, restricted, current, size - size / 2, size / 2 - 1);
            gocFlags[3] = false;
            gocFlags[0] = true;
        }
        return escapePath;
    }

    /**
     * Helper thử tính path đến khoảng (tx,ty) – một điểm ở “góc”.
     */
    private static String tryCorner(
            GameMap map,
            List<Node> restricted,
            Node current,
            int tx, int ty
    ) {
        Node corner = new Node(tx, ty);
        return PathUtils.getShortestPath(map, restricted, current, corner, false);
    }


    /**
     * Thực thi move hoặc loot/attack khi không còn path.
     *
     * @param map       gameMap hiện tại
     * @param hero      đối tượng Hero để gọi move/pickup/attack
     * @param current   node hiện tại của player
     * @param path      chuỗi directions (vd "urddl") hoặc null
     * @param inv       InventoryLocal để cập nhật khi pickup
     * @return true nếu đã thực hiện move; false nếu đã xử lý loot/attack hoặc không làm gì
     */
    public static boolean executePathOrLoot(
            GameMap map,
            Hero hero,
            Node current,
            Node target,
            String path,
            InventoryLocal inv,
            LocalHeroController myHero
    ) {
        int x = current.x, y = current.y;

        if (target!= null && PathUtils.distance(current, target) == 0) {
            try {
                Element elem = map.getElementByIndex(x, y);
                if (elem != null) {
                    for (int i = 0; i < 1000; i++) {
                        hero.pickupItem();
                    }
                    myHero.pickupItem(elem.getId());
                }

                // 2) Không pickup được → thử attack chest kề bên
                attackAdjacentChestsNoId(hero, map, x, y);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return false;
        }

        // 3) Nếu chưa đến → di chuyển theo path
        if (path != null && !path.isEmpty()) {
            String step = path.substring(0, 1);
            try {
                hero.move(step);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return true;
        }

        // Nếu không còn path và chưa đến target → cũng thử attack
        try {
            attackAdjacentChestsNoId(hero, map, x, y);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Nếu có chest kề bên, attack nó.
     */
    static void attackAdjacentChestsNoId(Hero hero, GameMap gameMap, int px, int py) {
        try {
            for (Obstacle chest : gameMap.getListChests()) {
                if (chest.getHp() <= 0) continue;         // chỉ quan tâm chest còn HP
                int dx = chest.getX() - px;
                int dy = chest.getY() - py;
                // chỉ attack khi chest kề cạnh (Manhattan distance == 1)
                if (Math.abs(dx) + Math.abs(dy) == 1) {
                    String dir;
                    if (dx ==  1) dir = "r";  // chest bên phải
                    else if (dx == -1) dir = "l";  // chest bên trái
                    else if (dy ==  1) dir = "u";  // chest phía trên
                    else            dir = "d";  // chest phía dưới
                    hero.attack(dir);
                    // nếu chỉ muốn đánh 1 lần/chest, có thể break ở đây
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
