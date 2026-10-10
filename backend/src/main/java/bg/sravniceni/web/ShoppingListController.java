package bg.sravniceni.web;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import bg.sravniceni.dto.ListDtos.MeDto;
import bg.sravniceni.dto.ListDtos.ShoppingListDto;
import bg.sravniceni.dto.ListDtos.ShoppingListRequest;
import bg.sravniceni.service.ShoppingListService;

/** Списъци за пазаруване на влезлия потребител. */
@RestController
@RequestMapping("/api")
public class ShoppingListController {

    private final ShoppingListService service;

    public ShoppingListController(ShoppingListService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public MeDto me(JwtAuthenticationToken auth) {
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return new MeDto(auth.getToken().getSubject(), auth.getToken().getClaimAsString("email"), admin);
    }

    @GetMapping("/lists")
    public List<ShoppingListDto> lists(@AuthenticationPrincipal Jwt jwt) {
        return service.findAll(userId(jwt));
    }

    @GetMapping("/lists/{id}")
    public ShoppingListDto get(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return service.get(userId(jwt), id);
    }

    @PostMapping("/lists")
    @ResponseStatus(HttpStatus.CREATED)
    public ShoppingListDto create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ShoppingListRequest request) {
        return service.create(userId(jwt), request);
    }

    @PutMapping("/lists/{id}")
    public ShoppingListDto update(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                  @Valid @RequestBody ShoppingListRequest request) {
        return service.update(userId(jwt), id, request);
    }

    @DeleteMapping("/lists/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        service.delete(userId(jwt), id);
    }

    /** sub в токена на Supabase е id на потребителя (auth.users.id). */
    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
