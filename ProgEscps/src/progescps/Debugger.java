package progescps;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class Debugger extends Hero {
    private static final long serialVersionUID = 1L;
    private int debugCooldown = 0;
    private int patchCooldown = 0;
    private int inspectCooldown = 0;

    public Debugger() {
        super(new Random());
        this.maxHP = 250;
        this.hp = this.maxHP;
        this.minDmg = 10;
        this.maxDmg = 20;
        this.maxMana = 100;
        this.mana = this.maxMana;
        this.attackNames = new String[]{"Debug", "Patch", "Inspect", "Breakpoint"};
    }

    @Override
    public String getClassName() {
        return "Debugger";
    }

    @Override
    protected List<String> getAllowedWeapons() {
        return Arrays.asList("Debug Tool", "Patch Kit", "Inspection Lens", "Breakpoint Hammer");
    }

    @Override
    protected List<String> getAllowedArmors() {
        return Arrays.asList("Firewall Vest", "Debug Armor");
    }

    @Override
    public void decrementCooldowns() {
        if (debugCooldown > 0) debugCooldown--;
        if (patchCooldown > 0) patchCooldown--;
        if (inspectCooldown > 0) inspectCooldown--;
    }

    @Override
    public void applyPassiveEffects() {
        // High defense passive is handled in Hero.receiveDamage
    }

    @Override
    public void useSkill(int skillIdx, Enemy enemy) {
        double multiplier = getSkillMultiplier();
        switch (skillIdx) {
            case 0: // Debug
                if (debugCooldown == 0 && mana >= 20) {
                    int baseDamage = minDmg + random.nextInt(maxDmg - minDmg + 1);
                    int damage = (int)(baseDamage * 1.5 * multiplier);
                    System.out.println("You debug the code and deal " + damage + " damage!");
                    enemy.receiveDamage(damage);
                    mana -= 20;
                    debugCooldown = 4;
                } else {
                    System.out.println("Debug is on cooldown or insufficient mana! Using normal attack.");
                    super.useSkill(1, enemy);
                }
                break;
            case 1: // Patch
                if (patchCooldown == 0 && mana >= 25) {
                    int heal = (int)(maxHP * 0.2);
                    hp = Math.min(hp + heal, maxHP);
                    System.out.println("You apply a patch and heal " + heal + " HP!");
                    mana -= 25;
                    patchCooldown = 5;
                } else {
                    System.out.println("Patch is on cooldown or insufficient mana! Using normal attack.");
                    super.useSkill(1, enemy);
                }
                break;
            case 2: // Inspect
                if (inspectCooldown == 0 && mana >= 15) {
                    int baseDamage = minDmg + random.nextInt(maxDmg - minDmg + 1);
                    int damage = (int)(baseDamage * multiplier);
                    System.out.println("You inspect and deal " + damage + " damage, revealing enemy weaknesses!");
                    enemy.receiveDamage(damage);
                    mana -= 15;
                    inspectCooldown = 3;
                } else {
                    System.out.println("Inspect is on cooldown or insufficient mana! Using normal attack.");
                    super.useSkill(1, enemy);
                }
                break;
            case 3: // Breakpoint
                if (mana >= 30) {
                    int baseDamage = minDmg + random.nextInt(maxDmg - minDmg + 1);
                    int damage = (int)(baseDamage * 2 * multiplier);
                    System.out.println("You set a breakpoint and deal " + damage + " damage, stunning the enemy!");
                    enemy.receiveDamage(damage);
                    enemy.stunnedForNextTurn = true;
                    mana -= 30;
                } else {
                    System.out.println("Insufficient mana for Breakpoint! Using normal attack.");
                    super.useSkill(1, enemy);
                }
                break;
            default:
                super.useSkill(1, enemy);
                break;
        }
    }
}