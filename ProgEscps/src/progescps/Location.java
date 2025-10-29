/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package progescps;

import java.io.Serializable;
import java.util.ArrayList;

public class Location implements Serializable {
    private static final long serialVersionUID = 1L;
    public String name;
    public String description;
    public int dangerLevel;
    public boolean hasTown;
    public String[] enemyPool;
    public ArrayList<String> features;
    public String environmentalEffect;

    public void setEnvironmentalEffect(String effect) {
        this.environmentalEffect = effect;
    }

    public Location(String name, String description, int dangerLevel, boolean hasTown, String[] enemyPool) {
        this.name = name;
        this.description = description;
        this.dangerLevel = Math.max(1, Math.min(5, dangerLevel));
        this.hasTown = hasTown;
        this.enemyPool = enemyPool != null ? enemyPool : new String[]{"Bandit"};
    this.features = new ArrayList<>();
    }

    public void addFeature(String feature) {
        features.add(feature);
    }
}