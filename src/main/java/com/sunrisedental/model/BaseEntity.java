package com.sunrisedental.model;

import java.time.LocalDateTime;

/**
 * Common state of persisted entities.
 *
 * <p>Equality is identity-by-primary-key: two instances of the same class are equal when they have
 * the same non-null id. Unsaved instances (id {@code null}) are only equal to themselves.
 * {@code hashCode} is deliberately constant per class so the hash of an entity does not change when
 * it receives its id on insert (otherwise it would get lost inside hash-based collections).
 */
public abstract class BaseEntity {

    private Long id;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BaseEntity other = (BaseEntity) o;
        return id != null && id.equals(other.id);
    }

    @Override
    public final int hashCode() {
        return getClass().hashCode();
    }
}
