import io.socket.emitter.Emitter;
import jsclub.codefest.sdk.algorithm.PathUtils;
import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.Element;
import jsclub.codefest.sdk.model.GameMap;
import jsclub.codefest.sdk.Hero;
import jsclub.codefest.sdk.model.obstacles.Obstacle;
import jsclub.codefest.sdk.model.players.Player;
import jsclub.codefest.sdk.model.weapon.Weapon;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Main {
    private static final String SERVER_URL = "https://cf25-server.jsclub.dev";
    private static final String GAME_ID = "148473";
    private static final String PLAYER_NAME = "TraPham";
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
    private final Hero hero;
    private  String targetID = null;
    private int currentStep = 0;
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
            currentStep++;
            handleGame(gameMap,player);
        } catch (Exception e) {
            System.err.println("Critical error in call method: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void handleGame(GameMap gameMap, Player player){
        String decision = getDecisionForNextStep(gameMap, player);
        System.out.println("=============DEBUG_PART ("+currentStep+")=================");
        System.out.println("Current Decision is : " + decision);
        System.out.println("my Inventory gunID has gunID: " + myInventory.getCurrentGunId());
        String codeFestInvenGunID = hero.getInventory().getGun() !=null? hero.getInventory().getGun().getId(): "null";
        System.out.println("codefest Inventory has gunID: " + codeFestInvenGunID);
        System.out.println("========================================");
        switch(decision){
            case "loot"-> handleLoot(gameMap, player);
            case "die"-> handleDie();
            case "revokeItemForTesting"->revokeItemForTesting();
            default-> System.out.println("should not go to default, do something else");
        }
    }

    public String getDecisionForNextStep(GameMap gameMap, Player player){
        if (player == null || player.getHealth() == 0) {
            return "die";
        }
        if(myInventory.getCurrentGunId() != null){
            return "revokeItemForTesting";
        }
        return "loot";
    }

    public void handleLoot(GameMap gameMap, Player player){
        String lootDecision = getLootDecision(gameMap,player);
        switch (lootDecision){
            case"lootGun"-> handleLootGun(gameMap,player);
            default -> System.out.println("go to loot default, poor logic");
        }
    }

    private String getLootDecision(GameMap gameMap, Player player){
        return "lootGun";
    }

    private void handleLootGun(GameMap gameMap, Player player){
        Node currentNode = new Node(player.getX(), player.getY());
        List<Node> nodesToAvoid = getNodesToAvoid(gameMap);
        handleSearchForGun(gameMap, player, nodesToAvoid, currentNode);

    }

    private void handleSearchForGun(GameMap gameMap, Player player, List<Node> nodesToAvoid, Node currenNode) {
        String pathToGun = findPathToGun(gameMap, nodesToAvoid, player);
        try{
            if(gameMap.getElementByIndex(currenNode.x,currenNode.y).getId().equalsIgnoreCase(targetID)){
                for (int i = 0; i < 100; i++) {
                    hero.pickupItem();
                }
                myInventory.setCurrentGunId(targetID);
                targetID = null;
            }else{
                if (pathToGun != null && !pathToGun.isEmpty()) {
                    String step = pathToGun.substring(0, 1);
                    hero.move(step);
                } else {
                    System.out.println("No path found to gun.");
                }

            }
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    private String findPathToGun(GameMap gameMap, List<Node> nodesToAvoid, Player player) {
        Weapon nearestGun = getNearestGun(gameMap, player);
        if (nearestGun == null) return null;
        return PathUtils.getShortestPath(gameMap, nodesToAvoid, player, nearestGun, false);
    }

    private Weapon getNearestGun(GameMap gameMap, Player player) {
        List<Weapon> guns = gameMap.getAllGun();

        Weapon nearestGun = null;
        double minDistance = Double.MAX_VALUE;

        for (Weapon gun : guns) {
            double distance = PathUtils.distance(player, gun);
            if (distance < minDistance) {
                minDistance = distance;
                nearestGun = gun;
            }
        }
        if (nearestGun != null && targetID == null) {
            System.out.println("ID target is: " + nearestGun.getId());
            targetID = nearestGun.getId();
        }


        return nearestGun;
    }

    public void handleDie(){
        myInventory.reset();
    }

    private void revokeItemForTesting(){
        try{
            hero.revokeItem(myInventory.getCurrentGunId());
            myInventory.setCurrentGunId(null);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private List<Node> getNodesToAvoid(GameMap gameMap) {
        List<Node> nodes = new ArrayList<>(gameMap.getListObstaclesUpdate());
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

}

