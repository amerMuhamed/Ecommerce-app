package com.spring.eCommerce.config;

import com.spring.eCommerce.entity.Category;
import com.spring.eCommerce.entity.Image;
import com.spring.eCommerce.entity.Product;
import com.spring.eCommerce.repository.CategoryRepo;
import com.spring.eCommerce.repository.ProductRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Seeds demo categories and products for the Lulumi storefront.
 * Idempotent: only missing records (matched by stable name) are created,
 * so restarts never duplicate data and existing production data is preserved.
 * Disable with {@code lulumi.seed.enabled=false}.
 */
@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lulumi.seed.enabled", havingValue = "true", matchIfMissing = true)
public class LulumiSeedRunner implements CommandLineRunner {

    private static final String IMG = "?auto=format&fit=crop&w=800&q=80";

    private final CategoryRepo categoryRepo;
    private final ProductRepo productRepo;

    @Override
    @Transactional
    public void run(String... args) {
        Map<String, List<SeedProduct>> catalog = catalog();
        catalog.forEach((categoryName, products) -> {
            Category category = categoryRepo.findByName(categoryName);
            if (category == null) {
                category = categoryRepo.save(Category.builder().name(categoryName).build());
                log.info("Seeded category '{}'", categoryName);
            }
            for (SeedProduct seed : products) {
                if (productRepo.findByName(seed.name()) != null) {
                    continue;
                }
                Product product = Product.builder()
                        .name(seed.name())
                        .description(seed.description())
                        .price(seed.price())
                        .availableQuantity(seed.stock())
                        .categories(new ArrayList<>(List.of(category)))
                        .images(new ArrayList<>(List.of(
                                Image.builder().imageUrl(seed.imageUrl()).build())))
                        .build();
                productRepo.save(product);
                log.info("Seeded product '{}'", seed.name());
            }
        });
    }

    private record SeedProduct(String name, String description, BigDecimal price, int stock, String imageUrl) {
    }

    private Map<String, List<SeedProduct>> catalog() {
        Map<String, List<SeedProduct>> catalog = new LinkedHashMap<>();
        catalog.put("Baby Clothing", List.of(
                p("Organic Cotton Baby Bodysuit",
                        "Buttery-soft organic cotton bodysuit with snap buttons for easy changes. Gentle on newborn skin.",
                        "249.00", 60, "https://images.unsplash.com/photo-1522771930-78848d9293e8" + IMG),
                p("Soft Knit Newborn Cardigan",
                        "Cozy knit cardigan for chilly evenings. Breathable, stretchy and easy to layer.",
                        "399.00", 35, "https://images.unsplash.com/photo-1519689680058-324335c77eba" + IMG),
                p("Baby Sleep Sleepsuit Set",
                        "Two-pack zip-up sleepsuits in calming cream tones. Two-way zipper for night changes.",
                        "549.00", 40, "https://images.unsplash.com/photo-1566004100631-35d015d6a491" + IMG)));
        catalog.put("Diapers & Changing", List.of(
                p("Portable Diaper Changing Mat",
                        "Foldable waterproof changing mat with storage pockets. Perfect for on-the-go parents.",
                        "329.00", 50, "https://images.unsplash.com/photo-1584467735871-8e85353a8413" + IMG),
                p("Gentle Baby Diaper Cream",
                        "Fragrance-free barrier cream with zinc and calendula to soothe delicate skin.",
                        "189.00", 80, "https://images.unsplash.com/photo-1556228720-195a672e8a03" + IMG)));
        catalog.put("Feeding & Nursing", List.of(
                p("Silicone Feeding Bowl Set",
                        "Suction-base silicone bowls with soft-tip spoons. BPA-free and dishwasher safe.",
                        "349.00", 55, "https://images.unsplash.com/photo-1584473457409-ce5a0d0b1a5a" + IMG),
                p("Anti-Colic Baby Bottle Duo",
                        "Two anti-colic bottles with slow-flow nipples for comfortable, happy feeding.",
                        "429.00", 45, "https://images.unsplash.com/photo-1604467794349-0b74285de7e7" + IMG),
                p("Soft Muslin Burp Cloths",
                        "Pack of four ultra-absorbent muslin burp cloths. Softer with every wash.",
                        "219.00", 70, "https://images.unsplash.com/photo-1522771930-78848d9293e8" + IMG)));
        catalog.put("Bath & Skincare", List.of(
                p("Gentle Baby Bath Wash",
                        "Tear-free daily wash with chamomile. Dermatologist tested for newborns.",
                        "199.00", 90, "https://images.unsplash.com/photo-1556228578-8c89e6adf883" + IMG),
                p("Hooded Baby Bath Towel",
                        "Plush hooded towel with cute bunny ears. Ultra-absorbent and quick-drying.",
                        "299.00", 48, "https://images.unsplash.com/photo-1584839404042-8bc21d240e91" + IMG),
                p("Baby Lotion with Shea Butter",
                        "Lightweight daily lotion that keeps baby skin soft and nourished all day.",
                        "179.00", 75, "https://images.unsplash.com/photo-1571781926291-c477ebfd024b" + IMG)));
        catalog.put("Nursery & Bedding", List.of(
                p("Soft Muslin Swaddle Blanket",
                        "Large breathable muslin swaddle in a starry Lulumi print. Gets softer with every wash.",
                        "279.00", 65, "https://images.unsplash.com/photo-1519689680058-324335c77eba" + IMG),
                p("Soft Baby Crib Sheet",
                        "Fitted crib sheet in 100% cotton sateen. Snug fit with deep elastic corners.",
                        "259.00", 42, "https://images.unsplash.com/photo-1560185127-6ed189bf02f4" + IMG),
                p("Starry Night Crib Mobile",
                        "Gentle musical mobile with soft stars and moon. Soothes babies to sleep.",
                        "499.00", 25, "https://images.unsplash.com/photo-1516627145497-ae6968895b74" + IMG)));
        catalog.put("Toys & Development", List.of(
                p("Baby Teether Ring",
                        "Soft silicone teether ring, easy for tiny hands to grip and gentle on gums.",
                        "149.00", 100, "https://images.unsplash.com/photo-1566576912321-d58ddd7a6088" + IMG),
                p("Cuddly Bunny Plush Toy",
                        "The Lulumi bunny: super-soft plush friend for cuddles, naps and playtime.",
                        "349.00", 38, "https://images.unsplash.com/photo-1559454403-b8fb88521f11" + IMG),
                p("Wooden Stacking Rainbow Toy",
                        "Colourful wooden rainbow stacker that builds motor skills and imagination.",
                        "429.00", 30, "https://images.unsplash.com/photo-1596461404969-9ae70f2830c1" + IMG)));
        catalog.put("Baby Care Accessories", List.of(
                p("Newborn Essentials Gift Set",
                        "A ready-to-gift box: bodysuit, swaddle, wash, lotion and bunny plush in Lulumi wrapping.",
                        "999.00", 20, "https://images.unsplash.com/photo-1513201099705-a9746e1e201f" + IMG),
                p("Baby Care Organizer",
                        "Compartment caddy for diapers, wipes and creams. Sturdy handles for nursery or travel.",
                        "379.00", 33, "https://images.unsplash.com/photo-1584839404042-8bc21d240e91" + IMG),
                p("Soft Baby Nail Care Kit",
                        "Gentle nail clippers, file and brush designed for tiny fingers and toes.",
                        "159.00", 85, "https://images.unsplash.com/photo-1556228720-195a672e8a03" + IMG)));
        return catalog;
    }

    private static SeedProduct p(String name, String description, String price, int stock, String imageUrl) {
        return new SeedProduct(name, description, new BigDecimal(price), stock, imageUrl);
    }
}
