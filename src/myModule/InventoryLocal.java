package myModule;

import jsclub.codefest.sdk.factory.WeaponFactory;
import jsclub.codefest.sdk.model.armors.Armor;
import jsclub.codefest.sdk.model.healing_items.HealingItem;
import jsclub.codefest.sdk.model.weapon.Weapon;

import java.util.ArrayList;
import java.util.List;

public class InventoryLocal {
    private int bullet = 0;
    private int numberThrow = 0;
    private Weapon gun;
    private Weapon melee = WeaponFactory.getWeaponById("HAND");
    private Weapon throwable;
    private Weapon special;
    private Armor armor;
    private Armor helmet;
    private List<HealingItem> listHealingItem = new ArrayList();

    public Weapon getGun() {
        return this.gun;
    }
    public void setGun(Weapon gun) {
        this.gun = gun;
        if(gun != null){
            bullet = gun.getUseCounts();
        }
    }
    public int getBullet(){
        if(hasGun()){
            return this.bullet;
        }
        return 0;
    }
    public void setBullet(int bullet){
        this.bullet = bullet;
    }
    public Weapon getMelee() {
        return this.melee;
    }
    public void setMelee(Weapon melee) {this.melee = melee;}
    public Weapon getThrowable() {
        return this.throwable;
    }
    public void setThrowable(Weapon throwable) {
        this.throwable = throwable;
        if(throwable != null){
            numberThrow = throwable.getUseCounts();
        }
    }
    public int getNumberThrow(){
        if(hasThrowable()){
            return  numberThrow;
        }
        return 0;
    }
    public void setNumberThrow(int numberThrow){
        this.numberThrow = numberThrow;
    }
    public Weapon getSpecial() {
        return this.special;
    }
    public void setSpecial(Weapon special) {
        this.special = special;
    }
    public Armor getHelmet() {
        return this.helmet;
    }
    public void setHelmet(Armor helmet) {
        this.helmet = helmet;
    }
    public Armor getArmor() {
        return this.armor;
    }
    public void setArmor(Armor armor) {this.armor = armor; }
    public List<HealingItem> getListHealingItem() {
        return this.listHealingItem;
    }
    public void setListHealingItem(List<HealingItem> listHealingItem) {
        this.listHealingItem = listHealingItem;
    }

    public boolean hasMelee() {
        return this.melee != null
                && !"HAND".equalsIgnoreCase(this.melee.getId());
    }

    public boolean hasThrowable() {
        return this.throwable != null;
    }

    public boolean hasHelmet(){
        return this.helmet != null;
    }

    public boolean hasArmor() {
        return this.armor != null;
    }

    public boolean hasSpecial(){return this.special != null;}
    public String[] getHealIds() {
        String[] ids = new String[4];
        for (int i = 0; i < listHealingItem.size() && i < 4; i++) {
            ids[i] = listHealingItem.get(i).getId();
        }
        return ids;
    }
    public boolean hasGun() {
        return this.gun != null;
    }

    public void reset() {
        this.setGun((Weapon)null);
        this.setMelee(WeaponFactory.getWeaponById("HAND"));
        this.setThrowable((Weapon)null);
        this.setSpecial((Weapon)null);
        this.setArmor((Armor)null);
        this.setHelmet((Armor)null);
        this.listHealingItem = new ArrayList<>();
    }

    @Override
    public String toString() {
        return "InventoryLocal{" +
                "gun=" + gun + "\n"+
                ", melee=" + melee + "\n"+
                ", throwable=" + throwable + "\n"+
                ", special=" + special + "\n"+
                ", armor=" + armor + "\n"+
                ", helmet=" + helmet + "\n"+
                ", listHealingItem=" + listHealingItem + "\n"+
                '}';
    }
}
