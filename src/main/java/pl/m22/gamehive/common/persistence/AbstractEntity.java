package pl.m22.gamehive.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.io.Serializable;
import java.time.Instant;

@MappedSuperclass
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public abstract class AbstractEntity<I extends Serializable> {

    @Column(updatable = false)
    private Instant createdAt;
    private Instant updatedAt;

    public abstract I getId();

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy hp
                ? hp.getHibernateLazyInitializer().getPersistentClass().hashCode()
                : getClass().hashCode();
    }

    @Override
    public final boolean equals(Object obj) {

        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }

        Class<?> thisClass = this instanceof HibernateProxy hp
                ? hp.getHibernateLazyInitializer().getPersistentClass()
                : getClass();
        Class<?> thatClass = obj instanceof HibernateProxy hp
                ? hp.getHibernateLazyInitializer().getPersistentClass()
                : obj.getClass();

        if (!thisClass.equals(thatClass)) {
            return false;
        }

        AbstractEntity<?> that = (AbstractEntity<?>) obj;

        return getId() != null && getId().equals(that.getId());
    }
}
