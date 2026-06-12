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
        if (this.chance < 1.0 && this.random.nextDouble() > this.chance) return 0;
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
