import io.socket.emitter.Emitter;
import jsclub.codefest.sdk.Hero;
import jsclub.codefest.sdk.algorithm.PathUtils;
import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.Element;
import jsclub.codefest.sdk.model.GameMap;
import jsclub.codefest.sdk.model.Inventory;
import jsclub.codefest.sdk.model.equipments.Armor;
import jsclub.codefest.sdk.model.npcs.Enemy;
import jsclub.codefest.sdk.model.obstacles.Obstacle;
import jsclub.codefest.sdk.model.obstacles.ObstacleTag;
import jsclub.codefest.sdk.model.players.Player;
import jsclub.codefest.sdk.model.weapon.Weapon;
import myModule.LocalPathUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

public class Main {
    private static final String SERVER_URL = "https://cf25-server.jsclub.dev";
    private static final String GAME_ID = "163768";
    private static final String PLAYER_NAME = "NeuroSama";
    private static final String SECRET_KEY = "sk-QF0trYSgT-uH8Ts5r2GjgQ:77yjD6Bql9CmfVeDElVtLKjigvaSViW4KH_UhnbER4zzDECm1Iy7E9CNAdjU8rqcbVP9eNAznl2JyV1UzHSCPA";
    public static void main(String[] args) throws IOException {
        Hero hero = new Hero(GAME_ID, PLAYER_NAME, SECRET_KEY);
        Emitter.Listener onMapUpdate = new MapUpdateListener(hero);
        hero.setOnMapUpdate(onMapUpdate);
        hero.start(SERVER_URL);
    }

}

class MapUpdateListener implements Emitter.Listener {
    public MapUpdateListener(Hero hero) {
        this.hero = hero;
    }
    private static final Logger log = LogManager.getLogger(MapUpdateListener.class);
    private final Hero hero;

    private int currentStep = 0;
    private final List<Node> restrictNode = new ArrayList<>();
    private static Node currentNodeTarget = null;
    private boolean[] gocFlags = {true, false, false, false};
    //    to condition to runBo
    private int currentSafeZone = 999;
    private boolean isShrinking = false;
    private Node safeNodeToRunBo = null;
    //    restrict node
    static List<Enemy> listNodeEnemySave = new ArrayList<>();
    private static final String[] enemyDirection = new String[100];
    private static final Boolean[][][] enemyMap = new Boolean[121][121][100];
    private static final int[] enemyMinEdge = new int[100];
    private static final int[] enemyMaxEdge = new int[100];
    private static final int[] toado = new int[100];

    List<String> listHealingInventory = new ArrayList<>();
    List<String> listSupportInventory = new ArrayList<>();
    Set<String> healingSet = Set.of("GOD_LEAF", "SPIRIT_TEAR", "MERMAID_TAIL", "PHOENIX_FEATHERS", "UNICORN_BLOOD");
    Set<String> specialSet = Set.of("ELIXIR", "MAGIC", "ELIXIR_OF_LIFE", "COMPASS");
//    List<String>
    @Override
    public void call(Object... args) {
        try {
            if (args == null || args.length == 0) return;
            GameMap gameMap = hero.getGameMap();
            gameMap.updateOnUpdateMap(args[0]);
            Player player = gameMap.getCurrentPlayer();
            if (player == null) {
                System.out.println("No current player, skip this step.");
                return;
            }
            handleGame(gameMap, player);
        } catch (Exception e) {
            System.err.println("Critical error in call method: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void handleGame(GameMap gameMap, Player player) throws IOException {
//        tam thoi tranh loi smoke
        if(hasThrowable()){
            Weapon currentThrow = hero.getInventory().getThrowable();
            if(currentThrow!= null && currentThrow.getId().equalsIgnoreCase("SMOKE")){
                hero.revokeItem("SMOKE");
            }
        }
//        end tam thoi tranh loi
        if (currentStep != 0) {
            isShrinking = (currentSafeZone != gameMap.getSafeZone());
        }
        currentSafeZone =gameMap.getSafeZone();
        if(safeNodeToRunBo == null
                || !gameMap.getElementByIndex(safeNodeToRunBo.getX(),safeNodeToRunBo.getY()).getId().equalsIgnoreCase("ROAD")
                || !PathUtils.checkInsideSafeArea(safeNodeToRunBo,gameMap.getSafeZone(),gameMap.getMapSize())
    || PathUtils.distance(safeNodeToRunBo,player.getPosition()) >5 ){
            safeNodeToRunBo = findClosestSafeSpot(gameMap,player);
        }
        currentStep++;
        updateRestrictNode(gameMap);

        updateCooldowns();
        String currentDecision  = getDecisionForNextStep(gameMap, player);
        switch (currentDecision ) {
            case "die" -> handleDie();
            case "runBo" -> handleRunBo(gameMap, player);
            case "fight"-> handleFight(gameMap,player);
            case "hide" -> handleHide(gameMap, player);
            case "loot" -> handleLoot(gameMap, player);
            case "hunting" -> handleHunting(gameMap, player);
            default -> System.out.println("Unexpected decision: " + currentDecision );
        }


        System.out.println("=============DEBUG_PART (" + currentStep + ")=================");
        System.out.println("Current Decision is : " + currentDecision);
        System.out.println("code fest inventory gun: " + hero.getInventory().getGun());
        System.out.println("code fest inventory melee: " + hero.getInventory().getMelee());
        System.out.println("code fest inventory throw: " + hero.getInventory().getThrowable());
        System.out.println("code fest inventory special: " + hero.getInventory().getSpecial());
        System.out.println("code fest inventory heal: " + hero.getInventory().getListHealingItem());
        System.out.println("top element: " + gameMap.getElementByIndex(player.getX(),player.getY()+1).getId());
        System.out.println("bot element: " + gameMap.getElementByIndex(player.getX(),player.getY()-1).getId());
        System.out.println("right element: " + gameMap.getElementByIndex(player.getX()+1,player.getY()).getId());
        System.out.println("left element: " + gameMap.getElementByIndex(player.getX()-1,player.getY()).getId());
        System.out.println("Current Node loot Target : "+ currentNodeTarget);
    }

    public void handleDie(){
        currentNodeTarget = null;
        gocFlags = new boolean[]{ true, false, false, false };
        resetCooldowns();
    }

    private void handleHide(GameMap gameMap, Player player) throws IOException {
        Node safestSpot = findSafeSpotAwayFromEnemies(gameMap, player);
        for (Player p : gameMap.getOtherPlayerInfo()) {
            restrictNode.add(new Node(p.getX(), p.getY()));
        }
        String pathToHide = LocalPathUtils.getShortestPath(gameMap,restrictNode, player.getPosition(), safestSpot,false);
        if(pathToHide != null){
            hero.move(pathToHide.substring(0,1));
        }else{
            int size = gameMap.getMapSize();
            String escapePath = null;
            // Góc 1: dưới-trái; 2: dưới-phải; 3: trên-phải; 4: trên-trái
            if (gocFlags[0]) {
                escapePath = tryCorner(gameMap, restrictNode, player.getPosition(), size / 2 - 1, size / 2 - 1);
                gocFlags[0] = false;
                gocFlags[1] = true;  // lần sau chuyển qua góc 2
            } else if (gocFlags[1]) {
                escapePath = tryCorner(gameMap, restrictNode, player.getPosition(), size / 2 - 1, size - size / 2);
                gocFlags[1] = false;
                gocFlags[2] = true;
            } else if (gocFlags[2]) {
                escapePath = tryCorner(gameMap, restrictNode, player.getPosition(), size - size / 2, size - size / 2);
                gocFlags[2] = false;
                gocFlags[3] = true;
            } else if (gocFlags[3]) {
                escapePath = tryCorner(gameMap, restrictNode, player.getPosition(), size - size / 2, size / 2 - 1);
                gocFlags[3] = false;
                gocFlags[0] = true;
            }
            if(escapePath != null){
                hero.move(escapePath.substring(0,1));
            }else{
                Node center = PathUtils.getCenterOfMap(gameMap.getMapSize());
                String pathToCenter = PathUtils.getShortestPath(gameMap,restrictNode,player.getPosition(),center,false);

                hero.move(pathToCenter.substring(0,1));
            }
        }

    }
    private String tryCorner(
            GameMap map,
            List<Node> restricted,
            Node current,
            int tx, int ty
    ) {
        Node corner = new Node(tx, ty);
        return LocalPathUtils.getShortestPath(map, restricted, current, corner, true);
    }

    private Node findSafeSpotAwayFromEnemies(GameMap gameMap, Player player) {
        int mapSize = gameMap.getMapSize();
        int safeZone = gameMap.getSafeZone();
        int centerX = mapSize / 2;
        int centerY = mapSize / 2;

        List<Enemy> enemies = gameMap.getListEnemies();
        Node safestNode = null;
        int maxDistanceToEnemy = Integer.MIN_VALUE;

        // Giới hạn vùng safe zone
        int minX = Math.max(centerX - safeZone, 0);
        int maxX = Math.min(centerX + safeZone, mapSize - 1);
        int minY = Math.max(centerY - safeZone, 0);
        int maxY = Math.min(centerY + safeZone, mapSize - 1);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                Node node = new Node(x, y);
                if (restrictNode.contains(node)) continue;

                // Tính khoảng cách tới enemy gần nhất
                int minDistToEnemy = Integer.MAX_VALUE;
                for (Enemy enemy : enemies) {
                    int dist = PathUtils.distance(node, new Node(enemy.getX(), enemy.getY()));
                    if (dist < minDistToEnemy) {
                        minDistToEnemy = dist;
                    }
                }

                // Chọn node có khoảng cách xa nhất với enemy
                if (minDistToEnemy > maxDistanceToEnemy) {
                    maxDistanceToEnemy = minDistToEnemy;
                    safestNode = node;
                }
            }
        }
        // Nếu không tìm được (do restrict hoặc enemy áp đảo), thì về gần tâm
        return safestNode != null ? safestNode : new Node(centerX, centerY);
    }

    private int gunCooldownTick = 0;
    private int throwCooldownTick = 0;
    private int meleeCooldownTick = 0;
    private int specialCooldownTick = 0;
    public boolean canUseGun(Player player, Node target, GameMap gameMap)     {
        return gunCooldownTick == 0 && canShoot(player,target,gameMap);


    }
    public boolean canUseThrow()   { return throwCooldownTick == 0; }
    public boolean canUseMelee()   { return meleeCooldownTick == 0; }
    public boolean canUseSpecial() { return specialCooldownTick == 0; }
    public boolean hasGun(){return hero.getInventory().getGun() !=null; }
    public boolean hasThrowable(){return hero.getInventory().getThrowable() !=null; }
    public boolean hasMelee(){ return hero.getInventory().getMelee() != null
            && !"HAND".equalsIgnoreCase(hero.getInventory().getMelee().getId()); }
    public boolean hasSpecial(){return hero.getInventory().getSpecial()!= null;}
    public boolean hasHealingItem(){
        return hero.getInventory().getListHealingItem().stream()
                .anyMatch(item -> healingSet.contains(item.getId()));
    }

    public boolean hasSupportItem() {
        return hero.getInventory().getListHealingItem().stream()
                .anyMatch(item -> specialSet.contains(item.getId()));
    }

    public boolean hasHelmet(){return hero.getInventory().getHelmet()!= null; }
    public boolean hasArmor(){return hero.getInventory().getArmor()!= null; }

    private void handleFight(GameMap gameMap, Player player) throws IOException {
        List<Player> otherPlayer = gameMap.getOtherPlayerInfo().stream().filter(p->p.getHealth()>0).toList();

        // --- SUPPORT ITEM LOGIC ---
        // Nếu máu thấp và có support item (healing/special) thì dùng trước khi đánh
        if ((hasHealingItem() || hasSupportItem()) && player.getHealth() < 100 * 0.7) {
            // Ưu tiên dùng ELIXIR_OF_LIFE, ELIXIR, MAGIC, COMPASS nếu có
            List<String> supportPriority = Arrays.asList(
                    "ELIXIR_OF_LIFE", "ELIXIR", "MAGIC", "COMPASS",
                    "GOD_LEAF", "SPIRIT_TEAR", "MERMAID_TAIL", "PHOENIX_FEATHERS", "UNICORN_BLOOD"
            );
            for (Element e : hero.getInventory().getListHealingItem()) {
                if (supportPriority.contains(e.getId())) {
                    hero.useItem(e.getId());
                    return;
                }
            }
        }

        List<Weapon> myWeapon = getMyListReadyWeapon();
        int maxRange = myWeapon.stream().mapToInt(this::getRangeWeaponAHead).max().orElse(1);
        List<Player> playersInRange = otherPlayer.stream().filter(p ->
                PathUtils.distance(player.getPosition(),p) <= maxRange
        ).toList();

        // --- NPC DODGE LOGIC: Nếu bị NPC nguy hiểm áp sát thì né tránh ---
        if (dodgeIfDangerousNpcNearby(gameMap, player)) {
            return;
        }

        // Nếu không có ai trong tầm thì bỏ qua
        if (playersInRange.isEmpty()) {
            dodgeOrRetreat(player, gameMap, player.getPosition());
            return;
        }

        Player target = playersInRange.stream().min(Comparator.comparingDouble(Player::getHealth)).orElse(null);

        restrictNode.remove(target);
        restrictNode.addAll(gameMap.getListChests().stream().filter(o->o.getHp()>0).toList());
        int distanceWithTarget = PathUtils.distance(player.getPosition(),target.getPosition());
        List<Weapon> myWeaponCanUse = getMyListReadyCanUseToFight(player,target,gameMap)
                .stream().filter(w-> getRangeWeaponAHead(w) >= distanceWithTarget).toList();


        Weapon currenWeapon = myWeaponCanUse.stream()
                .filter(w -> !w.getId().equalsIgnoreCase("SMOKE")) // Không dùng SMOKE để tấn công!
                .max(Comparator.comparingInt(Weapon::getDamage)).orElse(null);


        if(currenWeapon !=null){
            boolean canAttack = canAttack(player.getPosition(),target.getPosition(),maxRange);
            String dir = getDirection(player.getPosition(), target.getPosition());

            if (!canAttack) {
                String pathToAttack = getPathToAttack(gameMap,player.getPosition(),target.getPosition(),maxRange);
                if (pathToAttack == null || pathToAttack.isEmpty()) {
                    dodgeOrRetreat(player, gameMap, target.getPosition());
                    return;
                }
                hero.move(pathToAttack.substring(0,1));
            }else{
                System.out.printf("Hero dùng %s đánh Player[%d,%d]\n",
                        currenWeapon.getId(), target.getX(), target.getY());
                if(currenWeapon.getId().equalsIgnoreCase("ROPE")
                        || currenWeapon.getId().equalsIgnoreCase("BELL")
                        || currenWeapon.getId().equalsIgnoreCase("SAHUR_BAT")){
                    hero.useSpecial(dir);
                    specialCooldownTick = (int)Math.ceil(currenWeapon.getCooldown());
                }
                if(currenWeapon.getId().equalsIgnoreCase("SCEPTER")
                        || currenWeapon.getId().equalsIgnoreCase("CROSSBOW")
                        || currenWeapon.getId().equalsIgnoreCase("RUBBER_GUN")
                        || currenWeapon.getId().equalsIgnoreCase("SHOTGUN")){

                    hero.shoot(dir);
                    gunCooldownTick = (int)Math.ceil(currenWeapon.getCooldown());
                }
                if(currenWeapon.getId().equalsIgnoreCase("BANANA")
                        || currenWeapon.getId().equalsIgnoreCase("METEORITE_FRAGMENT")
                        || currenWeapon.getId().equalsIgnoreCase("CRYSTAL")
                        || currenWeapon.getId().equalsIgnoreCase("SEED")){
                    hero.throwItem(dir,getRangeWeaponAHead(currenWeapon));
                    throwCooldownTick = (int)Math.ceil(currenWeapon.getCooldown());
                }

                if(currenWeapon.getId().equalsIgnoreCase("KNIFE")
                        || currenWeapon.getId().equalsIgnoreCase("TREE_BRANCH")
                        || currenWeapon.getId().equalsIgnoreCase("BONE")
                        || currenWeapon.getId().equalsIgnoreCase("AXE")
                        || currenWeapon.getId().equalsIgnoreCase("MACE")
                ){
                    hero.attack(dir);
                    meleeCooldownTick = (int)Math.ceil(currenWeapon.getCooldown());
                }
            }
        }else{
            dodgeOrRetreat(player, gameMap,target.getPosition());
        }


    }
    private void dodgeOrRetreat(Player player,GameMap gameMap,Node enemyNode) throws IOException {
        int px = player.getX(), py = player.getY();
        int ex = enemyNode.getX(), ey = enemyNode.getY();

        String dir = null;
        int width = gameMap.getMapSize(), height = gameMap.getMapSize(); // kích thước map

        // 1) Nếu đang cùng cột => dịch ngang (tránh y alignment)
        if (px == ex) {
            if (px + 1 < width) {
                dir = "r";
            } else if (px - 1 >= 0) {
                dir = "l";
            }
        }
        // 2) Nếu đang cùng hàng => dịch dọc (tránh x alignment)
        else if (py == ey) {
            if (py + 1 < height) {
                dir = "u";
            } else if (py - 1 >= 0) {
                dir = "d";
            }
        }

        // 3) Nếu không còn cùng hàng/cột, nhưng vẫn quá gần (dist <=1) => lùi xa theo trục lớn hơn
        if (dir == null) {
            int dx = px - ex;
            int dy = py - ey;
            int dist = Math.abs(dx) + Math.abs(dy);
            if (dist <= 1) {
                // lui thêm 1 ô sao cho tăng Manhattan distance
                if (Math.abs(dx) >= Math.abs(dy)) {
                    dir = dx >= 0 ? "r" : "l";
                } else {
                    dir = dy >= 0 ? "u" : "d";
                }
            }
        }

        // 4) Nếu vẫn chưa xác định được, fallback lùi ngược hướng tới kẻ địch
        if (dir == null) {
            int dx = px - ex;
            int dy = py - ey;
            // chọn hướng lui ngược lại hướng tấn công
            if (Math.abs(dx) >= Math.abs(dy)) {
                dir = dx >= 0 ? "r" : "l";
            } else {
                dir = dy >= 0 ? "u" : "d";
            }
        }

        // Thực hiện di chuyển
        hero.move(dir);
    }

    public String getPathToAttack(GameMap gameMap, Node current, Node enemy, int range) {
        List<Node> candidatePositions = new ArrayList<>();
        int x2 = enemy.getX();
        int y2 = enemy.getY();

        // Xét tất cả vị trí trong tầm range trên cùng hàng
        for (int dx = -range; dx <= range; dx++) {
            if (dx == 0) continue;
            int x = x2 + dx;
            Node curNode = new Node(x,y2);
            if (Math.abs(dx) <= range && x >= 0 && x < gameMap.getMapSize()
            && PathUtils.checkInsideSafeArea(curNode,gameMap.getSafeZone(),gameMap.getMapSize())) {
                candidatePositions.add(curNode);
            }
        }

        // Xét tất cả vị trí trong tầm range trên cùng cột
        for (int dy = -range; dy <= range; dy++) {
            if (dy == 0) continue;
            int y = y2 + dy;
            Node curNode = new Node(x2,y);
            if (Math.abs(dy) <= range && y >= 0 && y < gameMap.getMapSize()
            && PathUtils.checkInsideSafeArea(curNode,gameMap.getSafeZone(),gameMap.getMapSize())) {
                candidatePositions.add(curNode);
            }
        }

        String bestPath = null;
        int minLength = Integer.MAX_VALUE;

        for (Node target : candidatePositions) {
            String path = LocalPathUtils.getShortestPath(gameMap, restrictNode, current, target, false);
            if (path != null && path.length() < minLength) {
                bestPath = path;
                minLength = path.length();
            }
        }

        return bestPath;
    }


    public static boolean canAttack(Node player, Node enemy, int range) {
        int x1 = player.getX(), y1 = player.getY();
        int x2 = enemy.getX(), y2 = enemy.getY();

        // Điều kiện: Cùng hàng hoặc cùng cột
        boolean sameRow = y1 == y2;
        boolean sameCol = x1 == x2;

        if (!sameRow && !sameCol) return false;

        // Tính khoảng cách Manhattan (vì chỉ cần thẳng hàng thì chỉ một chiều có độ lệch)
        int distance = Math.abs(x1 - x2) + Math.abs(y1 - y2);

        return distance <= range;
    }
    public boolean canShoot(Node player, Node enemy, GameMap gameMap) {
        if(enemy == null){
            return false;
        }
        if (player.getX() != enemy.getX() && player.getY() != enemy.getY()) {
            return false; // Không thể bắn chéo
        }

        // Lấy tất cả obstacle không thể bắn xuyên qua
        List<Obstacle> allObstacles = gameMap.getListObstacles();
        List<Obstacle> blockBullets = allObstacles.stream()
                .filter(ob -> !ob.getTag().contains(ObstacleTag.CAN_SHOOT_THROUGH))
                .toList();

        // Kiểm tra có obstacle nằm giữa không
        for (Obstacle ob : blockBullets) {
            Node pos = ob.getPosition();

            if (player.getX() == enemy.getX()) { // Cùng cột
                if (pos.getX() == player.getX() &&
                        isBetween(pos.getY(), player.getY(), enemy.getY())) {
                    return false; // Có vật cản giữa
                }
            } else { // Cùng hàng
                if (pos.getY() == player.getY() &&
                        isBetween(pos.getX(), player.getX(), enemy.getX())) {
                    return false; // Có vật cản giữa
                }
            }
        }

        return true; // Không có gì cản, có thể bắn
    }

    // Helper: kiểm tra giá trị có nằm giữa 2 điểm
    private boolean isBetween(int mid, int a, int b) {
        return (mid > Math.min(a, b) && mid < Math.max(a, b));
    }


    private static String getDirection(Node p, Node e) {
        int dx = e.getX() - p.getX();
        int dy = e.getY() - p.getY();
        if (Math.abs(dx) >= Math.abs(dy)) {
            return dx > 0 ? "r" : "l";
        } else {
            return dy > 0 ? "u" : "d";
        }
    }
    private int getRangeWeaponAHead(Weapon currenWeapon){
        if (currenWeapon.getId().equalsIgnoreCase("KNIFE")) return 1;
        if (currenWeapon.getId().equalsIgnoreCase("TREE_BRANCH")) return 1;
        if (currenWeapon.getId().equalsIgnoreCase("BONE")) return 1;
        if (currenWeapon.getId().equalsIgnoreCase("AXE")) return 1;
        if (currenWeapon.getId().equalsIgnoreCase("MACE")) return 3;

        if (currenWeapon.getId().equalsIgnoreCase("SCEPTER")) return 12;
        if (currenWeapon.getId().equalsIgnoreCase("CROSSBOW")) return 4;
        if (currenWeapon.getId().equalsIgnoreCase("RUBBER_GUN")) return 12;
        if (currenWeapon.getId().equalsIgnoreCase("SHOTGUN")) return 2;

        if (currenWeapon.getId().equalsIgnoreCase("BANANA")) return 6;
        if (currenWeapon.getId().equalsIgnoreCase("METEORITE_FRAGMENT")) return 6;
        if (currenWeapon.getId().equalsIgnoreCase("CRYSTAL")) return 6;
        if (currenWeapon.getId().equalsIgnoreCase("SEED")) return 5;

        if (currenWeapon.getId().equalsIgnoreCase("ROPE")) return 6;
        if (currenWeapon.getId().equalsIgnoreCase("BELL")) return 3;
        if (currenWeapon.getId().equalsIgnoreCase("SAHUR_BAT")) return 5;
        return 1;
    }
    private List<Weapon> getMyListReadyWeapon(){
        List<Weapon> result = new ArrayList<>();
        // Xử lý inventory null
        Inventory inv = hero.getInventory();
        if (inv == null) return result;

        Weapon gun = inv.getGun();
        if (hasGun() && gunCooldownTick == 0 && gun != null) {
            result.add(gun);
        }
        Weapon melee = inv.getMelee();
        if (hasMelee() && canUseMelee() && melee != null) {
            result.add(melee);
        }
        Weapon throwable = inv.getThrowable();
        if (hasThrowable() && canUseThrow() && throwable != null) {
            result.add(throwable);
        }
        Weapon special = inv.getSpecial();
        if (hasSpecial() && canUseSpecial() && special != null) {
            result.add(special);
        }
        return result;
    }

    private List<Weapon> getMyListReadyCanUseToFight( Player player, Node target, GameMap gameMap){
        List<Weapon> result = new ArrayList<>();
        if(hasGun() && canUseGun(player,target,gameMap) ){
            result.add(hero.getInventory().getGun());
        }
        if(hasMelee() && canUseMelee()){
            result.add(hero.getInventory().getMelee());
        }
        if(hasThrowable() && canUseThrow()){
            result.add(hero.getInventory().getThrowable());
        }
        if(hasSpecial() && canUseSpecial()){
            result.add(hero.getInventory().getSpecial());
        }
        return result;
    }

    public void updateCooldowns() {
        if (gunCooldownTick > 0) gunCooldownTick--;
        if (throwCooldownTick > 0) throwCooldownTick--;
        if (meleeCooldownTick > 0) meleeCooldownTick--;
        if (specialCooldownTick > 0) specialCooldownTick--;
    }
    public void resetCooldowns(){
        gunCooldownTick = 0;
        throwCooldownTick = 0;
        meleeCooldownTick = 0;
        specialCooldownTick = 0;
    }

    private void updateRestrictNode(GameMap gameMap) {
        //Tránh phình bộ nhớ hoặc trùng lặp node.
        restrictNode.clear();
        List<Node> nodes = new ArrayList<>(gameMap.getListIndestructibles());
        Iterator<Node> iterator = nodes.iterator();
        while (iterator.hasNext()) {
            Node n = iterator.next();
            if (n instanceof Element e) {
                if ("BUSH".equalsIgnoreCase(e.getId())
                || "POND".equalsIgnoreCase(e.getId())
                ) {
                    iterator.remove();
                }
            }
        }
        nodes.addAll(gameMap.getOtherPlayerInfo());
        restrictNode.addAll(nodes);
        restrictNode.addAll(gameMap.getListChests());
        restrictNode.removeAll(gameMap.getListChests().stream().filter(o->o.getHp()<=0).toList());
        addEnemyToRestrict(gameMap);

        // Loại trùng lặp node sau cùng
        removeDuplicateNodes();
    }

    private void removeDuplicateNodes() {
        Set<String> seen = new HashSet<>();
        Iterator<Node> iterator = restrictNode.iterator();
        while (iterator.hasNext()) {
            Node n = iterator.next();
            String key = n.getX() + ":" + n.getY();
            if (!seen.add(key)) {
                iterator.remove();
            }
        }
    }

    public void addEnemyToRestrict(GameMap gameMap){
        //_Cho enemy vao retriced__________________________
        List<Enemy> listEnemies = gameMap.getListEnemies();
        if (listNodeEnemySave.isEmpty())
            listNodeEnemySave.addAll(listEnemies);
        else {
            if (currentStep == 12) {
                for (int i = 0; i < listEnemies.size(); i++) {
                    if (enemyDirection.length > i && enemyDirection[i] != null && enemyDirection[i].equals("doc")) {
                        int xMax = Integer.MIN_VALUE;
                        int xMin = Integer.MAX_VALUE;
                        for (int j = 0; j < 121; j++) {
                            if (enemyMap[j][toado[i]][i] != null && enemyMap[j][toado[i]][i]) {
                                if (j > xMax) xMax = j;
                                if (j < xMin) xMin = j;
                            }
                        }
                        enemyMinEdge[i] = xMin;
                        enemyMaxEdge[i] = xMax;
                    }
                }
                for (int i = 0; i < listEnemies.size(); i++) {
                    if (enemyDirection.length > i && enemyDirection[i] != null && enemyDirection[i].equals("ngang")) {
                        int yMax = Integer.MIN_VALUE;
                        int yMin = Integer.MAX_VALUE;
                        for (int j = 0; j < 121; j++) {
                            if (enemyMap[toado[i]][j][i] != null && enemyMap[toado[i]][j][i]) {
                                if (j > yMax) yMax = j;
                                if (j < yMin) yMin = j;
                            }
                        }
                        enemyMinEdge[i] = yMin;
                        enemyMaxEdge[i] = yMax;
                    }
                }
                System.out.println("Gan xong");
            }
            if (currentStep >= 12) {
                List<Node> temp = new ArrayList<>();

                for (int i = 0; i < gameMap.getListEnemies().size(); i++) {
                    if (enemyDirection[i].equals("ngang")) {
                        int count = 0;
                        for (int j = enemyMinEdge[i] - 1; j <= enemyMaxEdge[i] + 1; j++) {
                            if (count % 2 == 1) {
                                temp.add(new Node(toado[i], j));
                                temp.add(new Node(toado[i] - 1, j));
                                temp.add(new Node(toado[i] + 1, j));
                            }
                            count++;
                        }
                    }
                    if (enemyDirection[i].equals("doc")) {
                        int count = 0;
                        for (int j = enemyMinEdge[i] - 1; j <= enemyMaxEdge[i] + 1; j++) {
                            if (count % 2 == 1) {
                                temp.add(new Node(j, toado[i]));
                                temp.add(new Node(j, toado[i] - 1));
                                temp.add(new Node(j, toado[i] + 1));
                            }
                            count++;
                        }
                    }
                }
                restrictNode.addAll(temp);

            }
            if (currentStep < 12) {
                for (int i = 0; i < gameMap.getListEnemies().size(); i++) {
                    List<Node> temp = new ArrayList<>();
                    enemyMap[listEnemies.get(i).x][listEnemies.get(i).y][i] = true;
                    if (listEnemies.get(i).x > listNodeEnemySave.get(i).x && listEnemies.get(i).y == listNodeEnemySave.get(i).y) {
                        enemyDirection[i] = "doc";
                        toado[i] = listEnemies.get(i).y;
                        for (int j = listEnemies.get(i).x - 1; j < listEnemies.get(i).x + 5; j++) {
                            for (int k = listEnemies.get(i).y - 1; k < listEnemies.get(i).y + 2; k++) {
                                temp.add(new Node(j, k));
                            }
                        }
                    }
                    if (listEnemies.get(i).x < listNodeEnemySave.get(i).x && listEnemies.get(i).y == listNodeEnemySave.get(i).y) {
                        enemyDirection[i] = "doc";
                        toado[i] = listEnemies.get(i).y;
                        for (int j = listEnemies.get(i).x - 4; j < listEnemies.get(i).x + 2; j++) {
                            for (int k = listEnemies.get(i).y - 1; k < listEnemies.get(i).y + 2; k++) {
                                temp.add(new Node(j, k));
                            }
                        }
                    }
                    if (listEnemies.get(i).x == listNodeEnemySave.get(i).x && listEnemies.get(i).y > listNodeEnemySave.get(i).y) {
                        enemyDirection[i] = "ngang";
                        toado[i] = listEnemies.get(i).x;
                        for (int j = listEnemies.get(i).y - 1; j < listEnemies.get(i).y + 5; j++) {
                            for (int k = listEnemies.get(i).x - 1; k < listEnemies.get(i).x + 2; k++) {
                                temp.add(new Node(k, j));
                            }
                        }
                    }
                    if (listEnemies.get(i).x == listNodeEnemySave.get(i).x && listEnemies.get(i).y < listNodeEnemySave.get(i).y) {
                        enemyDirection[i] = "ngang";
                        toado[i] = listEnemies.get(i).x;
                        for (int j = listEnemies.get(i).y - 4; j < listEnemies.get(i).y + 2; j++) {
                            for (int k = listEnemies.get(i).x - 1; k < listEnemies.get(i).x + 2; k++) {
                                temp.add(new Node(k, j));
                            }
                        }
                    }
                    restrictNode.addAll(temp);
                    listNodeEnemySave.remove(i);
                    listNodeEnemySave.add(i, listEnemies.get(i));
                }
            }
        }
    }

    public String getDecisionForNextStep(GameMap gameMap, Player player) {
        if (player == null || player.getHealth() <= 0) {
            return "die";
        }
        if(!PathUtils.checkInsideSafeArea(player.getPosition(),gameMap.getSafeZone(),gameMap.getMapSize())){
            System.out.println("vao day thi con js lam an nhu lon");
            return "runBo";
        }

        int x = player.getX(), y = player.getY();
        boolean needRunBo = !PathUtils.checkInsideSafeArea(new Node(x, y), gameMap.getSafeZone()-3, gameMap.getMapSize())
                && isShrinking;

        List<Player> others = gameMap.getOtherPlayerInfo().stream()
                .filter(p -> p.getHealth() > 0).toList();
        List<Weapon> myWeapon = getMyListReadyWeapon();
        int maxRange = myWeapon.stream().mapToInt(this::getRangeWeaponAHead).max().orElse(1);
        System.out.println("maxRange: " + maxRange);

        Player closest = others.stream()
                .filter(p -> PathUtils.distance(player.getPosition(), new Node(p.getX(), p.getY())) <= maxRange)
                .min(Comparator.comparingDouble(p ->
                        PathUtils.distance(player.getPosition(), new Node(p.getX(), p.getY()))
                ))
                .orElse(null);

        List<Weapon> myWeaponCanUse = getMyListReadyCanUseToFight(player,closest,gameMap);
        boolean enemyInRange = closest != null && !myWeaponCanUse.isEmpty();


        // condition for loot
        boolean needLoot = !hasGun() && gameMap.getAllGun() != null;
        List<Node> listChest =  findNearbyChests(gameMap,player.getX(),player.getY());
        boolean chestExist = !listChest.isEmpty();
        if(chestExist){
            needLoot = true;
        }
            if (!hasThrowable() && gameMap.getAllThrowable() != null) {
                needLoot = true;
            }
            if (!hasHealingItem() && gameMap.getListHealingItems() != null) {
                needLoot = true;
            }
            if (!hasMelee() && gameMap.getAllMelee() !=null) {
                needLoot = true;
            }

            if ((!hasArmor() && !hasHelmet()) && gameMap.getListArmors() != null) {
                needLoot = true;
            }

            if (!hasSpecial() && gameMap.getAllSpecial() != null) {
                needLoot = true;
            }
//        end condition loot
        // Nếu vừa muốn loot vừa có enemy trong tầm
        if (needLoot && enemyInRange) {
            if (currentNodeTarget == null) {
                needLoot = false; // Không có gì để loot
            } else {
                int distanceWithLoot = PathUtils.distance(player.getPosition(), currentNodeTarget);
                int distanceWithEnemy = PathUtils.distance(player.getPosition(), closest.getPosition());

                if (distanceWithEnemy <= 3) {
                    // Địch rất gần → Ưu tiên chiến
                    needLoot = false;
                }
                else if (distanceWithLoot <= 2 && distanceWithEnemy > 6) {
                    // Loot gần, enemy không đe dọa → ưu tiên loot
                    enemyInRange = false;
                }
                else if (distanceWithEnemy >= 8 && distanceWithEnemy <= 12) {
                    // Địch trong tầm bắn xa → ưu tiên chiến
                    needLoot = false;
                }
                else if (distanceWithEnemy > 12 && distanceWithLoot <= 5) {
                    // Địch rất xa, loot hơi gần → loot
                    enemyInRange = false;
                }
                else {
                    // Trường hợp mơ hồ → chọn ưu tiên mặc định, ví dụ ưu tiên đánh
                    needLoot = false;
                }
            }
        }

        if (needRunBo) return "runBo";
        if (enemyInRange) return "fight";
        if (needLoot) return "loot";
        return "hunting";
    }




    private void handleRunBo(GameMap gameMap, Player player) throws IOException {
        String pathRun = LocalPathUtils.getShortestPath(gameMap,restrictNode,player.getPosition(),safeNodeToRunBo,true);
        if(pathRun == null){
            handleHide(gameMap,player);
        }else{
            hero.move(pathRun.substring(0,1));
        }

    }

    private Node findClosestSafeSpot(GameMap gameMap, Player player) {
        int mapSize = gameMap.getMapSize();
        int safeZone = gameMap.getSafeZone()-8;
        int centerX = mapSize / 2;
        int centerY = mapSize / 2;

        Node playerNode = new Node(player.getX(), player.getY());

        // Giới hạn biên của safe zone
        int minX = centerX - safeZone;
        int maxX = centerX + safeZone;
        int minY = centerY - safeZone;
        int maxY = centerY + safeZone;
        // Clamp giá trị trong giới hạn bản đồ
        minX = Math.max(minX, 0);
        maxX = Math.min(maxX, mapSize - 1);
        minY = Math.max(minY, 0);
        maxY = Math.min(maxY, mapSize - 1);

        Node closest = null;
        int minDistance = Integer.MAX_VALUE;

        restrictNode.addAll(gameMap.getOtherPlayerInfo());
        // Tìm ô gần nhất trong vùng safe
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                Node node = new Node(x, y);
                if (!restrictNode.contains(node)) { // Tránh chướng ngại vật
                    int dist = PathUtils.distance(playerNode, node);
                    if (dist < minDistance) {
                        minDistance = dist;
                        closest = node;
                    }
                }
            }
        }

        return closest != null ? closest : new Node(centerX, centerY);
    }

    private void handleHunting(GameMap gameMap, Player player) throws IOException {
        if (dodgeIfDangerousNpcNearby(gameMap, player)) {
            return;
        }
        Player closestPlayer = gameMap.getOtherPlayerInfo().stream()
                .filter(p -> p.getHealth() > 0)
                .min(Comparator.comparingInt(p -> PathUtils.distance(player.getPosition(), p.getPosition())))
                .orElse(null);
        if(closestPlayer != null){
            restrictNode.remove(closestPlayer);
            String pathToClosetPlayer = PathUtils.getShortestPath(gameMap,restrictNode,player.getPosition(),closestPlayer,false);
            hero.move(pathToClosetPlayer.substring(0,1));
        }else{
            handleHide(gameMap,player);
        }
    }

    public void handleLoot(GameMap gameMap, Player player) throws IOException {
        // --- NPC DODGE LOGIC: Nếu bị NPC nguy hiểm áp sát thì né tránh ---
        if (dodgeIfDangerousNpcNearby(gameMap, player)) {
            return;
        }

        boolean canHeal = hasHealingItem()
                && player.getHealth() < 100 * 0.9;
        if(canHeal) {
            List<String> supportPriority = Arrays.asList(
                    "ELIXIR_OF_LIFE", "GOD_LEAF", "SPIRIT_TEAR", "MERMAID_TAIL", "PHOENIX_FEATHERS", "UNICORN_BLOOD"
            );
            for (Element e : hero.getInventory().getListHealingItem()) {
                if (supportPriority.contains(e.getId())) {
                    hero.useItem(e.getId());
                    return;
                }
            }
        }

        Node currentNode = new Node(player.getX(), player.getY());
        List<Node> targets = collectLootTargets(
                gameMap,
                currentNode
        );
        String path = findBestPath(
                gameMap,
                currentNode,
                targets,
                gameMap.getOtherPlayerInfo()
        );
        System.out.println("current loot Path: " + path);
        // Nếu không tìm được path hoặc path rỗng, chuyển sang hide để tránh đứng yên/lặp lại
        if (path == null || path.isEmpty()) {
            System.out.println("Loot path invalid, fallback to hide to avoid stuck.");
            handleHide(gameMap, player);
            return;
        }
      executePathOrLoot(
                gameMap,
                currentNode,
                path
        );
    }


    private boolean checkLootHealingItem(Element nextLootItem) {
        int healingCount = 0, specialCount = 0;

        for (Element e : hero.getInventory().getListHealingItem()) {
            String id = e.getId();
            if (healingSet.contains(id)) healingCount++;
            else if (specialSet.contains(id)) specialCount++;
        }

        int totalCount = healingCount + specialCount;
        String nextId = nextLootItem.getId();

        if (totalCount >= 4) return false;
        if (healingSet.contains(nextId)) return healingCount < 2;
        if (specialSet.contains(nextId)) return specialCount < 2;
        return false;
    }

    public List<Node> collectLootTargets(
            GameMap map,
            Node current
    ) {
        List<Node> targets = new ArrayList<>();
        int x = current.x, y = current.y;
        if (!hasGun() &&  existsWithin(map.getAllGun(), current, 5)) {
            addNearbySafeGunTargets(map, current, 5, targets);
            return targets;
        }


        if (hero.getInventory().getListHealingItem().size() < 4) {
            addTargetsFromList(
                    map.getListHealingItems(),
                    item -> new Node(item.getX(), item.getY()),
                    map,
                    targets,
                    this::checkLootHealingItem
            );
        }

        if (!hasHelmet()) {
            addTargetsFromList(
                    map.getListArmors(),
                    a -> new Node(a.getX(), a.getY()),
                    map,
                    targets,
                    armor -> armor.getId().equals("WOODEN_HELMET")
                            || armor.getId().equals("MAGIC_HELMET")
            );
        }

        if (!hasArmor()) {
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
        if (!hasGun()) {
            addTargetsFromList(map.getAllGun(), w -> new Node(w.getX(), w.getY()), map, targets);
        }
        if (!hasMelee()) {
            addTargetsFromList(map.getAllMelee(), w -> new Node(w.getX(), w.getY()), map, targets);
        }
        if (!hasThrowable() ) {
            addTargetsFromList(map.getAllThrowable(), w -> new Node(w.getX(), w.getY()), map, targets);
        }

        if(!hasSpecial()){
            addTargetsFromList(map.getAllSpecial(), w -> new Node(w.getX(), w.getY()), map, targets);
        }


        if (!hasGun() || !hasMelee() || !hasThrowable() || !hasHelmet() || !hasArmor() || hero.getInventory().getListHealingItem().size() <4) {
            targets.addAll(findNearbyChests(map, x, y));
        }

        return targets;
    }
    private  <T> void addTargetsFromList(
            List<T> items,
            Function<T, Node> toNode,
            GameMap map,
            List<Node> outTargets
    ) {
        addTargetsFromList(items, toNode, map, outTargets, item -> true);
    }

    private <T> void addTargetsFromList(
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
    private <T extends Element> boolean existsWithin(List<T> items, Node cur, int maxDist) {
        for (T it : items) {
            if (PathUtils.distance(it,cur) <= maxDist) {
                return true;
            }
        }
        return false;
    }
    private void addNearbySafeGunTargets(GameMap map, Node cur, int maxDist, List<Node> out) {
        for (Weapon g : map.getAllGun()) {
            Node n = new Node(g.getX(), g.getY());
            if (PathUtils.distance(n, cur) <= maxDist &&
                    PathUtils.checkInsideSafeArea(n, map.getSafeZone(), map.getMapSize())) {
                out.add(n);

            }
        }
    }

    private List<Node> findNearbyChests(GameMap map, int x, int y) {
        List<Node> result = new ArrayList<>();
        List<Obstacle> chests = map.getListChests();
        int size = map.getMapSize();
        for (int r = 1; r < size; r++) {
            for (Obstacle c : chests) {
                if (c.getHp() <= 0) continue;
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

    public String findBestPath(
            GameMap map,
            Node current,
            List<Node> targets,
            List<Player> otherPlayers

    ) {
        int minDistance = Integer.MAX_VALUE;
        String path = null;
        for (Node tgt : targets) {
            if(PathUtils.distance(tgt,current)< minDistance){
                minDistance = PathUtils.distance(tgt,current);
                currentNodeTarget = tgt;
            }
        }
        if(currentNodeTarget != null){
            path = LocalPathUtils.getShortestPath(map, restrictNode, current, currentNodeTarget, false);
        }
        if(path!= null && !path.isEmpty()){
            return path;
        }
        for (Player p : otherPlayers) {
            restrictNode.add(new Node(p.getX(), p.getY()));
        }
        int size = map.getMapSize();
        String escapePath = null;
        if (gocFlags[0]) {
            escapePath = tryCorner(map, restrictNode, current, size / 2 - 1, size / 2 - 1);
            gocFlags[0] = false;
            gocFlags[1] = true;  // lần sau chuyển qua góc 2
        } else if (gocFlags[1]) {
            escapePath = tryCorner(map, restrictNode, current, size / 2 - 1, size - size / 2);
            gocFlags[1] = false;
            gocFlags[2] = true;
        } else if (gocFlags[2]) {
            escapePath = tryCorner(map, restrictNode, current, size - size / 2, size - size / 2);
            gocFlags[2] = false;
            gocFlags[3] = true;
        } else if (gocFlags[3]) {
            escapePath = tryCorner(map, restrictNode, current, size - size / 2, size / 2 - 1);
            gocFlags[3] = false;
            gocFlags[0] = true;
        }
        return escapePath;
    }


    public void executePathOrLoot(
            GameMap map,
            Node current,
            String path
    ) throws IOException {
        int x = current.x, y = current.y;
        if (currentNodeTarget!= null && PathUtils.distance(current, currentNodeTarget) == 0) {
            Element elem = map.getElementByIndex(x, y);

            if (elem != null) {


                hero.pickupItem();
                if(healingSet.contains(elem.getId())){
                    listHealingInventory.add(elem.getId());
                }
                if(specialSet.contains(elem.getId())){
                    listSupportInventory.add(elem.getId());
                }
            }
            attackAdjacentChestsNoId(hero, map, x, y);
        }
        if (path != null && !path.isEmpty()) {
            String step = path.substring(0, 1);
            hero.move(step);
        }
        attackAdjacentChestsNoId(hero, map, x, y);
    }
    void attackAdjacentChestsNoId(Hero hero, GameMap gameMap, int px, int py) {
        try {
            for (Obstacle chest : gameMap.getListChests()) {
                if (chest.getHp() <= 0) continue;
                int dx = chest.getX() - px;
                int dy = chest.getY() - py;
                // chỉ attack khi chest kề cạnh (Manhattan distance == 1)
                if (Math.abs(dx) + Math.abs(dy) == 1) {
                    String dir;
                    if (dx ==  1) dir = "r";
                    else if (dx == -1) dir = "l";
                    else if (dy ==  1) dir = "u";
                    else            dir = "d";
                    hero.attack(dir);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // Helper: trả về node mới sau khi di chuyển 1 bước theo hướng dir
    private Node moveNode(Node pos, String dir) {
        int x = pos.getX(), y = pos.getY();
        return switch (dir) {
            case "l" -> new Node(x - 1, y);
            case "r" -> new Node(x + 1, y);
            case "u" -> new Node(x, y + 1);
            case "d" -> new Node(x, y - 1);
            default -> pos;
        };
    }

    // --- Add this helper method ---
    /**
     * If a dangerous NPC is adjacent (<=3 cells), dodge and return true.
     * Otherwise, return false.
     */
    private boolean dodgeIfDangerousNpcNearby(GameMap gameMap, Player player) throws IOException {
        List<String> dangerNpcIds = Arrays.asList("NATIVE", "GHOST", "LEOPARD", "ANACONDA", "RHINO", "GOLEM");
        boolean npcAdjacent = gameMap.getListEnemies().stream().anyMatch(
                npc -> dangerNpcIds.contains(npc.getId())
                        && PathUtils.distance(player.getPosition(), new Node(npc.getX(), npc.getY())) <= 2
        );
        if (npcAdjacent) {
            for (String dir : Arrays.asList("l", "r", "u", "d")) {
                Node next = moveNode(player.getPosition(), dir);
                if (next.getX() >= 0 && next.getX() < gameMap.getMapSize()
                        && next.getY() >= 0 && next.getY() < gameMap.getMapSize()
                        && !restrictNode.contains(next)) {
                    hero.move(dir);
                    return true;
                }
            }
            // fallback: try to retreat if no direction is available
            dodgeOrRetreat(player, gameMap, player.getPosition());
            return true;
        }
        return false;
    }
}
