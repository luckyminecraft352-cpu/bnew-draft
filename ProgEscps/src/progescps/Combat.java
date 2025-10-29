package progescps;

import java.util.Objects;
import java.util.Random;

public class Combat {
    private int comboCounter = 0;
    private static final double COMBO_MULTIPLIER = 0.1;
    private final Random random = new Random();
    private final Hero player;
    private final Enemy enemy;
    private boolean isOver = false;

    public Combat(Hero player, Enemy enemy) {
        this.player = Objects.requireNonNull(player, "Player cannot be null");
        this.enemy = Objects.requireNonNull(enemy, "Enemy cannot be null");
    }

    public boolean isCombatOver() {
        return isOver || !player.isAlive() || !enemy.isAlive();
    }

    public void processRound(String action) {
        if (action == null) {
            throw new IllegalArgumentException("Action cannot be null");
        }

        // Process player's action
        switch (action.toLowerCase()) {
            case "attack":
                int damage = calculateDamage(
                    player.getMinDamage() + random.nextInt(player.getMaxDamage() - player.getMinDamage() + 1),
                    true
                );
                enemy.receiveDamage(damage);
                System.out.println(Color.colorize("You deal " + damage + " damage!", Color.GREEN));
                comboCounter++;
                break;
            case "special":
                player.useSpecialAbility(this);
                break;
            case "flee":
                if (random.nextInt(100) < 40) { // 40% chance to flee
                    System.out.println(Color.colorize("You successfully fled from combat!", Color.YELLOW));
                    isOver = true;
                    return;
                } else {
                    System.out.println(Color.colorize("Failed to flee!", Color.RED));
                }
                break;
            default:
                System.out.println("Invalid action!");
                break;
        }

        // Process enemy's turn if still alive
        if (enemy.isAlive()) {
            processEnemyTurn();
        }

        // Update effects
        player.updateStatusEffects();
        enemy.updateStatusEffects();
    }

    private void processEnemyTurn() {
        if (enemy.stunnedForNextTurn) {
            System.out.println(Color.colorize("Enemy is stunned!", Color.YELLOW));
            enemy.stunnedForNextTurn = false;
            return;
        }

        // 20% chance for enemy to use special ability
        if (random.nextInt(100) < 20) {
            enemy.useSpecialAbility();
        } else {
            int damage = calculateDamage(
                enemy.minDmg + random.nextInt(enemy.maxDmg - enemy.minDmg + 1),
                false
            );
            player.receiveDamage(damage);
            System.out.println(Color.colorize("Enemy deals " + damage + " damage!", Color.RED));
        }
    }

    private int calculateDamage(int baseDamage, boolean isPlayer) {
        double totalDamage = baseDamage;
        
        if (isPlayer) {
            // Apply combo system for player
            totalDamage *= (1 + (comboCounter * COMBO_MULTIPLIER));
            
            // Critical hit system
            if (random.nextInt(100) < getCriticalChance()) {
                totalDamage *= 2;
                System.out.println(Color.colorize("Critical Hit!", Color.YELLOW));
            }
        }
        
        // Apply defense reduction
        if (isPlayer) {
            totalDamage = Math.max(1, totalDamage - enemy.getDefense());
        } else {
            totalDamage = Math.max(1, totalDamage - player.getDefense());
        }
        
        return (int) Math.round(totalDamage);
    }

    private int getCriticalChance() {
        // Base 15% crit chance
        int critChance = 15;
        
        // Bonus crit chance for certain classes
        if (player instanceof Tester) {
            critChance += 10;
        } else if (player instanceof PenTester) {
            critChance += 15;
        }
        
        return critChance;
    }

    public void resetCombo() {
        comboCounter = 0;
    }
}