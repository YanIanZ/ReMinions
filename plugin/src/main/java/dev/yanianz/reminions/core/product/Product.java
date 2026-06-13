package dev.yanianz.reminions.core.product;

import java.util.Random;
import dev.yanianz.reminions.config.SourceExpConfig;
import org.bukkit.inventory.ItemStack;

public abstract class Product {
    private final String id;
    protected final double price;
    private final int amountStart;
    private final int amountEnd;
    protected final double chance;
    private final String requiredProduct;
    private final int requiredAmount;
    private final SourceExpConfig expConfig;
    protected final Random random = new Random();

    public Product(String id, double price, int amountStart, int amountEnd,
                   double chance, String requiredProduct, int requiredAmount, SourceExpConfig expConfig) {
        this.id = id;
        this.price = price;
        this.amountStart = amountStart;
        this.amountEnd = amountEnd;
        this.chance = chance;
        this.requiredProduct = requiredProduct;
        this.requiredAmount = requiredAmount;
        this.expConfig = expConfig;
    }

    public int getAmount() {
        return this.getAmount(0.0);
    }

    /**
     * Like {@link #getAmount()} but with a LUCK multiplier baked into the chance roll.
     * A {@code luckMultiplier} of {@code 0.5} treats this drop as 1.5× more likely
     * (effective chance = {@code chance * (1 + luck)}, clamped to {@code [0, 1]}).
     * Negative values are clamped to {@code 0}.
     */
    public int getAmount(double luckMultiplier) {
        double effChance = this.chance * (1.0 + Math.max(0.0, luckMultiplier));
        if (effChance > 1.0) effChance = 1.0;
        if (effChance < 1.0 && this.random.nextDouble() > effChance) return 0;
        return this.random.nextInt(this.amountEnd - this.amountStart + 1) + this.amountStart;
    }

    public double getAverageAmount() {
        return (this.amountStart + this.amountEnd) / 2.0;
    }

    public double getExpectedAmount() {
        return this.chance * this.getAverageAmount();
    }

    public int getDeterministicAmount() {
        return Math.max(0, (int) Math.floor(this.getExpectedAmount()));
    }

    public abstract ItemStack buildItem();

    public abstract boolean matches(ItemStack stack);

    public String getId()               { return this.id; }
    public double getPrice()            { return this.price; }
    public double getChance()           { return this.chance; }
    public String getRequiredProduct()  { return this.requiredProduct; }
    public int getRequiredAmount()      { return this.requiredAmount; }
    public SourceExpConfig getExpConfig(){ return this.expConfig; }
}
