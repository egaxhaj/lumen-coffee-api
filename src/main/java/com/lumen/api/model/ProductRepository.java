package com.lumen.api.model;

import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory product catalog, seeded at startup. No database.
 */
@Repository
public class ProductRepository {

    private final Map<Long, Product> products = new LinkedHashMap<>();

    public ProductRepository() {
        seed(1L, "Yirgacheffe Sunrise", "Ethiopia", "Light", "Floral and citrusy, with a jasmine-tea finish. Our brightest single-origin.", "18.50", true);
        seed(2L, "Huila Reserve", "Colombia", "Medium", "Caramel-sweet with a soft red-apple acidity. The house crowd-pleaser.", "17.00", true);
        seed(3L, "Sumatra Night Roast", "Indonesia", "Dark", "Earthy and heavy-bodied, cedar and dark chocolate. For espresso lovers.", "16.50", true);
        seed(4L, "Kona Gold", "Hawaii, USA", "Medium", "Smooth, nutty, low acidity. Small lot, roasted in tiny batches.", "34.00", true);
        seed(5L, "Guatemala Antigua", "Guatemala", "Medium-Dark", "Cocoa and toasted almond with a gentle smokiness.", "17.75", true);
        seed(6L, "Decaf Honduras", "Honduras", "Medium", "Swiss-water processed. Brown sugar and toasted pecan, all the flavor, none of the buzz.", "17.00", false);
    }

    private void seed(Long id, String name, String origin, String roast, String description, String price, boolean inStock) {
        products.put(id, new Product(id, name, origin, roast, description, new BigDecimal(price), inStock));
    }

    public List<Product> findAll() {
        return List.copyOf(products.values());
    }

    public Optional<Product> findById(Long id) {
        return Optional.ofNullable(products.get(id));
    }
}
