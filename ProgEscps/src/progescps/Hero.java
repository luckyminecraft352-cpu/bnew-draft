package progescps;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public abstract class Hero implements Serializable {
    private static final long serialVersionUID = 1L;

    protected transient Random random;
    public int hp;
    public int maxHP;
    public int minDmg;
    public int maxDmg;
    public int mana;
    public int maxMana;
    public int gold;
    public int xp = 0;
    public int level = 1;
    public int xpToLevel = 50;
    protected String[] attackNames;
    private int shoutCooldown = 0;
    private String[] shouts = {"Code Force"};
    private static final int SHOUT_COOLDOWN = 3;
    private transient List<Faction> factions = new ArrayList<>();
    private transient List<StatusEffect> statusEffects = new ArrayList<>();

    public boolean isAlive() {
        return hp > 0;
    }

    public int getMinDamage() {
        return minDmg + equipment.weaponBonus;
    }

    public int getMaxDamage() {
        return maxDmg + equipment.weaponBonus;
    }

    public boolean useSpecialAbility(Combat combat) {
        // Default implementation - can be overridden by subclasses
        System.out.println(Color.colorize("Using special ability!", Color.YELLOW));
        return true;
    }

    public class Equipment implements Serializable {
        private static final long serialVersionUID = 1L;
        public String weapon;
        public String armor;
        public String accessory;
        public int weaponBonus;
        public int armorBonus;

        public Equipment() {
            weapon = "Fists";
            armor = "Clothes";
            accessory = "None";
            weaponBonus = 0;
            armorBonus = 0;
        }
    }

    public Equipment equipment = new Equipment();

    public class InventoryItem implements Serializable {
        private static final long serialVersionUID = 1L;
        public String name;
        public int quantity;
        public float weight;

        public InventoryItem(String name, float weight) {
            this.name = name;
            this.weight = weight;
            this.quantity = 1;
        }
    }

    private transient List<InventoryItem> inventory = new ArrayList<>();
    private float carryingCapacity = 100.0f;

    public String[] weaponTable = {
        "Basic Exploit", "Advanced Exploit", "Zero-Day Exploit", "Elegant Script", "Fragile Code", "Malicious Code",
        "Phishing Bow", "Long-Range Attack", "Multi-Vector Attack", "Stealthy Bow", "Precise Bow", "Dark Bow",
        "Firewall Script", "Encryption Wand", "Malware Script", "DDoS Script", "Patch Script",
        "Overload Wand", "Multi-Tool Orb",
        "Brute Force Tool", "Axe of Destruction", "Mace of Denial", "Flail of Errors",
        "Legendary Exploit", "Rootkit Code", "Freeze Script", "Anti-Virus Tool", "Legendary Bow",
        "Ebony Exploit"
    };

    public String[] itemTable = {
        "Energy Drink", "Caffeine Shot", "Overclock Potion", "Acceleration Potion", "Intelligence Boost",
        "Basic Firewall", "Chain Firewall", "Plate Firewall", "Protection Robe", "Shadow Cloak",
        "Password Cracker", "Data Gem", "Warp Drive", "Regeneration Chip",
        "Legendary Armor", "Ultimate Potion", "Energy Core", "Quantum Ring",
        "Coder Robes", "Elegant Armor", "Brute Armor", "Stealth Cloak", "Debugger Shroud"
    };

    public Hero(Random random) {
        this.random = random != null ? random : new Random();
        this.gold = 50;
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        this.random = new Random();
        if (this.factions == null) this.factions = new ArrayList<>();
        if (this.statusEffects == null) this.statusEffects = new ArrayList<>();
        if (this.inventory == null) this.inventory = new ArrayList<>();
    }

    public abstract String getClassName();
    protected abstract List<String> getAllowedWeapons();
    protected abstract List<String> getAllowedArmors();

    public boolean equipItem(String itemName) {
        List<String> allowedWeapons = getAllowedWeapons();
        List<String> allowedArmors = getAllowedArmors();
        for (InventoryItem item : inventory) {
            if (item.name.equalsIgnoreCase(itemName)) {
                if (allowedWeapons.contains(itemName)) {
                    equipment.weapon = itemName;
                    equipment.weaponBonus = calculateWeaponBonus(itemName);
                    System.out.println("Equipped " + itemName + " (+" + equipment.weaponBonus + " damage)");
                    return true;
                } else if (allowedArmors.contains(itemName)) {
                    equipment.armor = itemName;
                    equipment.armorBonus = calculateArmorBonus(itemName);
                    System.out.println("Equipped " + itemName + " (+" + equipment.armorBonus + " defense)");
                    return true;
                }
            }
        }
        System.out.println("Cannot equip " + itemName + ": not compatible with " + getClassName());
        return false;
    }

    public boolean useItem(String itemName) {
        for (InventoryItem item : inventory) {
            if (item.name.equalsIgnoreCase(itemName)) {
                switch (itemName.toLowerCase()) {
                    case "energy drink":
                        hp = Math.min(hp + 50, maxHP);
                        System.out.println("Used Energy Drink, restored 50 HP!");
                        break;
                    case "caffeine shot":
                        mana = Math.min(mana + 50, maxMana);
                        System.out.println("Used Caffeine Shot, restored 50 Mana!");
                        break;
                    case "ultimate potion":
                        hp = maxHP;
                        System.out.println("Used Ultimate Potion, fully restored HP!");
                        break;
                    case "energy core":
                        shoutCooldown = Math.max(0, shoutCooldown - 1);
                        System.out.println("Used Energy Core, reduced shout cooldown by 1!");
                        break;
                    default:
                        System.out.println("Cannot use " + itemName + "!");
                        return false;
                }
                item.quantity--;
                if (item.quantity == 0) {
                    inventory.remove(item);
                }
                return true;
            }
        }
        System.out.println("Item not found in inventory!");
        return false;
    }

    private int calculateWeaponBonus(String weapon) {
        if (weapon.contains("Basic Exploit")) return 3;
        if (weapon.contains("Advanced Exploit")) return 5;
        if (weapon.contains("Zero-Day Exploit")) return 8;
        if (weapon.contains("Elegant Script")) return 10;
        if (weapon.contains("Fragile Code")) return 12;
        if (weapon.contains("Malicious Code")) return 15;
        if (weapon.contains("Phishing Bow") || weapon.contains("Long-Range Attack")) return 5;
        if (weapon.contains("Multi-Vector Attack") || weapon.contains("Stealthy Bow") || weapon.contains("Precise Bow") || weapon.contains("Dark Bow")) return 7;
        if (weapon.contains("Firewall Script") || weapon.contains("Encryption Wand") || weapon.contains("Malware Script") || weapon.contains("DDoS Script") || weapon.contains("Patch Script") || weapon.contains("Overload Wand")) return 7;
        if (weapon.contains("Multi-Tool Orb")) return 10;
        if (weapon.contains("Brute Force Tool") || weapon.contains("Axe of Destruction") || weapon.contains("Mace of Denial") || weapon.contains("Flail of Errors")) return 6;
        if (weapon.contains("Legendary Exploit") || weapon.contains("Legendary Bow")) return 18;
        if (weapon.contains("Rootkit Code")) return 20;
        if (weapon.contains("Freeze Script") || weapon.contains("Anti-Virus Tool")) return 15;
        if (weapon.contains("Ebony Exploit")) return 14;
        return 0;
    }

    private int calculateArmorBonus(String armor) {
        if (armor.contains("Basic Firewall")) return 3;
        if (armor.contains("Chain Firewall")) return 6;
        if (armor.contains("Plate Firewall")) return 10;
        if (armor.contains("Protection Robe")) return 4;
        if (armor.contains("Shadow Cloak")) return 5;
        if (armor.contains("Legendary Armor")) return 15;
        if (armor.contains("Coder Robes")) return 6;
        if (armor.contains("Elegant Armor")) return 8;
        if (armor.contains("Brute Armor")) return 12;
        if (armor.contains("Stealth Cloak")) return 7;
        if (armor.contains("Debugger Shroud")) return 5;
        return 0;
    }

    public boolean addItem(String itemName, float weight) {
        if (getCurrentWeight() + weight > carryingCapacity) {
            System.out.println("Inventory full!");
            return false;
        }
        for (InventoryItem item : inventory) {
            if (item.name.equals(itemName)) {
                item.quantity++;
                return true;
            }
        }
        inventory.add(new InventoryItem(itemName, weight));
        return true;
    }

    public float getCurrentWeight() {
        float total = 0;
        for (InventoryItem item : inventory) {
            total += item.weight * item.quantity;
        }
        return total;
    }

    public void showInventory() {
        System.out.println("\n=== Inventory (" + getCurrentWeight() + "/" + carryingCapacity + ") ===");
        for (InventoryItem item : inventory) {
            System.out.println("- " + item.name + " x" + item.quantity + " (" + item.weight + " lbs each)");
        }
        System.out.println("\nEquipped:");
        System.out.println("- Weapon: " + equipment.weapon + " (+" + equipment.weaponBonus + ")");
        System.out.println("- Armor: " + equipment.armor + " (+" + equipment.armorBonus + ")");
    }

    public void showAttacks() {
        for (int i = 0; i < attackNames.length; i++) {
            System.out.println((i + 1) + ". " + attackNames[i]);
        }
    }

    public void addGold(int amount) {
        gold += amount;
        System.out.println("You received " + amount + " gold. Total gold: " + gold);
    }

    public boolean spendGold(int amount) {
        if (gold >= amount) {
            gold -= amount;
            System.out.println("You spent " + amount + " gold. Remaining gold: " + gold);
            return true;
        } else {
            System.out.println("Not enough gold!");
            return false;
        }
    }

    public void joinFaction(Faction faction) {
        factions.add(faction);
        faction.joinFaction();
        faction.applyBenefits(this);
    }

    public boolean isInFaction(String factionName) {
        for (Faction faction : factions) {
            if (faction.getName().equalsIgnoreCase(factionName)) {
                return faction.isMember();
            }
        }
        return false;
    }

    public void addFactionReputation(String factionName, int amount) {
        for (Faction faction : factions) {
            if (faction.getName().equalsIgnoreCase(factionName)) {
                faction.addReputation(amount);
                return;
            }
        }
    }

    public List<Faction> getFactions() {
        return factions;
    }

    public void addXP(int amount) {
        xp += amount;
        System.out.println("You gained " + amount + " XP. Total XP: " + xp + "/" + xpToLevel);
    }

    public boolean checkLevelUp() {
        return xp >= xpToLevel;
    }

    public void levelUp() {
        while (xp >= xpToLevel) {
            xp -= xpToLevel;
            level++;
            maxHP += 20;
            maxMana += 10;
            minDmg += 3;
            maxDmg += 5;
            hp = maxHP;
            mana = maxMana;
            xpToLevel = (int) (xpToLevel * 1.4);
            System.out.println("You leveled up to level " + level + "!");
            System.out.println("Stats increased! HP and Mana restored!");
        }
    }

    public void receiveDamage(int dmg) {
        if (this instanceof Debugger) {
            dmg = (int) (dmg * 0.9);
        }
        hp -= dmg;
        if (hp < 0) {
            hp = 0;
        }
        System.out.println(getClassName() + " takes " + dmg + " damage.");
    }

    public void decrementCooldowns() {
        if (shoutCooldown > 0) {
            shoutCooldown--;
        }
    }

    public void useSkill(int skillIdx, Enemy enemy) {
        double multiplier = getSkillMultiplier();
        int baseDmg = minDmg + random.nextInt(maxDmg - minDmg + 1);
        int damage = (int)(baseDmg * multiplier);
        System.out.println("You use " + attackNames[skillIdx] + " and deal " + damage + " damage!");
        enemy.receiveDamage(damage);
    }

    public void useShout(int shoutIdx, Enemy enemy) {
        if (shoutCooldown > 0) {
            System.out.println("Shout is on cooldown! (" + shoutCooldown + " turns remaining)");
            return;
        }
        if (shoutIdx >= 0 && shoutIdx < shouts.length) {
            String shout = shouts[shoutIdx];
            if (shout.equals("Code Force")) {
                int damage = 50 + random.nextInt(20);
                enemy.receiveDamage(damage);
                enemy.stunnedForNextTurn = true;
                System.out.println("You shout 'Code Force!' dealing " + damage + " damage and stunning the enemy!");
                shoutCooldown = SHOUT_COOLDOWN;
            }
        } else {
            System.out.println("Invalid shout!");
        }
    }

    public void showShouts() {
        System.out.println("\nAvailable Shouts:");
        for (int i = 0; i < shouts.length; i++) {
            System.out.println((i + 1) + ". " + shouts[i]);
        }
    }

    public String[] getShouts() {
        return shouts;
    }

    public double getSkillMultiplier() {
        int tier = (level - 1) / 10 + 1;
        return 1.0 + 0.1 * (tier - 1);
    }

    public String getStatsString() {
        return
            "Class: " + getClassName() + "\n" +
            "Level: " + level + "\n" +
            "XP: " + xp + "/" + xpToLevel + "\n" +
            "HP: " + hp + "/" + maxHP + "\n" +
            "Mana: " + mana + "/" + maxMana + "\n" +
            "Attack: " + minDmg + " - " + maxDmg + "\n" +
            "Gold: " + gold;
    }

    public List<InventoryItem> getInventory() {
        return inventory;
    }

    public int getGold() {
        return gold;
    }

    public void removeItem(String itemName) {
        Iterator<InventoryItem> it = inventory.iterator();
        while (it.hasNext()) {
            InventoryItem item = it.next();
            if (item.name.equals(itemName)) {
                item.quantity--;
                if (item.quantity <= 0) {
                    it.remove();
                }
                return;
            }
        }
    }

    public void clearStatusEffects() {
        statusEffects.clear();
    }

    public int getNextLevelXP() {
        return xpToLevel;
    }

    public void applyStatusEffect(StatusEffect effect) {
        if (statusEffects == null) statusEffects = new ArrayList<>();
        statusEffects.add(effect);
        effect.apply(this);
    }

    public void updateStatusEffects() {
        if (statusEffects == null) statusEffects = new ArrayList<>();
        for (Iterator<StatusEffect> it = statusEffects.iterator(); it.hasNext();) {
            StatusEffect effect = it.next();
            effect.tick(this);
            if (!effect.isActive()) {
                // restore any modified stats and remove the effect
                effect.restore(this);
                it.remove();
            }
        }
    }

    private void resetStat(String targetStat) {
        if (targetStat.equals("damage")) {
            // No-op: restoration is handled within StatusEffect.restore()
        } else if (targetStat.equals("speed")) {
            // Placeholder: Implement if speed is added
        }
    }

    public List<StatusEffect> getStatusEffects() {
        if (statusEffects == null) statusEffects = new ArrayList<>();
        return statusEffects;
    }

    public void applyPassiveEffects() {
        if (this instanceof Hacker) {
            mana = Math.min(mana + (int)(maxMana * 0.1), maxMana);
            System.out.println("Hacker regenerates " + (int)(maxMana * 0.1) + " mana.");
        }
    }

    public int getDefense() {
        return equipment.armorBonus + (this instanceof Debugger ? 5 : 0);
    }
}