package progescps;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class Support extends Hero {
    private static final long serialVersionUID = 1L;
    private int patchCooldown = 0;
    private int bufferCooldown = 0;
    private boolean bufferActive = false;

    public Support() {
        super(new Random());
        this.maxHP = 240;
        this.hp = this.maxHP;
        this.minDmg = 15;
        this.maxDmg = 30;
        this.maxMana = 100;
        this.mana = this.maxMana;
        this.attackNames = new String[]{"Patch", "Heal", "Buffer", "Restore"};
    }

    @Override
    public String getClassName() {
        return "Support";
    }

    @Override
    protected List<String> getAllowedWeapons() {
        return Arrays.asList("Code Injector", "Debug Tool", "Script Runner", "Patch Kit");
    }

    @Override
    protected List<String> getAllowedArmors() {
        return Arrays.asList("Firewall Vest", "Encryption Cloak");
    }

    @Override
    public void decrementCooldowns() {
        if (patchCooldown > 0) patchCooldown--;
        if (bufferCooldown > 0) {
            bufferCooldown--;
            if (bufferCooldown == 0) {
                bufferActive = false;
            }
        }
    }

    @Override
    public void applyPassiveEffects() {
        int heal = (int)(maxHP * 0.05);
        hp = Math.min(hp + heal, maxHP);
        if (heal > 0) {
            System.out.println("Support regenerates " + heal + " HP.");
        }
    }

    private void triggerCodeGrace() {
        if (random.nextInt(100) < 20) {
            String[] abilities = {"Patch", "Buffer"};
            int[] cooldowns = {patchCooldown, bufferCooldown};
            List<Integer> activeCooldowns = new ArrayList<>();
            for (int i = 0; i < cooldowns.length; i++) {
                if (cooldowns[i] > 0) {
                    activeCooldowns.add(i);
                }
            }
            if (!activeCooldowns.isEmpty()) {
                int idx = activeCooldowns.get(random.nextInt(activeCooldowns.size()));
                if (idx == 0) patchCooldown = 0;
                else if (idx == 1) bufferCooldown = 0;
                System.out.println("Code Grace resets the cooldown of " + abilities[idx] + "!");
            }
        }
    }

    @Override
    public void useSkill(int skillIdx, Enemy enemy) {
        double multiplier = getSkillMultiplier();
        switch (skillIdx) {
            case 0: // Patch
                if (patchCooldown == 0 && mana >= 20) {
                    int baseDamage = minDmg + random.nextInt(maxDmg - minDmg + 1);
                    int damage = (int)(baseDamage * 1.5 * multiplier);
                    System.out.println("You apply Patch and deal " + damage + " damage to all enemies!");
                    enemy.receiveDamage(damage);
                    mana -= 20;
                    patchCooldown = 4;
                } else {
                    System.out.println("Patch is on cooldown or insufficient mana! Using normal attack.");
                    super.useSkill(1, enemy);
                }
                break;
            case 1: // Heal
                super.useSkill(1, enemy);
                break;
            case 2: // Buffer
                if (bufferCooldown == 0 && mana >= 15) {
                    System.out.println("You cast Buffer, reducing damage taken next turn!");
                    bufferActive = true;
                    mana -= 15;
                    bufferCooldown = 3;
                } else {
                    System.out.println("Buffer is on cooldown or insufficient mana! Using normal attack.");
                    super.useSkill(1, enemy);
                }
                break;
            case 3: // Restore
                if (mana >= 25) {
                    int heal = (int)(maxHP * 0.4 * multiplier);
                    hp = Math.min(hp + heal, maxHP);
                    System.out.println("You cast Restore and restore " + heal + " HP!");
                    mana -= 25;
                    triggerCodeGrace();
                } else {
                    System.out.println("Insufficient mana for Restore! Using normal attack.");
                    super.useSkill(1, enemy);
                }
                break;
            default:
                super.useSkill(1, enemy);
                break;
        }
    }

    @Override
    public void receiveDamage(int dmg) {
        if (bufferActive) {
            dmg = (int)(dmg * 0.5);
            System.out.println("Buffer absorbs half the damage!");
        }
        super.receiveDamage(dmg);
    }
}