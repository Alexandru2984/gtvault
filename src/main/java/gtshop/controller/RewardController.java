package gtshop.controller;

import gtshop.service.CatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rewards")
@Tag(name = "Rewards", description = "Catalogul public de recompense")
public class RewardController {
    private final CatalogService catalog;

    public RewardController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    @Operation(summary = "Catalog: q (căutare), category, sort = featured | price_asc | price_desc | new | popular, inStock")
    public Map<String, Object> list(@RequestParam(required = false) String q,
                                    @RequestParam(required = false) String search,
                                    @RequestParam(required = false) String category,
                                    @RequestParam(defaultValue = "featured") String sort,
                                    @RequestParam(defaultValue = "false") boolean inStock) {
        return catalog.list(q != null ? q : search, category, sort, inStock);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detaliile unui produs și produse similare")
    public Map<String, Object> get(@PathVariable String id) {
        return catalog.get(id);
    }
}
