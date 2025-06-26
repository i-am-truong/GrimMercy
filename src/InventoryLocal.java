public class InventoryLocal {
    private String currentGunId;

    public InventoryLocal() {
    }

    public String getCurrentGunId() {
        return currentGunId;
    }

    public void setCurrentGunId(String currentGunId) {
        this.currentGunId = currentGunId;
    }

    public void reset(){
        this.currentGunId = null;
    }
}
