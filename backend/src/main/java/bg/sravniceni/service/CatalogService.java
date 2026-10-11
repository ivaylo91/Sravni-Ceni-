package bg.sravniceni.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bg.sravniceni.domain.Category;
import bg.sravniceni.domain.CategoryGroup;
import bg.sravniceni.domain.Product;
import bg.sravniceni.dto.CatalogDtos.CategoryDto;
import bg.sravniceni.dto.CatalogDtos.CategoryGroupDto;
import bg.sravniceni.dto.CatalogDtos.ChainDto;
import bg.sravniceni.dto.CatalogDtos.PageResult;
import bg.sravniceni.dto.CatalogDtos.ProductDetail;
import bg.sravniceni.dto.CatalogDtos.ProductOffer;
import bg.sravniceni.dto.CatalogDtos.SettlementDto;
import bg.sravniceni.dto.CatalogDtos.StatsDto;
import bg.sravniceni.repository.CategoryRepository;
import bg.sravniceni.repository.PriceQueryRepository;
import bg.sravniceni.repository.PriceQueryRepository.OfferQuery;
import bg.sravniceni.repository.PriceQueryRepository.SortOrder;
import bg.sravniceni.repository.ProductRepository;

@Service
@Transactional(readOnly = true)
public class CatalogService {

    private static final int HISTORY_DAYS = 60;

    private final PriceQueryRepository prices;
    private final CategoryRepository categories;
    private final ProductRepository products;

    public CatalogService(PriceQueryRepository prices, CategoryRepository categories, ProductRepository products) {
        this.prices = prices;
        this.categories = categories;
        this.products = products;
    }

    @Cacheable("settlements")
    public List<SettlementDto> settlements() {
        return prices.settlementsWithStores();
    }

    @Cacheable("chains")
    public List<ChainDto> chains(String settlementCode) {
        return prices.chains(settlementCode);
    }

    @Cacheable("categories")
    public List<CategoryGroupDto> categoryGroups() {
        Map<CategoryGroup, List<CategoryDto>> grouped = new LinkedHashMap<>();
        for (Category c : categories.findAllWithGroup()) {
            grouped.computeIfAbsent(c.getGroup(), g -> new ArrayList<>())
                    .add(new CategoryDto(c.getId(), c.getName()));
        }
        return grouped.entrySet().stream()
                .map(e -> new CategoryGroupDto(e.getKey().getId(), e.getKey().getName(), e.getKey().getIcon(), e.getValue()))
                .toList();
    }

    public PageResult<ProductOffer> search(String settlementCode, String text, Short categoryId, Long chainId,
                                           SortOrder sort, int page, int size) {
        if ((text == null || text.isBlank()) && categoryId == null) {
            throw new IllegalArgumentException("Въведете продукт за търсене или изберете категория");
        }
        // Взимаме един ред повече, за да знаем дали има следваща страница
        List<ProductOffer> rows = prices.searchOffers(
                new OfferQuery(settlementCode, text, categoryId, chainId, sort, size + 1, page * size));
        boolean hasMore = rows.size() > size;
        return new PageResult<>(hasMore ? rows.subList(0, size) : rows, page, size, hasMore);
    }

    public ProductDetail product(long id, String settlementCode) {
        Product p = products.findDetailedById(id)
                .orElseThrow(() -> new NotFoundException("Продуктът не е намерен"));
        return new ProductDetail(
                p.getId(), p.getName(), p.getCode(),
                p.getCategory().getId(), p.getCategory().getName(),
                p.getChain().getId(), p.getChain().getShortName(), p.getChain().getColor(),
                p.getPackageQty(), p.getPackageUnit(),
                prices.storePrices(id, settlementCode),
                prices.history(id, settlementCode, HISTORY_DAYS),
                prices.alternatives(id, settlementCode, 12));
    }

    @Cacheable("stats")
    public StatsDto stats() {
        return prices.stats();
    }
}
