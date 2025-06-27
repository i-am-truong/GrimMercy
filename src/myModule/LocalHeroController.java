package myModule;

import jsclub.codefest.sdk.factory.ArmorFactory;
import jsclub.codefest.sdk.factory.HealingItemFactory;
import jsclub.codefest.sdk.factory.WeaponFactory;
import jsclub.codefest.sdk.model.equipments.Armor;
import jsclub.codefest.sdk.model.equipments.HealingItem;
import jsclub.codefest.sdk.model.weapon.Weapon;

public class LocalHeroController {
    private final InventoryLocal inv;

    public LocalHeroController(InventoryLocal inv) {
        this.inv = inv;
    }

    /**
     * Nhặt item với đúng itemId.
     * - Nếu là Weapon, gán vào slot tương ứng.
     * - Nếu là Armor / Helmet, gán vào slot tương ứng.
     * - Nếu là HealingItem, thêm vào list.
     */
    public void pickupItem(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return;
        }

        // 1) Weapon?
        Weapon w = WeaponFactory.getWeaponById(itemId);
        if (w != null) {
            switch (w.getType()) {
                case GUN:
                    inv.setGun(w);
                    break;
                case SPECIAL:
                    inv.setSpecial(w);
                    break;
                case THROWABLE:
                    inv.setThrowable(w);
                    break;
                case MELEE:
                    inv.setMelee(w);
                    break;
                default:
                    // nếu có loại mới, xử lý ở đây
            }
            return;
        }

        // 2) HealingItem?
        HealingItem hi = HealingItemFactory.getHealingItemById(itemId);
        if (hi != null) {
            inv.getListHealingItem().add(hi);
            return;
        }

        // 3) Armor?
        Armor a = ArmorFactory.getArmorById(itemId);
        if (a != null) {
            // ArmorFactory trả về Armor; để biết là helmet hay body,
            // bạn có thể check ElementType hoặc các thuộc tính bên trong Armor
            if (a.getType() == jsclub.codefest.sdk.model.ElementType.HELMET) {
                inv.setHelmet(a);
            } else {
                inv.setArmor(a);
            }
        }
    }

    /**
     * Dùng một healing item (xóa khỏi list), trả về true nếu thành công.
     */
    public boolean useItem(String itemId) {
        HealingItem hi = HealingItemFactory.getHealingItemById(itemId);
        if (hi != null) {
            return inv.getListHealingItem().remove(hi);
        }
        return false;
    }

    /**
     * Drop (revoke) item: xóa khỏi bất kỳ slot hoặc list nào chứa itemId.
     */
    public void revokeItem(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return;
        }

        // 1. thử remove healing item
        HealingItem hi = HealingItemFactory.getHealingItemById(itemId);
        if (hi != null && inv.getListHealingItem().remove(hi)) {
            return;
        }

        // 2. thử remove weapon
        Weapon gun = inv.getGun();
        if (gun != null && gun.getId().equals(itemId)) {
            inv.setGun(null);
            return;
        }
        Weapon special = inv.getSpecial();
        if (special != null && special.getId().equals(itemId)) {
            inv.setSpecial(null);
            return;
        }
        Weapon throwable = inv.getThrowable();
        if (throwable != null && throwable.getId().equals(itemId)) {
            inv.setThrowable(null);
            return;
        }
        Weapon melee = inv.getMelee();
        if (melee != null && melee.getId().equals(itemId)) {
            // quay về tay không
            inv.setMelee(WeaponFactory.getWeaponById("HAND"));
            return;
        }

        // 3. thử remove armor / helmet
        Armor armor = inv.getArmor();
        if (armor != null && armor.getId().equals(itemId)) {
            inv.setArmor(null);
            return;
        }
        Armor helmet = inv.getHelmet();
        if (helmet != null && helmet.getId().equals(itemId)) {
            inv.setHelmet(null);
        }
    }
}
