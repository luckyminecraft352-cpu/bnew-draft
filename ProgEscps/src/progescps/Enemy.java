package progescps;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Enemy class with restored serialization handling and runtime hostility override.
 */
public class Enemy implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Tier { WEAK, NORMAL, STRONG }
    private Tier tier;

    Random random;
    int hp;
    int maxHP;
    int minDmg;
    int maxDmg;
    int level;

    int gluttonyCooldown = 0;
    boolean bleedingActive = false;
    public boolean stunnedForNextTurn = false;
    boolean nextAttackIsDoubleDamage = false;
    private boolean manaDrainActive = false; // For Dark Aura debuff
    private int manaDrainTurns = 0;
    private boolean playerDamageReduced = false; // For Crippling Blow
    private int damageReductionTurns = 0;

    // Persisted list of status effects (no longer transient so saves with enemy)
    private List<StatusEffect> statusEffects = new ArrayList<>();

    LinkedList<String> enemyNames;
    String currentName;
    private transient Map<String, Runnable> specialAbilities = new HashMap<>();
    private String selectedAbility;

    // Override for hostility set at runtime. When null, fallback to name-based hostility list.
    private Boolean hostileOverride = null;

    public Enemy() {
        this(Tier.NORMAL);
    }

    public Enemy(Tier tier) {
        this(tier, 1);
    }

    public Enemy(Tier tier, int playerLevel) {
        this.tier = tier;
        this.random = new Random();
        this.level = playerLevel;
        initializeStats();
        initializeEnemyNames();
        this.currentName = getRandomEnemyName();
        initializeSpecialAbilities();
        selectRandomAbility();
        maxHP = (int)(maxHP * (1 + level * 0.1));
        minDmg = (int)(minDmg * (1 + level * 0.1));
        maxDmg = (int)(maxDmg * (1 + level * 0.1));
        hp = maxHP;
        System.out.println("A " + getDisplayName() + " (Level " + level + ") has appeared!");
    }

    private void initializeStats() {
        switch (tier) {
            case WEAK:
                maxHP = 50 + random.nextInt(20);
                minDmg = 5 + random.nextInt(5);
                maxDmg = 10 + random.nextInt(5);
                break;
            case NORMAL:
                maxHP = 80 + random.nextInt(30);
                minDmg = 10 + random.nextInt(10);
                maxDmg = 20 + random.nextInt(10);
                break;
            case STRONG:
                maxHP = 120 + random.nextInt(50);
                minDmg = 15 + random.nextInt(15);
                maxDmg = 30 + random.nextInt(15);
                break;
        }
        hp = maxHP;
    }

    private void initializeSpecialAbilities() {
        specialAbilities = new HashMap<>();
        specialAbilities.put("Self-Repair", () -> {
            int heal = (int)(maxHP * 0.15);
            hp = Math.min(hp + heal, maxHP);
            System.out.println(currentName + " uses Self-Repair, restoring " + heal + " HP!");
        });
        specialAbilities.put("Data Poison", () -> {
            System.out.println(currentName + " inflicts data corruption! You lose 5 HP next turn.");
        });
        specialAbilities.put("Fork Bomb", () -> {
            System.out.println(currentName + " launches a fork bomb and attacks twice!");
        });
        specialAbilities.put("System Slowdown", () -> {
            int damage = random.nextInt(15) + 15;
            System.out.println(currentName + " causes System Slowdown, dealing " + damage + " damage and reducing your processing speed for 1 turn!");
            playerDamageReduced = true;
            damageReductionTurns = 1;
        });
        specialAbilities.put("Resource Drain", () -> {
            System.out.println(currentName + " drains your resources, reducing your mana for 2 turns!");
            manaDrainActive = true;
            manaDrainTurns = 2;
        });
    }

    public boolean useSpecialAbility() {
        if (specialAbilities == null || specialAbilities.isEmpty() || selectedAbility == null) {
            return false;
        }

        System.out.println(Color.colorize(currentName + " uses " + selectedAbility + "!", Color.RED));
        Runnable r = specialAbilities.get(selectedAbility);
        if (r != null) {
            r.run();
            return true;
        }
        return false;
    }

    public void receiveDamage(int damage) {
        hp -= damage;
        if (hp < 0) hp = 0;
    }

    public boolean isAlive() {
        return hp > 0;
    }

    public void updateStatusEffects() {
        if (statusEffects == null) statusEffects = new ArrayList<>();
        Iterator<StatusEffect> iterator = statusEffects.iterator();
        while (iterator.hasNext()) {
            StatusEffect effect = iterator.next();
            effect.tick(this);
            if (!effect.isActive()) {
                // restore any modified stats and remove effect
                effect.restore(this);
                iterator.remove();
            }
        }
    }

    String[] attackNames = {
        "Data Corruption", "Memory Leak", "System Crash",
        "Firewall Breach", "Code Injection", "Buffer Overflow",
        "DDoS Attack", "Encryption Break", "Privilege Escalation"
    };

    public static String[] listEnemyNames() {
        return new String[] {
            "Virus", "Trojan", "Malware", "Spyware", "Ransomware", "Worm",
            "Rootkit", "Buffer Overflow", "SQL Injection", "Phishing Scam",
            "Adware", "Keylogger", "Botnet", "Exploit", "Zero Day",
            "Firewall Guard", "Antivirus", "Debugger", "System Monitor",
            "Backup Service", "Patch Manager", "Security Scanner",
            "Data Miner", "Cryptojacker", "DDoS Bot", "Logic Bomb",
            "Macro Virus", "File Infector", "Network Worm", "Drive By",
            "Clickjacking", "Session Hijacker", "Man in the Middle",
            "Credential Harvester", "Brute Force", "Dictionary Attack",
            "Rainbow Table", "Social Engineer", "Pharming Attack",
            "DNS Poisoner", "ARP Spoofer", "IP Spoofer", "Packet Sniffer",
            "Port Scanner", "Vulnerability Scanner", "Exploit Kit",
            "Command Injector", "Cross Site", "Script Kiddie", "Black Hat"
        };
    }

    public void applyStatusEffect(StatusEffect effect) {
        if (statusEffects == null) statusEffects = new ArrayList<>();
        statusEffects.add(effect);
        effect.apply(this);
    }

    private void resetStat(String targetStat) {
        // For future stat resets - not used now because StatusEffect.restore handles restoration
    }

    public List<StatusEffect> getStatusEffects() {
        if (statusEffects == null) statusEffects = new ArrayList<>();
        return statusEffects;
    }

    private void selectRandomAbility() {
        if (specialAbilities == null || specialAbilities.isEmpty()) {
            selectRandomAbilityFallback();
            return;
        }
        List<String> abilityKeys = new ArrayList<>(specialAbilities.keySet());
        selectedAbility = abilityKeys.get(random.nextInt(abilityKeys.size()));
    }

    private void selectRandomAbilityFallback() {
        selectedAbility = "Self-Repair";
    }

    private void initializeEnemyNames() {
        enemyNames = new LinkedList<>();
        enemyNames.addAll(Arrays.asList(listEnemyNames()));
    }

    public String getCurrentName() {
        return currentName;
    }

    public String getDisplayName() {
        return currentName + " (" + tier + ")";
    }

    private String getRandomEnemyName() {
        if (enemyNames == null || enemyNames.isEmpty()) {
            initializeEnemyNames();
        }
        return enemyNames.get(random.nextInt(enemyNames.size()));
    }

    public void changeName(String newName) {
        if (enemyNames == null) initializeEnemyNames();
        if (enemyNames.contains(newName)) {
            this.currentName = newName;
        } else {
            this.currentName = getRandomEnemyName();
        }
    }

    public void applyPassiveEffects(Hero player) {
        // Passive: Auto-Repair Protocol
        int healAmount = (int)(maxHP * (tier == Tier.WEAK ? 0.03 : tier == Tier.NORMAL ? 0.05 : 0.07));
        if (healAmount > 0) {
            hp = Math.min(hp + healAmount, maxHP);
            System.out.println(currentName + " auto-repairs " + healAmount + " HP due to Auto-Repair Protocol!");
        }

        // Passive: Overclock Response
        if (hp < maxHP * 0.3) {
            minDmg = (int)(minDmg * 1.2);
            maxDmg = (int)(maxDmg * 1.2);
            System.out.println(currentName + " overclocks, increasing damage by 20%!");
        }
    }

    public void takeTurn(Hero player) {
        if (isDocile()) {
            System.out.println(currentName + " is a security program and refuses to fight.");
            return;
        }

        applyPassiveEffects(player);

        if (gluttonyCooldown > 0) {
            gluttonyCooldown--;
        }

        if (stunnedForNextTurn) {
            System.out.println("Enemy (" + currentName + ") is frozen and cannot act!");
            stunnedForNextTurn = false;
            return;
        }

        // Handle Dark Aura mana drain
        if (manaDrainActive && manaDrainTurns > 0) {
            int manaDrain = (int)(player.maxMana * 0.1);
            player.mana = Math.max(0, player.mana - manaDrain);
            System.out.println(currentName + "'s Resource Drain consumes " + manaDrain + " of your mana!");
            manaDrainTurns--;
            if (manaDrainTurns == 0) {
                manaDrainActive = false;
            }
        }

        // Apply damage reduction from Crippling Blow
        int originalPlayerMinDmg = player.minDmg;
        int originalPlayerMaxDmg = player.maxDmg;
        if (playerDamageReduced && damageReductionTurns > 0) {
            player.minDmg = (int)(player.minDmg * 0.8);
            player.maxDmg = (int)(player.maxDmg * 0.8);
            System.out.println("Your processing speed is reduced by System Slowdown!");
            damageReductionTurns--;
            if (damageReductionTurns == 0) {
                playerDamageReduced = false;
            }
        }

        if (random.nextInt(100) < 30 && selectedAbility != null && specialAbilities != null && specialAbilities.containsKey(selectedAbility)) {
            specialAbilities.get(selectedAbility).run();
            if (selectedAbility.equals("Data Poison")) {
                player.receiveDamage(5);
            } else if (selectedAbility.equals("Fork Bomb")) {
                for (int i = 0; i < 2; i++) {
                    int dmg = random.nextInt(maxDmg - minDmg + 1) + minDmg;
                    System.out.println("Enemy (" + currentName + ") attacks for " + dmg + " damage!");
                    player.receiveDamage(dmg);
                }
                return;
            } else if (selectedAbility.equals("System Slowdown")) {
                int dmg = random.nextInt(15) + 15;
                player.receiveDamage(dmg);
            }
        }

        int attackIdx = random.nextInt(attackNames.length);
        String attack = attackNames[attackIdx];

        switch (attack) {
            case "Data Corruption":
                int corruptionDmg = random.nextInt(20) + 20;
                System.out.println("Enemy (" + currentName + ") uses Data Corruption and deals " + corruptionDmg + " damage!");
                player.receiveDamage(corruptionDmg);
                break;
            case "Memory Leak":
                int leakDmg = random.nextInt(15) + 15;
                System.out.println("Enemy (" + currentName + ") uses Memory Leak and deals " + leakDmg + " damage!");
                player.receiveDamage(leakDmg);
                if (random.nextInt(100) < 20) {
                    System.out.println("You have been corrupted! (You lose 5 HP next turn)");
                    player.receiveDamage(5);
                }
                break;
            case "System Crash":
                int crashDmg = random.nextInt(10) + 5;
                System.out.println("Enemy (" + currentName + ") uses System Crash and deals " + crashDmg + " damage!");
                player.receiveDamage(crashDmg);
                if (random.nextInt(100) < 15) {
                    System.out.println("You are frozen and will miss your next turn!");
                }
                break;
            case "Firewall Breach":
                int breachHeal = random.nextInt(20) + 10;
                hp += breachHeal;
                if (hp > maxHP) {
                    hp = maxHP;
                }
                System.out.println("Enemy (" + currentName + ") uses Firewall Breach and restores " + breachHeal + " HP!");
                break;
            case "Code Injection":
                int injectionDmg = random.nextInt(15) + 20;
                System.out.println("Enemy (" + currentName + ") uses Code Injection and deals " + injectionDmg + " damage! You are infected!");
                player.receiveDamage(injectionDmg);
                System.out.println("You lose 5 HP from infection.");
                player.receiveDamage(5);
                break;
            case "Buffer Overflow":
                System.out.println("Enemy (" + currentName + ") uses Buffer Overflow and strikes twice!");
                for (int i = 0; i < 2; i++) {
                    int overflowDmg = random.nextInt(15) + 10;
                    player.receiveDamage(overflowDmg);
                }
                break;
            case "DDoS Attack":
                if (gluttonyCooldown == 0 && player.hp > 50) {
                    int chance = random.nextInt(100);
                    if (chance < 30) {
                        int steal = (int) (player.hp * 0.1);
                        player.receiveDamage(steal);
                        hp += steal;
                        if (hp > maxHP) {
                            hp = maxHP;
                        }
                        System.out.println("Enemy (" + currentName + ") uses DDoS Attack! Steals " + steal + " HP and heals itself!");
                        gluttonyCooldown = 4;
                    } else {
                        System.out.println("Enemy (" + currentName + ") tries to use DDoS Attack but fails!");
                    }
                } else {
                    int fallbackDmg = random.nextInt(maxDmg - minDmg + 1) + minDmg;
                    System.out.println("Enemy (" + currentName + ") attacks for " + fallbackDmg + " damage!");
                    player.receiveDamage(fallbackDmg);
                }
                break;
            case "Encryption Break":
                specialAbilities.get("System Slowdown").run();
                break;
            case "Privilege Escalation":
                specialAbilities.get("Resource Drain").run();
                break;
            default:
                int dmg = random.nextInt(maxDmg - minDmg + 1) + minDmg;
                System.out.println("Enemy (" + currentName + ") attacks for " + dmg + " damage!");
                player.receiveDamage(dmg);
                break;
        }

        // Restore player's damage after attack if reduction was applied
        if (playerDamageReduced && damageReductionTurns == 0) {
            player.minDmg = originalPlayerMinDmg;
            player.maxDmg = originalPlayerMaxDmg;
        }
    }

    public boolean isHostile() {
        if (hostileOverride != null) {
            return hostileOverride;
        }
        Set<String> alwaysHostile = new HashSet<>(Arrays.asList(
            "Virus", "Trojan", "Malware", "Spyware", "Ransomware", "Worm",
            "Rootkit", "Buffer Overflow", "SQL Injection", "Phishing Scam",
            "Adware", "Keylogger", "Botnet", "Exploit", "Zero Day",
            "Data Miner", "Cryptojacker", "DDoS Bot", "Logic Bomb",
            "Macro Virus", "File Infector", "Network Worm", "Drive By",
            "Clickjacking", "Session Hijacker", "Man in the Middle",
            "Credential Harvester", "Brute Force", "Dictionary Attack",
            "Rainbow Table", "Social Engineer", "Pharming Attack",
            "DNS Poisoner", "ARP Spoofer", "IP Spoofer", "Packet Sniffer",
            "Port Scanner", "Vulnerability Scanner", "Exploit Kit",
            "Command Injector", "Cross Site", "Script Kiddie", "Black Hat"
        ));
        return alwaysHostile.contains(currentName);
    }

    public boolean isDocile() {
        Set<String> alwaysDocile = new HashSet<>(Arrays.asList(
            "Firewall Guard", "Antivirus", "Debugger", "System Monitor",
            "Backup Service", "Patch Manager", "Security Scanner"
        ));
        return alwaysDocile.contains(currentName);
    }

    public void setHostile(boolean hostile) {
        this.hostileOverride = hostile;
    }

    public Tier getTier() {
        return tier;
    }

    public int getDefense() {
        switch (tier) {
            case WEAK: return 2;
            case NORMAL: return 5;
            case STRONG: return 8;
            default: return 0;
        }
    }

    /**
     * Ensure transient fields are initialized after deserialization.
     */
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        if (random == null) random = new Random();
        if (statusEffects == null) statusEffects = new ArrayList<>();
        if (enemyNames == null) initializeEnemyNames();
        if (specialAbilities == null) initializeSpecialAbilities();
        if (selectedAbility == null && specialAbilities != null && !specialAbilities.isEmpty()) {
            selectRandomAbility();
        }
    }
}