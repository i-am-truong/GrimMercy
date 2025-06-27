import io.socket.emitter.Emitter;
import jsclub.codefest.sdk.algorithm.PathUtils;
import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.Element;
import jsclub.codefest.sdk.model.GameMap;
import jsclub.codefest.sdk.Hero;
import jsclub.codefest.sdk.model.obstacles.Obstacle;
import jsclub.codefest.sdk.model.players.Player;
import jsclub.codefest.sdk.model.weapon.Weapon;
import myModule.InventoryLocal;
import myModule.LocalHeroController;
import myModule.LocalUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public class Main {
    private static final String SERVER_URL = "https://cf25-server.jsclub.dev";
    private static final String GAME_ID = "175449";
    private static final String PLAYER_NAME = "4nim0sity";
    private static final String SECRET_KEY = "sk-I66yrGdORXWDWQfpd4qtDA:vVGI_F8vMzFIdjgOH_nnMFp6WkRcYVnXZ9UwiHbPyRqjvTfelockEHJAYgCCZXKax-8jSJCb1HhBGt5ctIUN0A";

    public static void main(String[] args) throws IOException {
        Hero hero = new Hero(GAME_ID, PLAYER_NAME, SECRET_KEY);
        Emitter.Listener onMapUpdate = new MapUpdateListener(hero);
        hero.setOnMapUpdate(onMapUpdate);
        hero.start(SERVER_URL);
    }
}

class MapUpdateListener implements Emitter.Listener {
    private final InventoryLocal myInventory = new InventoryLocal();
    private final LocalHeroController myHero = new LocalHeroController(myInventory);
    private final Hero hero;
//    private String targetID = null;
    private int currentStep = 0;
    List<Node> restrictNode = new ArrayList<>();
    private static Node currentNodeTarget = null;
    private boolean[] gocFlags = {true, false, false, false};

    //    boolean for make Decision:
    private static boolean hyperDodge = false;
    boolean shouldHunting = false;
    boolean shouldRunBo = false;
    boolean shouldLoot = false;
    boolean shouldHeal = false;
    boolean shouldShoot = false;
    boolean shouldCloseCombat = false;
    boolean shouldDodge = false;
    boolean shouldThrow = false;

    private boolean canAttack = true;
    private int AttackcountDown = 0;

    private boolean canHeal = false;
    private int HealcountDown = 0;

    private boolean canShoot = false;
    private int ShootcountDown = 0;


    public MapUpdateListener(Hero hero) {
        this.hero = hero;
    }

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

    public void handleGame(GameMap gameMap, Player player) {
        setUpStartGame(gameMap);
        String decision = getDecisionForNextStep(gameMap, player);
        switch (decision) {
            case "die" -> handleDie();
            case "hyperDodge" -> handleHyperDodge(gameMap, player);
            case "runBo" -> handleRunBo(gameMap, player);
            case "closeCombat" -> handleCloseCombat(gameMap, player);
            case "dodgeBullet" -> handleDodgeBullet(gameMap, player);
            case "heal" -> handleHeal(gameMap, player);
            case "throwBomb" -> handleThrowBomb(gameMap, player);
            case "shoot" -> handleShoot(gameMap, player);
            case "loot" -> handleLoot(gameMap, player);
            case "hunting" -> handleHunting(gameMap, player);
            default -> System.out.println("Unexpected decision: " + decision);
        }
        System.out.println("=============DEBUG_PART (" + currentStep + ")=================");
        System.out.println("Current Decision is : " + decision);
        System.out.println("My Inventory: " + myInventory);
        System.out.println("Current Node Target: "+ currentNodeTarget);
        System.out.println("========================================");
    }
    public void handleDie(){
        myInventory.reset();
    }
    public void setUpStartGame(GameMap gameMap){
        currentStep++;
        restrictNode = getNodesToAvoid(gameMap);
    }
    private List<Node> getNodesToAvoid(GameMap gameMap) {
        List<Node> nodes = new ArrayList<>(gameMap.getListObstaclesInit());
        nodes.removeIf(node ->
                node instanceof Element && ((Element) node).getId().equalsIgnoreCase("BUSH")
        );
        for(Obstacle chest: gameMap.getListChests()){
            if(chest.getHp() >0){
                nodes.add(new Node(chest.getX(),chest.getY()));
            }
        }
        for(Player p : gameMap.getOtherPlayerInfo()){
            nodes.add(new Node(p.getX(), p.getY()));
        }
        return nodes;
    }

    public String getDecisionForNextStep(GameMap gameMap, Player player) {
        //retrun loot for testing:
        if(true) return "loot";
        if (player == null || player.getHealth() <= 0) {
            return "die";
        }
        if (hyperDodge) {
            return "hyperDodge";
        }
        if (shouldRunBo) {
            return "runBo";
        }
        if (shouldCloseCombat && canAttack) {
            return "closeCombat";
        }
        if (shouldDodgeBullet(gameMap, player)) {
            return "dodgeBullet";
        }
        if (shouldHeal) {
            return "heal";
        }
        if (shouldThrow && !shouldShoot) {
            return "throwBomb";
        }
        if (shouldShoot) {
            return "shoot";
        }
        if (shouldLoot) {
            return "loot";
        }
        if (shouldHunting) {
            return "hunting";
        }
        return "default";
    }

    private boolean shouldDodgeBullet(GameMap gameMap, Player player) {
//        if (gameMap.getElementByIndex(x, y + 3).getType().name().equalsIgnoreCase("bullet") ||
//                            gameMap.getElementByIndex(x, y - 3).getType().name().equalsIgnoreCase("bullet") ||
//                            gameMap.getElementByIndex(x + 3, y).getType().name().equalsIgnoreCase("bullet") ||
//                            gameMap.getElementByIndex(x - 3, y).getType().name().equalsIgnoreCase("bullet")) {
//                        System.out.println("run for bullet");
//                        shouldDodge = true;
//                    }
//                    for (int dX = x - 2; dX <= x + 2; dX++) {
//                        if (shouldDodge) {
//                            break;
//                        }
//                        if (dX == x) {
//                            continue;
//                        }
//                        for (int dY = y - 2; dY <= y + 2; dY++) {
//                            if (dY == y) {
//                                continue;
//                            }
//                            if (gameMap.getElementByIndex(dX, dY).getType().name().equalsIgnoreCase("bullet")) {
//                                shouldDodge = true;
//                                break;
//                            }
//                        }
//                    }
        return false;
    }

    private void handleHyperDodge(GameMap gameMap, Player player) {
        // copy khối dodge after melee của bạn (hyperDodge)
//        String path = null;
//        for (Player p : otherPlayers) {
//            if (p.getPlayerName().equalsIgnoreCase(savedName)) {
//                savedTarget=p;
//                break;
//            }
//        }
//        restrictedNodes.remove(savedTarget);
//        while (true) {
//            System.out.println("hyper lap:"+savedTarget);
//            if (AttackcountDown==1) {
//                if (PathUtils.getShortestPath(gameMap,restrictedNodes,currentNode,savedTarget,false)!=null)
//                {
//                    hero.move(PathUtils.getShortestPath(gameMap,restrictedNodes,currentNode,savedTarget,false));
//                    break;
//                }
//            }
//            if (savedTarget.getBulletNum()==0&&player.getHp()<100) {
//                currentPriority=3;
//                break;
//            }
//            Node tranhgiaotranh = null;
//            int x1, y1;
//            if(x>= savedTarget.x&&y>=savedTarget.y){
//                x1=savedTarget.x+1;
//                y1=savedTarget.y+2;
//                tranhgiaotranh= new Node(x1, y1);
//                for (int i = x1; i <mapSize-realSize ; i++) {
//                    for (int j = y1; j <mapSize-realSize ; j++) {
//                        tranhgiaotranh=new Node(i, j);
//                        if (PathUtils.getShortestPath(gameMap,restrictedNodes,currentNode,tranhgiaotranh,false)!=null
//                                && !PathUtils.getShortestPath(gameMap,restrictedNodes,currentNode,tranhgiaotranh,false).isEmpty()){
//                            path=PathUtils.getShortestPath(gameMap,restrictedNodes,currentNode,tranhgiaotranh,false);
//                            break;
//                        }
//                    }
//                    if (path!=null ){
//                        break;
//                    }
//                }
//            }
//            if(x<= savedTarget.x&&y>=savedTarget.y){
//                x1=savedTarget.x-1;
//                y1=savedTarget.y+2;
//                tranhgiaotranh= new Node(x1, y1);
//                for (int i = x1; i >0 ; i--) {
//                    for (int j = y1; j <mapSize-realSize ; j++) {
//                        tranhgiaotranh=new Node(i, j);
//                        if (PathUtils.getShortestPath(gameMap,restrictedNodes,currentNode,tranhgiaotranh,false)!=null){
//                            path=PathUtils.getShortestPath(gameMap,restrictedNodes,currentNode,tranhgiaotranh,false);
//                            break;
//                        }
//                    }
//                    if (path!=null){
//                        break;
//                    }
//                }
//            }
//            if(x>= savedTarget.x&&y<=savedTarget.y){
//                x1=savedTarget.x+2;
//                y1=savedTarget.y-1;
//                tranhgiaotranh= new Node(x1, y1);
//                for (int i = x1; i <mapSize-realSize ; i++) {
//                    for (int j = y1; j >0 ; j--) {
//                        tranhgiaotranh=new Node(i, j);
//                        if (PathUtils.getShortestPath(gameMap,restrictedNodes,currentNode,tranhgiaotranh,false)!=null){
//                            path=PathUtils.getShortestPath(gameMap,restrictedNodes,currentNode,tranhgiaotranh,false);
//                            break;
//                        }
//                    }
//                    if (path!=null){
//                        break;
//                    }
//                }
//            }
//            if(x<= savedTarget.x&&y<=savedTarget.y){
//                x1=savedTarget.x-1;
//                y1=savedTarget.y-2;
//                tranhgiaotranh= new Node(x1, y1);
//                for (int i = x1; i >0 ; i--) {
//                    for (int j = y1; j >0 ; j--) {
//                        tranhgiaotranh=new Node(i, j);
//                        if (PathUtils.getShortestPath(gameMap,restrictedNodes,currentNode,tranhgiaotranh,false)!=null){
//                            path=PathUtils.getShortestPath(gameMap,restrictedNodes,currentNode,tranhgiaotranh,false);
//                            break;
//                        }
//                    }
//                    if (path!=null){
//                        break;
//                    }
//                }
//            }
//            break;
//        }
//        System.out.println("hyper dodge:"+path);
//        hero.move(path);
    }
    private void handleRunBo(GameMap gameMap, Player player) {
        // copy khối run bo
//        if (currentPriority == 0 && countBo > 0) {
//            countBo --;
//            restrictedNodes.addAll(otherPlayesNode);
//            String path = null;
//            int i = 0;
//            if (y < realSize + 5) {
//                while (path == null) {
//                    path = PathUtils.getShortestPath(gameMap, restrictedNodes, currentNode, new Node(x + i, y + 8), true);
//                    i++;
//                }
//            }
//            if (x < realSize + 5) {
//                while (path == null) {
//                    path = PathUtils.getShortestPath(gameMap, restrictedNodes, currentNode, new Node(x + 8, y + i), true);
//                    i++;
//                }
//            }
//            if (y > mapSize - realSize - 5) {
//                while (path == null) {
//                    path = PathUtils.getShortestPath(gameMap, restrictedNodes, currentNode, new Node(x + i, y - 8), true);
//                    i++;
//                }
//            }
//            if (x > mapSize - realSize - 5) {
//                while (path == null) {
//                    path = PathUtils.getShortestPath(gameMap, restrictedNodes, currentNode, new Node(x - 8, y + i), true);
//                    i++;
//                }
//            }
//            System.out.println("path chay bo: " + path);
//            hero.move(path);
//        }

    }
    private void handleCloseCombat(GameMap gameMap, Player player) {
        // copy nguyên khối currentPriority == 1
        System.out.println("Vao cau lenh close combat");

        //lay node Of TargetPlayer
//        Node nodeOfTargetPlayer = null;
//        if (listPlayerInRangeCloseCombat.size() < 2) {// danh 1 vs 1
//            savedName = listPlayerInRangeCloseCombat.get(0).getPlayerName();
//            savedTarget = listPlayerInRangeCloseCombat.get(0);
//            nodeOfTargetPlayer = new Node(listPlayerInRangeCloseCombat.get(0).getX(),listPlayerInRangeCloseCombat.get(0).getY());
//        } else {// combat nhieu nguoi
//            //danh thang thap mau nhat
//            int lowestHp = Integer.MAX_VALUE;
//            System.out.println("--------------");
//            System.out.println("list Player in range ");
//            for (Player p : listPlayerInRangeCloseCombat ) {
//                System.out.println("Name: " + p.getPlayerName());
//                System.out.println("HP: "+ p.getHp());
//                if(p.getHp() < lowestHp){
//                    lowestHp = p.getHp();
//                    nodeOfTargetPlayer = new Node(p.getX(),p.getY());
//                    savedName = p.getPlayerName();
//                    savedTarget = p;
//                }
//            }
//            System.out.println("--------------");
//        }
//        restrictedNodes.remove(savedTarget);
//
//        String direction = getCloseCombatDirection(currentNode, nodeOfTargetPlayer);
//        if (direction.equalsIgnoreCase("planB")) {
//            System.out.println("Plan B in close combat is implementing");
//            System.out.println("Path to enemy: " +PathUtils.getShortestPath(gameMap, restrictedNodes, currentNode, nodeOfTargetPlayer, false));
//            hero.move(PathUtils.getShortestPath(gameMap, restrictedNodes, currentNode, nodeOfTargetPlayer, false));
//            if(PathUtils.getShortestPath(gameMap, restrictedNodes, currentNode, nodeOfTargetPlayer, false) == null){
//                System.out.println("Tam thoi di chuyen trong 3 step toi tranh loi");
//                canAttack = false;
//                AttackcountDown = 3;
//            }
//        } else {
//            System.out.println("hero attack at: "+ currentStep);
//            System.out.println("current melee is: "+ inventory.getMelee());
//            hero.attack(direction);
//            if(hasGun && inventory.getMelee().getCooldown() >1){
//                if(inventory.getMelee().getId().equalsIgnoreCase("LIGHT_SABER")){
//                    AttackcountDown = 2;
//                }else{
//                    AttackcountDown = 2;
//                }
//                canAttack = false;
//            }
//            if(hasGun && inventory.getMelee().getCooldown() <=1){
//                System.out.println("danh 1 cai xong ban");
//                AttackcountDown = 2;
//                canAttack = false;
//            }
//            if(!hasGun && (IDCurrentMelee.equalsIgnoreCase("BROOM")
//                    ||IDCurrentMelee.equalsIgnoreCase("SANDAL") )){
//                AttackcountDown = 6;
//                hyperDodge = true;
//                canAttack = false;
//            }
//            if(!hasGun && (IDCurrentMelee.equalsIgnoreCase("LIGHT_SABER"))){
//                AttackcountDown = 8;
//                hyperDodge = true;
//                canAttack = false;
//            }
//
//
//        }

    }
    private void handleDodgeBullet(GameMap gameMap, Player player) {
        // copy nguyên khối currentPriority == 2
        System.out.println("ne ne");
//                        if (!gameMap.getElementByIndex(x, y + 1).getType().name().equalsIgnoreCase("road")) {
//                            dodgeU += 10;
//                        }
//                        if (!gameMap.getElementByIndex(x, y - 1).getType().name().equalsIgnoreCase("road")) {
//                            dodgeD += 10;
//                        }
//                        if (!gameMap.getElementByIndex(x + 1, y).getType().name().equalsIgnoreCase("road")) {
//                            dodgeR += 10;
//                        }
//                        if (!gameMap.getElementByIndex(x - 1, y).getType().name().equalsIgnoreCase("road")) {
//                            dodgeL += 10;
//                        }
//                        String side = "l";
//                        int min = dodgeL;
//                        if (dodgeD < min) {
//                            min = dodgeD;
//                            side = "d";
//                        }
//                        if (dodgeR < min) {
//                            min = dodgeR;
//                            side = "r";
//                        }
//                        if (dodgeU < min) {
//                            min = dodgeU;
//                            side = "u";
//                        }
//                        hero.move(side);

    }
    private void handleHeal(GameMap gameMap, Player player) {
        // copy logic heal (priority 3)
//        System.out.println("heal heal heal");
//        if(shouldCloseCombat){
//            System.out.println("in combat use heal has usageTime <=1");
//            for (int i = 0; i < NumHeal; i++) {
//                if(IDHealItem[i].equalsIgnoreCase("SNACK") ||
//                        IDHealItem[i].equalsIgnoreCase("INSECTICIDE")
//                ){
//                    hero.useItem(IDHealItem[i]);
//                    for (int j = i; j < IDHealItem.length -1; j++) {
//                        IDHealItem[j] = IDHealItem[j + 1];
//                    }
//                    IDHealItem[IDHealItem.length - 1] = null;
//                    canHeal = false;
//                    HealcountDown = 2;
//                    NumHeal--;
//                    return;
//
//
//                }
//            }
//        }else{
//            System.out.println("not in combat use heal depend on currentHP");
//            boolean safePlace = true;
//
//            for (int i = 0; i < listEnemies.size(); i++) {
//                if (enemyDirection[i].equalsIgnoreCase("doc")) {
//                    if (toado[i] - 1 <= currentNode.y && currentNode.y <= toado[i] + 1 && enemyMinEdge[i] - 1 <= currentNode.x && currentNode.x <= 1 + enemyMaxEdge[i]) {
//                        safePlace = false;
//                        break;
//                    }
//                }
//                if (enemyDirection[i].equalsIgnoreCase("ngang")) {
//                    if (toado[i] - 1 <= currentNode.x && currentNode.x <= toado[i] + 1 && enemyMinEdge[i] - 1 <= currentNode.y && currentNode.y <= enemyMaxEdge[i] + 1) {
//                        safePlace = false;
//                        break;
//                    }
//                }
//            }
//
//            if(safePlace ){
//                for (int i = 0; i < NumHeal; i++) {
//                    if(IDHealItem[i].equalsIgnoreCase("LUNCH_BOX") ){
//
//                        hero.useItem(IDHealItem[i]);
//                        for (int j = i; j < IDHealItem.length -1; j++) {
//                            IDHealItem[j] = IDHealItem[j + 1];
//                        }
//                        IDHealItem[IDHealItem.length - 1] = null;
//                        canHeal = false;
//                        HealcountDown = 4;
//                        NumHeal--;
//                        return;
//
//
//                    }
//                }
//                for (int i = 0; i < NumHeal; i++) {
//                    if(IDHealItem[i].equalsIgnoreCase("BANDAGES") ){
//
//                        hero.useItem(IDHealItem[i]);
//
//                        for (int j = i; j < IDHealItem.length -1; j++) {
//                            IDHealItem[j] = IDHealItem[j + 1];
//                        }
//                        IDHealItem[IDHealItem.length - 1] = null;
//                        canHeal = false;
//                        HealcountDown = 2;
//                        NumHeal--;
//                        return;
//
//
//                    }
//                }
//                for (int i = 0; i < NumHeal; i++) {
//                    if(IDHealItem[i].equalsIgnoreCase("DRINK") ){
//
//                        hero.useItem(IDHealItem[i]);
//                        for (int j = i; j < IDHealItem.length -1; j++) {
//                            IDHealItem[j] = IDHealItem[j + 1];
//                        }
//                        IDHealItem[IDHealItem.length - 1] = null;
//                        canHeal = false;
//                        HealcountDown = 2;
//                        NumHeal--;
//                        return;
//
//
//                    }
//                }
//                for (int i = 0; i < NumHeal; i++) {
//                    if(IDHealItem[i].equalsIgnoreCase("INSECTICIDE") ){
//
//                        hero.useItem(IDHealItem[i]);
//                        for (int j = i; j < IDHealItem.length -1; j++) {
//                            IDHealItem[j] = IDHealItem[j + 1];
//                        }
//                        IDHealItem[IDHealItem.length - 1] = null;
//                        canHeal = false;
//                        HealcountDown = 2;
//                        NumHeal--;
//                        return;
//
//                    }
//                }for (int i = 0; i < NumHeal; i++) {
//                    if(IDHealItem[i].equalsIgnoreCase("SNACK") ){
//
//                        hero.useItem(IDHealItem[i]);
//                        for (int j = i; j < IDHealItem.length -1; j++) {
//                            IDHealItem[j] = IDHealItem[j + 1];
//                        }
//                        IDHealItem[IDHealItem.length - 1] = null;
//                        canHeal = false;
//                        HealcountDown = 2;
//                        NumHeal--;
//                        return;
//                    }
//                }
//            }
//            else  currentPriority=prePriority;
//
//        }

    }
    private void handleThrowBomb(GameMap gameMap, Player player) {
        // copy logic throw (priority 4)
//        System.out.println("Vao cau lenh throw");
//        boolean deploy = false;
//        for (Player target : listPlayerInRangeThrow) {
//            if (!getThrowDirection(currentNode, new Node(target.x, target.y)).equalsIgnoreCase("planB")) {
//                hero.throwItem(getThrowDirection(currentNode, new Node(target.x, target.y)));
//                hasThrow=false;
//                deploy = true;
//            }
//        }
//        if (!deploy) {
//            Node food = null;
//            int lowestHp = Integer.MAX_VALUE;
//            System.out.println("--------------");
//            System.out.println("list Player in range throw");
//            for (Player p : listPlayerInRangeThrow) {
//                System.out.println("Name: " + p.getPlayerName());
//                System.out.println("HP: " + p.getHp());
//                if (p.getHp() < lowestHp) {
//                    lowestHp = p.getHp();
//                    food = p;
//                }
//            }
//            System.out.println("Khong nem duoc, tiep tuc thuc hien hanh dong truoc:");
//            currentPriority = prePriority;
//        }

    }
    private void handleShoot(GameMap gameMap, Player player) {
        // copy khối currentPriority == 5
        System.out.println("Vao cau lenh shoot");

        //lay node Of TargetPlayer
//        Node nodeOfTargetPlayer = null;
//        if (listPlayerInRangeShoot.size() < 2) {// danh 1 vs 1
//            savedName = listPlayerInRangeCloseCombat.getFirst().getPlayerName();
//            savedTarget = listPlayerInRangeCloseCombat.getFirst();
//            nodeOfTargetPlayer = new Node(listPlayerInRangeShoot.get(0).getX(),listPlayerInRangeShoot.get(0).getY());
//        } else {// combat nhieu nguoi
//            //danh thang thap mau nhat
//            int lowestHp = Integer.MAX_VALUE;
//            System.out.println("--------------");
//            System.out.println("list Player in range shoot");
//            for (Player p : listPlayerInRangeShoot ) {
//                System.out.println("Name: " + p.getPlayerName());
//                System.out.println("HP: "+ p.getHp());
//                if(p.getHp() < lowestHp){
//                    savedName=p.getPlayerName();
//                    savedTarget=p;
//                    lowestHp = p.getHp();
//                    nodeOfTargetPlayer = new Node(p.getX(),p.getY());
//                }
//            }
//            System.out.println("--------------");
//        }
//        restrictedNodes.remove(savedTarget);
//        String direction = getCloseCombatDirection(currentNode, nodeOfTargetPlayer);
//        if (direction.equalsIgnoreCase("planB")) {
//            hero.move(PathUtils.getShortestPath(gameMap, restrictedNodes, currentNode, nodeOfTargetPlayer, false));
//        } else {
//            hero.shoot(direction);
//            NumBullet--;
//            System.out.println("hero shoot at: "+ currentStep);
//            ShootcountDown = 2;
//            canShoot = false;
//        }

    }

    private void handleHunting(GameMap gameMap, Player player) {
        // copy khối currentPriority == 7
//        if(currentPriority == 7 && nearPlayer != null){
//            System.out.println("Vao cau lenh Hunting");
//
//            if(targetPlayer!=null){
//                restrictedNodes.remove(targetPlayer);
//                System.out.println("Player target is: " + targetPlayer.getPlayerName());
//                hero.move(PathUtils.getShortestPath(gameMap,restrictedNodes,currentNode,new Node(targetPlayer.getX(), targetPlayer.getY()),false));
//                System.out.println("the path is: " + PathUtils.getShortestPath(gameMap,restrictedNodes,currentNode,new Node(targetPlayer.getX(), targetPlayer.getY()),false));
//            }
//            if(nearPlayer != null) {
//                restrictedNodes.remove(nearPlayer);
//                System.out.println("Player target is: " + nearPlayer.getPlayerName());
//                hero.move(PathUtils.getShortestPath(gameMap,restrictedNodes,currentNode,new Node(nearPlayer.getX(), nearPlayer.getY()),false));
//                System.out.println("the path is: " + PathUtils.getShortestPath(gameMap,restrictedNodes,currentNode,new Node(nearPlayer.getX(), nearPlayer.getY()),false));
//            }
//        }
//        if (currentPriority!=-1) prePriority=currentPriority;
//
//    }
}

    public void handleLoot(GameMap gameMap, Player player){
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
    public List<Node> collectLootTargets(
            GameMap map,
            Node current
    ) {
        List<Node> targets = new ArrayList<>();
        int x = current.x, y = current.y;
        if (!myInventory.hasGun() &&  existsWithin(map.getAllGun(), current, 5)) {
            addNearbySafeGunTargets(map, current, 5, targets);
            return targets;
        }

        if (myInventory.hasMelee() && !myInventory.hasGun() && existsWithin(map.getAllGun(), current, 10)) {
            addNearbySafeGunTargets(map, current, 10, targets);
            return targets;
        }

        if (myInventory.getHealIds()[3] == null) {
            addTargetsFromList(
                    map.getListHealingItems(),
                    item -> new Node(item.getX(), item.getY()),
                    map,
                    targets
            );
        }

        if (!myInventory.hasHelmet()) {
            addTargetsFromList(
                    map.getListArmors(),
                    a -> new Node(a.getX(), a.getY()),
                    map,
                    targets,
                    armor -> armor.getId().equals("WOODEN_HELMET")
                            || armor.getId().equals("MAGIC_HELMET")
            );
        }
        if (!myInventory.hasArmor()) {
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
        if (!myInventory.hasGun()) {
            addTargetsFromList(map.getAllGun(), w -> new Node(w.getX(), w.getY()), map, targets);
        }
        if (!myInventory.hasMelee()) {
            addTargetsFromList(map.getAllMelee(), w -> new Node(w.getX(), w.getY()), map, targets);
        }
        if (!myInventory.hasThrowable()) {
            addTargetsFromList(map.getAllThrowable(), w -> new Node(w.getX(), w.getY()), map, targets);
        }


        if (!myInventory.hasGun() || !myInventory.hasMelee() || !myInventory.hasThrowable() || !myInventory.hasHelmet() || !myInventory.hasArmor() || myInventory.getHealIds()[3] == null) {
            System.out.println("5) Nếu vẫn thiếu bất kỳ thứ gì → tìm chest");
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
     * @param current        node hiện tại
     * @param targets        list các node muốn tới (đã generate bởi collectLootTargets)
     * @param otherPlayers   list Player khác để bổ sung avoid nếu cần
     * @return chuỗi directions (vd "rruuld...") hoặc null nếu hoàn toàn không tìm được
     */
    public String findBestPath(
            GameMap map,
            Node current,
            List<Node> targets,
            List<Player> otherPlayers

    ) {
        // 1) Thử theo từng target
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

    /**
     * Helper thử tính path đến khoảng (tx,ty) – một điểm ở “góc”.
     */
    private String tryCorner(
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
     * @param current   node hiện tại của player
     * @param path      chuỗi directions (vd "urddl") hoặc null
     * @return true nếu đã thực hiện move; false nếu đã xử lý loot/attack hoặc không làm gì
     */
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
                    // nếu chỉ muốn đánh 1 lần/chest, có thể break ở đây
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    void attackAdjacentChestsNoId(GameMap gameMap, int px, int py) {
        try {
            for (Obstacle chest : gameMap.getListChests()) {
                if (chest.getHp() <= 0) continue;
                int dx = chest.getX() - px;
                int dy = chest.getY() - py;
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

