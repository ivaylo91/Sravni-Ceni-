package bg.sravniceni.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bg.sravniceni.dto.BasketDtos.BasketItemRequest;
import bg.sravniceni.dto.BasketDtos.BasketLine;
import bg.sravniceni.dto.BasketDtos.BasketRequest;
import bg.sravniceni.dto.BasketDtos.BasketResult;
import bg.sravniceni.dto.BasketDtos.SplitBasket;
import bg.sravniceni.dto.BasketDtos.StoreBasket;
import bg.sravniceni.repository.PriceQueryRepository;
import bg.sravniceni.repository.PriceQueryRepository.StoreInfo;
import bg.sravniceni.repository.PriceQueryRepository.StoreMatch;

/**
 * Намира най-евтината кошница за списък за пазаруване.
 * <ol>
 *   <li>За всеки ред от списъка и всеки магазин в населеното място се избира
 *       най-евтиният съвпадащ продукт (категория + ключови думи).</li>
 *   <li>Магазините се подреждат по брой намерени продукти, после по обща сума.</li>
 *   <li>Изчислява се и „разделено“ пазаруване – всеки продукт от магазина,
 *       където е най-евтин.</li>
 * </ol>
 */
@Service
@Transactional(readOnly = true)
public class BasketService {

    private static final int TOP_STORES = 10;

    private final PriceQueryRepository prices;

    public BasketService(PriceQueryRepository prices) {
        this.prices = prices;
    }

    public BasketResult calculate(BasketRequest request) {
        List<BasketItemRequest> items = request.items();
        Set<Long> chainIds = request.chainIds();
        Map<Long, StoreInfo> stores = new LinkedHashMap<>();
        for (StoreInfo s : prices.stores(request.settlementCode(), chainIds)) {
            stores.put(s.storeId(), s);
        }

        // matches[storeId][itemIndex] = най-евтиният съвпадащ продукт
        Map<Long, StoreMatch[]> matches = new HashMap<>();
        for (int i = 0; i < items.size(); i++) {
            BasketItemRequest item = items.get(i);
            String keyword = item.keyword() == null || item.keyword().isBlank() ? null : item.keyword();
            for (StoreMatch m : prices.cheapestMatchPerStore(request.settlementCode(), item.categoryId(), keyword, chainIds)) {
                matches.computeIfAbsent(m.storeId(), k -> new StoreMatch[items.size()])[i] = m;
            }
        }

        List<StoreBasket> baskets = new ArrayList<>();
        matches.forEach((storeId, found) -> {
            StoreInfo store = stores.get(storeId);
            if (store != null) {
                baskets.add(storeBasket(store, found, items));
            }
        });
        baskets.sort(Comparator.comparingInt(StoreBasket::foundCount).reversed()
                .thenComparing(StoreBasket::total)
                .thenComparing(StoreBasket::chainName));

        List<StoreBasket> bestPerChain = new ArrayList<>();
        Set<Long> seenChains = new HashSet<>();
        for (StoreBasket b : baskets) {
            if (seenChains.add(b.chainId())) {
                bestPerChain.add(b);
            }
        }

        return new BasketResult(
                request.settlementCode(),
                items.size(),
                baskets.subList(0, Math.min(TOP_STORES, baskets.size())),
                bestPerChain,
                split(matches, stores, items),
                savings(bestPerChain));
    }

    private static StoreBasket storeBasket(StoreInfo store, StoreMatch[] found, List<BasketItemRequest> items) {
        List<BasketLine> lines = new ArrayList<>();
        List<Integer> missing = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < found.length; i++) {
            StoreMatch m = found[i];
            if (m == null) {
                missing.add(i);
                continue;
            }
            BasketLine line = line(i, m, items.get(i).quantity(), store);
            lines.add(line);
            total = total.add(line.lineTotal());
        }
        return new StoreBasket(store.storeId(), store.chainId(), store.chainName(), store.chainColor(), store.address(),
                total, lines.size(), missing, lines);
    }

    private static SplitBasket split(Map<Long, StoreMatch[]> matches, Map<Long, StoreInfo> stores,
                                     List<BasketItemRequest> items) {
        List<BasketLine> lines = new ArrayList<>();
        List<Integer> missing = new ArrayList<>();
        Set<Long> usedStores = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < items.size(); i++) {
            StoreMatch best = null;
            for (StoreMatch[] perStore : matches.values()) {
                StoreMatch m = perStore[i];
                if (m != null && stores.containsKey(m.storeId())
                        && (best == null || m.price().compareTo(best.price()) < 0)) {
                    best = m;
                }
            }
            if (best == null) {
                missing.add(i);
                continue;
            }
            BasketLine line = line(i, best, items.get(i).quantity(), stores.get(best.storeId()));
            lines.add(line);
            usedStores.add(best.storeId());
            total = total.add(line.lineTotal());
        }
        return new SplitBasket(total, usedStores.size(), missing, lines);
    }

    /** Разлика между най-скъпата и най-евтината верига с еднакво покритие на списъка. */
    private static BigDecimal savings(List<StoreBasket> bestPerChain) {
        if (bestPerChain.size() < 2) {
            return BigDecimal.ZERO;
        }
        int maxFound = bestPerChain.get(0).foundCount();
        List<StoreBasket> comparable = bestPerChain.stream().filter(b -> b.foundCount() == maxFound).toList();
        if (comparable.size() < 2) {
            return BigDecimal.ZERO;
        }
        return comparable.get(comparable.size() - 1).total().subtract(comparable.get(0).total());
    }

    private static BasketLine line(int index, StoreMatch m, int quantity, StoreInfo store) {
        return new BasketLine(index, m.productId(), m.productName(), m.price(), m.promo(), quantity,
                m.price().multiply(BigDecimal.valueOf(quantity)), store.storeId(), store.chainName(), store.address());
    }
}
