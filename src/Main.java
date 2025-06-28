import io.socket.emitter.Emitter;
import jsclub.codefest.sdk.algorithm.PathUtils;
import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.Element;
import jsclub.codefest.sdk.model.GameMap;
import jsclub.codefest.sdk.Hero;
import jsclub.codefest.sdk.model.Inventory;
import jsclub.codefest.sdk.model.healing_items.HealingItem;
import jsclub.codefest.sdk.model.npcs.Enemy;
import jsclub.codefest.sdk.model.obstacles.Obstacle;
import jsclub.codefest.sdk.model.players.Player;
import jsclub.codefest.sdk.model.weapon.Weapon;
import myModule.InventoryLocal;
import myModule.LocalHeroController;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

public class Main {
    private static final String SERVER_URL = "https://cf25-server.jsclub.dev";
    private static final String GAME_ID = "196132";
    private static final String PLAYER_NAME = "4nim0sity";
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
    private final InventoryLocal myInventory = new InventoryLocal();
    private final LocalHeroController myHero = new LocalHeroController(myInventory);
    private final Hero hero;
    private int currentStep = 0;

    private List<Node> restrictNode = new ArrayList<>();
    private static Player savedTarget = null;
    private static String savedName = null;
    private static Node currentNodeTarget = null;
    private boolean[] gocFlags = {true, false, false, false};
    //    to condition
    private int currentSafeZone = 999;
    private boolean isShrinking = false;
    static List<Enemy> listNodeEnemySave = new ArrayList<>();
    private static String[] enemyDirection = new String[100];
    private static Boolean[][][] enemyMap = new Boolean[121][121][100];
    private static int enemyMinEdge[] = new int[100];
    private static int enemyMaxEdge[] = new int[100];

    private static int[] toado = new int[100];

    @Override
    public void call(Object... args) {
        try {
            if (args == null || args.length == 0) return;
            GameMap gameMap = hero.getGameMap();
            gameMap.updateOnUpdateMap(args[0]);
            Player player = gameMap.getCurrentPlayer();
            handleGame(gameMap, player);
        } catch (Exception e) {
            System.err.println("Critical error in call method: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void handleGame(GameMap gameMap, Player player) throws IOException {
        if(currentStep == 0){
            currentSafeZone =gameMap.getSafeZone();
        }else {
            isShrinking = (currentSafeZone != gameMap.getSafeZone());
            currentSafeZone = gameMap.getSafeZone();
        }
        getNodesToAvoid(gameMap);
        addEnemyToRestrict(gameMap,player);
        currentStep++;
        updateCooldowns();
        String decision = getDecisionForNextStep(gameMap, player);
        switch (decision) {
            case "die" -> handleDie();
            case "runBo" -> handleRunBo(gameMap, player);
            case "fight"-> handleFight(gameMap,player);
            case "hide" -> handleHide(gameMap, player);
            case "heal" -> handleHeal(gameMap, player);
            case "loot" -> handleLoot(gameMap, player);
            case "hunting" -> handleHunting(gameMap, player);
            default -> System.out.println("Unexpected decision: " + decision);
        }
        System.out.println("=============DEBUG_PART (" + currentStep + ")=================");
        System.out.println("Current Decision is : " + decision);
//        System.out.println("My Inventory: " + myInventory);
        System.out.println("code fest inventory gun: " + hero.getInventory().getGun());
        System.out.println("code fest inventory melee: " + hero.getInventory().getMelee());
        System.out.println("code fest inventory throw: " + hero.getInventory().getThrowable());
        System.out.println("code fest inventory special: " + hero.getInventory().getSpecial());
        System.out.println("code fest inventory heal: " + hero.getInventory().getListHealingItem());
        System.out.println("Current Node loot Target : "+ currentNodeTarget);
        System.out.println("========================================");
    }

    public void handleDie(){
        myInventory.reset();
        savedTarget = null;
        savedName = null;
        currentNodeTarget = null;

        gocFlags = new boolean[]{ true, false, false, false };
        resetCooldowns();
    }

    private void handleHide(GameMap gameMap, Player player) throws IOException {
        Node safestSpot1 = findSafeSpotAwayFromEnemies(gameMap, player);
        List<Node> list = new ArrayList<>();
        list.add(safestSpot1);
        String pathToHide = findBestPath(gameMap, player.getPosition(), list, gameMap.getOtherPlayerInfo());
        hero.move(pathToHide.substring(0,1));
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
                if (restrictNode.contains(node)) continue;  // Tránh vật cản

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
    public boolean canUseGun()     { return gunCooldownTick == 0; }
    public boolean canUseThrow()   { return throwCooldownTick == 0; }
    public boolean canUseMelee()   { return meleeCooldownTick == 0; }
    public boolean canUseSpecial() { return specialCooldownTick == 0; }
    public boolean hasGun(){return hero.getInventory().getGun() !=null; }
    public boolean hasThrowable(){return hero.getInventory().getThrowable() !=null; }
    public boolean hasMelee(){ return hero.getInventory().getMelee() != null
            && !"HAND".equalsIgnoreCase(hero.getInventory().getMelee().getId()); }
    public boolean hasSpecial(){return hero.getInventory().getSpecial()!= null;}
    public boolean hasHealingItem(){return !hero.getInventory().getListHealingItem().isEmpty(); }
    public boolean hasHelmet(){return hero.getInventory().getHelmet()!= null; }
    public boolean hasArmor(){return hero.getInventory().getArmor()!= null; }

    private void handleFight(GameMap gameMap, Player player) throws IOException {
        Inventory inv = hero.getInventory();
        List<Player> otherPlayer = gameMap.getOtherPlayerInfo().stream().filter(p->p.getHealth()>0).toList();
        List<Weapon> myWeapon = getMyListReadyWeapon();
        int maxRange = myWeapon.stream().mapToInt(this::getRangeWeaponAHead).max().orElse(0);
        List<Player> playersInRange = otherPlayer.stream().filter(p ->
                PathUtils.distance(player.getPosition(),p) <= maxRange
        ).toList();

        // Fix: Check if playersInRange is empty to avoid NoSuchElementException
        if (playersInRange.isEmpty()) {
            // Nếu không có ai trong tầm, fallback: di chuyển hoặc ẩn nấp
            handleHide(gameMap, player);
            return;
        }

        Player target = playersInRange.stream().min(Comparator.comparingDouble(Player::getHealth)).get();
        restrictNode.remove(target);

        Weapon currenWeapon = myWeapon.stream().max(Comparator.comparingInt(Weapon::getDamage)).orElse(null);

        if(currenWeapon !=null){
            String dir = getDirection(player.getPosition(), target.getPosition());
            int dist = Math.abs(player.getX() - target.getX())
                    + Math.abs(player.getY() - target.getY());
            if (dist > getRangeWeaponAHead(currenWeapon)) {
                hero.move(dir);
            }else{
                System.out.printf("Hero dùng %s đánh Player[%d,%d]\n",
                        currenWeapon.getId(), target.getX(), target.getY());
                if(currenWeapon.getId().equalsIgnoreCase("KNIFE")
                        || currenWeapon.getId().equalsIgnoreCase("TREE_BRANCH")
                        || currenWeapon.getId().equalsIgnoreCase("BONE")
                        || currenWeapon.getId().equalsIgnoreCase("AXE")
                        || currenWeapon.getId().equalsIgnoreCase("MACE")
                ){
                    hero.attack(dir);
                    meleeCooldownTick = (int)Math.ceil(currenWeapon.getCooldown());
                }

                if(currenWeapon.getId().equalsIgnoreCase("SCEPTER")
                        || currenWeapon.getId().equalsIgnoreCase("CROSSBOW")
                        || currenWeapon.getId().equalsIgnoreCase("RUBBER_GUN")
                        || currenWeapon.getId().equalsIgnoreCase("SHOTGUN")){
                    hero.shoot(dir);
                    gunCooldownTick = (int)Math.ceil(currenWeapon.getCooldown());
                }
                if(currenWeapon.getId().equalsIgnoreCase("BANANA")
                        || currenWeapon.getId().equalsIgnoreCase("SMOKE")
                        || currenWeapon.getId().equalsIgnoreCase("METEORITE_FRAGMENT")
                        || currenWeapon.getId().equalsIgnoreCase("CRYSTAL")
                        || currenWeapon.getId().equalsIgnoreCase("SEED")){
                    hero.throwItem(dir,getRangeWeaponAHead(currenWeapon));
                    throwCooldownTick = (int)Math.ceil(currenWeapon.getCooldown());
                }
                if(currenWeapon.getId().equalsIgnoreCase("ROPE")
                        || currenWeapon.getId().equalsIgnoreCase("BELL")
                        || currenWeapon.getId().equalsIgnoreCase("SAHUR_BAT")){
                    hero.useSpecial(dir);
                    specialCooldownTick = (int)Math.ceil(currenWeapon.getCooldown());
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
    private static String getDirection(Node p, Node e) {
        int dx = e.getX() - p.getX();
        int dy = e.getY() - p.getY();
        // Ưu tiên trục có sai khác lớn hơn để bước vào gần nhất
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
        if (currenWeapon.getId().equalsIgnoreCase("SMOKE")) return 3;
        if (currenWeapon.getId().equalsIgnoreCase("METEORITE_FRAGMENT")) return 6;
        if (currenWeapon.getId().equalsIgnoreCase("CRYSTAL")) return 6;
        if (currenWeapon.getId().equalsIgnoreCase("SEED")) return 5;

        if (currenWeapon.getId().equalsIgnoreCase("ROPE")) return 6;
        if (currenWeapon.getId().equalsIgnoreCase("BELL")) return 7;
        if (currenWeapon.getId().equalsIgnoreCase("SAHUR_BAT")) return 5;
        return 1;
    }

    private List<Weapon> getMyListReadyWeapon(){
        List<Weapon> result = new ArrayList<>();
        if(hasGun() && canUseGun() ){
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

    public void addEnemyToRestrict(GameMap gameMap,Player player){
        //_Cho enemy vao retriced__________________________
        List<Enemy> listEnemies = gameMap.getListEnemies();
        if (listNodeEnemySave.isEmpty())
            listNodeEnemySave.addAll(listEnemies);
        else {
            if (currentStep == 12) {
                for (int i = 0; i < listEnemies.size(); i++) {
                    if (enemyDirection[i].equals("doc")) {
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
                    if (enemyDirection[i].equals("ngang")) {
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

    private void getNodesToAvoid(GameMap gameMap) {
        List<Node> nodes = new ArrayList<>(gameMap.getListObstacles());
        Iterator<Node> iterator = nodes.iterator();
        while (iterator.hasNext()) {
            Node n = iterator.next();
            if (n instanceof Element) {
                Element e = (Element) n;
                if ("BUSH".equalsIgnoreCase(e.getId())) {
                    iterator.remove();
                }
            }
        }
        nodes.addAll(gameMap.getOtherPlayerInfo());
        restrictNode.addAll(nodes);
    }

    public String getDecisionForNextStep(GameMap gameMap, Player player) {
        if (player == null || player.getHealth() <= 0) {
            return "die";
        }
        int mapSize     = gameMap.getMapSize();
        double tickDur  = 0.5;
        double elapsed  = currentStep * tickDur;
        double totalSec;

        final int Smin = 40, Smax = 100;
        final int Tmin = 300, Tmax = 600;
        totalSec = Tmin + (mapSize - Smin)/(double)(Smax - Smin) * (Tmax - Tmin);

        double remaining = totalSec - elapsed;

        enum Phase { EARLY, MID, LATE }
        Phase phase;
        double percentRemaining = remaining / totalSec;

        if (percentRemaining > 0.85)      phase = Phase.EARLY;
        else if (percentRemaining > 0.3) phase = Phase.MID;
        else                              phase = Phase.LATE;

        System.out.println("Current Phase: " +phase);
        System.out.println("total sec: " +totalSec);
        System.out.println("remaining: " +remaining);

        int x = player.getX(), y = player.getY();
        boolean needRunBo = false;
            needRunBo = !checkInsideSafeArea(new Node(x, y), gameMap.getSafeZone()-2, gameMap.getMapSize())
            && isShrinking;
        Inventory inv = hero.getInventory();

        List<Player> others = gameMap.getOtherPlayerInfo().stream()
                .filter(p -> p.getHealth() > 0).toList();
        boolean enemyInRange = others.stream().anyMatch(p ->
                PathUtils.distance(new Node(x, y), new Node(p.getX(), p.getY())) <= 10);

        List<HealingItem> healingItems = inv.getListHealingItem();
        boolean canHeal = healingItems != null
                && !healingItems.isEmpty()
                && player.getHealth() < 100 * 0.7;


        boolean needLoot =
                inv.getGun() == null
                        || inv.getThrowable() == null
                        || inv.getListHealingItem().size() < 4
                        || inv.getMelee() == null
                        || !(inv.getArmor() != null || inv.getHelmet() != null)
                        || inv.getSpecial() ==null;

        switch (phase) {
            case EARLY:
                if (needRunBo) return "runBo";
                if (enemyInRange) {
                    if (needLoot) return "loot";
                    return "hide";
                }
                if (needLoot) return "loot";
                return "hide";
            case MID:
                if (needRunBo) return "runBo";
                if (canHeal) return "heal";
                if (enemyInRange) return "fight";
                if (needLoot) return "loot";
                return "hunting";
            case LATE:
                if (needRunBo) return "runBo";
                if (player.getHealth() < 100 * 0.3) return "hide";
                if (canHeal) return "heal";
                if (enemyInRange) return "fight";
                if (needLoot) return "loot";
                return "hunting";
        }
        return "default";
    }

    public static boolean checkInsideSafeArea(Node current, int safeZone, int mapSize) {
        if (safeZone <= 0) return false;
        int center = mapSize / 2;
        int dx = Math.abs(current.getX() - center);
        int dy = Math.abs(current.getY() - center);
        return dx < safeZone && dy < safeZone;
    }

    private void handleRunBo(GameMap gameMap, Player player) throws IOException {
        Node targetInSafeZone = findClosestSafeSpot(gameMap, player);
        String pathRun = PathUtils.getShortestPath(gameMap,restrictNode,player.getPosition(),targetInSafeZone,true);
        if (pathRun != null && !pathRun.isEmpty()) {
            hero.move(pathRun.substring(0,1));
        } else {
            hero.move("l");
        }
    }

    private Node findClosestSafeSpot(GameMap gameMap, Player player) {
        int mapSize = gameMap.getMapSize();
        int safeZone = gameMap.getSafeZone()-1;
        int centerX = mapSize / 2;
        int centerY = mapSize / 2;

        Node playerNode = new Node(player.getX(), player.getY());

        // Giới hạn biên của safe zone
        int minX = centerX - safeZone;
        int maxX = centerX + safeZone;
        int minY = centerY - safeZone;
        int maxY = centerY + safeZone;
        System.out.println("minX: " + minX);
        System.out.println("maxX: " + maxX);
        System.out.println("minY: " + minY);
        System.out.println("maxX: " + maxY);
        // Clamp giá trị trong giới hạn bản đồ
        minX = Math.max(minX, 0);
        maxX = Math.min(maxX, mapSize - 1);
        minY = Math.max(minY, 0);
        maxY = Math.min(maxY, mapSize - 1);

        Node closest = null;
        int minDistance = Integer.MAX_VALUE;

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

    private void handleHeal(GameMap gameMap, Player player) throws IOException {
        if(hasHealingItem()){
            hero.useItem(hero.getInventory().getListHealingItem().getFirst().getId());
        }

    }
    private void handleHunting(GameMap gameMap, Player player) throws IOException {
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
        System.out.println("current Path: " + path);
        boolean moved = executePathOrLoot(
                gameMap,
                currentNode,
                path
        );
    }

    // Danh sách id cho healing và special
    private static final List<String> listHealing = Arrays.asList(
            "GOD_LEAF",
            "SPIRIT_TEAR",
            "MERMAID_TAIL",
            "PHOENIX_FEATHERS",
            "UNICORN_BLOOD"
    );
    private static final List<String> listSpecial = Arrays.asList(
            "ELIXIR",
            "MAGIC",
            "ELIXIR_OF_LIFE",
            "COMPASS"
    );
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

        if (hasMelee() && !hasGun() && existsWithin(map.getAllGun(), current, 10)) {
            addNearbySafeGunTargets(map, current, 10, targets);
            return targets;
        }


       // 2) Tối ưu lấy Healing/Special (4 slot tổng, tối đa 2 mỗi loại)
        if (hero.getInventory().getListHealingItem().size() < 4) {
            // 1. Đếm số Healing và Special đã có trong túi
            int haveHealing = 0;
            int haveSpecial = 0;
            for (HealingItem h : hero.getInventory().getListHealingItem()) {
                String hid = h.getId();
                if (listHealing.contains(hid)) {
                    haveHealing++;
                } else if (listSpecial.contains(hid)) {
                    haveSpecial++;
                }
            }

            // 2. Duyệt qua tất cả HealingItem trên map
            for (HealingItem item : map.getListHealingItems()) {
                String id = item.getId();
                Node n = new Node(item.getX(), item.getY());

                // Chỉ xét trong vùng an toàn
                if (!PathUtils.checkInsideSafeArea(n, map.getSafeZone(), map.getMapSize())) {
                    continue;
                }

                // Nếu là Healing và chưa đủ 2
                if (listHealing.contains(id) && haveHealing < 2) {
                    targets.add(n);
                }
                // Nếu là Special và chưa đủ 2
                else if (listSpecial.contains(id) && haveSpecial < 2) {
                    targets.add(n);
                }

            }
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
        if (!hasThrowable()) {
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
//                    result.add(new Node(c.getX(), c.getY() - 1)); // ô phía trên
//                    result.add(new Node(c.getX(), c.getY() + 1)); // ô phía dưới

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
            path = PathUtils.getShortestPath(map, restrictNode, current, currentNodeTarget, false);
        }
        if(path!= null && !path.isEmpty()){
            return path;
        }
        for (Player p : otherPlayers) {
            restrictNode.add(new Node(p.getX(), p.getY()));
        }
        int size = map.getMapSize();
        String escapePath = null;
        // Góc 1: dưới-trái; 2: dưới-phải; 3: trên-phải; 4: trên-trái
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
    private String tryCorner(
            GameMap map,
            List<Node> restricted,
            Node current,
            int tx, int ty
    ) {
        Node corner = new Node(tx, ty);
        return PathUtils.getShortestPath(map, restricted, current, corner, false);
    }

    public boolean executePathOrLoot(
            GameMap map,
            Node current,
            String path
    ) {
        int x = current.x, y = current.y;

        if (currentNodeTarget!= null && PathUtils.distance(current, currentNodeTarget) == 0) {
            try {
                Element elem = map.getElementByIndex(x, y);
                if (elem != null) {
                    hero.pickupItem();
                    myHero.pickupItem(elem.getId());
                }
                attackAdjacentChestsNoId(hero, map, x, y);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return false;
        }
        if (path != null && !path.isEmpty()) {
            String step = path.substring(0, 1);
            try {
                hero.move(step);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return true;
        }

        try {
            attackAdjacentChestsNoId(hero, map, x, y);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }
     void attackAdjacentChestsNoId(Hero hero, GameMap gameMap, int px, int py) {
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
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void revokeItem(String ItemId){
        try{
            hero.revokeItem(ItemId);
            myHero.revokeItem(ItemId);
        }catch (Exception e){
            e.printStackTrace();
        }
    }



}
