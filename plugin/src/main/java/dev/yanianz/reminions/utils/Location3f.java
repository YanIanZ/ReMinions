package dev.yanianz.reminions.utils;

import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public class Location3f {
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private String worldName;

    public Location3f(double x, double y, double z, float yaw, float pitch, String worldName) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.worldName = worldName;
    }

    public Location3f(double x, double y, double z, String worldName) {
        this(x, y, z, 0.0F, 0.0F, worldName);
    }

    public Location3f(Location location) {
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
        this.yaw = location.getYaw();
        this.pitch = location.getPitch();
        this.worldName = location.getWorld() != null ? location.getWorld().getName() : null;
    }

    public Location toLocation() {
        if (this.worldName == null) return null;
        World world = Bukkit.getWorld(this.worldName);
        return world == null ? null : new Location(world, this.x, this.y, this.z, this.yaw, this.pitch);
    }

    public Location toLocation(World world) {
        return new Location(world, this.x, this.y, this.z, this.yaw, this.pitch);
    }

    public Location toLocation(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) throw new IllegalArgumentException("World '" + worldName + "' is not loaded.");
        return this.toLocation(world);
    }

    @Override
    public String toString() {
        return "Location3f{x=" + this.x + ", y=" + this.y + ", z=" + this.z
                + ", yaw=" + this.yaw + ", pitch=" + this.pitch + ", worldName='" + this.worldName + "'}";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Location3f other)) return false;
        return Double.compare(other.x, this.x) == 0
                && Double.compare(other.y, this.y) == 0
                && Double.compare(other.z, this.z) == 0
                && Float.compare(other.yaw, this.yaw) == 0
                && Float.compare(other.pitch, this.pitch) == 0
                && Objects.equals(this.worldName, other.worldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.x, this.y, this.z, this.yaw, this.pitch, this.worldName);
    }

    public World getWorld()   { return this.worldName != null ? Bukkit.getWorld(this.worldName) : null; }
    public int getBlockX()    { return (int) Math.floor(this.x); }
    public int getBlockY()    { return (int) Math.floor(this.y); }
    public int getBlockZ()    { return (int) Math.floor(this.z); }
    public double getX()      { return this.x; }
    public double getY()      { return this.y; }
    public double getZ()      { return this.z; }
    public float getYaw()     { return this.yaw; }
    public float getPitch()   { return this.pitch; }
    public String getWorldName(){ return this.worldName; }

    public void setX(double x)          { this.x = x; }
    public void setY(double y)          { this.y = y; }
    public void setZ(double z)          { this.z = z; }
    public void setYaw(float yaw)       { this.yaw = yaw; }
    public void setPitch(float pitch)   { this.pitch = pitch; }
    public void setWorldName(String w)  { this.worldName = w; }
}
