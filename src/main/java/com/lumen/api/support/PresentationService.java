package com.lumen.api.support;

import com.lumen.api.model.Order;
import com.lumen.api.model.Product;
import org.springframework.stereotype.Service;

import static com.lumen.api.support.BrandTokens.COLOR_AMBER;
import static com.lumen.api.support.BrandTokens.COLOR_CHARCOAL;
import static com.lumen.api.support.BrandTokens.COLOR_CREAM;
import static com.lumen.api.support.BrandTokens.FONT_BODY;
import static com.lumen.api.support.BrandTokens.FONT_HEADING;

/**
 * Builds the brand-styled {@code presentation} fragments embedded in product and
 * order responses (see {@code Prefer: brand=inline|none} negotiation).
 */
@Service
public class PresentationService {

    public Presentation forProduct(Product p) {
        String html = """
                <div style="font-family:%s;background:%s;border:1px solid %s;border-radius:12px;padding:20px;max-width:420px;">
                  <div style="font-family:%s;color:%s;font-size:12px;letter-spacing:.08em;text-transform:uppercase;">Lumen Coffee Roasters</div>
                  <h2 style="font-family:%s;color:%s;margin:6px 0 4px;">%s</h2>
                  <div style="color:%s;font-size:13px;margin-bottom:10px;">%s &middot; %s roast</div>
                  <p style="color:%s;font-size:14px;line-height:1.5;margin:0 0 12px;">%s</p>
                  <div style="font-family:%s;color:%s;font-size:20px;font-weight:bold;">$%s</div>
                  <div style="color:%s;font-size:12px;margin-top:4px;">%s</div>
                </div>
                """.formatted(
                FONT_BODY, COLOR_CREAM, COLOR_AMBER,
                FONT_BODY, COLOR_AMBER,
                FONT_HEADING, COLOR_CHARCOAL, escape(p.getName()),
                COLOR_CHARCOAL, escape(p.getOrigin()), escape(p.getRoastLevel()),
                COLOR_CHARCOAL, escape(p.getDescription()),
                FONT_HEADING, COLOR_AMBER, p.getPrice(),
                COLOR_CHARCOAL, p.isInStock() ? "In stock" : "Currently sold out"
        );

        String markdown = """
                **Lumen Coffee Roasters**

                ## %s
                *%s &middot; %s roast*

                %s

                **$%s** — %s
                """.formatted(p.getName(), p.getOrigin(), p.getRoastLevel(), p.getDescription(),
                p.getPrice(), p.isInStock() ? "in stock" : "currently sold out");

        return new Presentation(html, markdown);
    }

    public Presentation forOrder(Order order, String headline) {
        String html = """
                <div style="font-family:%s;background:%s;border:1px solid %s;border-radius:12px;padding:20px;max-width:420px;">
                  <div style="font-family:%s;color:%s;font-size:12px;letter-spacing:.08em;text-transform:uppercase;">Lumen Coffee Roasters</div>
                  <h2 style="font-family:%s;color:%s;margin:6px 0 4px;">%s</h2>
                  <p style="color:%s;font-size:14px;line-height:1.5;margin:0 0 8px;">
                    Order #%d &mdash; %d &times; %s
                  </p>
                  <div style="font-family:%s;color:%s;font-size:20px;font-weight:bold;">$%s</div>
                  <div style="color:%s;font-size:12px;margin-top:6px;text-transform:uppercase;letter-spacing:.05em;">Status: %s</div>
                </div>
                """.formatted(
                FONT_BODY, COLOR_CREAM, COLOR_AMBER,
                FONT_BODY, COLOR_AMBER,
                FONT_HEADING, COLOR_CHARCOAL, escape(headline),
                COLOR_CHARCOAL, order.getId(), order.getQuantity(), escape(order.getProductName()),
                FONT_HEADING, COLOR_AMBER, order.getTotalPrice(),
                COLOR_CHARCOAL, order.getStatus()
        );

        String markdown = """
                **Lumen Coffee Roasters**

                ## %s
                Order #%d — %d &times; %s

                **$%s**
                Status: **%s**
                """.formatted(headline, order.getId(), order.getQuantity(), order.getProductName(),
                order.getTotalPrice(), order.getStatus());

        return new Presentation(html, markdown);
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
