package com.sunrisedental.model;

import java.math.BigDecimal;

/** An entry of the clinic's price list ({@code treatments} table). */
public final class Treatment extends BaseEntity {

    private String code;
    private String name;
    private String description;
    private int durationMinutes;
    private BigDecimal price;
    private boolean active = true;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    @Override
    public String toString() {
        return "Treatment{id=" + getId() + ", code='" + code + "', price=" + price + '}';
    }
}
