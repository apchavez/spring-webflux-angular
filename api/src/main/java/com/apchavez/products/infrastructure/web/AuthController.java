package com.apchavez.products.infrastructure.web;

import com.apchavez.products.infrastructure.auth.DemoUserStore;
import com.apchavez.products.infrastructure.auth.InvalidCredentialsException;
import com.apchavez.products.infrastructure.config.JwtService;
import com.apchavez.products.infrastructure.web.dto.LoginRequestDTO;
import com.apchavez.products.infrastructure.web.dto.LoginResponseDTO;
import com.apchavez.products.infrastructure.web.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Autenticación — emite JWTs para el store de usuarios demo")
public class AuthController {

    private final DemoUserStore userStore;
    private final JwtService jwtService;

    public AuthController(DemoUserStore userStore, JwtService jwtService) {
        this.userStore = userStore;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    @Operation(summary = "Login",
            description = "Autentica un usuario demo y devuelve un JWT firmado. Credenciales demo: admin/admin123 (ADMIN), user/user123 (USER).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login exitoso",
                    content = @Content(schema = @Schema(implementation = LoginResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Error de validación en los campos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Usuario o contraseña inválidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Mono<ResponseEntity<LoginResponseDTO>> login(@Valid @RequestBody LoginRequestDTO request) {
        return Mono.fromCallable(() -> userStore.authenticate(request.username(), request.password())
                        .orElseThrow(InvalidCredentialsException::new))
                .map(roles -> ResponseEntity.ok(new LoginResponseDTO(
                        jwtService.generateToken(request.username(), roles),
                        "Bearer",
                        jwtService.getExpirationSeconds(),
                        request.username(),
                        roles)));
    }
}
