import io.socket.emitter.Emitter;
import jsclub.codefest.sdk.Hero;
import jsclub.codefest.sdk.algorithm.PathUtils;
import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.factory.SupportItemFactory;
import jsclub.codefest.sdk.factory.WeaponFactory;
import jsclub.codefest.sdk.model.Element;
import jsclub.codefest.sdk.model.GameMap;
import jsclub.codefest.sdk.model.Inventory;
import jsclub.codefest.sdk.model.effects.Effect;
import jsclub.codefest.sdk.model.npcs.Enemy;
import jsclub.codefest.sdk.model.obstacles.Obstacle;
import jsclub.codefest.sdk.model.obstacles.ObstacleTag;
import jsclub.codefest.sdk.model.players.Player;
import jsclub.codefest.sdk.model.support_items.SupportItem;
import jsclub.codefest.sdk.model.weapon.Weapon;
import myModule.LocalPathUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

public class Main2 {
    private static final String SERVER_URL = "https://cf25-server.jsclub.dev";
    private static final String GAME_ID = "154554";
    private static final String PLAYER_NAME = "NeuroSama";
    private static final String SECRET_KEY = "sk-jYHwqfPHRriQxLiwmFlLkQ:lw45Vm8gQPDYP6DpmdON7BngVLXhQ4wwAC8A7Sv-qVQMRPh_2siGmlFRkl0DbNR5a0x1MBjdPe_r-3MVr3Jnug";
    public static void main(String[] args) throws IOException {
        Hero hero = new Hero(GAME_ID, PLAYER_NAME, SECRET_KEY);
        Emitter.Listener onMapUpdate = new MapUpdateListener2(hero);
        hero.setOnMapUpdate(onMapUpdate);
        hero.start(SERVER_URL);
    }

}

class MapUpdateListener2 implements Emitter.Listener {
    public MapUpdateListener2(Hero hero) {
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
        if(hasSupportItem()){
            SupportItem supportItem = SupportItemFactory.getSupportItemById("ELIXIR_OF_LIFE");
            if(hero.getInventory().getListSupportItem().contains(supportItem)){
                hero.useItem(supportItem.getId());
            }
        }
        if(hasSupportItem()){
            SupportItem supportItem = SupportItemFactory.getSupportItemById("MAGIC");
            if(hero.getInventory().getListSupportItem().contains(supportItem)){
                hero.useItem(supportItem.getId());
            }
        }
        if(hasSupportItem()){
            List<Effect> currentEffect = hero.getEffects();
            boolean isCC = false;
            for (Effect effect: currentEffect){
                if(effect.id.equalsIgnoreCase("STUN")){
                    isCC = true;
                    break;
                }
            }
            SupportItem supportItem = SupportItemFactory.getSupportItemById("ELIXIR");
            if(hero.getInventory().getListSupportItem().contains(supportItem) && isCC){
                hero.useItem(supportItem.getId());
            }
        }
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
                || !PathUtils.checkInsideSafeArea(safeNodeToRunBo,gameMap.getSafeZone()-8,gameMap.getMapSize())
        ){
            safeNodeToRunBo = findClosestSafeSpot(gameMap,player);
        }
        currentStep++;
        updateRestrictNode(gameMap, player);

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
        System.out.println("code fest inventory supportItem: " + hero.getInventory().getListSupportItem());
        System.out.println("top element: " + gameMap.getElementByIndex(player.getX(),player.getY()+1).getId());
        System.out.println("bot element: " + gameMap.getElementByIndex(player.getX(),player.getY()-1).getId());
        System.out.println("right element: " + gameMap.getElementByIndex(player.getX()+1,player.getY()).getId());
        System.out.println("left element: " + gameMap.getElementByIndex(player.getX()-1,player.getY()).getId());
        System.out.println("Current Node loot Target : "+ currentNodeTarget);
        System.out.println("Current element want to loot  : "+ gameMap.getElementByIndex(currentNodeTarget.getX(),currentNodeTarget.getY()).getId());
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
            int size = gameMap.getSafeZone();
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
                Node originalCenter = new Node(gameMap.getMapSize()/2, gameMap.getMapSize()/2);
                List<Node> candidateCenters = new ArrayList<>();
                int radius = 1;
                boolean foundPath = false;
                String pathToCenter = null;
                // Tìm các node xung quanh theo bán kính tăng dần
                while (!foundPath && radius < gameMap.getMapSize()) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        for (int dy = -radius; dy <= radius; dy++) {
                            // Chỉ xét các node nằm trên "viền" của hình vuông (không lặp lại các node đã xét ở radius trước)
                            if (Math.abs(dx) == radius || Math.abs(dy) == radius) {
                                int x = originalCenter.getX() + dx;
                                int y = originalCenter.getY() + dy;

                                // Kiểm tra trong map
                                if (x >= 0 && x < gameMap.getMapSize() && y >= 0 && y < gameMap.getMapSize()) {
                                    Node candidate = new Node(x, y);
                                    pathToCenter = LocalPathUtils.getShortestPath(gameMap, restrictNode, player.getPosition(), candidate, false);

                                    if (pathToCenter != null && !pathToCenter.isEmpty()) {
                                        hero.move(pathToCenter.substring(0, 1));
                                        foundPath = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (foundPath) break;
                    }
                    radius++;
                }

                if (!foundPath) {
                    System.out.println("Không tìm thấy đường đến center hoặc xung quanh.");
                }

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
                    if(enemy != null){
                        int dist = PathUtils.distance(node, enemy.getPosition());
                        if (dist < minDistToEnemy) {
                            minDistToEnemy = dist;
                        }
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
        return hero.getInventory().getListSupportItem().stream()
                .anyMatch(item -> healingSet.contains(item.getId()));
    }

    public boolean hasSupportItem() {
        return hero.getInventory().getListSupportItem().stream()
                .anyMatch(item -> specialSet.contains(item.getId()));
    }

    public boolean hasHelmet(){return hero.getInventory().getHelmet()!= null; }
    public boolean hasArmor(){return hero.getInventory().getArmor()!= null; }

    private void handleFight(GameMap gameMap, Player player) throws IOException {
        List<Player> otherPlayer = gameMap.getOtherPlayerInfo().stream().filter(p->p.getHealth()>0).toList();


        if (hasHealingItem()&& player.getHealth() < 100 * 0.7) {
            List<String> supportPriority = Arrays.asList(

                    "GOD_LEAF", "SPIRIT_TEAR", "MERMAID_TAIL", "PHOENIX_FEATHERS", "UNICORN_BLOOD"
            );
            for (Element e : hero.getInventory().getListSupportItem()) {
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

        if (playersInRange.isEmpty()) {
            dodgeOrRetreat(player, gameMap, player.getPosition());
            return;
        }

        Player target = playersInRange.stream().min(Comparator.comparingDouble(Player::getHealth)).orElse(null);

//        restrictNode.remove(target);

        restrictNode.addAll(gameMap.getObstaclesByTag("DESTRUCTIBLE").stream().filter(o->o.getHp()>0).toList());
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

                if(hasSupportItem() && distanceWithTarget <=4){
                    SupportItem supportItem = SupportItemFactory.getSupportItemById("COMPASS");
                    if(hero.getInventory().getListSupportItem().contains(supportItem)){
                        hero.useItem(supportItem.getId());
                        return;
                    }
                }

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
                    hero.throwItem(dir);
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
                .filter(ob -> !ob.getTags().contains(ObstacleTag.CAN_SHOOT_THROUGH))
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

        if (currenWeapon.getId().equalsIgnoreCase("SCEPTER")) return 10;
        if (currenWeapon.getId().equalsIgnoreCase("CROSSBOW")) return 8;
        if (currenWeapon.getId().equalsIgnoreCase("RUBBER_GUN")) return 6;
        if (currenWeapon.getId().equalsIgnoreCase("SHOTGUN")) return 2;

        if (currenWeapon.getId().equalsIgnoreCase("BANANA")) return 6;
        if (currenWeapon.getId().equalsIgnoreCase("METEORITE_FRAGMENT")) return 6;
        if (currenWeapon.getId().equalsIgnoreCase("CRYSTAL")) return 6;
        if (currenWeapon.getId().equalsIgnoreCase("SEED")) return 5;

        if (currenWeapon.getId().equalsIgnoreCase("ROPE")) return 6;
        if (currenWeapon.getId().equalsIgnoreCase("BELL")) return 1;
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
        if(!hasMelee()){
            Weapon hand = WeaponFactory.getWeaponById("HAND");
            result.add(hand);
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

    private void updateRestrictNode(GameMap gameMap, Player player) throws IOException {
        //Tránh phình bộ nhớ hoặc trùng lặp node.
//        restrictNode.clear();
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
        restrictNode.addAll(gameMap.getObstaclesByTag("DESTRUCTIBLE"));
        restrictNode.removeAll(gameMap.getObstaclesByTag("DESTRUCTIBLE").stream().filter(o->o.getHp()<=0).toList());
        addEnemyToRestrict(gameMap, player);

        // Loại trùng lặp node sau cùng
//        removeDuplicateNodes();
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

    public void addEnemyToRestrict(GameMap gameMap, Player player) throws IOException {
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
        carveSafeZoneAndEscape(gameMap, player);
    }
    private void carveSafeZoneAndEscape(GameMap gameMap, Player player) throws IOException {
        Node p = player.getPosition();
        int size = gameMap.getMapSize();

        // 1) Kiểm tra xem 4 ô l/r/u/d xung quanh có bị chặn hết không
        boolean allBlocked = true;
        List<String> directions = List.of("l","r","u","d");
        for (String dir : directions) {
            Node n = moveNode(p, dir);
            if (n.getX() >= 0 && n.getX() < size
                    && n.getY() >= 0 && n.getY() < size
                    && !restrictNode.contains(n)) {
                allBlocked = false;
                break;
            }
        }

        if (allBlocked) {
            // 2) Carve vùng 3x3 quanh player (loại bỏ khỏi restrictNode)
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    int nx = p.getX() + dx, ny = p.getY() + dy;
                    if (nx >= 0 && nx < size && ny >= 0 && ny < size) {
                        restrictNode.remove(new Node(nx, ny));
                    }
                }
            }

            // 3) Di chuyển hero ngay một bước đầu tiên ra khỏi vị trí hiện tại
            for (String dir : directions) {
                Node next = moveNode(p, dir);
                if (next.getX() >= 0 && next.getX() < size
                        && next.getY() >= 0 && next.getY() < size
                        && !restrictNode.contains(next)) {
                    hero.move(dir);
                    return;
                }
            }
            // 4) Nếu vẫn không tìm được (góc bản đồ), fallback:
            dodgeOrRetreat(player, gameMap, p);
        }
    }

    public String getDecisionForNextStep(GameMap gameMap, Player player) {
        if (player == null || player.getHealth() <= 0) {
            return "die";
        }
        if(!PathUtils.checkInsideSafeArea(player.getPosition(),gameMap.getSafeZone(),gameMap.getMapSize())){
            return "runBo";
        }
        boolean needRunBo = !PathUtils.checkInsideSafeArea(player.getPosition(), gameMap.getSafeZone()-8, gameMap.getMapSize())
                && isShrinking;
        if (needRunBo) return "runBo";

//        condition fight
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
//     end condition fight

        // condition for loot
        boolean needLoot = !hasGun() && gameMap.getAllGun() != null;
        List<Node> listChest =  findNearbyChests(gameMap,player.getX(),player.getY());
        boolean chestExist = !listChest.isEmpty();
//        khong can special va melee de danh nhau va 1 giap la du roi
        if(chestExist && (!hasThrowable() || (hasHelmet() || hasArmor()) || !hasSupportItem() || !hasHealingItem())){
            needLoot = true;
        }
        System.out.println("Chest exist is : " + chestExist);
        boolean hasThrowableInSafeArea = gameMap.getAllThrowable().stream()
                .anyMatch(w -> PathUtils.checkInsideSafeArea(
                        w.getPosition(),
                        gameMap.getSafeZone(),
                        gameMap.getMapSize()
                ));
        if (!hasThrowable() && hasThrowableInSafeArea) {
            needLoot = true;
        }

        boolean hasSupportInSafeArea = gameMap.getListSupportItems().stream()
                .anyMatch(w -> PathUtils.checkInsideSafeArea(
                        w.getPosition(),
                        gameMap.getSafeZone(),
                        gameMap.getMapSize()
                ));

        if (hero.getInventory().getListSupportItem().size() <3 && hasSupportInSafeArea) {
            needLoot = true;
        }
        boolean hasMeleeInSafeArea = gameMap.getAllMelee().stream()
                .anyMatch(w -> PathUtils.checkInsideSafeArea(
                        w.getPosition(),
                        gameMap.getSafeZone(),
                        gameMap.getMapSize()
                ));

        if (!hasMelee() && hasMeleeInSafeArea) {
            needLoot = true;
        }
        boolean hasArmorInSafeArea = gameMap.getListArmors().stream().
                filter(armor -> armor.getId().equals("ARMOR")
                        || armor.getId().equals("MAGIC_ARMOR"))
                .anyMatch(w -> PathUtils.checkInsideSafeArea(
                        w.getPosition(),
                        gameMap.getSafeZone(),
                        gameMap.getMapSize()
                ));

        if (!hasArmor() && hasArmorInSafeArea) {
            needLoot = true;
        }
        boolean hasHelmetInSafeArea = gameMap.getListArmors().stream().
                filter(armor -> armor.getId().equals("WOODEN_HELMET")
                        || armor.getId().equals("MAGIC_HELMET"))
                .anyMatch(w -> PathUtils.checkInsideSafeArea(
                        w.getPosition(),
                        gameMap.getSafeZone(),
                        gameMap.getMapSize()
                ));
        if(!hasHelmet() && hasHelmetInSafeArea){
            needLoot = true;
        }
        boolean hasSpecialSafeArea = gameMap.getAllSpecial().stream()
                .anyMatch(w -> PathUtils.checkInsideSafeArea(
                        w.getPosition(),
                        gameMap.getSafeZone(),
                        gameMap.getMapSize()
                ));

        if (!hasSpecial() && hasSpecialSafeArea) {
            needLoot = true;
        }
//        end condition loot
        // Nếu vừa muốn loot vừa có enemy trong tầm
        if (needLoot && enemyInRange) {
            int distanceWithLoot = PathUtils.distance(player.getPosition(), currentNodeTarget);
            int distanceWithEnemy = PathUtils.distance(player.getPosition(), closest.getPosition());

            if (distanceWithEnemy <= 6) {
                needLoot = false;
            }
            else if (distanceWithLoot < distanceWithEnemy) {
                enemyInRange =false;
            }
            else {
                needLoot = false;
            }
        }
        System.out.println("In the end, needloot is : " + needLoot);

        if (enemyInRange) return "fight";
        if (needLoot) return "loot";
        return "hunting";
    }




    private void handleRunBo(GameMap gameMap, Player player) throws IOException {
        List<Player> others = gameMap.getOtherPlayerInfo().stream()
                .filter(p -> p.getHealth() > 0).toList();
        boolean hasEnemyAround = false;
        for (Player enemy: others){
            if(PathUtils.distance(player.getPosition(),enemy.getPosition()) <= 3){
                hasEnemyAround = true;
            }
        }
        if(hasSpecial()){
            Weapon currentSpecial = hero.getInventory().getSpecial();
            if(currentSpecial!= null && currentSpecial.getId().equalsIgnoreCase("BELL") && hasEnemyAround){
                hero.useItem("BELL");
            }

        }
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
//        if (dodgeIfDangerousNpcNearby(gameMap, player)) {
//            return;
//        }
        Player closestPlayer = gameMap.getOtherPlayerInfo().stream()
                .filter(p -> p.getHealth() > 0)
                .min(Comparator.comparingInt(p -> PathUtils.distance(player.getPosition(), p.getPosition())))
                .orElse(null);
        if(closestPlayer != null){
            restrictNode.remove(closestPlayer);
            String pathToClosetPlayer = PathUtils.getShortestPath(gameMap,restrictNode,player.getPosition(),closestPlayer,false);
            hero.move(pathToClosetPlayer.substring(0,1));
        }else{
            Node center = new Node(gameMap.getMapSize()/2,gameMap.getMapSize()/2);
            String pathToCenter = PathUtils.getShortestPath(gameMap,restrictNode,player.getPosition()
                    ,center,false);
            hero.move(pathToCenter.substring(0,1));
        }
    }

    public void handleLoot(GameMap gameMap, Player player) throws IOException {

        boolean canHeal = hasHealingItem()
                && player.getHealth() < 100 * 0.9;
        if(canHeal) {
            List<String> supportPriority = Arrays.asList(
                    "GOD_LEAF", "SPIRIT_TEAR", "MERMAID_TAIL", "PHOENIX_FEATHERS", "UNICORN_BLOOD"
            );
            for (Element e : hero.getInventory().getListSupportItem()) {
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

        executePathOrLoot(
                gameMap,
                player,
                path
        );
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


        if (hero.getInventory().getListSupportItem().size() < 4) {
            addTargetsFromList(
                    map.getListSupportItems(),
                    item -> new Node(item.getX(), item.getY()),
                    map,
                    targets
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


        if (!hasGun() || !hasMelee() || !hasThrowable() || !hasHelmet() || !hasArmor() || hero.getInventory().getListSupportItem().size() <4) {
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
        List<Obstacle> chests = map.getObstaclesByTag("DESTRUCTIBLE");
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
        }else{
            return null;
        }

    }


    public void executePathOrLoot(
            GameMap map,
            Player player,
            String path
    ) throws IOException {
        if (currentNodeTarget!= null && PathUtils.distance(player.getPosition(), currentNodeTarget) == 0) {
            attackAdjacentChestsNoId(map,player);
            hero.pickupItem();
        }
        if (path != null && !path.isEmpty()) {
            String step = path.substring(0, 1);
            hero.move(step);
        }

    }
    void attackAdjacentChestsNoId(GameMap gameMap, Player player) {
        try {
            for (Obstacle chest : gameMap.getObstaclesByTag("DESTRUCTIBLE")) {
                if (chest.getHp() <= 0) continue;
                int dx = chest.getX() - player.getX();
                int dy = chest.getY() - player.getY();

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

}
