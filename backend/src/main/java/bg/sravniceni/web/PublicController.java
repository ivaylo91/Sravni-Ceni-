package bg.sravniceni.web;

import java.time.Duration;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import bg.sravniceni.dto.BasketDtos.BasketRequest;
import bg.sravniceni.dto.BasketDtos.BasketResult;
import bg.sravniceni.dto.CatalogDtos.CategoryGroupDto;
import bg.sravniceni.dto.CatalogDtos.ChainDto;
import bg.sravniceni.dto.CatalogDtos.PageResult;
import bg.sravniceni.dto.CatalogDtos.ProductDetail;
import bg.sravniceni.dto.CatalogDtos.ProductOffer;
import bg.sravniceni.dto.CatalogDtos.SettlementDto;
import bg.sravniceni.dto.CatalogDtos.StatsDto;
import bg.sravniceni.repository.PriceQueryRepository.SortOrder;
import bg.sravniceni.service.BasketService;
import bg.sravniceni.service.CatalogService;

/** Публичен API – достъпен без вход. */
@RestController
@RequestMapping("/api/public")
@Validated
public class PublicController {

    private static final String SETTLEMENT = "\\d{5}";
    private static final String BAD_SETTLEMENT = "Невалиден код на населено място";
    private static final CacheControl CATALOG_CACHE = CacheControl.maxAge(Duration.ofHours(1)).cachePublic();

    private final CatalogService catalog;
    private final BasketService basket;

    public PublicController(CatalogService catalog, BasketService basket) {
        this.catalog = catalog;
        this.basket = basket;
    }

    @GetMapping("/stats")
    public StatsDto stats() {
        return catalog.stats();
    }

    @GetMapping("/settlements")
    public ResponseEntity<List<SettlementDto>> settlements() {
        return ResponseEntity.ok().cacheControl(CATALOG_CACHE).body(catalog.settlements());
    }

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryGroupDto>> categories() {
        return ResponseEntity.ok().cacheControl(CATALOG_CACHE).body(catalog.categoryGroups());
    }

    @GetMapping("/chains")
    public List<ChainDto> chains(
            @RequestParam(required = false) @Pattern(regexp = SETTLEMENT, message = BAD_SETTLEMENT) String settlement) {
        return catalog.chains(settlement);
    }

    /** Търсене на продукти: всяка дума от q трябва да присъства в името. */
    @GetMapping("/products")
    public PageResult<ProductOffer> search(
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) @Pattern(regexp = SETTLEMENT, message = BAD_SETTLEMENT) String settlement,
            @RequestParam(required = false) Short category,
            @RequestParam(required = false) Long chain,
            @RequestParam(defaultValue = "PRICE") SortOrder sort,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100) int page,
            @RequestParam(defaultValue = "30") @Min(1) @Max(100) int size) {
        return catalog.search(settlement, q, category, chain, sort, page, size);
    }

    @GetMapping("/products/{id}")
    public ProductDetail product(
            @PathVariable long id,
            @RequestParam(required = false) @Pattern(regexp = SETTLEMENT, message = BAD_SETTLEMENT) String settlement) {
        return catalog.product(id, settlement);
    }

    /** Най-евтина кошница – работи и за гости (списъкът идва в заявката). */
    @PostMapping("/basket")
    public BasketResult basket(@Valid @RequestBody BasketRequest request) {
        return basket.calculate(request);
    }
}
