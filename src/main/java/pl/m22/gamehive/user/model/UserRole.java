package pl.m22.gamehive.user.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pl.m22.gamehive.common.AbstractEntity;

@Entity
@Table(name = "user_role")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UserRole extends AbstractEntity {


    @NotBlank
    private String name;
    private String description;
}
