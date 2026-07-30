package pl.m22.gamehive.game.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Żądanie odrzucenia zgłoszenia gry. Powód jest widoczny dla autora zgłoszenia.")
public record RejectGameRequestDto(

        @Schema(description = "Powód odrzucenia (wymagany, niepusty).", example = "Duplikat istniejącej gry",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String reason
) {
}
